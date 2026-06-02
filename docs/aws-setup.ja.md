# AWS セットアップガイド

このガイドは、`starj` を AWS `ap-northeast-3` にデプロイするための作業メモです。
現時点では外部公開しないため、ALB、ACM、Route 53 を使った公開エンドポイント構成はまだ作りません。

## 方針

- アプリは ECS Fargate のプライベートサービスとして動かす。
- ECS タスクに public IP は付けない。
- 動作確認は SSM Session Manager で EC2 に入り、VPC 内から行う。
- ECR、Aurora、ElastiCache、S3 は既存リソースを使う。
- CodePipeline は、手動デプロイが一度成功してから追加する。

将来外部公開する場合だけ、ALB、ACM 証明書、DNS 設定を追加します。

## 確認済みの現状

AWS CLI で `ap-northeast-3` の状態を読み取り専用で確認しました。

| 項目 | 状態 |
| --- | --- |
| VPC | `10.0.0.0/16` の専用 VPC あり |
| public subnet | 2つあり。IGW 付き route table に関連付け済み |
| private subnet | 4つあり |
| NAT Gateway | なし |
| VPC Endpoint | S3 Gateway、SSM、SSM Messages、EC2 Messages あり |
| EC2 | private subnet 内で稼働。SSM Online |
| Aurora MySQL | available、private、暗号化あり |
| ElastiCache | Serverless Valkey available |
| S3 | bucket 作成済み。public access block 有効、AES256 暗号化あり |
| ECR | `turtton/starj` repository 作成済み。image は未 push |
| ECS | 未作成 |
| ALB / ACM / Route 53 | 未作成。現時点では不要 |
| CodeBuild / CodePipeline | 未作成 |
| SSM Parameter | 未作成 |
| Secrets Manager | RDS 管理 secret のみ確認済み |

## 先に解消する課題

### VPC Endpoint が不足している

NAT Gateway がないため、private subnet の ECS タスクが AWS サービスへ出るには VPC Endpoint が必要です。
現状は S3 と SSM 系はありますが、ECS タスク起動とログ出力に必要な endpoint が不足しています。

追加候補:

```text
com.amazonaws.ap-northeast-3.ecr.api
com.amazonaws.ap-northeast-3.ecr.dkr
com.amazonaws.ap-northeast-3.logs
com.amazonaws.ap-northeast-3.secretsmanager
```

Secrets Manager を使わず SSM Parameter Store に寄せる場合でも、ECS task definition の secrets 参照方式に合わせて必要な endpoint と IAM 権限を確認します。
SecureString や Secrets Manager で customer-managed KMS key を使う場合は、`kms:Decrypt` 権限と KMS endpoint の要否も確認します。

### Redis の Security Group が想定と違う

`prod-redis` Security Group には app Security Group から `tcp/6379` を許可するルールがあります。
ただし、確認時点の `starj-redis` は default Security Group に紐づいていました。

ECS から Redis に接続するには、ElastiCache Serverless 側の Security Group を app Security Group から到達可能なものに変更します。

目標:

```text
ECS app SG -> Redis SG : tcp/6379
```

### ECR に image がない

ECR repository `turtton/starj` はありますが、image はまだありません。
ECS service を作る前に、少なくとも1つ image を push します。

### S3 設定が MinIO 前提

現在のアプリの `S3Config.kt` は、MinIO 向けに `endpointOverride`、static credentials、path-style access を使う構成です。
AWS 本番では ECS task role で S3 にアクセスする形に寄せるのが安全です。

対応方針:

- local profile は MinIO 用設定を維持する。
- production profile は AWS SDK の default credentials chain を使う。
- production では S3 endpoint override と static credentials を使わない。
- S3 bucket は public にせず、ECS task role に必要最小限の権限を付ける。

## 手順 1: アプリ用 DB を用意する

Aurora の RDS 管理 master secret をアプリへ直接渡すのは避けます。
アプリ用 database/schema と app user を作成し、その接続情報を Secrets Manager または SSM Parameter Store に登録します。

必要な値:

```text
SPRING_DATASOURCE_URL=jdbc:mysql://<aurora-cluster-endpoint>:3306/starj
SPRING_DATASOURCE_USERNAME=<app-db-user>
SPRING_DATASOURCE_PASSWORD=<app-db-password>
```

`starj` database/schema が存在しない場合は、ECS 起動前に作成します。
Flyway はアプリ起動時に走るため、初回 ECS service の desired count は `1` にします。

### 手順 1 補足: SSM ポートフォワード経由で DB 作成する

