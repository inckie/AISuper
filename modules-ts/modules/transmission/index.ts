const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';

function btoa(input: string): string {
    let str = input;
    let output = '';

    for (let block = 0, charCode, i = 0, map = chars;
    str.charAt(i | 0) || (map = '=', i % 1);
    output += map.charAt(63 & block >> 8 - i % 1 * 8)) {

        charCode = str.charCodeAt(i += 3/4);

        if (charCode > 0xFF) {
            throw new Error("'btoa' failed: The string to be encoded contains characters outside of the Latin1 range.");
        }

        block = block << 8 | charCode;
    }

    return output;
}

let currentSessionId = "";

async function rpcCall(host: string, login?: string, password?: string, method?: string, args?: Record<string, unknown>): Promise<{ok: boolean, error?: string, data?: any}> {
    if (!host) {
        return { ok: false, error: "Host is required" };
    }

    const url = host.endsWith('/') ? `${host}transmission/rpc` : `${host}/transmission/rpc`;

    const headers: Record<string, string> = {};
    if (login && password) {
        headers["Authorization"] = "Basic " + btoa(`${login}:${password}`);
    }
    if (currentSessionId) {
        headers["X-Transmission-Session-Id"] = currentSessionId;
    }

    const bodyObj = {
        method: method || "session-get",
        arguments: args || {}
    };

    const bodyStr = JSON.stringify(bodyObj);

    let resStr = await httpRequestRaw("POST", url, bodyStr, headers);
    let res = JSON.parse(resStr);

    if (res.status === 409) {
        // Handle Session ID refresh
        let newSessionId = res.headers["X-Transmission-Session-Id"];
        if (!newSessionId && res.headers["x-transmission-session-id"]) {
            newSessionId = res.headers["x-transmission-session-id"];
        }
        if (newSessionId) {
            currentSessionId = newSessionId;
            headers["X-Transmission-Session-Id"] = currentSessionId;
            resStr = await httpRequestRaw("POST", url, bodyStr, headers);
            res = JSON.parse(resStr);
        } else {
             return { ok: false, error: "HTTP 409, but no session ID header found" };
        }
    }

    if (res.status !== 200) {
        return { ok: false, error: `HTTP Error: ${res.status} ${res.error || ''}` };
    }

    try {
        const parsedBody = JSON.parse(res.body);
        if (parsedBody.result === "success") {
            return { ok: true, data: parsedBody.arguments };
        } else {
            return { ok: false, error: parsedBody.result || "Unknown RPC error" };
        }
    } catch (e: any) {
        return { ok: false, error: "JSON Parse error: " + e.message };
    }
}

async function transmission_getTorrents(host: string, login?: string, password?: string): Promise<{ok: boolean, error?: string, torrents?: any[]}> {
    const res = await rpcCall(host, login, password, "torrent-get", {
        fields: ["id", "name", "status", "percentDone", "rateDownload", "rateUpload"]
    });
    if (!res.ok) {
        return res;
    }
    return { ok: true, torrents: res.data?.torrents || [] };
}

async function transmission_pauseTorrent(host: string, login: string | undefined, password: string | undefined, id: number): Promise<{ok: boolean, error?: string}> {
    const res = await rpcCall(host, login, password, "torrent-stop", {
        ids: [id]
    });
    if (!res.ok) return res;
    return { ok: true };
}

async function transmission_resumeTorrent(host: string, login: string | undefined, password: string | undefined, id: number): Promise<{ok: boolean, error?: string}> {
    const res = await rpcCall(host, login, password, "torrent-start", {
        ids: [id]
    });
    if (!res.ok) return res;
    return { ok: true };
}

registerExports("transmission", ["transmission_getTorrents", "transmission_pauseTorrent", "transmission_resumeTorrent"]);