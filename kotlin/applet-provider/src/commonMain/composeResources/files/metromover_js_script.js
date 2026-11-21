var loops = ["OMN", "BKL", "INN", "FUL", "OTR"];
var activeLoops = {
    "OMN": true,
    "BKL": true,
    "INN": true,
    "FUL": false,
    "OTR": false
};
var stations = [];
var activeTab = "arrivals";
var selectedArrivals = null;
var selectedStationTitle = "";
var selectedStationId = "";
var nearestStationId = "";
var nearestDistanceText = "";
var loopInfoText = "";
var loopSvgDataUri = "";
var liveTrainCount = 0;

function initialize() {
    setValue("metromover_status", "Loading Metromover data...");
    setValue("metromoverTabs", []);
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
        var loopResponse = await miamiMetromover_list_loops();
        var stationResponse = await miamiMetromover_list_stations();

        loops = normalizeArray(loopResponse);
        stations = normalizeArray(stationResponse);

        await updateNetworkMap();
        if (selectedStationId != "" && selectedStationTitle != "") {
            try {
                selectedArrivals = await miamiMetromover_get_station_arrivals(selectedStationId);
            } catch (e) {
                consoleError("refresh arrivals failed", e);
            }
        }
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
    activeTab = "arrivals";
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

        nearestStationId = nearestId;
        if (nearest != undefined && nearest != null && nearest.distanceKm != undefined && nearest.distanceKm != null) {
            nearestDistanceText = "Nearest station • " + nearest.distanceKm + " km away";
        } else {
            nearestDistanceText = "Nearest station";
        }

        selectedStationId = nearestId;
        await loadArrivals(nearestId, nearestTitle);
    } catch (e) {
        consoleError("findNearestStation failed", e);
        setValue("metromover_status", "Failed to find nearest station: " + stringifySafe(e));
        setValue("metromover_spinner_visible", false);
    }
}

async function switchTab(tab) {
    activeTab = tab === "map" ? "map" : "arrivals";
    if (activeTab === "map" && loopSvgDataUri == "") {
        setValue("metromover_spinner_visible", true);
        await updateNetworkMap();
        setValue("metromover_spinner_visible", false);
    }
    renderContent();
}

async function refreshMap() {
    setValue("metromover_status", "Refreshing live train map...");
    setValue("metromover_spinner_visible", true);
    await updateNetworkMap();
    renderContent();
    setValue("metromover_status", loopInfoText || "Map updated");
    setValue("metromover_spinner_visible", false);
}

