---
name: pr-review
description: Review code changes or a pull request — delegates to specialist agents for code quality, security, performance, and documentation.
argument-hint: "[PR number | staged | file path — or omit to auto-detect]"
disable-model-invocation: true
---

Review code changes by delegating to specialist agents and synthesizing a unified report. Works with PRs, staged changes, or specific files.

## Step 1: Determine Scope

Parse `$ARGUMENTS` to determine what to review:

- **PR number** (e.g., `123` or `#123`): fetch with `gh pr view $ARGUMENTS`. This is the full PR review path (includes PR quality checks in Step 2).
- **No argument**: try `gh pr view` to detect a PR for the current branch. If a PR exists, use it. If not, fall back to `git diff --cached` (staged), then `git diff` (unstaged).
- **`staged`**: review `git diff --cached`. If nothing staged, fall back to `git diff`.
- **File path**: review that specific file's current state.

If there are no changes to review, say so and stop.

## Step 2: PR Quality Check (PR path only)

Skip this step if reviewing staged changes or a file — jump to Step 3.

When reviewing a PR, fetch and check:
- PR title, description/body, author, base branch, head branch
- `gh pr diff $NUMBER` for the full diff
- `gh pr checks $NUMBER` for CI status
- `gh api repos/{owner}/{repo}/pulls/$NUMBER/comments` for review comments

Review the PR itself before the code:
- **Title**: descriptive and under 72 chars?
- **Description**: explains the *why*? Includes a test plan? Flag if empty or template-only.
- **Size**: count changed files and lines. Flag if >500 lines changed (suggest splitting).
- **Base branch**: targeting the right branch?
- **CI status**: passing, failing, or pending? If failing, note which checks — fix CI first.
- **Unresolved comments**: list open review threads with file:line and comment text.

## Step 3: Code Review (delegate to agents)

1. **Always**: delegate the diff to `@code-reviewer`
2. **If security-sensitive code changed** — auth, input handling, queries, tokens, session management, file path construction: delegate to `@security-reviewer`
3. **If performance-sensitive code changed** — endpoints, DB queries, loops over collections, caching, connection management: delegate to `@performance-reviewer`. Skip if changes are only docs, config, tests, or static assets.
4. **If documentation changed** — .md files, significant docstring/JSDoc changes, API docs: delegate to `@doc-reviewer`

Determine relevance by reading the diff content, not just file paths.

## Step 4: Synthesize Report

For PR reviews:
```
## PR Review: #[number] — [title]

**Author**: [author] | **Base**: [base] → **Head**: [head] | **Changed**: [N files, +X/-Y lines]

### PR Quality
- Title: [ok / needs improvement]
- Description: [ok / missing test plan / empty]
- Size: [ok / large — consider splitting]
- CI: [passing / failing — list failures]
- Unresolved comments: [none / list]

### Code Review
#### Critical / High
- [Agent] File:Line — issue

#### Medium
- [Agent] File:Line — issue

#### Low
- [Agent] File:Line — issue

### Verdict
[Ready to merge / Needs changes — summarize blockers]
```

For non-PR reviews (staged/file):
```
## Review Summary

**Scope**: [staged changes / file path]
**Agents run**: [list]

### Critical / High
- [Agent] File:Line — issue

### Medium / Low
- [Agent] File:Line — issue

### Passed
- [areas with no issues]
```

Deduplicate findings that overlap between agents. Attribute each finding to the agent that found it.
