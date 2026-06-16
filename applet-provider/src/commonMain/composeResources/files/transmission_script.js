var HOST_KEY = "transmission_host";
var LOGIN_KEY = "transmission_login";
var PASS_KEY = "transmission_pass";
var DOWNLOAD_PATH_KEY = "transmission_last_download_path";

var STATUS_STOPPED = 0;
var STATUS_CHECK_WAIT = 1;
var STATUS_CHECK = 2;
var STATUS_DOWNLOAD_WAIT = 3;
var STATUS_DOWNLOAD = 4;
var STATUS_SEED_WAIT = 5;
var STATUS_SEED = 6;

// Local cached credentials for list view
var currentHost = "";
var currentLogin = "";
var currentPass = "";
var lastDownloadPath = "";

async function initialize() {
    setValue("spinner_visible", false);
    setValue("torrentList", []);
    setValue("status_text", "");

    try {
        var host = await persistentStorageGet("feature", HOST_KEY);
        var login = await persistentStorageGet("feature", LOGIN_KEY);
        var pass = await persistentStorageGet("feature", PASS_KEY);
        var path = await persistentStorageGet("feature", DOWNLOAD_PATH_KEY);

        if (path) lastDownloadPath = path;

        if (host) {
            currentHost = host;
            currentLogin = login || "";
            currentPass = pass || "";
            await setLayout("list");
            refreshTorrents();
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
    setValue("host_input", currentHost);
    setValue("login_input", currentLogin);
    setValue("password_input", currentPass);
    setValue("status_text", "");
}

async function connectAndGoToList() {
    var host = getValue("host_input");
    var login = getValue("login_input");
    var pass = getValue("password_input");

    if (!host) {
        setValue("status_text", "Error: Host is required");
        return;
    }

    currentHost = host;
    currentLogin = login;
    currentPass = pass;

    await saveCredentials(host, login, pass);
    await setLayout("list");
    refreshTorrents();
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
    if (!currentHost) {
        setValue("status_text", "Error: Host is missing");
        return;
    }

    setValue("status_text", "Connecting...");
    setValue("spinner_visible", true);
    setValue("torrentList", []);

    try {
        var res = await transmission_transmission_getTorrents(currentHost, currentLogin, currentPass);
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
    if (!currentHost) return;

    setValue("status_text", "Pausing torrent " + id + "...");
    try {
        var res = await transmission_transmission_pauseTorrent(currentHost, currentLogin, currentPass, id);
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
    if (!currentHost) return;

    setValue("status_text", "Resuming torrent " + id + "...");
    try {
        var res = await transmission_transmission_resumeTorrent(currentHost, currentLogin, currentPass, id);
        if (!res.ok) {
            setValue("status_text", "Error resuming: " + res.error);
            return;
        }
        await refreshTorrents();
    } catch(e) {
         setValue("status_text", "Error resuming: " + e);
    }
}

async function goToAddTorrent() {
    await setLayout("add");
    setValue("magnet_input", "");
    setValue("download_path_input", lastDownloadPath);
    setValue("start_when_added", true);
    setValue("add_status_text", "");
}

async function goToList() {
    await setLayout("list");
    refreshTorrents();
}

async function confirmAddTorrent() {
    var magnet = getValue("magnet_input");
    var path = getValue("download_path_input");
    var startWhenAdded = getValue("start_when_added");

    if (!magnet) {
        setValue("add_status_text", "Error: Magnet link is required");
        return;
    }

    if (path) {
        lastDownloadPath = path;
        try {
            await persistentStoragePut("feature", DOWNLOAD_PATH_KEY, path);
        } catch (e) {
            consoleError("Failed to save download path", e);
        }
    }

    setValue("add_status_text", "Adding torrent...");
    try {
        var res = await transmission_transmission_addTorrent(currentHost, currentLogin, currentPass, magnet, path, !startWhenAdded);
        if (!res.ok) {
            setValue("add_status_text", "Error: " + (res.error || "Unknown"));
            return;
        }
        await goToList();
    } catch (e) {
        setValue("add_status_text", "Error: " + e);
    }
}
