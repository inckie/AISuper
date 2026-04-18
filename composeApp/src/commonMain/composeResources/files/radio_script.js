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
    if (query == undefined || query == null || query == "") {
        setValue("status_text", "Please enter station name");
        setValue("stationList", []);
        return;
    }

    setValue("status_text", "Searching for: " + query);

    try {
        var url = stationApiBase + encodeURIComponent(query) + "?limit=" + stationLimit;
        var body = await httpGet(url);
        if (body == undefined || body == null || body == "") {
            setValue("status_text", "Empty response");
            setValue("stationList", []);
            return;
        }

        if (body.indexOf("Error:") == 0) {
            setValue("status_text", body);
            setValue("stationList", []);
            return;
        }

        var stations = jsonParse(body);
        var widgets = [];

        var maxItems = stations.length;

        for (var i = 0; i < maxItems; i = i + 1) {
            var s = stations[i];
            var name = s.name;
            if (name == undefined || name == null || name == "") {
                name = "Unnamed station";
            }

            var streamUrl = s.url_resolved;
            if (streamUrl == undefined || streamUrl == null || streamUrl == "") {
                streamUrl = s.url;
            }

            if (streamUrl == undefined || streamUrl == null || streamUrl == "") {
                continue;
            }

            var subtitle = "";
            if (s.country != undefined && s.country != null && s.country != "") {
                subtitle = s.country;
            }
            if (s.codec != undefined && s.codec != null && s.codec != "") {
                if (subtitle == "") {
                    subtitle = s.codec;
                } else {
                    subtitle = subtitle + " | " + s.codec;
                }
            }

            var rowChildren = [];

            if (s.favicon != undefined && s.favicon != null && s.favicon != "") {
                rowChildren.push({
                    "type": "Image",
                    "url": s.favicon,
                    "description": name
                });
            }

            var infoChildren = [
                { "type": "Text", "text": name }
            ];
            if (subtitle != "") {
                infoChildren.push({ "type": "Text", "text": subtitle });
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
                "actionArgs": [streamUrl, name]
            });

            widgets.push({
                "type": "Row",
                "fillMaxWidth": true,
                "children": rowChildren
            });
        }

        setValue("stationList", widgets);
        setValue("status_text", "Found " + stations.length + " stations");
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

