var WEATHER_LOCATIONS = [
    { "name": "Miami", "lat": 25.7743, "lon": -80.1937 },
    { "name": "New York", "lat": 40.7128, "lon": -74.0060 },
    { "name": "London", "lat": 51.5074, "lon": -0.1278 },
    { "name": "Tokyo", "lat": 35.6762, "lon": 139.6503 }
];

var LOCATION_KEY = "selected_location";
var lastLoadedLocation = "";

async function initialize() {
    // Check saved location
    try {
        var savedLocation = await persistentStorageGet("feature", LOCATION_KEY);
        if (savedLocation != null && savedLocation != "") {
            lastLoadedLocation = savedLocation;
        }
    } catch (e) {
        consoleError("Failed to restore saved location", e);
    }
    
    // Default to My Location if not set
    if (lastLoadedLocation == "") {
        lastLoadedLocation = "MY_LOCATION|0|0";
    }

    var units = getValue("unit_selection");
    if (!units) {
        setValue("unit_selection", "metric");
    }

    await openMain();
}

async function openMain() {
    await setLayout("files/weather_main_layout.json");
    await updateDashboard();
}

async function openSettings() {
    await setLayout("files/weather_settings_layout.json");
    setValue("weather_status", "");
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

async function loadWeatherCurrent() {
    setValue("weather_status", "Resolving current location...");
    lastLoadedLocation = "MY_LOCATION|0|0";
    
    try {
        await persistentStoragePut("feature", LOCATION_KEY, lastLoadedLocation);
    } catch (e) {
        consoleError("Failed to save location", e);
    }
    
    await openMain();
}

async function refreshCurrent() {
    await openMain();
}

async function loadWeather(latitude, longitude, locationName) {
    lastLoadedLocation = latitude + "|" + longitude + "|" + locationName;
    try {
        await persistentStoragePut("feature", LOCATION_KEY, lastLoadedLocation);
    } catch (e) {
        consoleError("Failed to save location", e);
    }
    await openMain();
}

async function onUnitsChanged() {
    await updateDashboard();
}

async function updateDashboard() {
    var units = getValue("unit_selection") || "metric";
    setValue("location_name", "Fetching Location...");

    var lat = 37.7749;
    var lon = -122.4194;
    var locName = "San Francisco";

    if (lastLoadedLocation.startsWith("MY_LOCATION")) {
        try {
            var granted = geoRequestPermission();
            if (granted === true) {
                var geo = await geoGetCurrent();
                if (geo && geo.success && geo.latitude !== undefined && geo.longitude !== undefined) {
                    lat = geo.latitude;
                    lon = geo.longitude;
                    locName = "My Location";
                } else {
                    locName = "Location Unavailable";
                }
            } else {
                locName = "Permission Denied";
            }
        } catch (e) {
            consoleError("Failed to fetch location", e);
            locName = "Location Error";
        }
    } else {
        var parts = lastLoadedLocation.split("|");
        if (parts.length >= 3) {
            lat = stringToNumber(parts[0]);
            lon = stringToNumber(parts[1]);
            locName = parts[2];
        }
    }

    setValue("location_name", locName);

    try {
        var currentRes = await weather_get_current_weather(lat, lon, "auto");
        var hourlyRes = await weather_get_hourly_forecast(lat, lon, 24, "auto");
        var dailyRes = await weather_get_daily_forecast(lat, lon, "auto");

        // Update Current Weather
        if (currentRes && currentRes.current) {
            var curr = currentRes.current;
            var cTemp = formatTemp(curr.temperature_2m, units);
            var cEmoji = mapCodeToEmoji(curr.weathercode);
            var cCondition = mapCodeToConditionText(curr.weathercode);

            setValue("current_temp", cEmoji + " " + cTemp);
            setValue("current_condition", cCondition);
        }

        // Update Hourly Forecast (take every 3 hours)
        if (hourlyRes && hourlyRes.hourly) {
            var hourly = hourlyRes.hourly;
            var is24h = (units === "metric");
            
            // Map the first 8 slots at 3-hour intervals
            for (var i = 0; i < 8; i++) {
                var hourIndex = i * 3;
                if (hourIndex < hourly.length) {
                    var hr = hourly[hourIndex];
                    var timeStr = formatHourlyTime(hr.time, is24h);
                    var tempStr = formatTempNoUnit(hr.temperature_2m, units);
                    var emojiStr = mapCodeToEmoji(hr.weathercode);

                    setValue("h" + i + "_time", timeStr);
                    setValue("h" + i + "_emoji", emojiStr);
                    setValue("h" + i + "_temp", tempStr);
                }
            }
        }

        // Update Daily Forecast (5 days)
        if (dailyRes && dailyRes.daily) {
            var daily = dailyRes.daily;
            for (var j = 0; j < 5; j++) {
                if (j < daily.length) {
                    var dy = daily[j];
                    var dayName = getDayName(dy.time);
                    var minTemp = formatTemp(dy.temperature_2m_min, units);
                    var maxTemp = formatTemp(dy.temperature_2m_max, units);
                    var emojiStr = mapCodeToEmoji(dy.weathercode);

                    setValue("d" + j + "_day", dayName);
                    setValue("d" + j + "_emoji", emojiStr);
                    setValue("d" + j + "_temp", minTemp + " - " + maxTemp);
                }
            }
        }

    } catch (err) {
        consoleError("Failed to update dashboard", err);
        setValue("location_name", "Error Loading Weather");
    }
}

function mapCodeToEmoji(code) {
    if (code === undefined || code === null) return "☀️";
    if (code === 0) return "☀️";
    if (code === 1 || code === 2 || code === 3) return "⛅";
    if (code === 45 || code === 48) return "☁️";
    if (code === 51 || code === 53 || code === 55) return "☔";
    if (code === 56 || code === 57) return "☔";
    if (code === 61 || code === 63 || code === 65) return "☔";
    if (code === 66 || code === 67) return "☔";
    if (code === 71 || code === 73 || code === 75) return "❄️";
    if (code === 77) return "❄️";
    if (code === 80 || code === 81 || code === 82) return "☔";
    if (code === 85 || code === 86) return "❄️";
    if (code === 95) return "⛈️";
    if (code === 96 || code === 99) return "⛈️";
    return "☀️";
}

function mapCodeToConditionText(code) {
    if (code === undefined || code === null) return "Clear Sky";
    if (code === 0) return "Clear Sky";
    if (code === 1 || code === 2 || code === 3) return "Partly Cloudy";
    if (code === 45 || code === 48) return "Foggy";
    if (code === 51 || code === 53 || code === 55) return "Light Rain";
    if (code === 61 || code === 63 || code === 65) return "Rainy";
    if (code === 71 || code === 73 || code === 75) return "Snowy";
    if (code === 80 || code === 81 || code === 82) return "Showers";
    if (code === 95 || code === 96 || code === 99) return "Thunderstorm";
    return "Clear Sky";
}

function getDayName(dateStr) {
    var parts = dateStr.split("-");
    if (parts.length < 3) return dateStr;
    var year = parseInt(parts[0], 10);
    var month = parseInt(parts[1], 10) - 1;
    var day = parseInt(parts[2], 10);
    var date = new Date(year, month, day);
    var days = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
    return days[date.getDay()];
}

function formatHourlyTime(dateTimeStr, is24h) {
    var timePart = dateTimeStr.split("T")[1];
    if (!timePart) return dateTimeStr;
    var hourPart = parseInt(timePart.split(":")[0], 10);
    
    if (is24h) {
        return (hourPart < 10 ? "0" + hourPart : hourPart) + ":00";
    } else {
        var suffix = hourPart >= 12 ? "PM" : "AM";
        var displayHour = hourPart % 12;
        if (displayHour === 0) displayHour = 12;
        return displayHour + " " + suffix;
    }
}

function formatTemp(tempC, units) {
    if (units === "imperial") {
        var tempF = Math.round(tempC * 9 / 5 + 32);
        return tempF + "°F";
    } else {
        return Math.round(tempC) + "°C";
    }
}

function formatTempNoUnit(tempC, units) {
    if (units === "imperial") {
        var tempF = Math.round(tempC * 9 / 5 + 32);
        return tempF + "°";
    } else {
        return Math.round(tempC) + "°";
    }
}
