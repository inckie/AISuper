async function search(stationApiBase, stationLimit, query) {
    if (query == undefined || query == null || query == "") {
        return {
            "ok": false,
            "error": "Please enter station name",
            "stations": []
        };
    }

    var url = stationApiBase + encodeURIComponent(query) + "?limit=" + stationLimit;
    var body = await httpGet(url);

    if (body == undefined || body == null || body == "") {
        return {
            "ok": false,
            "error": "Empty response",
            "stations": []
        };
    }

    if (body.indexOf("Error:") == 0) {
        return {
            "ok": false,
            "error": body,
            "stations": []
        };
    }

    var stationsRaw = jsonParse(body);
    if (stationsRaw == undefined || stationsRaw == null) {
        return {
            "ok": false,
            "error": "Invalid response",
            "stations": []
        };
    }

    var stations = [];
    for (var i = 0; i < stationsRaw.length; i = i + 1) {
        var s = stationsRaw[i];
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

        stations.push({
            "name": name,
            "subtitle": subtitle,
            "streamUrl": streamUrl,
            "favicon": s.favicon
        });
    }

    return {
        "ok": true,
        "stations": stations,
        "total": stationsRaw.length
    };
}

// Declare exported functions to the host runtime
registerExports("onlineRadioBrowser", ["search"]);
