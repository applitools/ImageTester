# ImageTester

Java CLI + local-web GUI for visual regression testing of images/PDFs/Office docs via
the Applitools Eyes SDK. **Read `DEVELOPING.md` before non-trivial work** — it covers
building, running the GUI locally, tests, architecture, releasing, and CI debugging.

## Commands

- Build CLI jars: `mvn -DskipTests package` → `jars/` (JDK 11+)
- Run GUI: `java -jar jars/ImageTester_<version>.jar --gui` (`--gui` must be the only arg)
- Unit tests: `mvn test` — while iterating, run one class: `mvn test -Dtest=RunControllerTest`
- Frontend: `cd frontend && npm test` (vitest); after `npm run build`, copy into
  classpath with `mvn process-resources -Dskip.installnodenpm=true -Dskip.npm=true`
- Installers (JDK 17): `mvn -Pgui-installers -DskipTests -Dowasp.skip=true clean verify`

## Hard constraints

- **PII guard**: public repo that handles customer files during support work. Never
  `git add -f` customer files, never bypass `.githooks/pii-guard.sh`. Binary fixtures
  require an entry in `.github/fixture-allowlist.txt` and sanitization first
  (see CONTRIBUTING.md).
- **GUI cancel semantics**: cancel must never interrupt Eyes worker threads and never
  abort an open Eyes test (hangs the sync SDK / wedges the universal core / leaves
  half-created baselines). Cancel stops feeding work at page/test boundaries and
  abandons the session. Don't refactor this away.
- **pom.xml dependency order**: jackson must stay declared *before* EyesUtilities —
  that jar embeds a partial unrelocated jackson-core 2.16.1; wrong order =
  `NoSuchMethodError` at runtime.
- **GUI auth**: `/api/*` is token + Host/Origin gated (`TokenAuthFilter`). Never
  weaken this for dev convenience.
- **Releases**: tag base version must equal the pom `<version>`; bump pom +
  CHANGELOG.md first. See RELEASING.md.
- `libs/` is a checked-in file-based Maven repo (EyesUtilities isn't published
  anywhere public) — don't delete its jars despite the global `*.jar` gitignore.
- `docs/` and `.claude/` are gitignored; anything that must survive a fresh clone
  goes in tracked root files.
