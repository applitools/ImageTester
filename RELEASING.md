# Releasing ImageTester

One tag push builds and publishes everything: seven CLI jars and four GUI
installers, attached to a GitHub release marked Latest by
`.github/workflows/release.yml`.

## What a release contains

| Asset | Built by | Runner |
|---|---|---|
| `ImageTester_<version>.jar` (universal) + `_Windows` / `_Mac` / `_MacArm` / `_Linux` / `_Alpine` / `_Arm` | `build-jars` job — `mvn -DskipTests package` | ubuntu (one build produces all seven; they differ only by bundled eyes-universal-core binary) |
| `ImageTester-<version>-Windows.msi` | `build` matrix — `mvn -Pgui-installers -DskipTests -Dowasp.skip=true clean verify` | windows-latest |
| `ImageTester-<version>-macOS-AppleSilicon.dmg` | 〃 | macos-latest |
| `ImageTester-<version>-macOS-Intel.dmg` | 〃 | macos-15-intel |
| `ImageTester-<version>-Linux.deb` | 〃 | ubuntu-latest |

Release notes come from `.github/release-template.md` (`{{VERSION}}` substituted).

## Steps

1. **Bump the version** in `pom.xml` (`<version>`). The workflow refuses tags
   whose base version disagrees with the pom, so installer metadata and asset
   names always match.
2. Merge to `main` with CI green.
3. **Tag and push**:
   ```
   git tag v3.13.0
   git push origin v3.13.0
   ```
   Suffixed tags (`v3.13.0-rc1`) are allowed; the base must equal the pom version.
4. The Release workflow publishes a release marked **Latest** with all eleven
   assets plus `SHA256SUMS.txt`. The README's Download badge points at the latest
   release, so it goes live immediately — download and smoke-test an installer
   and a jar right away.

## Dry run

Run the Release workflow manually (`workflow_dispatch`): it builds every asset
as workflow artifacts but never creates a release.

## Building locally

- CLI jars: `mvn -DskipTests package` (JDK 11+) → `jars/ImageTester_<version>*.jar`.
- GUI installer for your OS: `mvn -Pgui-installers -DskipTests -Dowasp.skip=true clean verify`
  (needs JDK 17 — jpackage) → `target/installers/`.

## Signing

Installers currently ship unsigned (the release notes explain the OS warnings).
The workflow fails loudly if `WINDOWS_SIGNING_CERT` / `APPLE_SIGNING_CERT`
secrets are set before the signing steps are implemented, so a "signed" release
can never silently ship unsigned.
