---
paths:
  - "src/main/java/**/gui/**"
  - "src/main/java/**/lib/TestExecutor.java"
---

# GUI Server & Run Orchestration

- **Cancel must stay "soft"** — never interrupt Eyes worker threads, never call
  `eyes.abort()` on an open test. Interrupting mid-call hangs the sync SDK or wedges
  the universal core; aborting leaves half-created baselines. Cancel only stops
  feeding new work at page/test boundaries and abandons the session. The UI shows
  "Cancelling…" until the backend confirms — never pretend the run stopped early.
- **Never weaken `TokenAuthFilter`** — every `/api/*` request needs the session token
  and a localhost `Host` (and matching `Origin` when present). This is what stops
  other local processes and malicious web pages from driving the server.
  403 = Host/Origin, 401 = token. No dev-convenience bypasses.
- **One run at a time** — `RunController` owns the single in-flight run; a second
  start gets 409. Don't add parallel-run paths.
- **Log lines reaching the UI must pass `LogRedactor`** — don't add log or SSE paths
  that bypass it; API keys would leak to the browser.
