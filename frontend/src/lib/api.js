import { getToken } from "./token";
async function http(method, path, body) {
    const res = await fetch(path, {
        method,
        headers: {
            "Authorization": `Bearer ${getToken()}`,
            ...(body ? { "Content-Type": "application/json" } : {}),
        },
        body: body ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) {
        const detail = await res.text().catch(() => "");
        throw new Error(`${res.status}: ${detail}`);
    }
    if (res.status === 204)
        return undefined;
    return res.json();
}
export const api = {
    status: () => http("GET", "/api/status"),
    hasApiKey: () => http("GET", "/api/secret/api-key"),
    setApiKey: (value) => http("PUT", "/api/secret/api-key", { value }),
    deleteApiKey: () => http("DELETE", "/api/secret/api-key"),
    choosePath: (type) => http("POST", "/api/choose-path", { type }),
    run: (sourcePath, matchLevel) => http("POST", "/api/run", { sourcePath, matchLevel }),
    cancel: () => http("POST", "/api/cancel"),
};
