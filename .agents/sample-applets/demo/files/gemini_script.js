var APIKEY_KEY = "gemini_apikey";

var currentApiKey = "";
var chatHistory = [];

async function initialize() {
    setValue("spinner_visible", false);
    setValue("messageList", []);
    setValue("error_text", "");

    try {
        var apiKey = await persistentStorageGet("feature", APIKEY_KEY);

        if (apiKey) {
            currentApiKey = apiKey;
            await setLayout("chat");
            renderMessages();
        } else {
            await setLayout("settings");
        }
    } catch (e) {
        consoleError("Failed to load credentials", e);
        await setLayout("settings");
    }
}

async function goToSettings() {
    await setLayout("settings");
    setValue("apikey_input", currentApiKey);
    setValue("status_text", "");
}

async function connectAndGoToList() {
    var apiKey = getValue("apikey_input");

    if (!apiKey) {
        setValue("status_text", "Error: API Key is required");
        return;
    }

    currentApiKey = apiKey;

    try {
        await persistentStoragePut("feature", APIKEY_KEY, apiKey);
    } catch (e) {
        consoleError("Failed to save credentials", e);
    }

    await setLayout("chat");
    renderMessages();
}

function renderMessages() {
    var widgets = [];
    for (var i = 0; i < chatHistory.length; i++) {
        var msg = chatHistory[i];

        var prefix = msg.role === "user" ? "You: " : "Gemini: ";
        var styleClasses = msg.role === "user" ? ["user_message"] : ["ai_message"];
        var alignVal = msg.role === "user" ? "end" : "start";

        widgets.push({
            "type": "Text",
            "text": prefix + msg.text,
            "classes": styleClasses,
            "align": alignVal,
            "fillMaxWidth": true
        });
    }
    setValue("messageList", widgets);
}

async function processChat() {
    if (!currentApiKey) {
        setValue("error_text", "Error: API Key is missing");
        return;
    }

    var input = getValue("input_field");
    if (!input) return;

    chatHistory.push({ role: "user", text: input });
    renderMessages();
    setValue("input_field", "");
    setValue("error_text", "");
    setValue("spinner_visible", true);

    try {
        var res = await gemini_gemini_generateContent(currentApiKey, chatHistory);
        setValue("spinner_visible", false);

        if (!res.ok) {
            setValue("error_text", "Error: " + (res.error || "Unknown error"));
            // pop user message so they can retry
            chatHistory.pop();
            setValue("input_field", input);
            renderMessages();
            return;
        }

        if (res.text) {
             chatHistory.push({ role: "model", text: res.text });
             renderMessages();
        } else {
             setValue("error_text", "Error: No text returned");
        }
    } catch (e) {
        setValue("spinner_visible", false);
        setValue("error_text", "Error: " + e);
        chatHistory.pop();
        setValue("input_field", input);
        renderMessages();
    }
}