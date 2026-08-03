#!/usr/bin/env bash
# SessionStart — surface current branch, dirty files, and recent commits to Claude.
set -uo pipefail

repo="${CLAUDE_PROJECT_DIR:-$(pwd)}"
cd "$repo" 2>/dev/null || exit 0

branch=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "(not a git repo)")

porcelain=$(git status --porcelain 2>/dev/null || true)
if [ -z "$porcelain" ]; then
  status_block="(clean working tree)"
else
  modified=$(printf '%s\n' "$porcelain" | grep -c '^.M' || true)
  staged=$(printf '%s\n' "$porcelain" | grep -c '^M' || true)
  untracked=$(printf '%s\n' "$porcelain" | grep -c '^??' || true)
  status_block="$staged staged, $modified modified, $untracked untracked"
fi

recent=$(git log --oneline -5 2>/dev/null || echo "(no commits)")

hooks_warning=""
if [ "$(git config core.hooksPath 2>/dev/null)" != ".githooks" ]; then
  hooks_warning=$'\n\nWARNING: PII guard not enabled in this clone — run: git config core.hooksPath .githooks (see CONTRIBUTING.md)'
fi

context=$(printf 'Branch: %s\nWorking tree: %s\n\nRecent commits:\n%s%s' \
  "$branch" "$status_block" "$recent" "$hooks_warning")

# Emit JSON envelope so Claude sees this as additionalContext on session start.
node -e '
  const ctx = process.argv[1];
  process.stdout.write(JSON.stringify({
    hookSpecificOutput: {
      hookEventName: "SessionStart",
      additionalContext: ctx
    }
  }));
' "$context" 2>/dev/null || printf '%s\n' "$context" >&2

exit 0
