var loops = [];
var stations = [];
var selectedArrivals = null;
var selectedStationTitle = "";
var loopInfoText = "";
var nearestStationText = "";
var loopSvgDataUri = "";

function initialize() {
    setValue("metromover_status", "Loading Metromover data (JS module)...");
    setValue("metromoverContent", []);
    refreshOverview();
}

async function refreshOverview() {
    setValue("metromover_status", "Fetching loops and stations...");
    try {
        consoleLog("DEBUG: refreshOverview start");

        consoleLog("DEBUG: calling miamiMetromover_list_loops()");
        var loopResponse = await miamiMetromover_list_loops();
        consoleLog("DEBUG: loopResponse returned, type:", typeof loopResponse);
        consoleLog("DEBUG: loopResponse isArray?", Array.isArray(loopResponse));
        if (Array.isArray(loopResponse)) {
            consoleLog("DEBUG: loopResponse.length =", loopResponse.length);
        }
        consoleLog("DEBUG: loopResponse value:", stringifySafe(loopResponse));

        consoleLog("DEBUG: calling miamiMetromover_list_stations()");
        var stationResponse = await miamiMetromover_list_stations();
        consoleLog("DEBUG: stationResponse returned, type:", typeof stationResponse);
        consoleLog("DEBUG: stationResponse isArray?", Array.isArray(stationResponse));
        if (Array.isArray(stationResponse)) {
            consoleLog("DEBUG: stationResponse.length =", stationResponse.length);
            if (stationResponse.length > 0) {
                consoleLog("DEBUG: first station:", stringifySafe(stationResponse[0]));
            }
        }
        consoleLog("DEBUG: stationResponse value (first 200 chars):", stringifySafe(stationResponse).substring(0, 200));

        loops = normalizeArray(loopResponse);
        stations = normalizeArray(stationResponse);

        consoleLog("DEBUG: after normalize: loops.length =", loops.length);
        consoleLog("DEBUG: after normalize: stations.length =", stations.length);
        selectedArrivals = null;
        selectedStationTitle = "";
        loopInfoText = "";
        nearestStationText = "";
        loopSvgDataUri = "";

        renderContent();
        setValue("metromover_status", "Loaded " + stations.length + " stations");
    } catch (e) {
        consoleError("refreshOverview failed", e);
        setValue("metromover_status", "Failed to load Metromover data: " + stringifySafe(e));
        setValue("metromoverContent", []);
    }
}

async function findNearestStation() {
    setValue("metromover_status", "Resolving your location...");

    try {
        var granted = geoRequestPermission();
        if (granted !== true) {
            setValue("metromover_status", "Location permission requested. Tap Nearest again after granting.");
            return;
        }

        var geo = await geoGetCurrent();
        if (geo == undefined || geo == null || geo.success !== true) {
            var ge = "Location unavailable";
            if (geo != undefined && geo != null && geo.error != undefined && geo.error != null && geo.error != "") {
                ge = geo.error;
            }
            setValue("metromover_status", "Geolocation error: " + ge);
            return;
        }

        var nearest = await miamiMetromover_find_nearest_station(geo.latitude, geo.longitude, 80.0);
        var station = nearest != undefined && nearest != null ? nearest.station : null;
        var nearestId = readField(station, ["id", "stationId"]);
        var nearestTitle = readField(station, ["title", "stationTitle", "name"]);
        if (nearestTitle == "") {
            nearestTitle = nearestId;
        }

        if (nearestId == "") {
            setValue("metromover_status", "No nearby station found");
            return;
        }

        var distanceText = "";
        if (nearest != undefined && nearest != null && nearest.distanceKm != undefined && nearest.distanceKm != null) {
            distanceText = " - " + nearest.distanceKm + " km";
        }

        nearestStationText = "Nearest: " + nearestTitle + " (" + nearestId + ")" + distanceText;
        renderContent();
        setValue("metromover_status", "Nearest station resolved");
    } catch (e) {
        consoleError("findNearestStation failed", e);
        setValue("metromover_status", "Failed to find nearest station: " + stringifySafe(e));
    }
}

async function loadLoopSvg(loopId) {
    setValue("metromover_status", "Loading loop map: " + loopId + "...");

    try {
        var result = await miamiMetromover_get_loop_svg(loopId, 720, 720, 16);

        var svg = "";
        if (result != undefined && result != null) {
            if (result.svg != undefined && result.svg != null) {
                svg = result.svg;
            } else if (typeof result == "string") {
                svg = result;
            }
        }

        if (svg != "") {
            loopInfoText = "Loop " + loopId + " map received (SVG " + svg.length + " chars)";
            loopSvgDataUri = "data:image/svg+xml;utf8," + encodeURIComponent(svg);
        } else {
            loopInfoText = "Loop " + loopId + " map not available";
            loopSvgDataUri = "";
        }

        renderContent();
        setValue("metromover_status", "Loop " + loopId + " loaded");
    } catch (e) {
        consoleError("loadLoopSvg failed", e);
        setValue("metromover_status", "Failed to load loop map: " + stringifySafe(e));
    }
}

