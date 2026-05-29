{
  description = "A basic flake with a shell";
  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
  inputs.systems.url = "github:nix-systems/default";
  inputs.flake-utils = {
    url = "github:numtide/flake-utils";
    inputs.systems.follows = "systems";
  };
  inputs.nix-vite-plus = {
    inputs.nixpkgs.follows = "nixpkgs";
    url = "github:ryoppippi/nix-vite-plus";
  };

  outputs =
    {
      nixpkgs,
      flake-utils,
      nix-vite-plus,
      ...
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        lib = pkgs.lib;
        # The nix-vite-plus package also builds a pnpm-based
        # `node_modules/vite-plus` wrapper, which currently fails on darwin
        # (ERR_PNPM_EPERM). We only need the prebuilt `vp` binary because this
        # project declares `vite-plus` as a local devDependency, so we fetch
        # just the binary using nix-vite-plus's pinned sources.json.
        vpSources = builtins.fromJSON (builtins.readFile "${nix-vite-plus}/sources.json");
        vpPlatform =
          vpSources.platforms.${system}
            or (throw "vp: unsupported system ${system}");
        vp = pkgs.stdenv.mkDerivation {
          pname = "vp";
          version = vpSources.version;
          src = pkgs.fetchurl { inherit (vpPlatform) url hash; };
          sourceRoot = "package";
          dontConfigure = true;
          dontBuild = true;
          dontStrip = true;
          nativeBuildInputs =
            [ pkgs.makeWrapper ]
            ++ lib.optionals pkgs.stdenv.hostPlatform.isLinux [ pkgs.autoPatchelfHook ];
          buildInputs = lib.optionals pkgs.stdenv.hostPlatform.isLinux [ pkgs.stdenv.cc.cc.lib ];
          installPhase = ''
            runHook preInstall
            mkdir -p $out/bin
            install -m755 vp $out/bin/vp
            wrapProgram $out/bin/vp --prefix PATH : ${lib.makeBinPath [ pkgs.nodejs_24 ]}
            runHook postInstall
          '';
          meta.mainProgram = "vp";
        };
      in
      {
        formatter = pkgs.nixfmt-tree;
        devShells.default = pkgs.mkShell {
          packages = [
            pkgs.bashInteractive
            pkgs.jdk17
            pkgs.nodejs_24
            pkgs.pnpm
            vp
          ];
        };
      }
    );
}
