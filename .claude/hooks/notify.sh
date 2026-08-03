#!/usr/bin/env bash
# Notification — surface "Claude needs your attention" via the OS notification system.
# Uses BurntToast on Windows (Install-Module -Name BurntToast -Scope CurrentUser),
# osascript on macOS, notify-send on Linux. Falls back to a terminal bell.
set -uo pipefail

. "$(dirname "$0")/_lib.sh"

payload=$(cat 2>/dev/null || true)
message=$(get_field "$payload" message)
[ -z "$message" ] && message="Claude Code needs your attention"

# Strip control characters and quotes that would break PowerShell string literal interpolation.
clean=$(printf '%s' "$message" | tr -d '\r\n' | tr "'" ' ' | tr '"' ' ')

if command -v powershell.exe >/dev/null 2>&1; then
  # Register a custom AppId so the toast belongs to "Claude Code", not powershell.exe.
  # Without this, clicking the toast launches the firing app (PowerShell) which flashes.
  # Background activation + custom AppId with no COM handler = click is a no-op.
  powershell.exe -NoProfile -NonInteractive -WindowStyle Hidden -Command \
    "\$AppId = 'Anthropic.ClaudeCode';
     \$Path  = \"HKCU:\\Software\\Classes\\AppUserModelId\\\$AppId\";
     if (-not (Test-Path \$Path)) {
       New-Item -Path \$Path -Force | Out-Null;
       New-ItemProperty -Path \$Path -Name 'DisplayName' -Value 'Claude Code' -PropertyType String -Force | Out-Null;
     }
     Import-Module BurntToast -ErrorAction SilentlyContinue;
     \$action  = New-BTAction -Arguments 'dismiss' -ActivationType Background;
     \$visual  = New-BTVisual -BindingGeneric (New-BTBinding -Children (New-BTText -Text 'Claude Code'),(New-BTText -Text '$clean'));
     \$content = New-BTContent -Visual \$visual -Actions \$action;
     Submit-BTNotification -Content \$content -AppId \$AppId" \
    >/dev/null 2>&1 || printf '\a'
elif command -v osascript >/dev/null 2>&1; then
  osascript -e "display notification \"$clean\" with title \"Claude Code\"" >/dev/null 2>&1 || printf '\a'
elif command -v notify-send >/dev/null 2>&1; then
  notify-send "Claude Code" "$clean" >/dev/null 2>&1 || printf '\a'
else
  printf '\a'
fi

exit 0
