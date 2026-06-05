export function getVersion(): string {
  const meta = document.querySelector('meta[name="gui-version"]') as HTMLMetaElement | null;
  const v = meta?.content ?? "";
  // The server replaces __GUI_VERSION__ at request time; if the literal sentinel leaks through
  // (dev server, stale bundle), show nothing rather than the placeholder string.
  return v.startsWith("__") ? "" : v;
}
