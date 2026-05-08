var playerName = "radioMain";
var stationApiBase = "https://de1.api.radio-browser.info/json/stations/byname/";
var stationLimit = 20;

function initialize() {
    audioSubscribe(playerName, "onAudioEvent");
    setValue("search_query", "nightwave");
    setValue("status_text", "Type a station name and press Find");
    setValue("stationList", []);
    setValue("playerPanel", []);
    setValue("search_spinner_visible", false);
    setValue("radioMain.media.progress", 0);
}

async function findStations() {
    var query = getValue("search_query");
    setValue("status_text", "Searching for: " + query);
    setValue("search_spinner_visible", true);

    try {
        var result = await onlineRadioBrowser_search(stationApiBase, stationLimit, query);
        if (!result.ok) {
            setValue("status_text", result.error || "Search failed");
            setValue("stationList", []);
            setValue("search_spinner_visible", false);
            return;
        }

        var stations = result.stations;
        var widgets = [];

        for (var i = 0; i < stations.length; i = i + 1) {
            var s = stations[i];
            var rowChildren = [];

            if (s.favicon != undefined && s.favicon != null && s.favicon != "") {
                rowChildren.push({
                    "type": "Image",
                    "url": s.favicon,
                    "description": s.name
                });
            }

            var infoChildren = [
                { "type": "Text", "text": s.name }
            ];
            if (s.subtitle != "") {
                infoChildren.push({ "type": "Text", "text": s.subtitle });
            }

            rowChildren.push({
                "type": "Column",
                "weight": 1,
                "children": infoChildren
            });

            rowChildren.push({
                "type": "Button",
                "text": "Play",
                "action": "playStation",
                "actionArgs": [s.streamUrl, s.name]
            });

            widgets.push({
                "type": "Row",
                "fillMaxWidth": true,
                "children": rowChildren
            });
        }

        setValue("stationList", widgets);
        setValue("status_text", "Found " + result.total + " stations");
        setValue("search_spinner_visible", false);
    } catch (e) {
        consoleError("findStations failed", e);
        setValue("status_text", "Search failed: " + e);
        setValue("stationList", []);
        setValue("search_spinner_visible", false);
    }
}

function playStation(url, name) {
    if (url == undefined || url == null || url == "") {
        setValue("status_text", "Invalid stream URL");
        return;
    }

    audioLoad(playerName, url);
    audioPlay(playerName);
    setValue("radioMain.media.progress", 0);

    setValue("status_text", "Playing: " + name);
    setValue("playerPanel", [
        {
            "type": "Text",
            "text": "Now playing: " + name
        },
        {
            "type": "AudioPlayer",
            "player": playerName,
            "title": "Player",
            "fillMaxWidth": true
        }
    ]);
}

function onAudioEvent(event) {
    if (event == undefined || event == null) {
        return;
    }

    var state = event.state;
    if (state != undefined && state != null && state != "") {
        setValue("radioMain.media.state", state);
    }

    var positionMs = readNumber(event.positionMs);
    var durationMs = readNumber(event.durationMs);

    if ((durationMs == null || durationMs <= 0) && event.media != undefined && event.media != null) {
        durationMs = readNumber(event.media.durationMs);
    }
    if ((positionMs == null || positionMs < 0) && event.media != undefined && event.media != null) {
        positionMs = readNumber(event.media.positionMs);
    }

    var progress = 0;
    if (positionMs != null && durationMs != null && durationMs > 0) {
        progress = positionMs / durationMs;
        if (progress < 0) {
            progress = 0;
        }
        if (progress > 1) {
            progress = 1;
        }
    }

    setValue("radioMain.media.progress", progress);
}

function readNumber(value) {
    if (value == undefined || value == null) {
        return null;
    }
    if (typeof value == "number") {
        return value;
    }
    var parsed = parseFloat(value);
    if (isNaN(parsed)) {
        return null;
    }
    return parsed;
}
