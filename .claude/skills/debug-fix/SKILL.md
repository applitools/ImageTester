---
name: debug-fix
description: Find and fix a bug or issue — from any source (GitHub issue, error message, user report, or observed behavior)
argument-hint: "[issue number, error message, or description of the problem]"
disable-model-invocation: true
---

Find and fix the following issue:

**Problem**: $ARGUMENTS

## Step 1: Understand the Problem

Determine what kind of input this is:
- **Issue number** → fetch it: `gh issue view $ARGUMENTS` (GitHub), or check the project's issue tracker
- **Error message / stack trace** → parse it for file, line, error type, and the call chain leading to it
- **Description of behavior** → identify what's expected vs what's happening
- **URL / screenshot** → examine the referenced resource

If the problem is unclear, ask clarifying questions before proceeding.

## Step 2: Reproduce

- Find or write the simplest way to trigger the issue (a test, a curl command, a script)
- Confirm you can reproduce it reliably
- If you can't reproduce:
  - **Environment-specific?** Check env vars, OS, Node/Python version, database state
  - **Intermittent?** Likely a race condition — look for shared mutable state, timing dependencies, or async ordering assumptions
  - **Already fixed?** Check `git log` for recent commits that mention the issue

## Step 3: Investigate

Follow this sequence — don't skip ahead to guessing:

1. **Locate the symptom**: which file and line produces the wrong output/error?
2. **Read the code path**: trace backwards from the symptom. What function called this? What data did it pass? Read each caller.
3. **Check git history**: `git log --oneline -20 -- <file>` to see what recently changed in the affected files. `git log --all --grep="<keyword>"` to find related commits.
4. **Narrow the scope**: use `git bisect` or targeted grep to identify when the behavior changed, or which input triggers it.
5. **Form a hypothesis**: "I think [X] is wrong because [evidence]."
6. **Verify the hypothesis**: add a targeted log/assertion/test that would confirm or deny it. Run it.
7. **If wrong, update**: don't keep guessing with the same hypothesis. Go back to step 2 and trace a different path.

## Step 4: Fix

- Make the minimal change that fixes the root cause
- Don't patch symptoms — if a value is wrong, trace back to where it becomes wrong and fix it there
- Don't refactor surrounding code while fixing the bug
- Don't add defensive checks that mask the problem — fix why the bad data exists

## Step 5: Verify

- Write a test that reproduces the original bug and now passes with the fix
- Run related tests to check for regressions
- Run lint and typecheck
- Temporarily revert your fix and confirm the new test fails — this proves the test actually catches the bug

## Step 6: Wrap Up

- Create a branch if not already on one
- Stage only the relevant files (fix + test, nothing else)
- Commit with a message that references the issue if one exists: `fix: <what was wrong and why> (#number)`
