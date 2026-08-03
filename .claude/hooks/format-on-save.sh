#!/usr/bin/env bash
# PostToolUse / Edit|Write — auto-format the file Claude just touched.
# Backend has no formatter configured. Frontend uses ESLint with --fix.
# Failures are swallowed so a lint error never blocks the conversation.
set -uo pipefail

. "$(dirname "$0")/_lib.sh"

payload=$(cat)
file_path=$(get_field "$payload" tool_input.file_path)
[ -z "$file_path" ] && exit 0
[ ! -f "$file_path" ] && exit 0

norm=$(normalize_path "$file_path")
project_dir="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
project_norm=$(normalize_path "$project_dir")

# Frontend JS/JSX → eslint --fix on the single file.
case "$norm" in
  "$project_norm"/frontend/*.js|"$project_norm"/frontend/*.jsx)
    ( cd "$project_dir/frontend" && npx --no-install eslint --fix "$file_path" ) >/dev/null 2>&1 || true
    ;;
esac

# Backend / Scripts: no formatter configured. Add one here when needed, e.g.:
#   case "$norm" in
#     "$project_norm"/backend/*.js)
#       ( cd "$project_dir/backend" && npx --no-install prettier --write "$file_path" ) >/dev/null 2>&1 || true
#       ;;
#   esac

exit 0
