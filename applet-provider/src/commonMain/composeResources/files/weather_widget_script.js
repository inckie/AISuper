// Widget script for Weather (Local) - minimal periodic fetch for home screen widget
var DEFAULT_LAT = 25.7743;
var DEFAULT_LON = -80.1937;
var DEFAULT_NAME = "Miami";

function initialize() {
    setValue("widget_title", "Weather");
    setValue("widget_temperature", "--");
    setValue("widget_humidity", "--");
    setValue("widget_wind", "--");
    setValue("widget_location", DEFAULT_NAME);
    setValue("widget_status", "Loading...");
    loadWeather(DEFAULT_LAT, DEFAULT_LON, DEFAULT_NAME);
}

async function refresh() {
    loadWeather(DEFAULT_LAT, DEFAULT_LON, DEFAULT_NAME);
}

async function loadWeather(latitude, longitude, locationName) {
    setValue("widget_status", "Updating...");
    try {
        var result = await weather_get_current_weather(latitude, longitude, "auto");
        var cw = result.current;
        if (cw != undefined && cw != null) {
            var temp = cw.temperature_2m;
            var humidity = cw.relative_humidity_2m;
            var wind = cw.wind_speed_10m;
            var feelsLike = cw.apparent_temperature;

            setValue("widget_temperature", temp != undefined ? (temp + " °C") : "--");
            setValue("widget_humidity", humidity != undefined ? (humidity + "%") : "--");
            setValue("widget_wind", wind != undefined ? (wind + " km/h") : "--");
            setValue("widget_feels_like", feelsLike != undefined ? (feelsLike + " °C") : "--");
        }
        setValue("widget_location", locationName + (result.timezone ? " · " + result.timezone : ""));
        setValue("widget_status", "");
    } catch (e) {
        consoleError("widget loadWeather failed", e);
        setValue("widget_status", "Error: " + e);
    }
}

