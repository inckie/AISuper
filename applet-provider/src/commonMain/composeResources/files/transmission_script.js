var HOST_KEY = "transmission_host";
var LOGIN_KEY = "transmission_login";
var PASS_KEY = "transmission_pass";

var STATUS_STOPPED = 0;
var STATUS_CHECK_WAIT = 1;
var STATUS_CHECK = 2;
var STATUS_DOWNLOAD_WAIT = 3;
var STATUS_DOWNLOAD = 4;
var STATUS_SEED_WAIT = 5;
var STATUS_SEED = 6;

async function initialize() {
    setValue("spinner_visible", false);
    setValue("torrentList", []);
    setValue("status_text", "Status: Not connected");

    try {
        var host = await persistentStorageGet("feature", HOST_KEY);
        var login = await persistentStorageGet("feature", LOGIN_KEY);
        var pass = await persistentStorageGet("feature", PASS_KEY);

        if (host) setValue("host_input", host);
        if (login) setValue("login_input", login);
        if (pass) setValue("password_input", pass);
    } catch (e) {
        consoleError("Failed to load credentials", e);
    }
}

async function saveCredentials(host, login, pass) {
    try {
        await persistentStoragePut("feature", HOST_KEY, host || "");
        await persistentStoragePut("feature", LOGIN_KEY, login || "");
        await persistentStoragePut("feature", PASS_KEY, pass || "");
    } catch (e) {
        consoleError("Failed to save credentials", e);
    }
}

function getStatusString(status) {
    switch (status) {
        case STATUS_STOPPED: return "Paused";
        case STATUS_CHECK_WAIT: return "Queued for checking";
        case STATUS_CHECK: return "Checking";
        case STATUS_DOWNLOAD_WAIT: return "Queued for download";
        case STATUS_DOWNLOAD: return "Downloading";
        case STATUS_SEED_WAIT: return "Queued for seeding";
        case STATUS_SEED: return "Seeding";
        default: return "Unknown (" + status + ")";
    }
}

async function refreshTorrents() {
    var host = getValue("host_input");
    var login = getValue("login_input");
    var pass = getValue("password_input");

    if (!host) {
        setValue("status_text", "Error: Host is required");
        return;
    }

    await saveCredentials(host, login, pass);

    setValue("status_text", "Connecting...");
    setValue("spinner_visible", true);
    setValue("torrentList", []);

    try {
        var res = await transmission_transmission_getTorrents(host, login, pass);
        setValue("spinner_visible", false);

        if (!res.ok) {
            setValue("status_text", "Error: " + (res.error || "Unknown"));
            return;
        }

        var torrents = res.torrents;
        if (!torrents || torrents.length === 0) {
            setValue("status_text", "Status: Connected, no active torrents.");
            return;
        }

        setValue("status_text", "Status: Connected, " + torrents.length + " torrents.");

        var listChildren = [];
        for (var i = 0; i < torrents.length; i++) {
            var t = torrents[i];

            var progressStr = (t.percentDone * 100).toFixed(1) + "%";
            var speedStr = "";
            if (t.status === STATUS_DOWNLOAD) speedStr = " ⬇ " + (t.rateDownload / 1024).toFixed(1) + " kB/s";
            if (t.status === STATUS_SEED) speedStr = " ⬆ " + (t.rateUpload / 1024).toFixed(1) + " kB/s";

            var infoText = getStatusString(t.status) + " | " + progressStr + speedStr;

            var isPaused = t.status === STATUS_STOPPED;

            var rowChildren = [
                {
                    "type": "Column",
                    "weight": 1,
                    "children": [
                        { "type": "Text", "text": t.name },
                        { "type": "Text", "text": infoText }
                    ]
                },
                {
                    "type": "Button",
                    "text": isPaused ? "Resume" : "Pause",
                    "action": isPaused ? "resumeTorrent" : "pauseTorrent",
                    "actionArgs": [t.id]
                }
            ];

            listChildren.push({
                "type": "Row",
                "fillMaxWidth": true,
                "children": rowChildren
            });
        }

        setValue("torrentList", listChildren);
    } catch (e) {
        setValue("spinner_visible", false);
        setValue("status_text", "Error: " + e);
    }
}

async function pauseTorrent(id) {
    var host = getValue("host_input");
    var login = getValue("login_input");
    var pass = getValue("password_input");

    setValue("status_text", "Pausing torrent " + id + "...");
    try {
        var res = await transmission_transmission_pauseTorrent(host, login, pass, id);
        if (!res.ok) {
            setValue("status_text", "Error pausing: " + res.error);
            return;
        }
        await refreshTorrents();
    } catch(e) {
         setValue("status_text", "Error pausing: " + e);
    }
}

async function resumeTorrent(id) {
    var host = getValue("host_input");
    var login = getValue("login_input");
    var pass = getValue("password_input");

    setValue("status_text", "Resuming torrent " + id + "...");
    try {
        var res = await transmission_transmission_resumeTorrent(host, login, pass, id);
        if (!res.ok) {
            setValue("status_text", "Error resuming: " + res.error);
            return;
        }
        await refreshTorrents();
    } catch(e) {
         setValue("status_text", "Error resuming: " + e);
    }
}
