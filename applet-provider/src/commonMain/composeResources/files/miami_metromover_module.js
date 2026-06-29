// Generated from modules-ts (miamiMetromover). Do not edit manually.
"use strict";
const API_ROOT = "https://www.miamidade.gov/apps/dtpw/transitapps/api/mover";
const STATIONS_URL = `${API_ROOT}/stations`;
const SHAPE_URL = `${API_ROOT}/shape?routeId=&mapMode=light`;
const VEHICLES_URL = `${API_ROOT}/vehicles?routeId=&mapMode=light&track=YES&curLatitude=0&curLongitude=0`;
const TRACKER_URL = `${API_ROOT}/tracker`;
const CACHE_TTL_MS = 60 * 60 * 1e3;
let stationsCache = null;
let loopsCache = null;
const loopShapeCache = /* @__PURE__ */ new Map();
function nowMs() {
  return Date.now();
}
function validCache(entry) {
  if (!entry) return null;
  return nowMs() <= entry.expiresAt ? entry.value : null;
}
function setCache(value) {
  return {
    value,
    expiresAt: nowMs() + CACHE_TTL_MS
  };
}
async function getApiKey() {
  try {
    const key = await persistentStorageGet("feature", "metromover_api_key");
    return key || "P98EG7NGA9A02NAE00Y";
  } catch (e) {
    return "P98EG7NGA9A02NAE00Y";
  }
}
async function fetchJson(url) {
  const apiKey = await getApiKey();
  const headers = {
    "accept": "*/*",
    "x-api-key": apiKey
  };
  const body = await httpGet(url, headers);
  if (!body || !body.trim()) {
    throw new Error("Empty response");
  }
  if (body.startsWith("Error:")) {
    throw new Error(body);
  }
  return body;
}
async function list_stations() {
  const cached = validCache(stationsCache);
  if (cached) return cached;
  const jsonStr = await fetchJson(STATIONS_URL);
  const stations = parseStationsJson(jsonStr);
  stationsCache = setCache(stations);
  return stations;
}
async function list_loops() {
  const cached = validCache(loopsCache);
  if (cached) return cached;
  const jsonStr = await fetchJson(SHAPE_URL);
  const loops = parseShapeLoopsJson(jsonStr);
  loopsCache = setCache(loops);
  return loops;
}
async function get_trains(train_id) {
  let trainId = train_id;
  if (trainId === "null") {
    trainId = null;
  }
  const jsonStr = await fetchJson(VEHICLES_URL);
  let trains = parseTrainsJson(jsonStr);
  if (trainId) {
    const idNum = parseIntSafe(trainId);
    trains = trains.filter((t) => t.id === idNum);
  }
  return trains;
}
async function get_station_arrivals(station_id) {
  const normalized = normalizeId(station_id);
  if (!normalized) {
    throw new Error("station_id is required.");
  }
  const stations = await list_stations();
  const station = stations.find((item) => normalizeId(item.id) === normalized);
  if (!station) {
    throw new Error(`Unknown station_id '${station_id}'.`);
  }
  const url = `${TRACKER_URL}?stationID=${encodeURIComponent(normalized)}&track=YES`;
  const jsonStr = await fetchJson(url);
  return {
    stationId: normalized,
    stationTitle: station.title,
    arrivals: parseArrivalsJson(jsonStr)
  };
}
async function get_loop_svg(loop_id, width = 800, height = 800, padding = 12) {
  const normalized = normalizeId(loop_id);
  if (!normalized) {
    throw new Error("loop_id is required.");
  }
  if (width <= 0 || height <= 0) {
    throw new Error("width and height must be greater than 0.");
  }
  if (padding < 0) {
    throw new Error("padding must be 0 or greater.");
  }
  const loops = await list_loops();
  if (!loops.includes(normalized)) {
    throw new Error(`Unknown loop_id '${loop_id}'.`);
  }
  const shapePoints = await getLoopShape(normalized);
  if (shapePoints.length < 2) {
    throw new Error(`Loop '${normalized}' shape has too few points.`);
  }
  const svg = shapeToSvg(shapePoints, normalized, width, height, padding);
  return {
    loopId: normalized,
    pointCount: shapePoints.length,
    svg
  };
}
async function find_nearest_station(latitude, longitude, max_distance_km) {
  const lat = Number(latitude);
  const lon = Number(longitude);
  if (!Number.isFinite(lat)) {
    throw new Error(`Invalid latitude: ${latitude} (type: ${typeof latitude})`);
  }
  if (!Number.isFinite(lon)) {
    throw new Error(`Invalid longitude: ${longitude} (type: ${typeof longitude})`);
  }
  if (max_distance_km != null && max_distance_km <= 0) {
    throw new Error("max_distance_km must be greater than 0.");
  }
  const stations = await list_stations();
  if (stations.length === 0) {
    throw new Error("No MetroMover stations available.");
  }
  let nearest = null;
  let nearestDistance = Number.POSITIVE_INFINITY;
  for (const station of stations) {
    const stationLat = Number(station.latitude);
    const stationLon = Number(station.longitude);
    if (!Number.isFinite(stationLat) || !Number.isFinite(stationLon)) {
      continue;
    }
    const distanceKm = haversineKm(lat, lon, stationLat, stationLon);
    if (distanceKm < nearestDistance) {
      nearestDistance = distanceKm;
      nearest = station;
    }
  }
  if (!nearest) {
    throw new Error("No station with coordinates available.");
  }
  if (max_distance_km != null && nearestDistance > max_distance_km) {
    throw new Error(
      `No station found within ${max_distance_km.toFixed(3)} km (nearest is ${nearestDistance.toFixed(3)} km).`
    );
  }
  return {
    station: nearest,
    distanceKm: round3(nearestDistance)
  };
}
async function getLoopShape(loopId) {
  var _a;
  const cached = validCache((_a = loopShapeCache.get(loopId)) != null ? _a : null);
  if (cached) return cached;
  const url = `${API_ROOT}/shape?routeId=${encodeURIComponent(loopId)}&mapMode=light`;
  const jsonStr = await fetchJson(url);
  const points = parseLoopShapeJson(jsonStr, loopId);
  loopShapeCache.set(loopId, setCache(points));
  return points;
}
function parseStationsJson(jsonStr) {
  const data = JSON.parse(jsonStr);
  const stations = [];
  for (const item of data) {
    const stationId = item.StationID;
    const title = item.Station;
    const lat = item.Lat;
    const lon = item.Long;
    if (!stationId || !title || lat === void 0 || lon === void 0) {
      continue;
    }
    stations.push({
      id: stationId,
      title,
      latitude: lat,
      longitude: lon,
      address: item.Address || void 0,
      city: item.City || void 0,
      state: item.State || void 0,
      zip: item.Zip || void 0,
      stationIdShow: item.StationIDshow || void 0,
      connectingOther: item.ConnectingOther || void 0,
      placesOfInterest: item.PlacesOfInterest || void 0,
      other: item.Other || void 0
    });
  }
  return stations;
}
function parseShapeLoopsJson(jsonStr) {
  const data = JSON.parse(jsonStr);
  const loops = [];
  for (const item of data) {
    if (item.RouteID && !loops.includes(item.RouteID)) {
      loops.push(item.RouteID);
    }
  }
  return loops;
}
function parseLoopShapeJson(jsonStr, loopId) {
  const data = JSON.parse(jsonStr);
  const points = [];
  const route = data.find((item) => normalizeId(item.RouteID || "") === normalizeId(loopId));
  if (route && route.Points) {
    route.Points.forEach((pt, index) => {
      if (pt.Latitude !== void 0 && pt.Longitude !== void 0) {
        points.push({
          loopId,
          order: index + 1,
          latitude: pt.Latitude,
          longitude: pt.Longitude
        });
      }
    });
  }
  return points;
}
function parseTrainsJson(jsonStr) {
  const data = JSON.parse(jsonStr);
  const trains = [];
  const loopNames = {
    "OMN": "Omni",
    "BKL": "Brickell",
    "INN": "Inner"
  };
  for (const item of data) {
    if (item.OutOfService === "1") {
      continue;
    }
    const idVal = parseIntSafe(item.ID || "");
    if (!idVal || item.Latitude === void 0 || item.Longitude === void 0) {
      continue;
    }
    trains.push({
      id: idVal,
      latitude: item.Latitude,
      longitude: item.Longitude,
      loopId: item.ShapeID || void 0,
      loopName: item.ShapeID ? loopNames[item.ShapeID] || item.ShapeID : void 0
    });
  }
  return trains;
}
function parseArrivalsJson(jsonStr) {
  const data = JSON.parse(jsonStr);
  const arrivals = [];
  for (const item of data) {
    const loopId = item.LoopID;
    const loopName = item.LoopName;
    if (!loopId || !loopName || loopName === "***") {
      continue;
    }
    const times = [];
    if (item.Time1Est && item.Time1Est.trim() !== "") {
      times.push(item.Time1Est.trim());
    } else if (item.Estimate1 !== null && item.Estimate1 !== void 0 && String(item.Estimate1).trim() !== "") {
      times.push(`${String(item.Estimate1).trim()} min`);
    } else if (item.ArrivalTime1 && item.ArrivalTime1.trim() !== "") {
      times.push(item.ArrivalTime1.trim());
    }
    if (item.Time2Est && item.Time2Est.trim() !== "") {
      times.push(item.Time2Est.trim());
    } else if (item.Estimate2 !== null && item.Estimate2 !== void 0 && String(item.Estimate2).trim() !== "") {
      times.push(`${String(item.Estimate2).trim()} min`);
    } else if (item.ArrivalTime2 && item.ArrivalTime2.trim() !== "") {
      times.push(item.ArrivalTime2.trim());
    }
    if (times.length > 0) {
      arrivals.push({
        loopId,
        loopName,
        arrivals: times
      });
    }
  }
  return arrivals;
}
function haversineKm(lat1, lon1, lat2, lon2) {
  const earthRadiusKm = 6371;
  const lat1Rad = toRadians(lat1);
  const lon1Rad = toRadians(lon1);
  const lat2Rad = toRadians(lat2);
  const lon2Rad = toRadians(lon2);
  const dLat = lat2Rad - lat1Rad;
  const dLon = lon2Rad - lon1Rad;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return earthRadiusKm * c;
}
function shapeToSvg(points, loopId, width, height, padding) {
  const coords = points.filter((point) => {
    const lon = Number(point.longitude);
    const lat = Number(point.latitude);
    return Number.isFinite(lon) && Number.isFinite(lat);
  }).map((point) => ({ lon: Number(point.longitude), lat: Number(point.latitude) }));
  if (coords.length < 2) {
    throw new Error(`Loop '${loopId}' has no drawable coordinates.`);
  }
  const longitudes = coords.map((item) => item.lon);
  const latitudes = coords.map((item) => item.lat);
  const minLon = Math.min(...longitudes);
  const maxLon = Math.max(...longitudes);
  const minLat = Math.min(...latitudes);
  const maxLat = Math.max(...latitudes);
  const lonSpan = Math.max(maxLon - minLon, 1e-9);
  const latSpan = Math.max(maxLat - minLat, 1e-9);
  const drawableWidth = Math.max(width - 2 * padding, 1);
  const drawableHeight = Math.max(height - 2 * padding, 1);
  const scale = Math.min(drawableWidth / lonSpan, drawableHeight / latSpan);
  const xOffset = (width - lonSpan * scale) / 2;
  const yOffset = (height - latSpan * scale) / 2;
  const polyline = coords.map((item) => {
    const x = xOffset + (item.lon - minLon) * scale;
    const y = yOffset + (maxLat - item.lat) * scale;
    return `${x.toFixed(2)},${y.toFixed(2)}`;
  }).join(" ");
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img" aria-label="MetroMover loop ${loopId}"><rect width="100%" height="100%" fill="white"/><polyline fill="none" stroke="#006600" stroke-width="2" points="${polyline}"/></svg>`;
}
function normalizeId(value) {
  return (value || "").trim().toUpperCase();
}
function parseFloatSafe(value) {
  if (value === null || value === void 0) {
    return 0;
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}
function parseIntSafe(value) {
  if (value === null || value === void 0) return 0;
  if (typeof value === "number") {
    return Number.isFinite(value) ? Math.floor(value) : 0;
  }
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : 0;
}
function toRadians(value) {
  return value * Math.PI / 180;
}
function round3(value) {
  return Math.round(value * 1e3) / 1e3;
}
registerExports("miamiMetromover", [
  "list_stations",
  "list_loops",
  "get_trains",
  "get_station_arrivals",
  "get_loop_svg",
  "find_nearest_station"
]);
