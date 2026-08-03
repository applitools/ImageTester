# Contributing

For building, running the GUI locally, tests, architecture, releasing, and CI
debugging, see [DEVELOPING.md](DEVELOPING.md). This file covers the data-handling
rules every contributor must follow.

## One-time setup: PII guard

This is a public repository that regularly handles customer files during
support work. A guard blocks customer data from being committed. Enable the
local hook once per clone:

```
git config core.hooksPath .githooks
```

CI runs the same scan (`.githooks/pii-guard.sh --all`) on every PR, push, and
release, so the guard holds even without the local hook — the hook just fails
faster.

## Test fixture policy

- **Never commit customer files.** Files from support tickets (PDFs, xlsx,
  screenshots) stay outside the repo or in gitignored directories
  (`TestData/` is gitignored; tracked fixtures there were explicitly
  force-added).
- Every binary fixture must be listed in `.github/fixture-allowlist.txt`.
  Adding one is a deliberate, reviewed act: open the file first — **including
  every embedded image**, since spreadsheets and documents rasterize emails
  and names into media parts that text scans cannot see.
- If a test needs a structure only a customer file has, sanitize a copy:
  replace embedded media with generated images and scrub metadata (see
  `TestData/xlsx-header-watermark.xlsx`, a scrubbed clone of a customer file,
  and the note in `XlsxWatermarkStamperTest`).
- The email-domain allowlist lives in `.githooks/pii-guard.sh`
  (`example.com`, `applitools.com`, …). Extend it only in a reviewed change.
