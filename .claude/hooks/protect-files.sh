#!/usr/bin/env bash
# PreToolUse / Edit|Write — block edits to generated, locked, or never-touch files.
set -uo pipefail

. "$(dirname "$0")/_lib.sh"

payload=$(cat)
file_path=$(get_field "$payload" tool_input.file_path)
[ -z "$file_path" ] && exit 0

norm=$(normalize_path "$file_path")

# Patterns Claude should not edit. Source files only — never generated output, lockfiles, or VCS internals.
patterns=(
  "/node_modules/"
  "/dist/"
  "/build/"
  "/coverage/"
  "/\.git/"
  "/\.next/"
  "/\.vite/"
  "package-lock\.json$"
  "yarn\.lock$"
  "pnpm-lock\.yaml$"
)

for p in "${patterns[@]}"; do
  if printf '%s' "$norm" | grep -Eq -- "$p"; then
    {
      echo "Blocked by protect-files hook: '$file_path'"
      echo "Matched pattern: $p"
      echo "These paths are generated or locked. Edit the source instead, or run the regenerator."
    } >&2
    exit 2
  fi
done

exit 0
