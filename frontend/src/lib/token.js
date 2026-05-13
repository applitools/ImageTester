export function getToken() {
    const meta = document.querySelector('meta[name="gui-token"]');
    return meta?.content ?? "";
}
