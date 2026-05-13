// Generated from modules-ts (weather). Do not edit manually.
"use strict";
const API_BASE_URL = "https://api.open-meteo.com/v1/forecast";
const USER_AGENT = "weather-module/1.0";
const HOURLY_FIELDS = [
  "temperature_2m",
  "rain",
  "showers",
  "snowfall",
  "precipitation_probability",
  "apparent_temperature",
  "relative_humidity_2m",
  "wind_speed_10m"
];
const CURRENT_FIELDS = [
  "temperature_2m",
  "relative_humidity_2m",
  "apparent_temperature",
  "rain",
  "showers",
  "snowfall",
  "precipitation_probability",
  "wind_speed_10m"
];
async function get_current_weather(latitude, longitude, timezone = "auto") {
  validateCoordinates(latitude, longitude);
  const params = buildQueryString({
    latitude: String(latitude),
    longitude: String(longitude),
    current: CURRENT_FIELDS.join(","),
    timezone
  });
  const url = `${API_BASE_URL}?${params}`;
  const response = await fetchJson(url);
  if (!response || typeof response !== "object") {
    throw new Error("Invalid API response format");
  }
  const result = response;
  const current = result.current;
  if (!current || typeof current !== "object") {
    throw new Error("API response did not contain a current weather block");
  }
  return {
    latitude: toNumber(result.latitude),
    longitude: toNumber(result.longitude),
    timezone: toString(result.timezone),
    timezoneAbbreviation: toString(result.timezone_abbreviation),
    current
  };
}
async function get_hourly_forecast(latitude, longitude, hours = 24, timezone = "auto") {
  validateCoordinates(latitude, longitude);
  if (hours <= 0 || hours > 240) {
    throw new Error("hours must be between 1 and 240");
  }
  const params = buildQueryString({
    latitude: String(latitude),
    longitude: String(longitude),
    hourly: HOURLY_FIELDS.join(","),
    timezone
  });
  const url = `${API_BASE_URL}?${params}`;
  const response = await fetchJson(url);
  if (!response || typeof response !== "object") {
    throw new Error("Invalid API response format");
  }
  const result = response;
  const hourly = result.hourly;
  if (!hourly || typeof hourly !== "object") {
    throw new Error("API response did not contain an hourly forecast block");
  }
  const times = hourly.time;
  if (!Array.isArray(times)) {
    throw new Error("Hourly forecast did not contain a time axis");
  }
  const maxLen = Math.min(hours, times.length);
  const rows = [];
  for (let i = 0; i < maxLen; i++) {
    const row = {
      time: toString(times[i])
    };
    for (const field of HOURLY_FIELDS) {
      const values = hourly[field];
      if (Array.isArray(values) && i < values.length) {
        const value = values[i];
        row[field] = typeof value === "number" ? value : void 0;
      }
    }
    rows.push(row);
  }
  return {
    latitude: toNumber(result.latitude),
    longitude: toNumber(result.longitude),
    timezone: toString(result.timezone),
    timezoneAbbreviation: toString(result.timezone_abbreviation),
    hourly: rows,
    requestedHours: hours,
    availableHours: times.length
  };
}
function buildQueryString(params) {
  const pairs = [];
  const keys = Object.keys(params);
  for (let i = 0; i < keys.length; i++) {
    const k = keys[i];
    pairs.push(encodeURIComponent(k) + "=" + encodeURIComponent(params[k]));
  }
  return pairs.join("&");
}
function validateCoordinates(latitude, longitude) {
  const lat = Number(latitude);
  const lon = Number(longitude);
  if (!Number.isFinite(lat) || lat < -90 || lat > 90) {
    throw new Error(`Invalid latitude: ${latitude} (must be between -90 and 90)`);
  }
  if (!Number.isFinite(lon) || lon < -180 || lon > 180) {
    throw new Error(`Invalid longitude: ${longitude} (must be between -180 and 180)`);
  }
}
async function fetchJson(url) {
  try {
    const body = await httpGet(url);
    if (!body || !body.trim()) {
      throw new Error("Empty response from Open-Meteo API");
    }
    return JSON.parse(body);
  } catch (error) {
    throw new Error(`Failed to fetch from Open-Meteo API: ${errorToString(error)}`);
  }
}
function toNumber(value) {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }
  if (typeof value === "string") {
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}
function toString(value) {
  if (typeof value === "string") {
    return value;
  }
  if (value === null || value === void 0) {
    return "";
  }
  return String(value);
}
function errorToString(error) {
  if (typeof error === "string") {
    return error;
  }
  if (typeof error === "object" && error !== null) {
    const maybe = error;
    if (typeof maybe.message === "string") {
      return maybe.message;
    }
  }
  return String(error);
}
registerExports("weather", [
  "get_current_weather",
  "get_hourly_forecast"
]);
