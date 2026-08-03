#!/usr/bin/env bash
# PreToolUse / Edit|Write — block edits inside build artifact directories.
# Distinct from protect-files because this is about wrong-target detection,
# not protecting source. Kept separate so you can tune patterns independently.
set -uo pipefail

. "$(dirname "$0")/_lib.sh"

payload=$(cat)
file_path=$(get_field "$payload" tool_input.file_path)
[ -z "$file_path" ] && exit 0

norm=$(normalize_path "$file_path")

if printf '%s' "$norm" | grep -Eq -- "/(\.cache|\.turbo|\.parcel-cache|\.swc|\.eslintcache|\.next/cache|\.vite/deps)/"; then
  {
    echo "Blocked by warn-large-files hook: '$file_path' is inside a tooling cache."
    echo "Caches regenerate from source. Edit the source file instead."
  } >&2
  exit 2
fi

exit 0
