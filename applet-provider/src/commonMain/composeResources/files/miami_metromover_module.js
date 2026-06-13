// Generated from modules-ts (miamiMetromover). Do not edit manually.
"use strict";
const API_ROOT = "https://www.miamidade.gov/transit/mobile/xml";
const STATIONS_URL = "http://www.miamidade.gov/transit/WebServices/MoverStations/";
const LOOPS_URL = "http://www.miamidade.gov/transit/WebServices/MoverMapShapeLoops/";
const LOOP_SHAPE_URL = "http://www.miamidade.gov/transit/WebServices/MoverMapShape/";
const CACHE_TTL_MS = 60 * 60 * 1e3;
const PREFIXES = ["first", "second", "third", "forth", "fifth"];
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
async function list_stations() {
  const cached = validCache(stationsCache);
  if (cached) return cached;
  const stations = parseStationsXml(await fetchXml(STATIONS_URL));
  stationsCache = setCache(stations);
  return stations;
}
async function list_loops() {
  const cached = validCache(loopsCache);
  if (cached) return cached;
  const loops = parseShapeLoopsXml(await fetchXml(LOOPS_URL));
  loopsCache = setCache(loops);
  return loops;
}
async function get_trains(train_id) {
  let trainId = train_id;
  if (trainId === "null") {
    trainId = null;
  }
  const query = trainId ? `?TrainID=${encodeURIComponent(trainId)}` : "";
  const xml = await fetchXml(`${API_ROOT}/MoverTrains/${query}`);
  return parseTrainsXml(xml);
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
  const xml = await fetchXml(`${API_ROOT}/MoverTracker/?StationID=${encodeURIComponent(normalized)}`);
  return {
    stationId: normalized,
    stationTitle: station.title,
    arrivals: parseArrivalsXml(xml)
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
  const xml = await fetchXml(`${LOOP_SHAPE_URL}?LoopID=${encodeURIComponent(loopId)}`);
  const points = parseLoopShapeXml(xml);
  loopShapeCache.set(loopId, setCache(points));
  return points;
}
async function fetchXml(url) {
  try {
    return await fetchXmlOnce(url);
  } catch (error) {
    const fallbackUrl = swapScheme(url);
    if (fallbackUrl !== url) {
      try {
        return await fetchXmlOnce(fallbackUrl);
      } catch (fallbackError) {
        throw new Error(`Failed to fetch '${url}'. Fallback '${fallbackUrl}' failed: ${errorToString(fallbackError)}`);
      }
    }
    throw new Error(`Failed to fetch '${url}': ${errorToString(error)}`);
  }
}
async function fetchXmlOnce(url) {
  const body = await httpGet(url);
  if (!body || !body.trim()) {
    throw new Error("Empty response");
  }
  if (body.startsWith("Error:")) {
    throw new Error(body);
  }
  return body;
}
function parseTrainsXml(xml) {
  const trains = [];
  const parsed = xmlParse(xml);
  const rootKey = Object.keys(parsed)[0];
  const root = rootKey ? parsed[rootKey] : {};
  const trainElements = root["Train"];
  const trainArray = Array.isArray(trainElements) ? trainElements : trainElements ? [trainElements] : [];
  for (const trainEl of trainArray) {
    const trainIdText = extractText(trainEl, "TrainID");
    if (!trainIdText) continue;
    trains.push({
      id: parseIntSafe(trainIdText),
      latitude: parseFloatSafe(extractText(trainEl, "Latitude")),
      longitude: stringToDouble(extractText(trainEl, "Longitude")),
      loopId: extractText(trainEl, "LoopID") || void 0,
      loopName: extractText(trainEl, "LoopName") || void 0,
      direction: extractText(trainEl, "vehDirection") || void 0
    });
  }
  return trains;
}
function parseArrivalsXml(xml) {
  const parsed = xmlParse(xml);
  const rootKey = Object.keys(parsed)[0];
  const root = rootKey ? parsed[rootKey] : {};
  const infoEl = root["Info"];
  if (!infoEl) return [];
  const infoBlock = typeof infoEl === "object" ? infoEl : {};
  const arrivals = [];
  for (const prefix of PREFIXES) {
    const loopId = extractText(infoBlock, `${prefix}LoopID`);
    if (!loopId) break;
    const loopName = extractText(infoBlock, `${prefix}LoopName`);
    if (!loopName || loopName === "*****") break;
    const time1 = extractText(infoBlock, `${prefix}Time1`);
    const time2 = extractText(infoBlock, `${prefix}Time2`);
    const times = [time1, time2].filter((value) => Boolean(value));
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
function parseStationsXml(xml) {
  const stations = [];
  const parsed = xmlParse(xml);
  const rootKey = Object.keys(parsed)[0];
  const root = rootKey ? parsed[rootKey] : {};
  const recordElements = root["Record"];
  const recordArray = Array.isArray(recordElements) ? recordElements : recordElements ? [recordElements] : [];
  for (let i = 0; i < recordArray.length; i++) {
    const block = recordArray[i];
    const stationId = extractText(block, "StationID");
    const title = extractText(block, "Station");
    const latitudeRaw = extractText(block, "Latitude");
    const longitudeRaw = extractText(block, "Longitude");
    if (!stationId || !title || !latitudeRaw || !longitudeRaw) {
      continue;
    }
    const lat = parseFloatSafe(latitudeRaw);
    const lon = stringToDouble(longitudeRaw);
    stations.push({
      id: stationId,
      title,
      latitude: lat,
      longitude: lon,
      address: extractText(block, "Address") || void 0,
      city: extractText(block, "City") || void 0,
      state: extractText(block, "State") || void 0,
      zip: extractText(block, "Zip") || void 0,
      stationIdShow: extractText(block, "StationIDshow") || void 0,
      connectingOther: extractText(block, "ConnectingOther") || void 0,
      placesOfInterest: extractText(block, "PlacesOfInterest") || void 0,
      other: extractText(block, "Other") || void 0
    });
  }
  return stations;
}
function parseShapeLoopsXml(xml) {
  const loops = [];
  const parsed = xmlParse(xml);
  const rootKey = Object.keys(parsed)[0];
  const root = rootKey ? parsed[rootKey] : {};
  const recordElements = root["Record"];
  const recordArray = Array.isArray(recordElements) ? recordElements : recordElements ? [recordElements] : [];
  for (const block of recordArray) {
    const loopId = extractText(block, "LoopID");
    if (loopId) loops.push(loopId);
  }
  return loops;
}
function parseLoopShapeXml(xml) {
  const points = [];
  const parsed = xmlParse(xml);
  const rootKey = Object.keys(parsed)[0];
  const root = rootKey ? parsed[rootKey] : {};
  const recordElements = root["Record"];
  const recordArray = Array.isArray(recordElements) ? recordElements : recordElements ? [recordElements] : [];
  for (const block of recordArray) {
    const loopId = extractText(block, "LoopID");
    const orderRaw = extractText(block, "OrderNum");
    const latitudeRaw = extractText(block, "Latitude");
    const longitudeRaw = extractText(block, "Longitude");
    if (!loopId || !orderRaw || !latitudeRaw || !longitudeRaw) continue;
    points.push({
      loopId,
      order: parseIntSafe(orderRaw),
      latitude: parseFloatSafe(latitudeRaw),
      longitude: stringToDouble(longitudeRaw)
    });
  }
  points.sort((left, right) => left.order - right.order);
  return points;
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
function swapScheme(url) {
  if (url.startsWith("http://")) {
    return `https://${url.slice("http://".length)}`;
  }
  if (url.startsWith("https://")) {
    return `http://${url.slice("https://".length)}`;
  }
  return url;
}
function normalizeId(value) {
  return (value || "").trim().toUpperCase();
}
function parseFloatSafe(value) {
  if (!value) {
    return 0;
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}
function stringToDouble(value) {
  if (value === null || value === void 0 || value === "") {
    return 0;
  }
  if (typeof value !== "string") {
    value = String(value);
  }
  return parseFloat(value);
}
function parseIntSafe(value) {
  if (!value) return 0;
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : 0;
}
function toRadians(value) {
  return value * Math.PI / 180;
}
function round3(value) {
  return Math.round(value * 1e3) / 1e3;
}
function extractText(element, tagName) {
  const child = element[tagName];
  if (child === null || child === void 0) return null;
  if (typeof child === "string") {
    return child.length > 0 ? child : null;
  }
  if (typeof child === "number") {
    return String(child);
  }
  if (Array.isArray(child)) {
    if (child.length > 0) {
      const first = child[0];
      if (typeof first === "string") {
        return first.length > 0 ? first : null;
      }
      if (typeof first === "number") {
        return String(first);
      }
      if (typeof first === "object" && first !== null) {
        const obj = first;
        const text = obj["#text"];
        if (typeof text === "string" && text.length > 0) {
          return text;
        }
        if (typeof text === "number") {
          return String(text);
        }
      }
    }
    return null;
  }
  if (typeof child === "object" && child !== null) {
    const obj = child;
    const text = obj["#text"];
    if (text !== null && text !== void 0) {
      if (typeof text === "string") {
        return text.length > 0 ? text : null;
      }
      if (typeof text === "number") {
        return String(text);
      }
    }
    const alternatives = ["text", "_text", "value", "content"];
    for (const alt of alternatives) {
      const altValue = obj[alt];
      if (altValue !== null && altValue !== void 0) {
        if (typeof altValue === "string") {
          return altValue.length > 0 ? altValue : null;
        }
        if (typeof altValue === "number") {
          return String(altValue);
        }
      }
    }
  }
  return null;
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
registerExports("miamiMetromover", [
  "list_stations",
  "list_loops",
  "get_trains",
  "get_station_arrivals",
  "get_loop_svg",
  "find_nearest_station"
]);
