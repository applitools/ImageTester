---
alwaysApply: true
---

# Testing

- Write tests that verify behavior, not implementation details.
- Run the specific test file after changes, not the full suite — faster feedback.
- If a test is flaky, fix or delete it. Never retry to make it pass.
- Prefer real implementations over mocks. Only mock at system boundaries (network, filesystem, clock).
- One assertion per test. If the name needs "and", split it.
- Test names describe behavior: `should return empty array when input is empty`, not `test1`.
- Arrange-Act-Assert structure. No logic (if/loops) in tests.
- Never `expect(true)` or assert a mock was called without checking its arguments.
