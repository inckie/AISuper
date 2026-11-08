// Generated from modules-ts (miamiMetromover). Do not edit manually.
"use strict";
const BASE_ORIGIN = "https://www.miamidade.gov";
const API_ROOT = `${BASE_ORIGIN}/apps/dtpw/transitapps/api/mover`;
const TRACKING_URL = `${BASE_ORIGIN}/apps/dtpw/transitapps/DynamicImageTracking/index?value=www.miamidade.gov`;
const REFERER_URL = `${BASE_ORIGIN}/global/transportation/tracker/metromover-tracker.page?stationid=SCB`;
const STATIONS_URL = `${API_ROOT}/stations`;
const SHAPE_URL = `${API_ROOT}/shape?routeId=OMN&mapMode=light`;
const VEHICLES_URL = `${API_ROOT}/vehicles?routeId=&mapMode=light&track=YES&curLatitude=0&curLongitude=0`;
const TRACKER_URL = `${API_ROOT}/tracker`;
const DEFAULT_API_KEY = "P98EG7NGA9A02NAE00Y";
const CACHE_TTL_MS = 60 * 60 * 1e3;
const SESSION_TTL_MS = 20 * 60 * 1e3;
let stationsCache = null;
let loopsCache = null;
const loopShapeCache = /* @__PURE__ */ new Map();
let sessionCookie = null;
let sessionExpiresAt = 0;
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
    return key || DEFAULT_API_KEY;
  } catch (e) {
    return DEFAULT_API_KEY;
  }
}
async function ensureSession(force = false) {
  if (sessionCookie && !force && nowMs() < sessionExpiresAt) {
    return sessionCookie;
  }
  const headers = {
    "accept": "*/*",
    "referer": REFERER_URL,
    "origin": BASE_ORIGIN,
    "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"
  };
  try {
    if (typeof httpRequestRaw === "function") {
      const resStr = await httpRequestRaw("GET", TRACKING_URL, "", headers);
      if (resStr && resStr.trim()) {
        const parsed = JSON.parse(resStr);
        const rawHeaders = parsed.headers || {};
        const setCookie = rawHeaders["set-cookie"] || rawHeaders["Set-Cookie"];
        if (setCookie) {
          const cookieParts = [];
          const lines = String(setCookie).split(/[\r\n]+/);
          for (const line of lines) {
            const part = line.split(";")[0].trim();
            if (part && (part.startsWith(".AspNetCore.Session=") || part.startsWith("NSC_"))) {
              cookieParts.push(part);
            }
          }
          if (cookieParts.length > 0) {
            sessionCookie = cookieParts.join("; ");
            sessionExpiresAt = nowMs() + SESSION_TTL_MS;
            return sessionCookie;
          }
        }
      }
    }
  } catch (e) {
  }
  try {
    await httpGet(TRACKING_URL, headers);
    sessionExpiresAt = nowMs() + SESSION_TTL_MS;
  } catch (e) {
  }
  return sessionCookie;
}
async function fetchMetromoverJson(url) {
  const apiKey = await getApiKey();
  let cookie = await ensureSession(false);
  const buildHeaders = (c) => {
    const h = {
      "accept": "*/*",
      "x-api-key": apiKey,
      "referer": REFERER_URL,
      "origin": BASE_ORIGIN,
      "user-agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"
    };
    if (c) {
      h["cookie"] = c;
    }
    return h;
  };
  if (typeof httpRequestRaw === "function") {
    try {
      let resStr = await httpRequestRaw("GET", url, "", buildHeaders(cookie));
      if (resStr && resStr.trim()) {
        let parsed = JSON.parse(resStr);
        if (parsed.status === 401) {
          cookie = await ensureSession(true);
          resStr = await httpRequestRaw("GET", url, "", buildHeaders(cookie));
          parsed = JSON.parse(resStr);
        }
        if (parsed.status !== void 0 && parsed.status >= 200 && parsed.status < 300) {
          return parsed.body || "";
        }
        if (parsed.status) {
          throw new Error(`HTTP ${parsed.status}: ${parsed.body || parsed.error || ""}`);
        }
      }
    } catch (e) {
      if (String(e).includes("HTTP ")) {
        throw e;
      }
    }
  }
  let body = await httpGet(url, buildHeaders(cookie));
  if (body && (body.includes("401") || body.startsWith("Error: HTTP 401"))) {
    cookie = await ensureSession(true);
    body = await httpGet(url, buildHeaders(cookie));
  }
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
  const jsonStr = await fetchMetromoverJson(STATIONS_URL);
  const stations = parseStationsJson(jsonStr);
  stationsCache = setCache(stations);
  return stations;
}
let allShapesCache = null;
let allShapesExpiresAt = 0;
async function getAllShapes() {
  if (allShapesCache && nowMs() < allShapesExpiresAt) {
    return allShapesCache;
  }
  const jsonStr = await fetchMetromoverJson(SHAPE_URL);
  const data = JSON.parse(jsonStr);
  allShapesCache = data;
  allShapesExpiresAt = nowMs() + CACHE_TTL_MS;
  return data;
}
async function list_loops() {
  const cached = validCache(loopsCache);
  if (cached) return cached;
  const data = await getAllShapes();
  const loops = parseShapeLoopsJson(JSON.stringify(data));
  loopsCache = setCache(loops);
  return loops;
}
async function get_trains(train_id) {
  let trainId = train_id;
  if (trainId === "null") {
    trainId = null;
  }
  const jsonStr = await fetchMetromoverJson(VEHICLES_URL);
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
  const jsonStr = await fetchMetromoverJson(url);
  const parsed = parseArrivalsJson(jsonStr);
  return {
    stationId: normalized,
    stationTitle: station.title,
    arrivals: parsed.arrivals,
    messageAlert: parsed.messageAlert,
    messageFacilities: parsed.messageFacilities,
    messageServiceUpdate: parsed.messageServiceUpdate
  };
}
async function get_loop_svg(loop_id, width = 800, height = 800, padding = 16) {
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
  const shapeData = await getLoopShape(normalized);
  if (shapeData.points.length < 2) {
    throw new Error(`Loop '${normalized}' shape has too few points.`);
  }
  const { svg, svgDataUri } = shapeToSvg(shapeData.points, normalized, width, height, padding, shapeData.color);
  return {
    loopId: normalized,
    pointCount: shapeData.points.length,
    svg,
    svgDataUri
  };
}
function escapeXml(str) {
  return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&apos;");
}
async function get_network_svg(active_loops, highlight_station_id, width = 720, height = 720, padding = 24, include_trains = true) {
  var _a;
  const allShapes = await getAllShapes();
  const stations = await list_stations();
  const defaultColors = {
    "OMN": "#0066FF",
    "BKL": "#FF6600",
    "INN": "#009933",
    "FUL": "#9900FF",
    "OTR": "#2A3A8D"
  };
  const routeMap = /* @__PURE__ */ new Map();
  for (const route of allShapes) {
    const id = normalizeId(route.Id || route.RouteID || "");
    if (!id) continue;
    const rawColor = route.Color || defaultColors[id] || "#0066FF";
    const color = rawColor.startsWith("#") ? rawColor : `#${rawColor}`;
    const shapesList = route.Shapes || route.Points || [];
    const points = shapesList.filter((pt) => Number.isFinite(pt.Latitude) && Number.isFinite(pt.Longitude)).map((pt) => ({ lat: Number(pt.Latitude), lon: Number(pt.Longitude) }));
    if (points.length > 0) {
      routeMap.set(id, { id, color, points });
    }
  }
  let allLons = [];
  let allLats = [];
  for (const r of routeMap.values()) {
    allLons.push(...r.points.map((p) => p.lon));
    allLats.push(...r.points.map((p) => p.lat));
  }
  if (allLons.length === 0) {
    throw new Error("No shape coordinates available to synthesize network map.");
  }
  const minLon = Math.min(...allLons);
  const maxLon = Math.max(...allLons);
  const minLat = Math.min(...allLats);
  const maxLat = Math.max(...allLats);
  const avgLatRad = (minLat + maxLat) / 2 * (Math.PI / 180);
  const cosLat = Math.cos(avgLatRad);
  const lonSpan = Math.max((maxLon - minLon) * cosLat, 1e-9);
  const latSpan = Math.max(maxLat - minLat, 1e-9);
  const padY = Math.max(padding, 20);
  const padX = Math.max(padding, 40);
  const drawableW = Math.max(width - 2 * padX, 1);
  const drawableH = Math.max(height - 2 * padY, 1);
  const scale = Math.min(drawableW / lonSpan, drawableH / latSpan);
  const viewWidth = Math.round(lonSpan * scale + 2 * padX);
  const viewHeight = Math.round(latSpan * scale + 2 * padY);
  const project = (lat, lon) => {
    const x = padX + (lon - minLon) * cosLat * scale;
    const y = padY + (maxLat - lat) * scale;
    return [Math.round(x * 10) / 10, Math.round(y * 10) / 10];
  };
  const activeList = active_loops && active_loops.length > 0 ? active_loops.map(normalizeId) : ["OMN", "BKL", "INN"];
  const activeSet = new Set(activeList);
  const svgParts = [];
  svgParts.push(`<svg xmlns="http://www.w3.org/2000/svg" width="${viewWidth}" height="${viewHeight}" viewBox="0 0 ${viewWidth} ${viewHeight}" role="img" aria-label="Miami Metromover Network Map">`);
  for (const route of routeMap.values()) {
    const pointsStr = route.points.map((p) => project(p.lat, p.lon).join(",")).join(" ");
    svgParts.push(`<polyline fill="none" stroke="#CBD5E1" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" opacity="0.6" points="${pointsStr}"/>`);
  }
  for (const route of routeMap.values()) {
    if (!activeSet.has(route.id)) continue;
    const pointsStr = route.points.map((p) => project(p.lat, p.lon).join(",")).join(" ");
    svgParts.push(`<polyline fill="none" stroke="white" stroke-width="14" stroke-linecap="round" stroke-linejoin="round" opacity="0.95" points="${pointsStr}"/>`);
    svgParts.push(`<polyline fill="none" stroke="${route.color}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round" points="${pointsStr}"/>`);
  }
  const normHighlightId = highlight_station_id ? normalizeId(highlight_station_id) : "";
  let highlightedStationItem = null;
  for (const station of stations) {
    const lat = Number(station.latitude);
    const lon = Number(station.longitude);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    const [sx, sy] = project(lat, lon);
    if (normHighlightId && normalizeId(station.id) === normHighlightId) {
      highlightedStationItem = { title: station.title, x: sx, y: sy };
    } else {
      svgParts.push(`<circle cx="${sx}" cy="${sy}" r="4" fill="white" stroke="#334155" stroke-width="2"/>`);
    }
  }
  let liveTrainsCount = 0;
  if (include_trains) {
    try {
      const trains = await get_trains();
      for (const train of trains) {
        if (!Number.isFinite(train.latitude) || !Number.isFinite(train.longitude)) continue;
        const loopId = normalizeId(train.loopId || "");
        if (loopId && !activeSet.has(loopId) && activeSet.size > 0) continue;
        const [tx, ty] = project(train.latitude, train.longitude);
        const trainColor = loopId && ((_a = routeMap.get(loopId)) == null ? void 0 : _a.color) || "#1E293B";
        svgParts.push(`<circle cx="${tx}" cy="${ty}" r="7" fill="${trainColor}" stroke="white" stroke-width="2"/>`);
        svgParts.push(`<circle cx="${tx}" cy="${ty}" r="2.5" fill="white"/>`);
        liveTrainsCount++;
      }
    } catch (_) {
    }
  }
  if (highlightedStationItem) {
    const { title, x, y } = highlightedStationItem;
    svgParts.push(`<circle cx="${x}" cy="${y}" r="8" fill="#FF6600" stroke="white" stroke-width="2.5"/>`);
    const textWidth = Math.min(title.length * 7 + 12, 140);
    const labelX = x + 12 + textWidth > viewWidth ? x - textWidth - 12 : x + 12;
    svgParts.push(`<rect x="${labelX}" y="${y - 10}" width="${textWidth}" height="20" rx="4" fill="#0F172A" opacity="0.85"/>`);
    svgParts.push(`<text x="${labelX + 6}" y="${y + 4}" font-family="system-ui, -apple-system, sans-serif" font-size="11" font-weight="bold" fill="white">${escapeXml(title)}</text>`);
  }
  svgParts.push("</svg>");
  const svg = svgParts.join("");
  const svgDataUri = `data:image/svg+xml;base64,${toBase64(svg)}`;
  return {
    activeLoops: activeList,
    svg,
    svgDataUri,
    viewBox: `0 0 ${viewWidth} ${viewHeight}`,
    stationCount: stations.length,
    trainCount: liveTrainsCount
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
  const normId = normalizeId(loopId);
  const cached = validCache((_a = loopShapeCache.get(normId)) != null ? _a : null);
  if (cached) return cached;
  const data = await getAllShapes();
  const shapeResult = parseLoopShapeJson(JSON.stringify(data), normId);
  loopShapeCache.set(normId, setCache(shapeResult));
  return shapeResult;
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
      connecting: item.Connecting || void 0,
      connectingOther: item.ConnectingOther || void 0,
      placesOfInterest: item.PlacesOfInterest || void 0,
      other: item.Other || void 0,
      directionsLink: item.DirectionsLink || void 0,
      streetViewLink: item.StreetViewLink || void 0
    });
  }
  return stations;
}
function parseShapeLoopsJson(jsonStr) {
  const data = JSON.parse(jsonStr);
  const loops = [];
  const priority = ["OMN", "BKL", "INN"];
  for (const item of data) {
    const routeId = item.Id || item.RouteID;
    if (routeId && !loops.includes(routeId)) {
      loops.push(routeId);
    }
  }
  loops.sort((a, b) => {
    const ia = priority.indexOf(a);
    const ib = priority.indexOf(b);
    if (ia !== -1 && ib !== -1) return ia - ib;
    if (ia !== -1) return -1;
    if (ib !== -1) return 1;
    return a.localeCompare(b);
  });
  return loops;
}
function parseLoopShapeJson(jsonStr, loopId) {
  const data = JSON.parse(jsonStr);
  const points = [];
  let color;
  const route = data.find((item) => normalizeId(item.Id || item.RouteID || "") === normalizeId(loopId));
  if (route) {
    color = route.Color;
    const pointsList = route.Shapes || route.Points || [];
    pointsList.forEach((pt, index) => {
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
  return { points, color };
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
    let direction;
    if (item.RouteImage) {
      const match = item.RouteImage.match(/solid-(SB|NB|EB|WB)/i);
      if (match) direction = match[1].toUpperCase();
    }
    if (!direction && item.Info) {
      const match = item.Info.match(/\((SB|NB|EB|WB)\)/i);
      if (match) direction = match[1].toUpperCase();
    }
    trains.push({
      id: idVal,
      latitude: item.Latitude,
      longitude: item.Longitude,
      loopId: item.ShapeID || void 0,
      loopName: item.ShapeID ? loopNames[item.ShapeID] || item.ShapeID : void 0,
      direction,
      cars: item.vehicleNumber || void 0,
      updated: item.CarsUpdated || void 0
    });
  }
  return trains;
}
function parseArrivalsJson(jsonStr) {
  const data = JSON.parse(jsonStr);
  const arrivals = [];
  let messageAlert;
  let messageFacilities;
  let messageServiceUpdate;
  if (Array.isArray(data) && data.length > 0) {
    if (data[0].MessageAlert) messageAlert = data[0].MessageAlert;
    if (data[0].MessageFacilities) messageFacilities = data[0].MessageFacilities;
    if (data[0].MessageServiceUpdate) messageServiceUpdate = data[0].MessageServiceUpdate;
  }
  for (const item of data) {
    const loopId = item.LoopID;
    const baseLoopName = item.LoopName;
    if (!loopId || !baseLoopName || baseLoopName === "***") {
      continue;
    }
    const direction = item.Direction ? item.Direction.trim() : void 0;
    const color = item.Color ? item.Color.trim() : void 0;
    const loopName = direction ? `${baseLoopName} (${direction})` : baseLoopName;
    const times = [];
    if (item.Estimate1 !== null && item.Estimate1 !== void 0 && String(item.Estimate1).trim() !== "" && String(item.Estimate1).trim() !== "-") {
      const est1 = String(item.Estimate1).trim();
      times.push(est1.toLowerCase().includes("min") ? est1 : `${est1} min`);
    } else if (item.Time1Est !== null && item.Time1Est !== void 0 && String(item.Time1Est).trim() !== "") {
      const val1 = String(item.Time1Est).trim();
      if (val1.toLowerCase().includes("min")) {
        times.push(val1);
      } else {
        const secs = Number(val1);
        if (!isNaN(secs)) {
          times.push(`${Math.round(secs / 60)} min`);
        } else {
          times.push(val1);
        }
      }
    } else if (item.ArrivalTime1 && String(item.ArrivalTime1).trim() !== "") {
      times.push(String(item.ArrivalTime1).trim());
    }
    if (item.Estimate2 !== null && item.Estimate2 !== void 0 && String(item.Estimate2).trim() !== "" && String(item.Estimate2).trim() !== "-") {
      const est2 = String(item.Estimate2).trim();
      times.push(est2.toLowerCase().includes("min") ? est2 : `${est2} min`);
    } else if (item.Time2Est !== null && item.Time2Est !== void 0 && String(item.Time2Est).trim() !== "") {
      const val2 = String(item.Time2Est).trim();
      if (val2.toLowerCase().includes("min")) {
        times.push(val2);
      } else {
        const secs = Number(val2);
        if (!isNaN(secs)) {
          times.push(`${Math.round(secs / 60)} min`);
        } else {
          times.push(val2);
        }
      }
    } else if (item.ArrivalTime2 && String(item.ArrivalTime2).trim() !== "") {
      times.push(String(item.ArrivalTime2).trim());
    }
    if (times.length > 0) {
      arrivals.push({
        loopId,
        loopName,
        arrivals: times,
        direction,
        color
      });
    }
  }
  return {
    arrivals,
    messageAlert,
    messageFacilities,
    messageServiceUpdate
  };
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
function toBase64(input) {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
  let output = "";
  const bytes = [];
  for (let i2 = 0; i2 < input.length; i2++) {
    const c = input.charCodeAt(i2);
    if (c < 128) {
      bytes.push(c);
    } else if (c < 2048) {
      bytes.push(c >> 6 | 192);
      bytes.push(c & 63 | 128);
    } else {
      bytes.push(c >> 12 | 224);
      bytes.push(c >> 6 & 63 | 128);
      bytes.push(c & 63 | 128);
    }
  }
  let i = 0;
  while (i < bytes.length) {
    const b1 = bytes[i++];
    const b2 = i < bytes.length ? bytes[i++] : NaN;
    const b3 = i < bytes.length ? bytes[i++] : NaN;
    const enc1 = b1 >> 2;
    const enc2 = (b1 & 3) << 4 | (isNaN(b2) ? 0 : b2 >> 4);
    const enc3 = isNaN(b2) ? 64 : (b2 & 15) << 2 | (isNaN(b3) ? 0 : b3 >> 6);
    const enc4 = isNaN(b3) ? 64 : b3 & 63;
    output += chars.charAt(enc1) + chars.charAt(enc2) + chars.charAt(enc3) + chars.charAt(enc4);
  }
  return output;
}
function shapeToSvg(points, loopId, width, height, padding, strokeColor) {
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
  const avgLatRad = (minLat + maxLat) / 2 * (Math.PI / 180);
  const cosLat = Math.cos(avgLatRad);
  const lonSpan = Math.max((maxLon - minLon) * cosLat, 1e-9);
  const latSpan = Math.max(maxLat - minLat, 1e-9);
  const pad = Math.max(padding, 16);
  const drawableW = Math.max(width - 2 * pad, 1);
  const drawableH = Math.max(height - 2 * pad, 1);
  const scale = Math.min(drawableW / lonSpan, drawableH / latSpan);
  const viewWidth = Math.round(lonSpan * scale + 2 * pad);
  const viewHeight = Math.round(latSpan * scale + 2 * pad);
  const polyline = coords.map((item) => {
    const x = pad + (item.lon - minLon) * cosLat * scale;
    const y = pad + (maxLat - item.lat) * scale;
    return `${x.toFixed(1)},${y.toFixed(1)}`;
  }).join(" ");
  const color = strokeColor || (loopId === "OMN" ? "#0066FF" : loopId === "BKL" ? "#FF6600" : "#009933");
  const strokeWidth = 14;
  const casingWidth = 22;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${viewWidth}" height="${viewHeight}" viewBox="0 0 ${viewWidth} ${viewHeight}" role="img" aria-label="MetroMover loop ${loopId}"><polyline fill="none" stroke="white" stroke-width="${casingWidth}" stroke-linecap="round" stroke-linejoin="round" points="${polyline}"/><polyline fill="none" stroke="${color}" stroke-width="${strokeWidth}" stroke-linecap="round" stroke-linejoin="round" points="${polyline}"/></svg>`;
  const svgDataUri = `data:image/svg+xml;base64,${toBase64(svg)}`;
  return { svg, svgDataUri };
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
  "get_network_svg",
  "find_nearest_station"
]);
