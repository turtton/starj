# AWS セットアップガイド

`starj` を AWS `ap-northeast-3` にデプロイするための作業メモです。
現時点では外部公開しないため、ALB、ACM、Route 53 はまだ作りません。

## 進捗サマリー

| 手順 | 内容 | 状態 |
| --- | --- | --- |
| 1 | アプリ用 DB | 未着手 |
| 2 | Secrets Manager にアプリ設定を登録 | 未着手 |
| 3 | EC2 instance profile（IAM） | 未着手 |
| 4 | VPC Endpoint 追加 | 未着手（S3 Gateway route は確認済み） |
| 5 | CodeBuild で ECR に image push | **完了** |
| 6 | EC2 で Docker デプロイ（`scripts/deploy.sh`） | **次** |
| 7 | Security Group 通信経路 | 未着手 |
| 8 | VPC 内動作確認 | 未着手 |
| 9 | CodePipeline | 未着手（手動デプロイ成功後） |

## 方針

- アプリは **private subnet 上の EC2** で **Docker コンテナ**として動かす。
- 機密値は **Secrets Manager** に置き、起動時は [`scripts/deploy.sh`](../scripts/deploy.sh) が取得して `docker run --env-file` で渡す。
- S3 アクセスは **production profile** の instance profile（default credentials chain）で行う。
- **image の build & push は VPC 外の CodeBuild**（リポジトリ直下の [`buildspec.yml`](../buildspec.yml)）で行う。
- CodePipeline は、手動デプロイが一度成功してから追加する（`Source → CodeBuild`、デプロイは CodeDeploy または EventBridge + SSM）。
- 動作確認は SSM Session Manager で EC2 に入り、`curl http://127.0.0.1:8080/health` などで行う。

将来外部公開する場合だけ、ALB、ACM 証明書、DNS 設定を追加します。

## 前提: AWS CLI の認証

このガイドの AWS CLI コマンドは、**操作者のローカル端末**で実行します。
`aws sts get-caller-identity --region ap-northeast-3` が通る状態を先に確認してください。

## インフラの現状

AWS CLI で `ap-northeast-3` の状態を読み取り専用で確認した時点のメモです。

| 項目 | 状態 |
| --- | --- |
| VPC | `10.0.0.0/16` の専用 VPC あり |
| public subnet | 2つあり。IGW 付き route table に関連付け済み |
| private subnet | 4つあり |
| NAT Gateway | なし |
| VPC Endpoint | S3 Gateway、SSM、SSM Messages、EC2 Messages あり |
| S3 Gateway route table | 全 private subnet の route table に S3 ルートあり（`vpce-0bdf4edcff0086039`） |
| EC2 | private subnet 内で稼働。SSM Online |
| Aurora MySQL | available、private、暗号化あり |
| ElastiCache | Serverless Valkey available |
| S3 | bucket 作成済み。public access block 有効、AES256 暗号化あり |
| ECR | `turtton/starj`。**CodeBuild で image push 済み** |
| CodeBuild | プロジェクト作成済み。初回ビルド成功 |
| CodePipeline | 未作成 |
| ALB / ACM / Route 53 | 未作成。現時点では不要 |
| Secrets Manager | RDS 管理 secret のみ確認済み。アプリ用 secret は未作成 |

## デプロイ前に解消する課題

### VPC Endpoint が不足している

NAT Gateway がないため、private subnet の EC2 が ECR pull と Secrets Manager 読み取りには Interface Endpoint が必要です。

追加候補:

```text
com.amazonaws.ap-northeast-3.ecr.api
com.amazonaws.ap-northeast-3.ecr.dkr
com.amazonaws.ap-northeast-3.secretsmanager
```

CloudWatch Logs にコンテナログを送る場合のみ `logs` を追加します。

endpoint の Security Group には **EC2 app Security Group から `tcp/443`** を許可します。

### Redis の Security Group

`prod-redis` Security Group には app Security Group から `tcp/6379` を許可するルールがある想定です。
ElastiCache Serverless 側が default SG のままなら、**EC2 app SG から到達可能な SG** に変更します。

```text
EC2 app SG -> Redis SG : tcp/6379
```

### S3（production profile）

- local / 非 production: MinIO（`endpointOverride` + static credentials）
- production: AWS SDK default credentials chain（**EC2 instance profile**）

`S3Config.kt` は profile で切り替え済みです。instance profile に bucket オブジェクト権限を付与します。

## 手順 1: アプリ用 DB を用意する

Aurora の RDS 管理 master secret をアプリへ直接渡すのは避けます。
アプリ用 database / user を作成し、接続情報は手順 2 の Secrets Manager に登録します。

`starj` database が無い場合は、初回デプロイ前に作成します。Flyway はアプリ起動時に走るため、同時に複数インスタンスを立てないでください。

### SSM ポートフォワード経由で DB 作成

```bash
aws ssm start-session \
  --target <ec2-instance-id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<aurora-cluster-endpoint>"],"portNumber":["3306"],"localPortNumber":["13306"]}' \
  --region ap-northeast-3
```

