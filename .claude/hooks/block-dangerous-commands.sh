#!/usr/bin/env bash
# PreToolUse / Bash — block destructive commands that have no easy undo.
set -uo pipefail

. "$(dirname "$0")/_lib.sh"

payload=$(cat)
cmd=$(get_field "$payload" tool_input.command)
[ -z "$cmd" ] && exit 0

block() {
  {
    echo "Blocked by block-dangerous-commands hook: $1"
    echo "Command: $cmd"
    echo "If you really need this, run it yourself in your terminal."
  } >&2
  exit 2
}

# rm against root, home, or with no path
if printf '%s' "$cmd" | grep -Eq 'rm[[:space:]]+(-[a-zA-Z]*[rRf][a-zA-Z]*[[:space:]]+)+(/|~|\$HOME)([[:space:]]|$|\*|/[[:space:]]?\*)'; then
  block "destructive 'rm' against root or home"
fi
if printf '%s' "$cmd" | grep -Eq 'rm[[:space:]]+-[a-zA-Z]*[rR][a-zA-Z]*[fF]?[a-zA-Z]*[[:space:]]+\*([[:space:]]|$)'; then
  block "'rm -rf *' is too broad"
fi

# git push --force / -f to main or master
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+push[[:space:]]+'; then
  if printf '%s' "$cmd" | grep -Eq -- '(--force([^-]|$)|--force-with-lease[[:space:]]*$|[[:space:]]-f([[:space:]]|$))'; then
    if printf '%s' "$cmd" | grep -Eq '(^|[[:space:]/:])(main|master)([[:space:]]|$)'; then
      block "force-push targeting main/master"
    fi
  fi
fi

# git add -f / --force — bypasses .gitignore; this repo's PII policy forbids it
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+add[[:space:]]+([^;&|]*[[:space:]])?(--force|-[a-zA-Z]*f[a-zA-Z]*)([[:space:]]|$)'; then
  block "'git add -f/--force' bypasses .gitignore — never force-add files here (PII policy, see CONTRIBUTING.md)"
fi

# git reset --hard with no explicit target — discards uncommitted work
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+reset[[:space:]]+--hard([[:space:]]*$|[[:space:]]+HEAD([[:space:]]|$))'; then
  block "'git reset --hard' without an explicit ref discards uncommitted work"
fi

# git checkout . / git restore . — wholesale revert
if printf '%s' "$cmd" | grep -Eq '(^|[;&|[:space:]])(git[[:space:]]+checkout[[:space:]]+\.|git[[:space:]]+restore[[:space:]]+\.)([[:space:]]|$|;|&|\|)'; then
  block "wholesale revert ('git checkout .' / 'git restore .') discards uncommitted work"
fi

# git clean -fdx — wipes untracked
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+clean[[:space:]]+-[a-zA-Z]*[fF][a-zA-Z]*[dDxX]'; then
  block "'git clean -fd*' deletes untracked files"
fi

# --no-verify on commit / push
if printf '%s' "$cmd" | grep -Eq 'git[[:space:]]+(commit|push)[[:space:]]+.*--no-verify'; then
  block "--no-verify bypasses pre-commit/pre-push checks"
fi

# chmod 777
if printf '%s' "$cmd" | grep -Eq 'chmod[[:space:]]+(-[a-zA-Z]+[[:space:]]+)?[0-7]?777'; then
  block "chmod 777 is almost never correct"
fi

# DROP TABLE / DATABASE / SCHEMA
if printf '%s' "$cmd" | grep -iEq 'DROP[[:space:]]+(TABLE|DATABASE|SCHEMA)'; then
  block "destructive SQL (DROP TABLE/DATABASE/SCHEMA)"
fi

# curl|sh / wget|sh — remote code execution
if printf '%s' "$cmd" | grep -Eq '(curl|wget)[[:space:]][^|]*\|[[:space:]]*(sh|bash|zsh|pwsh|powershell)([[:space:]]|$)'; then
  block "piping a remote download into a shell"
fi

exit 0
