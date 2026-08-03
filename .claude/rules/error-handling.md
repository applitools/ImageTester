---
paths:
  - "src/api/**"
  - "src/services/**"
  - "**/controllers/**"
  - "**/routes/**"
  - "**/handlers/**"
---

# Error Handling

- Use typed/custom error classes with error codes — not generic `Error("something went wrong")`.
- Never swallow errors silently. Log or rethrow with added context about what operation failed.
- Handle every rejected promise. No floating (unhandled) async calls.
- HTTP error responses: consistent shape (e.g., `{ error: { code, message } }`), correct status codes (400 for validation, 401 for auth, 404 for not found, 500 for unexpected).
- Never expose stack traces, internal paths, or raw database errors in production responses.
- Retry transient errors (network timeouts, rate limits) with exponential backoff. Fail fast on validation and auth errors — don't retry.
- Include correlation/request IDs in error logs when available.
