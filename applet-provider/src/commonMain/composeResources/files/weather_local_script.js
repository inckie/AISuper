var lastLoadedLocation = "";

var WEATHER_LOCATIONS = [
    { "name": "Miami", "lat": 25.7743, "lon": -80.1937 },
    { "name": "New York", "lat": 40.7128, "lon": -74.0060 },
    { "name": "London", "lat": 51.5074, "lon": -0.1278 },
    { "name": "Tokyo", "lat": 35.6762, "lon": 139.6503 }
];

// Storage key for persisting location selection
var LOCATION_KEY = "selected_location";

async function initialize() {
    setValue("weather_status", "Loading...");
    setValue("weatherResult", []);

    var buttons = [];
    for (var i = 0; i < WEATHER_LOCATIONS.length; i = i + 1) {
        var city = WEATHER_LOCATIONS[i];
        buttons.push({
            "type": "Button",
            "text": city.name,
            "action": "loadWeather",
            "actionArgs": [city.lat, city.lon, city.name],
            "fillMaxWidth": true
        });
    }
    setValue("locationButtons", buttons);

    // Restore previously selected location from persistent storage
    try {
        var savedLocation = await persistentStorageGet("feature", LOCATION_KEY);
        if (savedLocation != null && savedLocation != "") {
            var parts = savedLocation.split("|");
            if (parts.length >= 3) {
                var lat = stringToNumber(parts[0]);
                var lon = stringToNumber(parts[1]);
                var name = parts[2];
                lastLoadedLocation = savedLocation;
                setValue("weather_status", "Restoring last location: " + name + "...");
                await loadWeather(lat, lon, name);
                return;
            }
        }
    } catch (e) {
        consoleError("Failed to restore saved location", e);
    }

    setValue("weather_status", "Pick a location to fetch weather");
}

async function loadWeatherCurrent() {
    setValue("weather_status", "Resolving current location...");

    try {
        var granted = geoRequestPermission();
        if (granted !== true) {
            setValue("weather_status", "Location permission requested. Tap again after granting.");
            return;
        }

        var geo = await geoGetCurrent();
        if (geo == undefined || geo == null || geo.success !== true) {
            var ge = "Location unavailable";
            if (geo != undefined && geo != null && geo.error != undefined && geo.error != null && geo.error != "") {
                ge = geo.error;
            }
            setValue("weather_status", "Geolocation error: " + ge);
            return;
        }

        await loadWeather(geo.latitude, geo.longitude, "My Location");
    } catch (e) {
        consoleError("loadWeatherCurrent failed", e);
        setValue("weather_status", "Failed to resolve location: " + e);
    }
}

async function refreshCurrent() {
    if (lastLoadedLocation === "") {
        setValue("weather_status", "No location to refresh. Pick a location first.");
        return;
    }

    var parts = lastLoadedLocation.split("|");
    if (parts.length >= 3) {
        var lat = stringToNumber(parts[0]);
        var lon = stringToNumber(parts[1]);
        var name = parts[2];
        await loadWeather(lat, lon, name);
    }
}

async function loadWeather(latitude, longitude, locationName) {
    setValue("weather_status", "Loading weather for " + locationName + "...");

    try {
        var result = await weather_get_current_weather(latitude, longitude, "auto");

        renderWeather(locationName, result);
        setValue("weather_status", "Weather loaded for " + locationName);

        // Persist the selected location so it restores on next open
        lastLoadedLocation = latitude + "|" + longitude + "|" + locationName;
        try {
            await persistentStoragePut("feature", LOCATION_KEY, lastLoadedLocation);
        } catch (e) {
            consoleError("Failed to save location", e);
        }
    } catch (e) {
        consoleError("loadWeather failed", e);
        setValue("weather_status", "Failed to load weather: " + e);
        setValue("weatherResult", []);
    }
}

function renderWeather(locationName, payload) {
    var widgets = [];

    widgets.push({
        "type": "Text",
        "text": "Weather in " + locationName
    });

    if (payload == undefined || payload == null) {
        widgets.push({ "type": "Text", "text": "No payload returned" });
        setValue("weatherResult", widgets);
        return;
    }

    var cw = payload.current;
    if (cw == undefined || cw == null) {
        cw = payload.current_weather;
    }
    if (cw != undefined && cw != null) {
        var temp = cw.temperature_2m;
        if (temp == undefined || temp == null) {
            temp = cw.temperature;
        }

        var windSpeed = cw.wind_speed_10m;
        if (windSpeed == undefined || windSpeed == null) {
            windSpeed = cw.windspeed;
        }

        if (temp != undefined && temp != null) {
            widgets.push({ "type": "Text", "text": "Temperature: " + temp + " C" });
        }
        if (windSpeed != undefined && windSpeed != null) {
            widgets.push({ "type": "Text", "text": "Wind speed: " + windSpeed + " km/h" });
        }
        if (cw.wind_direction != undefined && cw.wind_direction != null) {
            widgets.push({ "type": "Text", "text": "Wind direction: " + cw.wind_direction + " deg" });
        }
        if (cw.relative_humidity_2m != undefined && cw.relative_humidity_2m != null) {
            widgets.push({ "type": "Text", "text": "Humidity: " + cw.relative_humidity_2m + "%" });
        }
        if (cw.apparent_temperature != undefined && cw.apparent_temperature != null) {
            widgets.push({ "type": "Text", "text": "Feels like: " + cw.apparent_temperature + " C" });
        }
        if (cw.rain != undefined && cw.rain != null && cw.rain > 0) {
            widgets.push({ "type": "Text", "text": "Rain: " + cw.rain + " mm" });
        }
        if (cw.precipitation_probability != undefined && cw.precipitation_probability != null && cw.precipitation_probability > 0) {
            widgets.push({ "type": "Text", "text": "Precipitation: " + cw.precipitation_probability + "%" });
        }
        if (payload.timezone != undefined && payload.timezone != null) {
            widgets.push({ "type": "Text", "text": "Timezone: " + payload.timezone });
        }
        setValue("weatherResult", widgets);
        return;
    }

    if (payload.temperature != undefined) {
        widgets.push({ "type": "Text", "text": "Temperature: " + payload.temperature });
    }

    widgets.push({ "type": "Text", "text": "Unsupported payload shape" });

    setValue("weatherResult", widgets);
}

