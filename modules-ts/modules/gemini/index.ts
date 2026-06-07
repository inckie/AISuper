export interface ChatMessage {
    role: "user" | "model";
    text: string;
}

export interface GeminiResponse {
    ok: boolean;
    error?: string;
    text?: string;
}

async function gemini_generateContent(apiKey: string, history: ChatMessage[]): Promise<GeminiResponse> {
    if (!apiKey) {
        return { ok: false, error: "API key is required" };
    }
    if (!history || history.length === 0) {
        return { ok: false, error: "History cannot be empty" };
    }

    const url = `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${apiKey}`;

    // Format history into Gemini's payload structure
    const contents = [];
    for (let i = 0; i < history.length; i++) {
        const msg = history[i];
        contents.push({
            role: msg.role,
            parts: [{ text: msg.text }]
        });
    }

    const bodyObj = {
        contents: contents
    };

    const bodyStr = JSON.stringify(bodyObj);
    const headers = {
        "Content-Type": "application/json"
    };

    try {
        const resStr = await httpRequestRaw("POST", url, bodyStr, headers);
        const res = JSON.parse(resStr);

        if (res.status !== 200) {
            return { ok: false, error: `HTTP Error: ${res.status} ${res.body ? res.body : ''}` };
        }

        const parsedBody = JSON.parse(res.body);

        if (parsedBody.error) {
             return { ok: false, error: parsedBody.error.message || "Unknown Gemini API Error" };
        }

        // Extract the candidate text
        if (parsedBody.candidates && parsedBody.candidates.length > 0) {
            const candidate = parsedBody.candidates[0];
            if (candidate.content && candidate.content.parts && candidate.content.parts.length > 0) {
                return { ok: true, text: candidate.content.parts[0].text };
            }
        }

        return { ok: false, error: "Unexpected response format from Gemini API" };
    } catch (e: any) {
        return { ok: false, error: "Request execution error: " + e.message };
    }
}

registerExports("gemini", ["gemini_generateContent"]);