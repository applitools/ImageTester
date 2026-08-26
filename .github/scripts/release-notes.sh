#!/usr/bin/env bash
# Builds release notes: the tagged version's CHANGELOG section on top,
# followed by the release template with {{VERSION}} substituted.
# Fails if the CHANGELOG has no section for the version, so a release
# can never ship without its changes listed.
set -euo pipefail

if [[ $# -ne 4 ]]; then
  echo "usage: $0 <version> <changelog> <template> <output>" >&2
  exit 2
fi

VERSION="$1"
CHANGELOG="$2"
TEMPLATE="$3"
OUTPUT="$4"

# Suffixed versions (3.16.5-rc1) share the base version's CHANGELOG section.
BASE="${VERSION%%-*}"

SECTION=$(awk -v ver="$BASE" '
  { sub(/\r$/, "") }
  $1 == "##" && $2 == ver { found = 1; next }
  found && $1 == "##" { exit }
  found { print }
' "$CHANGELOG")

if [[ -z "${SECTION//[[:space:]]/}" ]]; then
  echo "$CHANGELOG has no section for version $BASE — add one before tagging." >&2
  exit 1
fi

{
  echo "## What's changed"
  echo
  printf '%s\n' "$SECTION"
  echo
  sed "s/{{VERSION}}/$VERSION/g" "$TEMPLATE"
} > "$OUTPUT"
