var loops = ["OMN", "BKL", "INN", "FUL", "OTR"];
var activeLoops = {
    "OMN": true,
    "BKL": true,
    "INN": true,
    "FUL": false,
    "OTR": false
};
var stations = [];
var selectedArrivals = null;
var selectedStationTitle = "";
var selectedStationId = "";
var loopInfoText = "";
var nearestStationText = "";
var loopSvgDataUri = "";

function initialize() {
    setValue("metromover_status", "Loading Metromover data (JS module)...");
    setValue("metromoverContent", []);
    setValue("metromover_spinner_visible", true);
    refreshOverview();
}

async function openSettings() {
    await setLayout("files/metromover_settings_layout.json");
    var savedApiKey = "";
    try {
        savedApiKey = await persistentStorageGet("feature", "metromover_api_key") || "";
    } catch (e) {
        consoleError("Failed to load api key", e);
    }
    setValue("metromover_apikey_input", savedApiKey);
    setValue("settings_status_text", "");
}

async function saveSettings() {
    var apiKey = getValue("metromover_apikey_input") || "";
    try {
        await persistentStoragePut("feature", "metromover_api_key", apiKey);
        setValue("settings_status_text", "Settings saved!");
        await openMain();
    } catch (e) {
        consoleError("Failed to save api key", e);
        setValue("settings_status_text", "Failed to save: " + stringifySafe(e));
    }
}

async function openMain() {
    await setLayout("files/metromover_layout.json");
    initialize();
}

async function refreshOverview() {
    setValue("metromover_status", "Fetching loops and stations...");
    setValue("metromover_spinner_visible", true);
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

        await updateNetworkMap();
        renderContent();
        setValue("metromover_status", "Loaded " + stations.length + " stations");
        setValue("metromover_spinner_visible", false);
    } catch (e) {
        consoleError("refreshOverview failed", e);
        setValue("metromover_status", "Failed to load Metromover data: " + stringifySafe(e));
        setValue("metromoverContent", []);
        setValue("metromover_spinner_visible", false);
    }
}

async function findNearestStation() {
    setValue("metromover_status", "Resolving your location...");
    setValue("metromover_spinner_visible", true);

    try {
        var granted = await geoRequestPermission();
        if (granted !== true) {
            setValue("metromover_status", "Location permission denied.");
            setValue("metromover_spinner_visible", false);
            return;
        }

        var geo = await geoGetCurrent();
        if (geo == undefined || geo == null || geo.success !== true) {
            var ge = "Location unavailable";
            if (geo != undefined && geo != null && geo.error != undefined && geo.error != null && geo.error != "") {
                ge = geo.error;
            }
            setValue("metromover_status", "Geolocation error: " + ge);
            setValue("metromover_spinner_visible", false);
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
            setValue("metromover_spinner_visible", false);
            return;
        }

        var distanceText = "";
        if (nearest != undefined && nearest != null && nearest.distanceKm != undefined && nearest.distanceKm != null) {
            distanceText = " - " + nearest.distanceKm + " km";
        }

        nearestStationText = "Nearest: " + nearestTitle + " (" + nearestId + ")" + distanceText;
        selectedStationId = nearestId;
        await loadArrivals(nearestId, nearestTitle);
    } catch (e) {
        consoleError("findNearestStation failed", e);
        setValue("metromover_status", "Failed to find nearest station: " + stringifySafe(e));
        setValue("metromover_spinner_visible", false);
    }
}

