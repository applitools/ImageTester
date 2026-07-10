#!/usr/bin/env bash
# Blocks customer data from entering this public repo.
#
# Checks (staged files on commit, whole tree in CI):
#   1. Known customer file names (e.g. PermWat) are rejected outright.
#   2. Every binary fixture must be listed in .github/fixture-allowlist.txt —
#      adding one forces a reviewable allowlist edit in the same change.
#   3. OOXML files (xlsx/docx/pptx) are unzipped and their XML scanned for
#      email addresses outside the allowed domains.
#   4. Text additions are scanned for email addresses outside allowed domains.
#
# Note: an email rasterized into an image cannot be detected here — that is
# what the allowlist review step is for.
#
# Usage: pii-guard.sh --staged | --all

set -u
MODE="${1:---staged}"
cd "$(git rev-parse --show-toplevel)" || exit 1

ALLOWLIST=".github/fixture-allowlist.txt"
EMAIL_RE='[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
ALLOWED_DOMAINS_RE='@(applitools\.com|example\.com|proxy\.local|([A-Za-z0-9-]+\.)*noreply\.github\.com|anthropic\.com)$'
BINARY_EXT_RE='\.(pdf|xlsx?|docx?|pptx?|png|jpe?g|gif|bmp|ico|zip|jar|ps)$'
OOXML_EXT_RE='\.(xlsx|docx|pptx)$'
BLOCKED_NAME_RE='permwat|black_card'

fail=0
err() { printf 'PII-GUARD: %s\n' "$1" >&2; fail=1; }

if [ "$MODE" = "--staged" ]; then
    files=$(git diff --cached --name-only --diff-filter=ACMR)
else
    files=$(git ls-files)
fi

# --- 1 & 2 & 3: per-file checks -------------------------------------------
while IFS= read -r f; do
    [ -z "$f" ] && continue
    lower=$(printf '%s' "$f" | tr '[:upper:]' '[:lower:]')

    if printf '%s' "$lower" | grep -qE "$BLOCKED_NAME_RE"; then
        err "'$f' matches a known customer-data name and must never be committed"
        continue
    fi

    if printf '%s' "$lower" | grep -qE "$BINARY_EXT_RE"; then
        if ! grep -qxF "$f" "$ALLOWLIST" 2>/dev/null; then
            err "binary fixture '$f' is not in $ALLOWLIST — verify it contains no customer data (open any embedded images!), then add its path"
        fi
    fi

    if printf '%s' "$lower" | grep -qE "$OOXML_EXT_RE"; then
        tmp=$(mktemp) || exit 1
        if [ "$MODE" = "--staged" ]; then
            git show ":$f" > "$tmp" 2>/dev/null || cp "$f" "$tmp"
        else
            cp "$f" "$tmp"
        fi
        hits=$(unzip -p "$tmp" '*.xml' 2>/dev/null \
            | grep -oE "$EMAIL_RE" | grep -vE "$ALLOWED_DOMAINS_RE" | sort -u)
        rm -f "$tmp"
        if [ -n "$hits" ]; then
            err "'$f' embeds non-allowlisted email(s): $(printf '%s' "$hits" | tr '\n' ' ')"
        fi
    fi
done <<EOF
$files
EOF

# --- 4: text scan -----------------------------------------------------------
if [ "$MODE" = "--staged" ]; then
    text_hits=$(git diff --cached --diff-filter=ACMR -U0 \
        | grep -E '^\+[^+]' \
        | grep -oE "$EMAIL_RE" | grep -vE "$ALLOWED_DOMAINS_RE" | sort -u)
else
    text_hits=$(git grep -IhoE "$EMAIL_RE" -- . 2>/dev/null \
        | grep -vE "$ALLOWED_DOMAINS_RE" | sort -u)
fi
if [ -n "$text_hits" ]; then
    err "non-allowlisted email address(es) in text: $(printf '%s' "$text_hits" | tr '\n' ' ')"
fi

if [ "$fail" -ne 0 ]; then
    {
        echo 'PII-GUARD: commit blocked.'
        echo 'PII-GUARD: if this is genuinely clean synthetic data, add binaries to .github/fixture-allowlist.txt'
        echo 'PII-GUARD: or extend the allowed-domain list in .githooks/pii-guard.sh — in the same reviewed change.'
    } >&2
    exit 1
fi
exit 0
