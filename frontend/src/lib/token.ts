export function getToken(): string {
  const meta = document.querySelector('meta[name="gui-token"]') as HTMLMetaElement | null;
  return meta?.content ?? "";
}