async function loadLoopSvg(loopId) {
    setValue("metromover_status", "Loading loop map: " + loopId + "...");
    setValue("metromover_spinner_visible", true);

    try {
        var result = await miamiMetromover_get_loop_svg(loopId, 720, 720, 16);

        var svg = "";
        var svgUri = "";
        if (result != undefined && result != null) {
            if (result.svgDataUri != undefined && result.svgDataUri != null && result.svgDataUri != "") {
                svgUri = result.svgDataUri;
            }
            if (result.svg != undefined && result.svg != null) {
                svg = result.svg;
            } else if (typeof result == "string") {
                svg = result;
            }
        }

        if (svgUri == "" && svg != "") {
            svgUri = "data:image/svg+xml;base64," + base64Encode(svg);
        }

        if (svgUri != "") {
            var charCount = svg != "" ? svg.length : svgUri.length;
            loopInfoText = "Loop " + loopId + " map received (SVG " + charCount + " chars)";
            loopSvgDataUri = svgUri;
        } else {
            loopInfoText = "Loop " + loopId + " map not available";
            loopSvgDataUri = "";
        }

        renderContent();
        setValue("metromover_status", "Loop " + loopId + " loaded");
        setValue("metromover_spinner_visible", false);
    } catch (e) {
        consoleError("loadLoopSvg failed", e);
        setValue("metromover_status", "Failed to load loop map: " + stringifySafe(e));
        setValue("metromover_spinner_visible", false);
    }
}

async function loadArrivals(stationId, stationTitle) {
    setValue("metromover_status", "Loading arrivals for " + stationTitle + "...");
    setValue("metromover_spinner_visible", true);

    try {
        var result = await miamiMetromover_get_station_arrivals(stationId);

        selectedArrivals = result;
        selectedStationTitle = stationTitle;
        selectedStationId = stationId;
        await updateNetworkMap();
        renderContent();
        setValue("metromover_status", "Arrivals updated for " + stationTitle);
        setValue("metromover_spinner_visible", false);
    } catch (e) {
        consoleError("loadArrivals failed", e);
        setValue("metromover_status", "Failed to load arrivals: " + stringifySafe(e));
        setValue("metromover_spinner_visible", false);
    }
}


async function updateNetworkMap() {
    var activeList = [];
    for (var id in activeLoops) {
        if (activeLoops[id]) {
            activeList.push(id);
        }
    }

    try {
        var result = await miamiMetromover_get_network_svg(activeList, selectedStationId, 720, 720, 24, true);
        if (result != undefined && result != null) {
            if (result.svgDataUri != undefined && result.svgDataUri != null && result.svgDataUri != "") {
                loopSvgDataUri = result.svgDataUri;
            } else if (result.svg != undefined && result.svg != null) {
                loopSvgDataUri = "data:image/svg+xml;base64," + base64Encode(result.svg);
            }

            var activeNames = activeList.map(function(id) {
                if (id == "OMN") return "Omni";
                if (id == "BKL") return "Brickell";
                if (id == "INN") return "Inner";
                if (id == "FUL") return "Full";
                if (id == "OTR") return "Outer";
                return id;
            });

            var trainInfo = (result.trainCount !== undefined && result.trainCount !== null)
                ? " - " + result.trainCount + " trains active"
                : "";
            loopInfoText = "Active loops: " + (activeNames.length > 0 ? activeNames.join(", ") : "None") + trainInfo;
        }
    } catch (e) {
        consoleError("updateNetworkMap failed", e);
    }
}

async function toggleLoop(loopId) {
    var normId = (loopId || "").trim().toUpperCase();
    if (activeLoops[normId] === undefined) {
        activeLoops[normId] = false;
    }
    activeLoops[normId] = !activeLoops[normId];
    await updateNetworkMap();
    renderContent();
}

async function toggleAllLoops() {
    var anyActive = false;
    for (var k in activeLoops) {
        if (activeLoops[k]) {
            anyActive = true;
            break;
        }
    }
    var newState = !anyActive;
    for (var j in activeLoops) {
        activeLoops[j] = newState;
    }
    await updateNetworkMap();
    renderContent();
}

