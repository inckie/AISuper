var playerName = "radioMain";
var stationApiBase = "https://de1.api.radio-browser.info/json/stations/byname/";
var stationLimit = 20;

function initialize() {
    audioSubscribe(playerName, "onAudioEvent");
    setValue("search_query", "nightwave");
    setValue("status_text", "Type a station name and press Find");
    setValue("stationList", []);
    setValue("playerPanel", []);
}

async function findStations() {
    var query = getValue("search_query");
    setValue("status_text", "Searching for: " + query);

    try {
        var result = await onlineRadioBrowser_search(stationApiBase, stationLimit, query);
        if (!result.ok) {
            setValue("status_text", result.error || "Search failed");
            setValue("stationList", []);
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
    } catch (e) {
        consoleError("findStations failed", e);
        setValue("status_text", "Search failed: " + e);
        setValue("stationList", []);
    }
}

function playStation(url, name) {
    if (url == undefined || url == null || url == "") {
        setValue("status_text", "Invalid stream URL");
        return;
    }

    audioLoad(playerName, url);
    audioPlay(playerName);

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
}
