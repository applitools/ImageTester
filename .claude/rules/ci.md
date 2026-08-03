---
paths:
  - ".github/workflows/**"
---

# GitHub Actions

- **Every Maven step must set `shell: bash`** — the default `pwsh` on Windows splits
  `-Dflag=value` arguments at the dot.
- **Actions are pinned to commit SHAs** — never hand-edit to floating tags; Dependabot
  bumps the pins and the infosec scanners flag tags.
- **`secrets.*` can't be referenced in job/step `if:`** — surface presence via `env:`
  instead (see `HAS_WINDOWS_SIGNING` in release.yml).
- **Eyes tests skip silently** on fork PRs and when the secret is empty — a green run
  doesn't prove they ran; check the job.
- **Installer matrix is `fail-fast: false` on purpose**; CI cancels in-progress runs
  on the same ref (`concurrency`), Release never cancels. Keep both.
- **The signing seam fails loudly by design** — if `WINDOWS_SIGNING_CERT` /
  `APPLE_SIGNING_CERT` are set, the release workflow errors until real signing steps
  exist. A "signed" release must never silently ship unsigned.