Aurora が private のため、ローカル端末から直接 `mysql` 接続はできません。
SSM ポートフォワードで EC2 を踏み台にして、ローカルの `mysql` クライアントから作業できます。

```bash
aws ssm start-session \
  --target <ec2-instance-id> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<aurora-cluster-endpoint>"],"portNumber":["3306"],"localPortNumber":["13306"]}' \
  --region ap-northeast-3
```

別ターミナルで接続:

```bash
mysql -h 127.0.0.1 -P 13306 -u <master-user> -p
```

作成例:

```sql
CREATE DATABASE IF NOT EXISTS starj CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'starj_app'@'%' IDENTIFIED BY '<strong-random-password>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
  ON starj.* TO 'starj_app'@'%';
FLUSH PRIVILEGES;
```

この作業で作成した `starj_app` の接続情報を、Secrets Manager または SSM Parameter Store に登録して ECS task definition から参照します。

## 手順 2: 設定値と secret を整理する

ECS task definition に渡す値を、機密値と非機密値に分けます。

非機密値の例:

```text
SPRING_DATASOURCE_URL
REDIS_HOST
REDIS_PORT
STORAGE_S3_REGION
STORAGE_S3_BUCKET
```

機密値の例:

```text
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
```

Redis に auth token を設定する場合は、それも secret として扱います。
ElastiCache Serverless Valkey は TLS が必要になる可能性があるため、実際の設定に合わせて `SPRING_DATA_REDIS_SSL_ENABLED=true` と Redis password/auth token の要否を確認します。

## 手順 3: IAM role を分けて作る

EC2 用の既存 role は ECS task に流用しません。
ECS 用に role を分けます。

### ECS task execution role

ECS エージェントが使う role です。

必要な権限:

- ECR image pull
- CloudWatch Logs への出力
- task definition の secrets で参照する Secrets Manager / SSM Parameter の読み取り
- customer-managed KMS key を使う場合の `kms:Decrypt`

### ECS task role

アプリケーションコンテナが使う role です。

必要な権限:

- 対象 S3 bucket への `GetObject`
- 対象 S3 bucket への `PutObject`
- 対象 S3 bucket への `DeleteObject`
- 必要な場合のみ `ListBucket`

DB と Redis は IAM ではなく、Security Group と接続情報で制御します。

## 手順 4: VPC Endpoint を追加する

NAT Gateway を使わない前提では、ECS タスクが ECR、CloudWatch Logs、Secrets Manager に到達できるようにします。

追加する endpoint の Security Group は、ECS app Security Group から `tcp/443` を許可します。

目標:

```text
ECS app SG -> VPC endpoint SG : tcp/443
```

S3 Gateway endpoint は既にあるため、route table の関連付けが ECS 用 private subnet に含まれているか確認します。

## 手順 5: ECR に初回 image を push する

ECS service 作成前に image を push します。

```bash
AWS_REGION=ap-northeast-3
ACCOUNT_ID=<account-id>
ECR_REGISTRY=${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com
ECR_URI=${ECR_REGISTRY}/turtton/starj

aws ecr get-login-password --region ${AWS_REGION} \
  | docker login --username AWS --password-stdin ${ECR_REGISTRY}

docker buildx build --platform linux/amd64 \
  -t ${ECR_URI}:initial \
  --push .
```

ローカル端末や EC2 が arm64 の場合に備えて、まずは `linux/amd64` に固定します。
ECS 側で ARM64 を使う場合は、task definition の runtime platform と image architecture を合わせます。

private subnet 内の EC2 からこの build を行う場合、NAT Gateway がないと Docker Hub の base image、Gradle 依存、pnpm 依存を取得できず失敗します。
初回 push はインターネットへ到達できるローカル環境か、VPC 外の CodeBuild で行うのが簡単です。

## 手順 6: ECS を private service として作る

作成するもの:

- ECS cluster
- CloudWatch log group
- ECS task definition
- ECS service

task definition の要点:

```text
launch type: Fargate
container image: ECR image URI
container port: 8080
log driver: awslogs
execution role: ECS task execution role
task role: ECS task role
```

network configuration の要点:

```text
subnets: private subnet
assignPublicIp: DISABLED
securityGroups: app Security Group
```

現時点では外部公開しないため、ALB には紐づけません。
ECS service は private subnet 内で起動し、VPC 内からだけ確認します。

## 手順 7: Security Group の通信経路を確認する

必要な通信経路:

```text
ECS app SG -> Aurora SG : tcp/3306
ECS app SG -> Redis SG : tcp/6379
ECS app SG -> VPC endpoint SG : tcp/443
EC2 verifier SG -> ECS app SG : tcp/8080
```