async function loadArrivals(stationId, stationTitle) {
    setValue("metromover_status", "Loading arrivals for " + stationTitle + "...");

    try {
        var result = await miamiMetromover_get_station_arrivals(stationId);

        selectedArrivals = result;
        selectedStationTitle = stationTitle;
        renderContent();
        setValue("metromover_status", "Arrivals updated for " + stationTitle);
    } catch (e) {
        consoleError("loadArrivals failed", e);
        setValue("metromover_status", "Failed to load arrivals: " + stringifySafe(e));
    }
}

function renderContent() {
    var widgets = [];

    widgets.push({ "type": "Text", "text": "Free automated transit in Downtown, Brickell, and Omni." });

    widgets.push({ "type": "Text", "text": "Loops" });
    for (var i = 0; i < loops.length; i = i + 1) {
        var loopValue = loops[i];
        var loopId = valueToText(loopValue);
        widgets.push({
            "type": "Button",
            "text": "Loop " + loopId,
            "action": "loadLoopSvg",
            "actionArgs": [loopId],
            "fillMaxWidth": true
        });
    }

    if (loopInfoText != "") {
        widgets.push({ "type": "Text", "text": loopInfoText });
    }

    if (loopSvgDataUri != "") {
        widgets.push({
            "type": "Image",
            "data": loopSvgDataUri,
            "description": "Metromover loop map",
            "fillMaxWidth": true
        });
    }

    if (nearestStationText != "") {
        widgets.push({ "type": "Text", "text": nearestStationText });
    }

    widgets.push({ "type": "Text", "text": "Stations" });
    for (var j = 0; j < stations.length; j = j + 1) {
        var station = stations[j];
        var stationId = readField(station, ["id", "stationId"]);
        var stationTitle = readField(station, ["title", "name", "stationTitle"]);

        if (stationId == "") {
            continue;
        }

        if (stationTitle == "") {
            stationTitle = stationId;
        }

        widgets.push({
            "type": "Row",
            "fillMaxWidth": true,
            "children": [
                {
                    "type": "Text",
                    "text": stationTitle + " (" + stationId + ")",
                    "weight": 1
                },
                {
                    "type": "Button",
                    "text": "Arrivals",
                    "action": "loadArrivals",
                    "actionArgs": [stationId, stationTitle]
                }
            ]
        });
    }

    if (selectedArrivals != undefined && selectedArrivals != null) {
        widgets.push({ "type": "Text", "text": "Arrivals: " + selectedStationTitle });

        var groups = selectedArrivals.arrivals;
        if (groups == undefined || groups == null || groups.length == 0) {
            widgets.push({ "type": "Text", "text": "No arrivals currently available." });
        } else {
            for (var k = 0; k < groups.length; k = k + 1) {
                var g = groups[k];
                var loopName = readField(g, ["loopName", "loopId"]);
                if (loopName == "") {
                    loopName = "Loop";
                }

                var times = g.arrivals;
                var timesText = "No ETA";
                if (times != undefined && times != null && times.length > 0) {
                    timesText = times.join(", ");
                }

                widgets.push({
                    "type": "Text",
                    "text": loopName + ": " + timesText
                });
            }
        }
    }

    setValue("metromoverContent", widgets);
}

function normalizeArray(value) {
    if (value == undefined || value == null) {
        return [];
    }
    if (value.length == undefined) {
        return [];
    }
    return value;
}

function readField(obj, keys) {
    if (obj == undefined || obj == null) {
        return "";
    }

    for (var i = 0; i < keys.length; i = i + 1) {
        var key = keys[i];
        var value = obj[key];
        if (value != undefined && value != null && value != "") {
            return valueToText(value);
        }
    }

    return "";
}

function valueToText(value) {
    if (value == undefined || value == null) {
        return "";
    }
    if (typeof value == "string") {
        return value;
    }
    if (typeof value == "number") {
        return "" + value;
    }
    if (typeof value == "boolean") {
        if (value) {
            return "true";
        }
        return "false";
    }
    return stringifySafe(value);
}

function stringifySafe(value) {
    if (value == undefined || value == null) {
        return "";
    }
    try {
        return JSON.stringify(value);
    } catch (e) {
        return "" + value;
    }
}