別ターミナル:

```bash
mysql -h 127.0.0.1 -P 13306 -u <master-user> -p
```

```sql
CREATE DATABASE IF NOT EXISTS starj CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'starj_app'@'%' IDENTIFIED BY '<strong-random-password>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON starj.* TO 'starj_app'@'%';
FLUSH PRIVILEGES;
```

## 手順 2: Secrets Manager にアプリ設定を登録する

Secret 名の例: `starj/app/production`

JSON のキー名は Spring Boot の環境変数名と一致させます（`deploy.sh` がそのまま `--env-file` に展開します）。

```json
{
  "SPRING_DATASOURCE_URL": "jdbc:mysql://<aurora-endpoint>:3306/starj",
  "SPRING_DATASOURCE_USERNAME": "starj_app",
  "SPRING_DATASOURCE_PASSWORD": "<password>",
  "REDIS_HOST": "<valkey-endpoint>",
  "REDIS_PORT": "6379",
  "STORAGE_S3_REGION": "ap-northeast-3",
  "STORAGE_S3_BUCKET": "<bucket-name>"
}
```

Redis で TLS / auth が必要な場合は、実際の ElastiCache 設定に合わせて次を追加します。

```text
SPRING_DATA_REDIS_SSL_ENABLED=true
SPRING_DATA_REDIS_PASSWORD=<token>
```

`deploy.sh` は常に `SPRING_PROFILES_ACTIVE=production` を付与します。

RDS 管理 secret（`rds!cluster-...`）は参照しません。

## 手順 3: EC2 instance profile（IAM）

**Role 名の例:** `starj-ec2-app-role`  
**Instance profile 名の例:** `starj-ec2-app-profile`

| ポリシー | 用途 |
| --- | --- |
| `AmazonSSMManagedInstanceCore` | Session Manager |
| `starj-ec2-ecr-pull` | `docker pull` |
| `starj-ec2-s3` | 実行中コンテナの S3（production） |
| `starj-ec2-secrets` | `deploy.sh` が secret 取得 |

### Trust Policy

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": { "Service": "ec2.amazonaws.com" },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

### `starj-ec2-ecr-pull`

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "EcrAuth",
      "Effect": "Allow",
      "Action": "ecr:GetAuthorizationToken",
      "Resource": "*"
    },
    {
      "Sid": "EcrPullStarj",
      "Effect": "Allow",
      "Action": [
        "ecr:BatchCheckLayerAvailability",
        "ecr:GetDownloadUrlForLayer",
        "ecr:BatchGetImage"
      ],
      "Resource": "arn:aws:ecr:ap-northeast-3:<ACCOUNT_ID>:repository/turtton/starj"
    }
  ]
}
```

### `starj-ec2-s3`

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "StarjObjectAccess",
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::<BUCKET_NAME>/*"
    }
  ]
}
```

### `starj-ec2-secrets`

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ReadAppSecrets",
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue",
        "secretsmanager:DescribeSecret"
      ],
      "Resource": "arn:aws:secretsmanager:ap-northeast-3:<ACCOUNT_ID>:secret:starj/app/*"
    }
  ]
}
```

customer-managed KMS で暗号化している場合は `kms:Decrypt`（`kms:ViaService` = `secretsmanager.ap-northeast-3.amazonaws.com`）を追加します。

**付けないもの:** `AmazonS3FullAccess`、ECR push 権限（CodeBuild 用）、ECS 向け権限、RDS master secret への読み取り。

## 手順 4: VPC Endpoint を追加する

```text
EC2 app SG -> VPC endpoint SG : tcp/443
```

S3 は Gateway Endpoint 経由（route table 確認済み: `vpce-0bdf4edcff0086039`）。

### S3 Gateway Endpoint の route table 確認

```bash
REGION=ap-northeast-3

aws ec2 describe-vpc-endpoints --region "$REGION" \
  --filters Name=vpc-endpoint-type,Values=Gateway \
  --query 'VpcEndpoints[*].{Service:ServiceName,EndpointId:VpcEndpointId,State:State,RouteTables:RouteTableIds}' \
  --output table
```

**確認済み（2026-06）:** 全 private subnet に `pl-a4a540cd->vpce-0bdf4edcff0086039`。追加作業は不要。

## 手順 5: CodeBuild で ECR に image を push する（完了）

[`buildspec.yml`](../buildspec.yml) で test → docker build → ECR push します。

| 名前 | 値 |
| --- | --- |
| `AWS_REGION` | `ap-northeast-3` |
| `IMAGE_REPO_NAME` | `turtton/starj` |
| `CONTAINER_NAME` | `starj` |

image タグ: Git ソースはコミット SHA 先頭 7 文字。手動ビルドは `CODEBUILD_BUILD_NUMBER`。常に `:latest` も push。

`post_build` で `imagedefinitions.json` も生成します（ECS 移行時用。EC2 デプロイでは未使用）。

```bash
aws codebuild start-build --project-name <project-name> --region ap-northeast-3

