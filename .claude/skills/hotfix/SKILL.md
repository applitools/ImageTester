---
name: hotfix
description: Emergency production fix — create hotfix branch, minimal change, critical tests only, ship fast
argument-hint: "[issue number, error message, or description of production problem]"
disable-model-invocation: true
allowed-tools:
  - Bash(git *)
  - Bash(gh *)
  - Bash(npm run test *)
  - Bash(npm run build)
  - Read
  - Glob
  - Grep
  - Edit
  - Write
---

Emergency production fix. Speed matters — make the smallest correct change, verify it works, and ship.

## Step 1: Create Hotfix Branch

- Determine the production branch (`main` or `master` — check with `git remote show origin` or `git symbolic-ref refs/remotes/origin/HEAD`)
- Stash any uncommitted work if needed
- Create and switch to `hotfix/<short-description>` branch from the production branch
- **ASK the user to confirm** the branch name before creating

## Step 2: Understand the Problem

- If `$ARGUMENTS` is a GitHub issue number: fetch it with `gh issue view`
- If it's an error message or description: search the codebase for the relevant code
- Identify the root cause — trace from symptom to source
- **Briefly state** what you found and your proposed fix to the user

## Step 3: Fix — Minimal Change Only

- Make the smallest change that correctly fixes the issue
- **Do NOT**:
  - Refactor surrounding code
  - Add new features
  - Clean up unrelated issues
  - Change formatting or style
  - Add comments beyond what's necessary to understand the fix
- If the fix requires more than ~50 lines changed, warn the user — this may not be a hotfix

## Step 4: Verify

- Run only the tests directly relevant to the changed code (not the full suite)
- Run the build to ensure it compiles
- If there's a way to reproduce the original error, verify it's fixed
- **ASK the user** if they want to run any additional verification

## Step 5: Ship

- Stage only the fix files (never stage secrets, locks, build output)
- Draft a commit message: `hotfix: <short description>` with a brief explanation
- **ASK the user to confirm** the commit message
- Push with `git push -u origin hotfix/<description>`
- Create a PR targeting the production branch:
  - Title: `[HOTFIX] <description>`
  - Body: what broke, what caused it, what this fixes
  - Add label `hotfix` if the repo has it: `gh pr create ... --label hotfix` (fall back to no label if it fails)
- Show the PR URL

## Rules

- NEVER skip confirmation steps
- NEVER force-push
- NEVER commit secrets or unrelated changes
- NEVER refactor — this is a hotfix, not a cleanup
- If the user says "skip" at any step, skip it and move to the next
- If the fix turns out to be complex, tell the user and suggest a regular branch instead