外部公開しないため、以下は現時点では不要です。

```text
Internet -> ALB : tcp/443
ALB SG -> ECS app SG : tcp/8080
```

外部非公開を保つため、ECS app SG に `0.0.0.0/0` からの ingress は追加しません。

## 手順 8: VPC 内から動作確認する

EC2 は SSM Online なので、Session Manager で EC2 に入り、VPC 内から ECS タスクへアクセスして確認します。

確認項目:

```bash
curl http://<ecs-task-private-ip>:8080/health
```

期待値:

```text
OK
```

次に CloudWatch Logs で Spring Boot の起動ログを確認します。

ALB を使わない間は、ECS service が HTTP target health を見ません。
必要なら task definition の container health check で `localhost:8080/health` を確認します。
runtime image に `curl` は入っていないため、health check 用コマンドを追加する場合は image 側に明示的に用意します。

確認すること:

- image pull に失敗していない。
- datasource 接続に失敗していない。
- Flyway migration に失敗していない。
- Redis 接続に失敗していない。
- S3 client 初期化やアップロード処理に失敗していない。

## 手順 9: CodeBuild を作る

手動 ECS 起動が成功してから CodeBuild を作ります。

CodeBuild は Docker build を行うため、privileged mode を有効にします。
DB 接続が不要な build だけなら、CodeBuild は VPC に入れない方が簡単です。
VPC に入れると、NAT なし環境では GitHub 取得、依存取得、ECR push で詰まりやすくなります。

`buildspec.yml` の役割:

- `./gradlew build` を実行する。
- Docker image を build する。
- ECR に push する。
- `imagedefinitions.json` を出力する。

`imagedefinitions.json` の例:

```json
[
  {
    "name": "starj",
    "imageUri": "<account-id>.dkr.ecr.ap-northeast-3.amazonaws.com/turtton/starj:<tag>"
  }
]
```

`name` は ECS task definition の container name と完全に一致させます。

## 手順 10: CodePipeline を作る

CodePipeline は次の形にします。

```text
Source -> CodeBuild -> ECS Deploy
```

ECS standard deploy action は `imagedefinitions.json` を使って既存 ECS service の task definition revision を更新します。

注意点:

- 確認時点で CodeBuild / CodePipeline は未作成。
- `ap-northeast-3` では CodeConnections / CodeStar Connections の endpoint 呼び出しが失敗した。
- GitHub を Source にする場合は、`ap-northeast-3` で利用できるか先に確認する。
- 詰まる場合は、GitHub Actions から ECR push と ECS deploy を行う構成も検討する。

## 将来外部公開する場合

外部公開する段階で、次を追加します。

- ALB
- ACM 証明書
- Route 53 または外部 DNS 設定
- public subnet 上の ALB
- ALB target group
- HTTPS listener
- ALB access log / WAF

その場合の通信経路:

```text
Internet -> ALB : tcp/443
ALB SG -> ECS app SG : tcp/8080
```

target group の health check は `/health` を使います。

また、ALB で HTTPS 終端する場合は Spring Boot 側で forwarded headers を解釈できるようにします。

候補:

```properties
server.forward-headers-strategy=framework
```

セッション Cookie と CSRF を使うため、Secure Cookie、SameSite、HTTPS scheme 認識も本番公開前に確認します。

## 運用前チェックリスト

- [ ] Redis の Security Group が ECS app SG から到達可能になっている。
- [ ] Redis が TLS/auth を要求する場合、Spring の Redis SSL/password 設定が入っている。
- [ ] ECR に少なくとも1つ image がある。
- [ ] `ecr.api`、`ecr.dkr`、`logs`、`secretsmanager` endpoint がある、または NAT Gateway がある。
- [ ] VPC endpoint の Private DNS、subnet/route table 関連付け、endpoint policy が利用を妨げていない。
- [ ] ECS task execution role と ECS task role が分離されている。
- [ ] アプリ用 DB user と secret が master secret とは別に用意されている。
- [ ] S3Config が AWS 本番で task role 認証を使える。
- [ ] ECS task は private subnet で `assignPublicIp: DISABLED` になっている。
- [ ] ECS app SG に public ingress がない。
- [ ] EC2 verifier SG から ECS app SG へ `tcp/8080` が許可されている。
- [ ] CloudWatch Logs に起動ログが出ている。
- [ ] VPC 内から `/health` が `OK` を返す。
- [ ] CodePipeline を作る前に、手動 ECS デプロイが成功している。