function renderContent() {
    var widgets = [];

    widgets.push({ "type": "Text", "text": "Free automated transit in Downtown, Brickell, and Omni." });

    widgets.push({ "type": "Text", "text": "Loops & Trails", "classes": ["section_title"] });

    widgets.push({
        "type": "Row",
        "fillMaxWidth": true,
        "children": [
            {
                "type": "Button",
                "text": (activeLoops["OMN"] ? "✓ " : "") + "Omni (OMN)",
                "classes": activeLoops["OMN"] ? [] : ["ghost_button"],
                "action": "toggleLoop",
                "actionArgs": ["OMN"],
                "weight": 1
            },
            {
                "type": "Button",
                "text": (activeLoops["BKL"] ? "✓ " : "") + "Brickell (BKL)",
                "classes": activeLoops["BKL"] ? [] : ["ghost_button"],
                "action": "toggleLoop",
                "actionArgs": ["BKL"],
                "weight": 1
            },
            {
                "type": "Button",
                "text": (activeLoops["INN"] ? "✓ " : "") + "Inner (INN)",
                "classes": activeLoops["INN"] ? [] : ["ghost_button"],
                "action": "toggleLoop",
                "actionArgs": ["INN"],
                "weight": 1
            }
        ]
    });

    widgets.push({
        "type": "Row",
        "fillMaxWidth": true,
        "children": [
            {
                "type": "Button",
                "text": (activeLoops["FUL"] ? "✓ " : "") + "Full (FUL)",
                "classes": activeLoops["FUL"] ? [] : ["ghost_button"],
                "action": "toggleLoop",
                "actionArgs": ["FUL"],
                "weight": 1
            },
            {
                "type": "Button",
                "text": (activeLoops["OTR"] ? "✓ " : "") + "Outer (OTR)",
                "classes": activeLoops["OTR"] ? [] : ["ghost_button"],
                "action": "toggleLoop",
                "actionArgs": ["OTR"],
                "weight": 1
            },
            {
                "type": "Button",
                "text": "All",
                "classes": ["ghost_button"],
                "action": "toggleAllLoops",
                "weight": 1
            }
        ]
    });

    if (loopSvgDataUri != "") {
        widgets.push({
            "type": "Image",
            "data": loopSvgDataUri,
            "description": "Metromover network map with loops and trails",
            "fillMaxWidth": true,
            "height": 280
        });
    }

    if (nearestStationText != "") {
        widgets.push({ "type": "Text", "text": nearestStationText });
    }

    if (selectedArrivals != undefined && selectedArrivals != null) {
        widgets.push({ "type": "Text", "text": "Arrivals: " + selectedStationTitle, "classes": ["section_title"] });

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

        if (selectedArrivals.messageAlert != undefined && selectedArrivals.messageAlert != null && selectedArrivals.messageAlert != "") {
            widgets.push({ "type": "Text", "text": "Alert: " + selectedArrivals.messageAlert });
        }
        if (selectedArrivals.messageServiceUpdate != undefined && selectedArrivals.messageServiceUpdate != null && selectedArrivals.messageServiceUpdate != "") {
            widgets.push({ "type": "Text", "text": "Service update: " + selectedArrivals.messageServiceUpdate });
        }
    }

    widgets.push({ "type": "Text", "text": "Stations", "classes": ["section_title"] });
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


function base64Encode(input) {
    if (typeof btoa === "function") {
        try {
            return btoa(unescape(encodeURIComponent(input)));
        } catch (e) {}
    }
    var chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
    var str = unescape(encodeURIComponent(input));
    var output = "";
    for (var i = 0; i < str.length; i += 3) {
        var chr1 = str.charCodeAt(i);
        var chr2 = str.charCodeAt(i + 1);
        var chr3 = str.charCodeAt(i + 2);
        var enc1 = chr1 >> 2;
        var enc2 = ((chr1 & 3) << 4) | (chr2 >> 4);
        var enc3 = isNaN(chr2) ? 64 : (((chr2 & 15) << 2) | (chr3 >> 6));
        var enc4 = isNaN(chr2) || isNaN(chr3) ? 64 : (chr3 & 63);
        output += chars.charAt(enc1) + chars.charAt(enc2) + chars.charAt(enc3) + chars.charAt(enc4);
    }
    return output;
}
