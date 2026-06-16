const API_BASE_URL = "https://api.open-meteo.com/v1/forecast";
const USER_AGENT = "weather-module/1.0";

// Host functions - declared here and implemented in Kotlin
declare function httpGet(url: string): Promise<string>;
declare function consoleLog(args: any[]): void;
declare function consoleError(args: any[]): void;

type UnknownRecord = Record<string, unknown>;

type CurrentWeatherData = {
  temperature_2m: number;
  relative_humidity_2m?: number;
  apparent_temperature?: number;
  rain?: number;
  showers?: number;
  snowfall?: number;
  precipitation_probability?: number;
  wind_speed_10m?: number;
  time?: string;
  weathercode?: number;
};

type HourlyWeatherRow = {
  time: string;
  temperature_2m?: number;
  rain?: number;
  showers?: number;
  snowfall?: number;
  precipitation_probability?: number;
  apparent_temperature?: number;
  relative_humidity_2m?: number;
  wind_speed_10m?: number;
  weathercode?: number;
};

type DailyWeatherRow = {
  time: string;
  temperature_2m_max: number;
  temperature_2m_min: number;
  weathercode: number;
  precipitation_sum?: number;
  precipitation_probability_max?: number;
  sunrise?: string;
  sunset?: string;
};

type DailyForecastResult = {
  latitude: number;
  longitude: number;
  timezone: string;
  timezoneAbbreviation?: string;
  daily: DailyWeatherRow[];
};

type CurrentWeatherResult = {
  latitude: number;
  longitude: number;
  timezone: string;
  timezoneAbbreviation?: string;
  current: CurrentWeatherData;
};

type HourlyForecastResult = {
  latitude: number;
  longitude: number;
  timezone: string;
  timezoneAbbreviation?: string;
  hourly: HourlyWeatherRow[];
  requestedHours: number;
  availableHours: number;
};

const HOURLY_FIELDS = [
  "temperature_2m",
  "rain",
  "showers",
  "snowfall",
  "precipitation_probability",
  "apparent_temperature",
  "relative_humidity_2m",
  "wind_speed_10m",
  "weathercode"
];

const CURRENT_FIELDS = [
  "temperature_2m",
  "relative_humidity_2m",
  "apparent_temperature",
  "rain",
  "showers",
  "snowfall",
  "precipitation_probability",
  "wind_speed_10m",
  "weathercode"
];

const DAILY_FIELDS = [
  "temperature_2m_max",
  "temperature_2m_min",
  "weathercode",
  "precipitation_sum",
  "precipitation_probability_max",
  "sunrise",
  "sunset"
];

/**
 * Get current weather for given coordinates.
 *
 * @param latitude - Station latitude (e.g., 25.7743 for Miami)
 * @param longitude - Station longitude (e.g., -80.1937 for Miami)
 * @param timezone - Optional timezone (default: "auto")
 * @returns Current weather data including temperature, humidity, wind speed
 * @throws If coordinates are invalid or API request fails
 */
async function get_current_weather(
  latitude: number,
  longitude: number,
  timezone: string = "auto"
): Promise<CurrentWeatherResult> {
  validateCoordinates(latitude, longitude);

  const params = buildQueryString({
    latitude: String(latitude),
    longitude: String(longitude),
    current: CURRENT_FIELDS.join(","),
    timezone: timezone
  });

  const url = `${API_BASE_URL}?${params}`;
  const response = await fetchJson(url);

  if (!response || typeof response !== "object") {
    throw new Error("Invalid API response format");
  }

  const result = response as UnknownRecord;
  const current = result.current as UnknownRecord | undefined;

  if (!current || typeof current !== "object") {
    throw new Error("API response did not contain a current weather block");
  }

  return {
    latitude: toNumber(result.latitude),
    longitude: toNumber(result.longitude),
    timezone: toString(result.timezone),
    timezoneAbbreviation: toString(result.timezone_abbreviation),
    current: current as CurrentWeatherData
  };
}

/**
 * Get hourly weather forecast for given coordinates.
 *
 * @param latitude - Station latitude
 * @param longitude - Station longitude
 * @param hours - Number of hours to forecast (1-240, default: 24)
 * @param timezone - Optional timezone (default: "auto")
 * @returns Hourly forecast data
 * @throws If coordinates are invalid, hours out of range, or API request fails
 */
