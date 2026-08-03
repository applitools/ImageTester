# ImageTester Developer Guide

Everything you need to clone this repo, build it, run the GUI, fix a bug, and ship a
release. For user-facing docs (CLI flags, GUI features), see [README.md](README.md).
For fixture/PII policy, see [CONTRIBUTING.md](CONTRIBUTING.md).

- [What this is](#what-this-is)
- [One-time setup](#one-time-setup)
- [Repo map](#repo-map)
- [Building](#building)
- [Running the GUI locally](#running-the-gui-locally)
- [Running the CLI locally](#running-the-cli-locally)
- [Tests](#tests)
- [Architecture](#architecture)
- [Releasing](#releasing)
- [GitHub Actions](#github-actions)
- [Landmines and gotchas](#landmines-and-gotchas)
- [Working on this repo with Claude Code](#working-on-this-repo-with-claude-code)

## What this is

ImageTester performs visual regression testing on images, PDFs, and Office documents
using the Applitools Eyes SDK. It ships two ways from a single codebase:

- **CLI** — `java -jar ImageTester.jar -k <api-key> -f <path>`: recursive file
  discovery, one Eyes test per file/folder/PDF, results on the Applitools dashboard.
- **GUI** — a local desktop app (`--gui` flag, or the installers): a Jetty server on
  `127.0.0.1` serving a React frontend, driving the same CLI core underneath.

## One-time setup

```bash
git clone https://github.com/applitools/ImageTester.git
cd ImageTester
git config core.hooksPath .githooks   # enable the PII guard — do this first
```

**This is a public repo that handles customer files during support work.** The PII
guard (`.githooks/pii-guard.sh`) blocks customer data from being committed. CI runs
the same scan on every PR, push, and release, so the guard holds either way — the
local hook just fails faster. Read [CONTRIBUTING.md](CONTRIBUTING.md) before adding
any test fixture.

Toolchain:

| Tool | Version | Needed for |
|---|---|---|
| JDK | 11+ | Building jars, running tests |
| JDK | 17 (with `jpackage` on PATH) | Building GUI installers only |
| Maven | 3.6+ | Everything |
| Node | none required | Maven installs its own Node v20 for the frontend build. Install Node 20 yourself only if you want to run `npm` directly in `frontend/` |
| LibreOffice | any recent | Only for the multi-format test suite (`soffice` on PATH) |

An `APPLITOOLS_API_KEY` (any test account) is needed to actually run comparisons and
for the Eyes integration test suite — not for building or unit tests.

## Repo map

```
src/main/java/com/applitools/imagetester/
├── ImageTester.java       # Entry point: CLI parsing, --gui dispatch
├── Suite.java             # Recursive file discovery → tests/batches
├── TestObjects/           # ITest implementations (image, PDF, folder, ...)
├── BatchObjects/          # IBatch implementations
├── BatchMapper/           # CSV-driven test definitions (-mp)
├── gui/                   # Jetty server, REST + SSE API, run orchestration
└── lib/                   # Config, TestExecutor, EyesFactory, Logger, utils
    └── converters/        # Office/markdown/rtf → PDF via LibreOffice, watermark stamping
frontend/                  # React + TypeScript + Vite + Tailwind GUI (built into the jar)
src/test/java/
├── com/.../               # Unit tests (`mvn test`); `*IT.java` classes need -Dtest (see Tests)
├── Tests/                 # Integration suites, only run under profiles (see Tests)
└── infra/TestBase.java    # Shared test infra (excluded from direct runs)
libs/                      # Checked-in file-based Maven repo for EyesUtilities (not on any public repo)
jars/                      # Output: shaded CLI jars (gitignored)
installer/                 # Icons/assets for jpackage installers
.githooks/                 # pii-guard.sh + pre-commit hook
.github/workflows/         # CI, Release, Security, multi-format tests
TestData/                  # Test fixtures; gitignored EXCEPT deliberately force-added, allowlisted files
```

Gitignored and therefore **not present in a fresh clone**: `docs/`, the root-level
`demo/` and `debug/` scratch dirs, and most of `TestData/` (the
`src/main/java/.../demo` Java package is tracked and clones fine). Anything that
must survive a fresh clone goes in tracked root files (this file, README,
RELEASING, CONTRIBUTING) or the shared `.claude/` config (local-only overrides
belong in `.claude/settings.local.json` / `CLAUDE.local.md`).

## Building

**CLI jars** (JDK 11+):

```bash
mvn -DskipTests package
```

Produces seven jars in `jars/`: `ImageTester_<version>.jar` (universal) plus
`_Windows`, `_Mac`, `_MacArm`, `_Linux`, `_Alpine`, `_Arm`. They differ **only** by
which `eyes-universal-core` native binary is bundled — one build produces all seven.
The build also compiles the frontend (`npm ci` + `npm run build` via
`frontend-maven-plugin`, which installs its own Node) and embeds the bundle at
`/web` inside the jar.

**GUI installer for your OS** (JDK 17 — `jpackage` requirement):

```bash
mvn -Pgui-installers -DskipTests -Dowasp.skip=true clean verify
```

Output: `target/installers/ImageTester-*.{msi|dmg|deb}`. OS-specific `installer-os-*`
profiles auto-activate on the host OS and bundle the slim per-OS jar (~62 MB) instead
of the universal one (~233 MB). Windows builds an **MSI, not EXE** on purpose:
jpackage's EXE wrapper ships without a supportedOS manifest and trips compatibility
heuristics.

## Running the GUI locally

### Full app (what users get)

```bash
mvn -DskipTests package
java -jar jars/ImageTester_<version>.jar --gui
```

`--gui` must be the **only** argument. The server binds `127.0.0.1` on a random free
port, generates a per-session auth token, injects it into `index.html` (meta tag
`gui-token`), and opens your default browser.

Security model (`gui/GuiServer.java`, `gui/TokenAuthFilter.java`): every `/api/*`
request must carry the token (Bearer header or `?token=`) and a `Host` of
`localhost:<actual-port>` or `127.0.0.1:<actual-port>`; if an `Origin` header is
present it must match the same allowlist. This is what keeps other local processes
and malicious web pages from driving the server. Keep it in mind when anything
API-shaped misbehaves: 403 = wrong Host/Origin, 401 = missing/bad token.

### Backend iteration (fastest loop)

Run `com.applitools.imagetester.ImageTester` from your IDE with program argument
`--gui`. The frontend bundle must exist at `target/classes/web/` first — any of these
puts it there:

```bash
mvn -DskipTests package        # full build
mvn process-resources          # npm ci + build + copy only (no compile/shade)
```

Then just re-run from the IDE after each Java change.

### Frontend iteration

There is no live dev-server loop: `frontend/vite.config.ts` has a proxy to port 8765,
but the backend picks a random port and the auth filter rejects the Vite origin, so
`npm run dev` cannot reach a real backend today. The working loop is:

```bash
cd frontend
npm ci                # once
npm test              # vitest — fast feedback for logic/components
npm run build         # produces frontend/dist
cd ..
mvn process-resources -Dskip.installnodenpm=true -Dskip.npm=true   # copy dist → target/classes/web (~2s)
```

…then restart the backend from the IDE (or re-run `mvn -DskipTests package` and use
the jar). The two `skip` properties turn off the Node install and both `npm` steps
(`ci` + `run build`), so Maven only copies your locally built `frontend/dist`.

### GUI key classes

| Class | Role |
|---|---|
| `GuiServer` | Jetty wiring: servlets, filters, token, random port |
| `RunController` | Owns the single in-flight run; start/cancel/status |
| `RunStream` / `SseServlet` | Server-sent events pushing live test status to the UI |
| `RunRequestTranslator` | GUI form fields → the same `Config` the CLI builds |
| `SecretsStore` | API key persistence in the OS keychain (java-keyring: Credential Manager / Keychain / Secret Service) |
| `UploadStore` | Drag-and-dropped files → temp dir; cleaned by shutdown hook |
| `UpdateService` / `UpdateInstaller` | Release check on launch, one-click installer download |
| `LogRedactor` | Strips secrets before log lines reach the UI |

## Running the CLI locally

```bash
java -jar jars/ImageTester_<version>.jar -k $APPLITOOLS_API_KEY -f TestData/ -debug
```

`TestData/` contains committed, safe fixtures (images, Lorem PDFs) that exercise
folder/batch discovery. See the README for the full flag reference. `-debug` prints
verbose progress; `-log` adds Eyes SDK logs.

## Tests

Follow the repo testing rules: run the **specific test file** you're working on, not
the whole suite; behavior over implementation; if a test is flaky, fix it or delete it.

| Suite | Command | Requires |
|---|---|---|
| Java unit (includes most of `gui/`) | `mvn test` | JDK 11+ |
| Single test class | `mvn test -Dtest=RunControllerTest` | 〃 |
| Local Jetty ITs (`*IT.java`) | `mvn test -Dtest='*IT'` | 〃 — **not** run by plain `mvn test`, see below |
| Integration (everything under `Tests/`) | `mvn test -Peyes-tests` | `APPLITOOLS_API_KEY` env var; multi-format tests self-skip if LibreOffice is absent |
| Multi-format (`Tests/MultiFormatIntegrationTest`) | `mvn -Pmulti-format-tests test` | LibreOffice (`soffice --version` works) |
| Frontend unit (vitest) | `cd frontend && npm test` | Node 20 |

How the profiles work: the default surefire config **excludes** `Tests/**` (those
need network/API keys/LibreOffice), so `mvn test` is always safe to run without
credentials (the first run still needs network once, to download Node and Maven/npm
dependencies). The `eyes-tests` and `multi-format-tests` profiles invert the
includes to run only those suites. `infra/TestBase.java` is shared infrastructure,
excluded from direct runs.

**Known gap**: the `*IT.java` classes (`GuiServerBootIT`, `PreviewServletIT`,
`ShadeServicesSmokeIT`) don't match surefire's default `*Test` include patterns, so
plain `mvn test` skips them — and nothing in CI runs them either. Run them
explicitly when touching GUI server boot, previews, or the shade packaging:

```bash
mvn -DskipTests package   # ShadeServicesSmokeIT needs a built shaded jar
mvn test "-Dtest=*IT" -Dimagetester.jar=jars/ImageTester_<version>.jar
```

Without `-Dimagetester.jar`, `ShadeServicesSmokeIT` falls back to a hardcoded stale
jar path and fails. Fixing all this properly means adding `**/*IT.java` to surefire
includes (or adopting failsafe) and deriving the jar path from the pom version.

`Tests/BenchmarkTest` exists for PDF-pipeline performance work. If you benchmark:
**never compare a run that created new baselines against a run that matched existing
ones** — new-test uploads are a different code path with different timing, and the
comparison is meaningless.

## Architecture

### CLI core execution flow

```
main() → parse CLI args → Config + EyesFactory
    ↓
Suite.create(root, config, executor)
    ↓ recursive file/folder discovery
    ├─ Image file → ImageFileTest (single-step)
    ├─ PDF file → PdfFileTest (multi-step, one step per page)
    ├─ PDF + splitSteps (-st) → PDFFileBatch (each page = separate test)
    ├─ PDF + batchMapper (-mp) → BatchMappedPdfFileTest
    ├─ Office/markdown/etc. → converted to PDF first (lib/converters/)
    └─ Folder → FolderTest (all images = steps in one test)
    ↓
suite.run() → enqueues tests to TestExecutor
    ↓
TestExecutor (fixed thread pool, ThreadLocal<Eyes> — one Eyes instance per worker)
    ↓ per test: eyes.open() → eyes.check() per step → eyes.close()
    ↓
Results logged, batches closed
```

Class hierarchy: `ITest` ← `TestBase` ← { `ImageFileTest`, `FolderTest`,
`DocumentTestBase` ← { `PdfFileTest`, `BatchMappedPdfFileTest` },
`PDFFileBatch.PDFPageTest` }; `IBatch` ← `BatchBase` ← { `Batch`, `PDFFileBatch` }.

Key design points:

- **Config flow**: CLI args → `Config` (mutable POJO, public fields — yes, really) →
  `EyesFactory` (builder, immutable after build) → `TestExecutor` → `Suite`. The GUI
  reuses this exact path via `RunRequestTranslator`, so CLI and GUI behavior can't
  drift.
- **Threading**: `TestExecutor` wraps a fixed pool (`-th`, default 2 × CPU cores) with
  `ThreadLocal<Eyes>`. PDF page rendering is additionally parallelized (`-rt`) while
  keeping step order. `PDFFileBatch` synchronizes on the parent because PDFBox's
  `PDFRenderer` is not thread-safe.
- **PDF rendering**: PDFBox `renderImageWithDPI`, default 250 DPI (`-di`). JBIG2
  codec registered manually for legacy PDFs. TIFFs auto-convert to PNG.
- **Document conversion** (`lib/converters/`): a `ConversionRegistry` routes
  non-native formats (docx/xlsx/pptx via LibreOffice, md, rtf, txt) to PDF before
  testing. LibreOffice is located at runtime (`LibreOfficeLocator`); files convert
  best-effort and skips are tracked, not fatal.

### GUI backend

`GuiServer` boots embedded Jetty on `127.0.0.1:<random>`; static assets come from the
jar's `/web` resources; `/api/*` is token-gated (see
[Running the GUI locally](#running-the-gui-locally)). `RunController` allows **one run
at a time** (a second Run gets 409), translates the form into a `Config`, and executes
the same `Suite`/`TestExecutor` pipeline. Progress flows back as SSE events
(`RunStream` → `/api/events`): run-started, per-test status with thumbnail previews,
run-finished.

**Cancellation semantics — do not "simplify" these.** Cancel must never interrupt
Eyes worker threads and never abort an already-open Eyes test: interrupting mid-call
can hang the sync SDK path or wedge the universal core, and `eyes.abort()` on an open
test leaves half-created baselines. Instead, cancel stops feeding new work (takes
effect at the next page/test boundary), lets in-flight uploads settle, and abandons
the session without closing the test. The UI shows "Cancelling…" until the backend
confirms — it never pretends the run stopped early.

### Frontend

React 18 + TypeScript, Vite build, Tailwind. `App.tsx` owns run state via a reducer
fed by SSE events; `components/` maps to the visible cards (Setup, Options drawer,
Status pane, Test rows, Update banner); `lib/` holds the API client, SSE wiring,
token retrieval, and the options schema that mirrors the CLI flags. Option tabs
deep-link to the matching README sections.

### Dependencies worth knowing

| Library | Purpose |
|---|---|
| `eyes-images-java5` / `eyes-universal-core-*` | Applitools Eyes SDK + per-platform native binaries |
| `EyesUtilities` (from `libs/`) | Enterprise: download diffs/images/GIFs — **not on any public Maven repo** |
| `pdfbox` + `jbig2-imageio` + TwelveMonkeys | PDF rendering and image codecs |
| Jetty 11 (`jakarta.servlet`) | GUI server |
| `jackson-core` / `jackson-dataformat-csv` | JSON API + BatchMapper CSV — **see ordering landmine below** |
| `commons-cli` | Flag parsing |

## Releasing

Full details in [RELEASING.md](RELEASING.md); short version:

1. **Bump `<version>` in `pom.xml`**, update `CHANGELOG.md` (move Unreleased items
   under the new version) and bump version at the top of src/main/java/com/applitools/imagetester/ImageTester.java. The release workflow *refuses* tags whose base version
   disagrees with the pom; suffixed tags (`v3.17.0-rc1`) are fine.
2. Merge to `main`, wait for CI green.
3. Tag and push:
   ```bash
   git tag v3.17.0
   git push origin v3.17.0
   ```
4. The Release workflow builds seven CLI jars + four installers (Windows MSI, macOS
   Intel + Apple Silicon DMGs, Linux DEB), generates `SHA256SUMS.txt`, and publishes
   a GitHub release marked **Latest** with notes from `.github/release-template.md`.
5. Download and smoke-test at least one installer and one jar. The README download
   badge points at the latest release, so this is live immediately.

**Dry run**: trigger the Release workflow manually (`workflow_dispatch`, Actions tab
or `gh workflow run release.yml`) — it builds every asset as workflow artifacts but
never creates a release. Do this when touching the release workflow or packaging.

**Signing**: installers currently ship unsigned (release notes explain the OS
warnings). If someone sets `WINDOWS_SIGNING_CERT` / `APPLE_SIGNING_CERT` secrets, the
workflow **fails loudly** until real signing steps are implemented — a "signed"
release can never silently ship unsigned.

## GitHub Actions

| Workflow | Triggers | What it does |
|---|---|---|
| `ci.yml` | push/PR to main, manual | PII scan (parallel, doesn't gate the rest) + unit tests (JDK 11); green unit tests then fan out to Eyes integration tests (needs secret) and GUI installer builds on all three OSes (JDK 17) |
| `multi-format.yml` | push/PR to main | Installs LibreOffice, runs `-Pmulti-format-tests` |
| `security.yml` | PR, weekly (Mon 05:00 UTC), manual | PR gate: dependency-review + OSV + Trivy fail on CRITICAL/HIGH. Weekly audit: full scans reported as SARIF to the Security tab (audit jobs don't fail on findings — job status means "scan ran") |
| `release.yml` | `v*` tag, manual (= dry run) | Validate tag↔pom → build installers (4-OS matrix) + jars → publish release |

Secrets in play: `APPLITOOLS_API_KEY` (Eyes tests; skipped when absent and for fork
PRs), `WINDOWS_SIGNING_CERT` / `APPLE_SIGNING_CERT` (signing seam — see Releasing).

### Debugging a failed run

```bash
gh run list --workflow=ci.yml --limit 5      # recent runs
gh run view <run-id> --log-failed            # only the failing steps' logs
gh run rerun <run-id> --failed               # re-run just the failed jobs
gh run download <run-id> -n imagetester-installer-windows-latest  # CI artifact
gh run download <run-id> -n installer-Windows  # release.yml artifact (names differ!)
gh workflow run release.yml                  # release dry run
```

Quirks you will eventually hit (all learned the hard way — several are documented in
comments in the workflow files):

- **Windows + Maven `-D` flags**: the default `pwsh` shell splits `-Dowasp.skip=true`
  at the dot. Every Maven step forces `shell: bash` — keep it that way.
- **Secrets in `if:`**: GitHub Actions can't reference `secrets.*` in job/step `if:`
  conditions. The pattern used here is surfacing presence via `env:` (see
  `HAS_WINDOWS_SIGNING` in release.yml).
- **Actions are pinned to commit SHAs**, not tags (`actions/checkout@11d5960...`).
  Dependabot bumps them; don't hand-edit to floating tags — the infosec scanners flag it.
- **Runner labels drift**: `macos-13` (Intel) was retired mid-2026; the Intel builder
  is `macos-15-intel`. When a matrix job dies with "no runner available", check
  GitHub's runner-image deprecation announcements first.
- **Installer matrix is `fail-fast: false`** so one OS failing doesn't cancel the
  others mid-build; CI on the same ref cancels in-progress runs (`concurrency`),
  Release never cancels.
- **Eyes tests are conditional**: they don't run on fork PRs and silently skip when
  the secret is empty — a green run doesn't always mean Eyes tests ran; check the job.

## Landmines and gotchas

Things that look wrong but are load-bearing, or look safe but bite:

1. **Jackson must be declared before EyesUtilities in `pom.xml`.** The EyesUtilities
   jar embeds a *partial, unrelocated* jackson-core 2.16.1 fragment. Whichever is
   first on the classpath wins; if the fragment wins, newer jackson classes throw
   `NoSuchMethodError` at runtime. There's a pom comment guarding this — respect it
   when adding/reordering dependencies.
2. **`libs/` is a checked-in file-based Maven repo** holding EyesUtilities, which
   isn't published anywhere public. `.gitignore` excludes `*.jar` globally but
   un-ignores `libs/**`. Don't "clean up" the jars in there. To upgrade it, build
   the jar from [applitools/EyesUtilities](https://github.com/applitools/EyesUtilities),
   deploy it into the repo, bump the pom version, and commit the new `libs/` files:
   ```bash
   mvn deploy:deploy-file -Dfile=EyesUtilities-<version>.jar \
     -DgroupId=com.applitools.eyesutilities -DartifactId=EyesUtilities \
     -Dversion=<version> -Dpackaging=jar -Durl=file:./libs
   ```
3. **PII guard**: never `git add -f` a customer file, never bypass the hook. The
   fixture policy (allowlist, sanitization, embedded-image gotchas) lives in
   [CONTRIBUTING.md](CONTRIBUTING.md).
4. **GUI cancel semantics** are deliberate and fragile — see
   [GUI backend](#gui-backend). Any "why don't we just interrupt the thread" refactor
   will hang the SDK.
5. **Baseline-invalidating flags**: `-nf` (font normalization), `-tp` (trim),
   `-rwauto` (watermark removal), and resize/DPI changes all change what Eyes sees —
   existing baselines mismatch on first run. This is expected; warn users, plan
   baseline refreshes, and don't "fix" it.
6. **`-Dowasp.skip=true` is currently a no-op** — no OWASP/dependency-check plugin
   exists in the pom anymore. The flag survives in the installer command and CI as a
   historical artifact; it's harmless, but don't go hunting for the plugin.
7. **`Config` uses public fields**, not getters. Not idiomatic, but every test object
   reads it by reference. Changing this is a large refactor, not a cleanup.
8. **Customer-facing deliverables must work out of the box** — no "first install X",
   no setup steps. That's why installers bundle a runtime, the frontend build is
   inside the Maven build, and the universal jar embeds all platform binaries.
9. **Deleting the "stale" `dependency-reduced-pom.xml`** — it's a shade-plugin
   artifact, gitignored; it regenerates. Ignore it.

## Working on this repo with Claude Code

The repo has a tracked `CLAUDE.md` giving Claude the critical constraints (PII guard,
cancel semantics, jackson ordering, test commands) and pointing here. That means a
fresh `claude` session in a fresh clone already knows the landmines.

Suggestions that work well in this codebase:

- Point it at this file first for anything broad: *"Read DEVELOPING.md, then …"*
- Bug fixing: have it reproduce with a failing test before fixing —
  `mvn test -Dtest=<TheOneClass>` keeps the loop fast.
- GUI work: it can build and launch the app itself (`mvn -DskipTests package`, then
  `java -jar jars/... --gui`) and drive the browser with Playwright/DevTools tooling
  if installed.
- Release prep: it can do the pom bump + CHANGELOG move + tag commands, but **you**
  push the tag and smoke-test the artifacts.
- CI failures: paste the run URL or let it use `gh run view --log-failed`.

Things to tell it explicitly (they're in CLAUDE.md, but repetition is cheap): never
commit customer files, never weaken the GUI auth filter, never make cancel interrupt
Eyes workers, and run single test files instead of the full suite while iterating.