aws ecr describe-images --region ap-northeast-3 \
  --repository-name turtton/starj \
  --query 'imageDetails[*].{Tags:imageTags,Pushed:imagePushedAt}' \
  --output table
```

## 手順 6: EC2 で Docker デプロイする

### EC2 ホストの前提

- Docker インストール済み
- `aws` CLI v2、`jq`、`curl`（Amazon Linux 2023 なら `dnf install -y docker jq curl` など）
- instance profile `starj-ec2-app-profile` がアタッチ済み
- [`scripts/deploy.sh`](../scripts/deploy.sh) を EC2 に配置（git clone、SCP、または CodeDeploy artifact）

```bash
chmod +x scripts/deploy.sh
```

### 手動デプロイ

1. CodeBuild で image を push し、タグ（例: `abc1234`）を確認
2. SSM で EC2 に接続
3. デプロイ実行

```bash
./scripts/deploy.sh abc1234
```

スクリプトの処理:

1. Secrets Manager から JSON を取得し、一時 env ファイル（`chmod 600`）に展開
2. `SPRING_PROFILES_ACTIVE=production` を付与
3. ECR login → `docker pull`
4. 既存コンテナ `starj` を削除 → `docker run -d -p 8080:8080 --env-file ...`
5. `http://127.0.0.1:8080/health` が `OK` になるまで待機（最大約 60 秒）

環境変数で上書き可能:

| 変数 | デフォルト |
| --- | --- |
| `AWS_REGION` | `ap-northeast-3` |
| `STARJ_SECRET_ID` | `starj/app/production` |
| `STARJ_CONTAINER_NAME` | `starj` |
| `STARJ_IMAGE_REPO` | `turtton/starj` |
| `STARJ_HOST_PORT` | `8080` |

### IAM 動作確認（EC2 上）

```bash
aws secretsmanager get-secret-value --secret-id starj/app/production --region ap-northeast-3 --query ARN

aws ecr get-login-password --region ap-northeast-3 | docker login --username AWS --password-stdin \
  $(aws sts get-caller-identity --query Account --output text).dkr.ecr.ap-northeast-3.amazonaws.com
```

## 手順 7: Security Group の通信経路

```text
EC2 app SG -> Aurora SG : tcp/3306
EC2 app SG -> Redis SG : tcp/6379
EC2 app SG -> VPC endpoint SG : tcp/443
```

外部非公開のため、EC2 app SG に `0.0.0.0/0` からの ingress は付けません。

別 EC2（検証用）からアプリを叩く場合:

```text
EC2 verifier SG -> EC2 app SG : tcp/8080
```

## 手順 8: VPC 内から動作確認する

SSM で EC2 に入り:

```bash
curl -s http://127.0.0.1:8080/health
docker logs --tail 100 starj
```

期待値: `OK`

確認項目:

- ECR pull 成功
- Secrets Manager 読み取り成功
- datasource / Flyway / Redis / S3

## 手順 9: CodePipeline（任意）

手動デプロイ成功後に追加します。

### フェーズ 1: ビルドのみ自動化

```text
Source -> CodeBuild（既存 buildspec.yml）
```

デプロイは SSM で `./scripts/deploy.sh <tag>` を手動実行。

### フェーズ 2: デプロイ自動化

ECS Deploy アクションは使いません。EC2 + Docker 向けの例:

| 方式 | パイプライン |
| --- | --- |
| CodeDeploy for EC2 | `Source -> CodeBuild -> CodeDeploy`（`appspec.yml` で `deploy.sh` 実行） |
| EventBridge + SSM | CodeBuild 成功 → SSM `AWS-RunShellScript` で `deploy.sh` |

`buildspec.yml` の artifact に `image_tag.txt` を足すと、SSM 側でタグを渡しやすくなります。

CodePipeline / CodeBuild のサービスロールに **Secrets Manager 読み取りは不要**（secret は EC2 上の `deploy.sh` が読む）。

注意: `ap-northeast-3` では CodeConnections が失敗することがある。詰まったら S3 ソースや GitHub Actions を検討。

## 将来外部公開する場合

- ALB（public subnet）+ ACM + Route 53
- `server.forward-headers-strategy=framework`
- セッション Cookie の Secure / SameSite

```text
Internet -> ALB : tcp/443
ALB SG -> EC2 app SG : tcp/8080
```

## 運用前チェックリスト

- [x] ECR に CodeBuild で image がある
- [x] CodeBuild が VPC 外・privileged で動作する
- [ ] Secrets Manager に `starj/app/production` がある
- [ ] EC2 instance profile に SSM / ECR pull / S3 / Secrets Manager がある
- [ ] `ecr.api`、`ecr.dkr`、`secretsmanager` endpoint がある
- [x] private subnet の route table に S3 Gateway ルートがある
- [ ] Redis SG が EC2 app SG から到達可能
- [ ] アプリ用 DB user が master secret とは別
- [ ] `scripts/deploy.sh` で手動デプロイが成功する
- [ ] `/health` が `OK`
- [ ] CodePipeline 追加前に手動デプロイが成功している
