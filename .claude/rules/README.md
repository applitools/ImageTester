# Rules

Rules are modular instruction files that Claude loads automatically. They extend CLAUDE.md without bloating it.

- `alwaysApply: true` — loaded every session, regardless of what files are open
- `paths: [...]` — loaded only when working with files matching the glob patterns

## Available Rules

### code-quality.md
**Scope**: Always

Principles (single-responsibility, no premature abstraction, composition over inheritance), naming conventions (files, variables, functions, constants), comment guidelines, code markers (TODO/FIXME/HACK/NOTE), and file organization (import order, export patterns, function ordering).

### testing.md
**Scope**: Always

Three focused principles: test behavior not implementation, run single test files not the full suite, fix or delete flaky tests. Comprehensive test writing is handled by the `test-writer` skill.

### security.md
**Scope**: Path-scoped (`src/main/java/**/gui/**`, `frontend/src/lib/**`)

Loads when touching this repo's auth/security boundary — the GUI server (`TokenAuthFilter`, `SecretsStore`, servlets) and the frontend API client/token handling. Covers input validation, XSS prevention, token handling, secret logging, constant-time comparison, security headers.

### error-handling.md
**Scope**: Path-scoped (`src/main/java/**/gui/**`, `frontend/src/lib/**`)

Loads alongside security.md on the GUI REST/SSE code. Typed errors, no silent swallowing, consistent HTTP error shapes and status codes, no stack traces in responses.

### frontend.md
**Scope**: Path-scoped (`**/*.tsx`, `**/*.jsx`, `**/*.vue`, `**/*.svelte`, `**/*.css`, `**/*.scss`, `**/*.html`, `**/components/**`, `**/pages/**`, etc.)

Loads when touching frontend files. Design token requirements, design principles pick-list, component framework options, layout rules, accessibility (WCAG 2.1 AA), performance.

### gui.md
**Scope**: Path-scoped (`src/main/java/**/gui/**`, `src/main/java/**/lib/TestExecutor.java`)

The load-bearing GUI constraints: soft-cancel semantics (never interrupt Eyes workers or abort open tests), the `TokenAuthFilter` auth model, the single-run rule, `LogRedactor`.

### ci.md
**Scope**: Path-scoped (`.github/workflows/**`)

Hard-won workflow quirks: `shell: bash` for Maven steps, SHA-pinned actions, the secrets-in-`if:` pattern, silent Eyes-test skips, fail-fast/concurrency intent, the signing seam.

### pom.md
**Scope**: Path-scoped (`pom.xml`)

Dependency landmines: jackson-before-EyesUtilities ordering, the `libs/` file repo, the owasp no-op flag, generated `dependency-reduced-pom.xml`, release version lockstep.

## Adding Your Own

Create a new `.md` file in this directory:

```yaml
---
alwaysApply: true
---

# Your Rule Name

- Your instructions here
```

Or path-scoped:

```yaml
---
paths:
  - "src/your-area/**"
---

# Your Rule Name

- Instructions that only apply when touching these files
```

See [Claude Code docs](https://code.claude.com/docs/en/memory#path-specific-rules) for glob pattern syntax.
