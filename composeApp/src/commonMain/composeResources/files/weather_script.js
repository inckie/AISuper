var WEATHER_LOCATIONS = [
    { "name": "Miami", "lat": 25.7743, "lon": -80.1937 },
    { "name": "New York", "lat": 40.7128, "lon": -74.0060 },
    { "name": "London", "lat": 51.5074, "lon": -0.1278 },
    { "name": "Tokyo", "lat": 35.6762, "lon": 139.6503 }
];

function initialize() {
    setValue("weather_status", "Pick a location to fetch weather");
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
}

async function loadWeather(latitude, longitude, locationName) {
    setValue("weather_status", "Loading weather for " + locationName + "...");

    try {
        var result = await mcpCall("weather", "get_current_weather", {
            "latitude": latitude,
            "longitude": longitude,
            "timezone": "auto"
        });

        renderWeather(locationName, result);
        setValue("weather_status", "Weather loaded for " + locationName);
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

    var cw = payload.current_weather;
    if (cw == undefined || cw == null) {
        cw = payload.current;
    }
    if (cw != undefined && cw != null) {
        var temp = cw.temperature;
        if (temp == undefined || temp == null) {
            temp = cw.temperature_2m;
        }

        var windSpeed = cw.windspeed;
        if (windSpeed == undefined || windSpeed == null) {
            windSpeed = cw.wind_speed_10m;
        }

        if (temp != undefined && temp != null) {
            widgets.push({ "type": "Text", "text": "Temperature: " + temp + " C" });
        }
        if (windSpeed != undefined && windSpeed != null) {
            widgets.push({ "type": "Text", "text": "Wind speed: " + windSpeed + " km/h" });
        }
        if (cw.winddirection != undefined && cw.winddirection != null) {
            widgets.push({ "type": "Text", "text": "Wind direction: " + cw.winddirection + " deg" });
        }
        if (cw.relative_humidity_2m != undefined && cw.relative_humidity_2m != null) {
            widgets.push({ "type": "Text", "text": "Humidity: " + cw.relative_humidity_2m + "%" });
        }
        if (cw.time != undefined && cw.time != null) {
            widgets.push({ "type": "Text", "text": "Time: " + cw.time });
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

