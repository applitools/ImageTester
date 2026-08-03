# Skills

Skills are slash commands you invoke with `/name`. They run in the main conversation context (they see all loaded rules and CLAUDE.md).

- `disable-model-invocation: true` — manual only, you type `/name` to trigger
- Without that flag — Claude can also trigger the skill automatically when relevant

## Available Skills

### /setupdotclaude
**Trigger**: Manual only

Scans the project codebase and customizes all `.claude/` config files to match the actual tech stack, conventions, and patterns. Run this after adding the `.claude/` folder to a new project. Confirms every change with the user. Runs a final review pass with `/refactor` in plan mode against the full codebase.

### /debug-fix [issue number, error, or description]
**Trigger**: Manual only

Find and fix a bug from any source — GitHub issue number, error message, stack trace, behavior description, or URL. Follows a structured flow: understand → reproduce → investigate → fix → verify → commit.

### /ship [optional message]
**Trigger**: Manual only

Full shipping workflow with confirmation at every step: scan changes → stage & commit → push → create PR. Proposes commit messages and PR descriptions. Blocks secrets, force-push, and push to main.

### /pr-review [PR number | staged | file path]
**Trigger**: Manual only

Reviews code changes by delegating to specialist agents (`@code-reviewer`, `@security-reviewer`, `@performance-reviewer`, `@doc-reviewer`). When given a PR number (or auto-detected from branch), also checks PR title, description quality, CI status, unresolved comments, and size — ending with a clear merge/needs-changes verdict. Also works on staged changes or specific files for pre-PR review.

### /tdd [feature description]
**Trigger**: Manual only

Strict Test-Driven Development loop. Red: write a failing test for the smallest next behavior. Green: write the minimum code to pass. Refactor: clean up without changing behavior. Repeat. Commits after each green+refactor cycle.

### /explain [file, function, or concept]
**Trigger**: Manual only

Explains code with a one-sentence summary, mental model analogy, ASCII diagram, key details, and modification guide.

### /refactor [target]
**Trigger**: Manual only

Safe refactoring with tests as a safety net. Writes tests first if none exist, makes changes in small testable steps, verifies no behavior change.

### /test-writer
**Trigger**: Automatic (when new features are added)

Writes comprehensive tests covering every code path: happy path, edge cases, nulls, type boundaries, error paths, concurrency, state transitions. Covers API endpoints, UI components, database operations, and async. Verifies tests actually catch bugs by breaking the code.

## Adding Your Own

Create a directory with a `SKILL.md` file:

```
your-skill/
└── SKILL.md
```

```yaml
---
name: your-skill
description: What it does and when to use it
disable-model-invocation: true
---

Your instructions here. Use $ARGUMENTS for user input.
```

See [Claude Code docs](https://code.claude.com/docs/en/skills) for all frontmatter options.