async function loadArrivals(stationId, stationTitle) {
    activeTab = "arrivals";
    setValue("metromover_status", "Loading arrivals for " + stationTitle + "...");
    setValue("metromover_spinner_visible", true);

    try {
        var result = await miamiMetromover_get_station_arrivals(stationId);

        selectedArrivals = result;
        selectedStationTitle = stationTitle;
        selectedStationId = stationId;
        if (stationId !== nearestStationId) {
            nearestDistanceText = "";
        }
        renderContent();
        setValue("metromover_status", "Arrivals updated for " + stationTitle);
        setValue("metromover_spinner_visible", false);

        // Refresh map highlight & train positions in background
        updateNetworkMap().then(function() {
            renderTabs();
            if (activeTab === "map") {
                renderContent();
            }
        });
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

            if (result.trainCount !== undefined && result.trainCount !== null) {
                liveTrainCount = Number(result.trainCount) || 0;
            }
            var trainInfo = liveTrainCount > 0
                ? " • " + liveTrainCount + " active trains"
                : "";
            loopInfoText = "Loops: " + (activeNames.length > 0 ? activeNames.join(", ") : "None") + trainInfo;
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

function renderTabs() {
    var mapLabel = liveTrainCount > 0 ? "Map (" + liveTrainCount + ")" : "Map";
    setValue("metromoverTabs", [
        {
            "type": "Row",
            "fillMaxWidth": true,
            "children": [
                {
                    "type": "Button",
                    "text": "Arrivals",
                    "classes": activeTab === "arrivals" ? [] : ["ghost_button"],
                    "action": "switchTab",
                    "actionArgs": ["arrivals"],
                    "weight": 1
                },
                {
                    "type": "Button",
                    "text": mapLabel,
                    "classes": activeTab === "map" ? [] : ["ghost_button"],
                    "action": "switchTab",
                    "actionArgs": ["map"],
                    "weight": 1
                }
            ]
        }
    ]);
}

function formatNoticeHtml(raw) {
    if (raw == undefined || raw == null) {
        return "";
    }
    var text = String(raw);
    text = text.replace(/<\s*br\s*\/?>/gi, "\n");
    text = text.replace(/<\s*\/?(p|div|h[1-6]|ul|ol)\b[^>]*>/gi, "\n");
    text = text.replace(/<\s*li\b[^>]*>/gi, "\n• ");
    text = text.replace(/<\s*\/li\s*>/gi, "");
    text = text.replace(/<[^>]+>/g, "");
    text = text
        .replace(/&nbsp;/gi, " ")
        .replace(/&amp;/gi, "&")
        .replace(/&lt;/gi, "<")
        .replace(/&gt;/gi, ">")
        .replace(/&quot;/gi, "\"")
        .replace(/&#39;/gi, "'")
        .replace(/&ndash;/gi, "-")
        .replace(/&mdash;/gi, "—");

    var lines = text.split(/\r?\n/);
    var cleaned = [];
    for (var i = 0; i < lines.length; i = i + 1) {
        var line = lines[i].replace(/\s+/g, " ").trim();
        if (line !== "") {
            cleaned.push(line);
        }
    }
    return cleaned.join("\n");
}

function renderArrivalsTab(widgets) {
    if (selectedArrivals != undefined && selectedArrivals != null) {
        var cardChildren = [];

        cardChildren.push({
            "type": "Row",
            "fillMaxWidth": true,
            "children": [
                {
                    "type": "Column",
                    "weight": 1,
                    "children": [
                        {
                            "type": "Text",
                            "text": selectedStationTitle + " (" + selectedStationId + ")",
                            "classes": ["section_title"]
                        }
                    ].concat(
                        nearestDistanceText != ""
                            ? [{ "type": "Text", "text": nearestDistanceText, "classes": ["small_text"] }]
                            : []
                    )
                },
                {
                    "type": "Button",
                    "text": "Refresh",
                    "classes": ["ghost_button"],
                    "action": "loadArrivals",
                    "actionArgs": [selectedStationId, selectedStationTitle]
                }
            ]
        });

        var groups = selectedArrivals.arrivals;
        if (groups == undefined || groups == null || groups.length == 0) {
            cardChildren.push({ "type": "Text", "text": "No arrivals currently available." });
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

                cardChildren.push({
                    "type": "Row",
                    "fillMaxWidth": true,
                    "classes": ["daily_row_alt", "p-2", "rounded-lg"],
                    "children": [
                        { "type": "Text", "text": loopName, "weight": 1 },
                        { "type": "Text", "text": timesText, "classes": ["arrival_eta"] }
                    ]
                });
            }
        }

        widgets.push({
            "type": "Column",
            "fillMaxWidth": true,
            "classes": ["arrival_card", "p-2", "rounded-lg"],
            "children": cardChildren
        });

        var alertText = formatNoticeHtml(selectedArrivals.messageAlert);
        var updateText = formatNoticeHtml(selectedArrivals.messageServiceUpdate);
        var facilitiesText = formatNoticeHtml(selectedArrivals.messageFacilities);

        if (alertText != "" || updateText != "" || facilitiesText != "") {
            var noticeChildren = [
                {
                    "type": "Text",
                    "text": "Service Alerts & Notices",
                    "classes": ["notice_title"]
                }
            ];
            if (alertText != "") {
                noticeChildren.push({
                    "type": "Text",
                    "text": "Alert: " + alertText,
                    "classes": ["small_text"]
                });
            }
            if (updateText != "") {
                noticeChildren.push({
                    "type": "Text",
                    "text": updateText,
                    "classes": ["small_text"]
                });
            }
            if (facilitiesText != "") {
                noticeChildren.push({
                    "type": "Text",
                    "text": "Facilities: " + facilitiesText,
                    "classes": ["small_text"]
                });
            }

            widgets.push({
                "type": "Column",
                "fillMaxWidth": true,
                "classes": ["notice_card", "p-2", "rounded-lg"],
                "children": noticeChildren
            });
        }
    } else {
        widgets.push({
            "type": "Column",
            "fillMaxWidth": true,
            "classes": ["arrival_card", "p-2", "rounded-lg"],
            "children": [
                {
                    "type": "Text",
                    "text": "Tap Nearest Station above or pick a station below to see live arrivals immediately."
                }
            ]
        });
    }

    widgets.push({ "type": "Text", "text": "Stations (" + stations.length + ")", "classes": ["section_title"] });
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

        var isSelected = stationId === selectedStationId;
        widgets.push({
            "type": "Row",
            "fillMaxWidth": true,
            "classes": isSelected ? ["arrival_card", "p-2", "rounded-lg"] : [],
            "children": [
                {
                    "type": "Text",
                    "text": (isSelected ? "● " : "") + stationTitle + " (" + stationId + ")",
                    "weight": 1
                },
                {
                    "type": "Button",
                    "text": isSelected ? "Viewing" : "Arrivals",
                    "classes": isSelected ? [] : ["ghost_button"],
                    "action": "loadArrivals",
                    "actionArgs": [stationId, stationTitle]
                }
            ]
        });
    }
}

function renderMapTab(widgets) {
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

    widgets.push({
        "type": "Row",
        "fillMaxWidth": true,
        "children": [
            {
                "type": "Text",
                "text": loopInfoText != "" ? loopInfoText : "Live network map",
                "classes": ["small_text"],
                "weight": 1
            },
            {
                "type": "Button",
                "text": "Refresh Map",
                "classes": ["ghost_button"],
                "action": "refreshMap"
            }
        ]
    });

    if (loopSvgDataUri != "") {
        widgets.push({
            "type": "Image",
            "data": loopSvgDataUri,
            "description": "Metromover network map with live directional trains",
            "fillMaxWidth": true,
            "height": 360
        });
    }

    widgets.push({
        "type": "Text",
        "text": "▲ Train + direction   ● Selected station   ○ Station",
        "classes": ["small_text", "text-center"]
    });
}

function renderContent() {
    renderTabs();
    var widgets = [];
    if (activeTab === "map") {
        renderMapTab(widgets);
    } else {
        renderArrivalsTab(widgets);
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
