#!/usr/bin/env bash
# PreToolUse / Edit|Write — block writes that contain likely secrets.
set -uo pipefail

. "$(dirname "$0")/_lib.sh"

payload=$(cat)
file_path=$(get_field "$payload" tool_input.file_path)

# Edit puts the proposed text in new_string; Write puts it in content.
content=$(get_field "$payload" tool_input.new_string)
[ -z "$content" ] && content=$(get_field "$payload" tool_input.content)
[ -z "$content" ] && exit 0

norm=$(normalize_path "$file_path")

# Skip files that are allowed to contain placeholder secrets.
if printf '%s' "$norm" | grep -Eq -- "(\.env\.example|\.env\.sample|/docs/|/\.claude/|README\.md|CHANGELOG\.md|\.test\.[jt]sx?$|/test/|/__tests__/|/fixtures/)"; then
  exit 0
fi

# name|regex
patterns=(
  "Anthropic API key|sk-ant-[A-Za-z0-9_-]{20,}"
  "OpenAI API key|sk-[A-Za-z0-9]{32,}"
  "AWS access key ID|AKIA[0-9A-Z]{16}"
  "Google API key|AIza[0-9A-Za-z_-]{35}"
  "GitHub personal token|gh[pousr]_[A-Za-z0-9]{36,}"
  "Slack token|xox[baprs]-[A-Za-z0-9-]{10,}"
  "Stripe secret|sk_live_[A-Za-z0-9]{24,}"
  "Private key block|-----BEGIN[ A-Z]*PRIVATE KEY"
  "JWT|eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"
)

for entry in "${patterns[@]}"; do
  name=${entry%%|*}
  regex=${entry#*|}
  if printf '%s' "$content" | grep -Eq -- "$regex"; then
    {
      echo "Blocked by scan-secrets hook: '$file_path' looks like it contains a $name."
      echo "Move the value to .env (gitignored) and reference it via process.env."
      echo "If this is a false positive, edit .claude/hooks/scan-secrets.sh."
    } >&2
    exit 2
  fi
done

exit 0
