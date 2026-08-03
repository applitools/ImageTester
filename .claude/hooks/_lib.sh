#!/usr/bin/env bash
# Shared helpers for hook scripts. Source via: . "$(dirname "$0")/_lib.sh"

# Read a single field from the JSON payload on stdin.
# Usage: get_field "$payload" tool_input.file_path
get_field() {
  local payload="$1"
  local key="$2"
  printf '%s' "$payload" | node -e '
    let s = "";
    process.stdin.on("data", d => s += d);
    process.stdin.on("end", () => {
      try {
        const o = JSON.parse(s);
        const k = process.argv[1].split(".").filter(Boolean);
        let v = o;
        for (const p of k) v = v?.[p];
        if (v != null) process.stdout.write(String(v));
      } catch {}
    });
  ' "$key" 2>/dev/null
}

# Forward-slash a Windows-style path so regex matches consistently.
normalize_path() {
  printf '%s' "$1" | tr '\\' '/'
}