async function get_hourly_forecast(
  latitude: number,
  longitude: number,
  hours: number = 24,
  timezone: string = "auto"
): Promise<HourlyForecastResult> {
  validateCoordinates(latitude, longitude);

  if (hours <= 0 || hours > 240) {
    throw new Error("hours must be between 1 and 240");
  }

  const params = buildQueryString({
    latitude: String(latitude),
    longitude: String(longitude),
    hourly: HOURLY_FIELDS.join(","),
    timezone: timezone
  });

  const url = `${API_BASE_URL}?${params}`;
  const response = await fetchJson(url);

  if (!response || typeof response !== "object") {
    throw new Error("Invalid API response format");
  }

  const result = response as UnknownRecord;
  const hourly = result.hourly as UnknownRecord | undefined;

  if (!hourly || typeof hourly !== "object") {
    throw new Error("API response did not contain an hourly forecast block");
  }

  const times = hourly.time;
  if (!Array.isArray(times)) {
    throw new Error("Hourly forecast did not contain a time axis");
  }

  const maxLen = Math.min(hours, times.length);
  const rows: HourlyWeatherRow[] = [];

  for (let i = 0; i < maxLen; i++) {
    const row: HourlyWeatherRow = {
      time: toString(times[i])
    };

    for (const field of HOURLY_FIELDS) {
      const values = hourly[field];
      if (Array.isArray(values) && i < values.length) {
        const value = values[i];
        row[field as keyof HourlyWeatherRow] = typeof value === "number" ? value : undefined;
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

/**
 * Get daily weather forecast for given coordinates.
 *
 * @param latitude - Station latitude
 * @param longitude - Station longitude
 * @param timezone - Optional timezone (default: "auto")
 * @returns Daily forecast data including min/max temperatures and weathercode
 * @throws If coordinates are invalid or API request fails
 */
async function get_daily_forecast(
  latitude: number,
  longitude: number,
  timezone: string = "auto"
): Promise<DailyForecastResult> {
  validateCoordinates(latitude, longitude);

  const params = buildQueryString({
    latitude: String(latitude),
    longitude: String(longitude),
    daily: DAILY_FIELDS.join(","),
    timezone: timezone
  });

  const url = `${API_BASE_URL}?${params}`;
  const response = await fetchJson(url);

  if (!response || typeof response !== "object") {
    throw new Error("Invalid API response format");
  }

  const result = response as UnknownRecord;
  const daily = result.daily as UnknownRecord | undefined;

  if (!daily || typeof daily !== "object") {
    throw new Error("API response did not contain a daily forecast block");
  }

  const times = daily.time;
  if (!Array.isArray(times)) {
    throw new Error("Daily forecast did not contain a time axis");
  }

  const rows: DailyWeatherRow[] = [];
  for (let i = 0; i < times.length; i++) {
    rows.push({
      time: toString(times[i]),
      temperature_2m_max: toNumber(Array.isArray(daily.temperature_2m_max) ? daily.temperature_2m_max[i] : 0),
      temperature_2m_min: toNumber(Array.isArray(daily.temperature_2m_min) ? daily.temperature_2m_min[i] : 0),
      weathercode: toNumber(Array.isArray(daily.weathercode) ? daily.weathercode[i] : 0),
      precipitation_sum: toNumber(Array.isArray(daily.precipitation_sum) ? daily.precipitation_sum[i] : 0),
      precipitation_probability_max: toNumber(Array.isArray(daily.precipitation_probability_max) ? daily.precipitation_probability_max[i] : 0),
      sunrise: toString(Array.isArray(daily.sunrise) ? daily.sunrise[i] : ""),
      sunset: toString(Array.isArray(daily.sunset) ? daily.sunset[i] : "")
    });
  }

  return {
    latitude: toNumber(result.latitude),
    longitude: toNumber(result.longitude),
    timezone: toString(result.timezone),
    timezoneAbbreviation: toString(result.timezone_abbreviation),
    daily: rows
  };
}

/**
 * Build a query string from key-value pairs without URLSearchParams.
 */
function buildQueryString(params: Record<string, string>): string {
  const pairs: string[] = [];
  const keys = Object.keys(params);
  for (let i = 0; i < keys.length; i++) {
    const k = keys[i];
    pairs.push(encodeURIComponent(k) + "=" + encodeURIComponent(params[k]));
  }
  return pairs.join("&");
}

/**
 * Validate that latitude and longitude are within valid ranges.
 */
function validateCoordinates(latitude: number, longitude: number): void {
  const lat = Number(latitude);
  const lon = Number(longitude);

  if (!Number.isFinite(lat) || lat < -90 || lat > 90) {
    throw new Error(`Invalid latitude: ${latitude} (must be between -90 and 90)`);
  }

  if (!Number.isFinite(lon) || lon < -180 || lon > 180) {
    throw new Error(`Invalid longitude: ${longitude} (must be between -180 and 180)`);
  }
}

/**
 * Fetch and parse JSON from URL.
 */
async function fetchJson(url: string): Promise<unknown> {
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

/**
 * Safely convert value to number.
 */
function toNumber(value: unknown): number {
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }
  if (typeof value === "string") {
    const parsed = Number.parseFloat(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

/**
 * Safely convert value to string.
 */
function toString(value: unknown): string {
  if (typeof value === "string") {
    return value;
  }
  if (value === null || value === undefined) {
    return "";
  }
  return String(value);
}

/**
 * Convert error to string message.
 */
function errorToString(error: unknown): string {
  if (typeof error === "string") {
    return error;
  }
  if (typeof error === "object" && error !== null) {
    const maybe = error as UnknownRecord;
    if (typeof maybe.message === "string") {
      return maybe.message;
    }
  }
  return String(error);
}

registerExports("weather", [
  "get_current_weather",
  "get_hourly_forecast",
  "get_daily_forecast"
]);

