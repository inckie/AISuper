const API_ROOT = "https://www.miamidade.gov/apps/dtpw/transitapps/api/mover";
const STATIONS_URL = `${API_ROOT}/stations`;
const SHAPE_URL = `${API_ROOT}/shape?routeId=&mapMode=light`;
const VEHICLES_URL = `${API_ROOT}/vehicles?routeId=&mapMode=light&track=YES&curLatitude=0&curLongitude=0`;
const TRACKER_URL = `${API_ROOT}/tracker`;

const CACHE_TTL_MS = 60 * 60 * 1000;

// Host functions - declared here and implemented in Kotlin
declare function httpGet(url: string, headers?: Record<string, string>): Promise<string>;
declare function consoleLog(args: any[]): void;
declare function persistentStorageGet(scope: string, key: string): Promise<string | null>;
declare function registerExports(moduleName: string, functions: string[]): void;

type UnknownRecord = Record<string, unknown>;

type Station = {
  id: string;
  title: string;
  latitude: number;
  longitude: number;
  address?: string;
  city?: string;
  state?: string;
  zip?: string;
  stationIdShow?: string;
  connectingOther?: string;
  placesOfInterest?: string;
  other?: string;
};

type LoopShapePoint = {
  loopId: string;
  order: number;
  latitude: number;
  longitude: number;
};

type ArrivalGroup = {
  loopId: string;
  loopName: string;
  arrivals: string[];
};

type Train = {
  id: number;
  latitude: number;
  longitude: number;
  loopId?: string;
  loopName?: string;
  direction?: string;
};

type CacheEntry<T> = {
  value: T;
  expiresAt: number;
};

let stationsCache: CacheEntry<Station[]> | null = null;
let loopsCache: CacheEntry<string[]> | null = null;
const loopShapeCache = new Map<string, CacheEntry<LoopShapePoint[]>>();

function nowMs(): number {
  return Date.now();
}

function validCache<T>(entry: CacheEntry<T> | null): T | null {
  if (!entry) return null;
  return nowMs() <= entry.expiresAt ? entry.value : null;
}

function setCache<T>(value: T): CacheEntry<T> {
  return {
    value,
    expiresAt: nowMs() + CACHE_TTL_MS
  };
}

async function getApiKey(): Promise<string> {
  try {
    const key = await persistentStorageGet("feature", "metromover_api_key");
    return key || "P98EG7NGA9A02NAE00Y";
  } catch (e) {
    return "P98EG7NGA9A02NAE00Y";
  }
}

async function fetchJson(url: string): Promise<string> {
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

async function list_stations(): Promise<Station[]> {
  const cached = validCache(stationsCache);
  if (cached) return cached;

  const jsonStr = await fetchJson(STATIONS_URL);
  const stations = parseStationsJson(jsonStr);
  stationsCache = setCache(stations);
  return stations;
}

async function list_loops(): Promise<string[]> {
  const cached = validCache(loopsCache);
  if (cached) return cached;

  const jsonStr = await fetchJson(SHAPE_URL);
  const loops = parseShapeLoopsJson(jsonStr);
  loopsCache = setCache(loops);
  return loops;
}

async function get_trains(train_id?: string | null): Promise<Train[]> {
  let trainId = train_id;
  if (trainId === "null") {
    trainId = null;
  }

  const jsonStr = await fetchJson(VEHICLES_URL);
  let trains = parseTrainsJson(jsonStr);
  
  if (trainId) {
    const idNum = parseIntSafe(trainId);
    trains = trains.filter(t => t.id === idNum);
  }
  
  return trains;
}

async function get_station_arrivals(station_id: string): Promise<{
  stationId: string;
  stationTitle: string;
  arrivals: ArrivalGroup[];
}> {
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

async function get_loop_svg(loop_id: string, width = 800, height = 800, padding = 12): Promise<{
  loopId: string;
  pointCount: number;
  svg: string;
}> {
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

async function find_nearest_station(
  latitude: number,
  longitude: number,
  max_distance_km?: number | null
): Promise<{ station: Station; distanceKm: number }> {
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

  let nearest: Station | null = null;
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

async function getLoopShape(loopId: string): Promise<LoopShapePoint[]> {
  const cached = validCache(loopShapeCache.get(loopId) ?? null);
  if (cached) return cached;

  const url = `${API_ROOT}/shape?routeId=${encodeURIComponent(loopId)}&mapMode=light`;
  const jsonStr = await fetchJson(url);
  const points = parseLoopShapeJson(jsonStr, loopId);
  loopShapeCache.set(loopId, setCache(points));
  return points;
}

type StationJson = {
  StationID?: string;
  Station?: string;
  Lat?: number;
  Long?: number;
  Address?: string;
  City?: string;
  State?: string;
  Zip?: string;
  StationIDshow?: string;
  ConnectingOther?: string;
  PlacesOfInterest?: string;
  Other?: string;
};

function parseStationsJson(jsonStr: string): Station[] {
  const data = JSON.parse(jsonStr) as StationJson[];
  const stations: Station[] = [];
  for (const item of data) {
    const stationId = item.StationID;
    const title = item.Station;
    const lat = item.Lat;
    const lon = item.Long;
    if (!stationId || !title || lat === undefined || lon === undefined) {
      continue;
    }
    stations.push({
      id: stationId,
      title,
      latitude: lat,
      longitude: lon,
      address: item.Address || undefined,
      city: item.City || undefined,
      state: item.State || undefined,
      zip: item.Zip || undefined,
      stationIdShow: item.StationIDshow || undefined,
      connectingOther: item.ConnectingOther || undefined,
      placesOfInterest: item.PlacesOfInterest || undefined,
      other: item.Other || undefined
    });
  }
  return stations;
}

type ShapeJson = {
  RouteID?: string;
  Points?: any[];
};

function parseShapeLoopsJson(jsonStr: string): string[] {
  const data = JSON.parse(jsonStr) as ShapeJson[];
  const loops: string[] = [];
  for (const item of data) {
    if (item.RouteID && !loops.includes(item.RouteID)) {
      loops.push(item.RouteID);
    }
  }
  return loops;
}

type ShapePointJson = {
  Latitude?: number;
  Longitude?: number;
};

type LoopShapeJson = {
  RouteID?: string;
  Points?: ShapePointJson[];
};

function parseLoopShapeJson(jsonStr: string, loopId: string): LoopShapePoint[] {
  const data = JSON.parse(jsonStr) as LoopShapeJson[];
  const points: LoopShapePoint[] = [];
  
  const route = data.find((item) => normalizeId(item.RouteID || "") === normalizeId(loopId));
  if (route && route.Points) {
    route.Points.forEach((pt, index) => {
      if (pt.Latitude !== undefined && pt.Longitude !== undefined) {
        points.push({
          loopId: loopId,
          order: index + 1,
          latitude: pt.Latitude,
          longitude: pt.Longitude
        });
      }
    });
  }
  return points;
}

type VehicleJson = {
  ID?: string;
  Latitude?: number;
  Longitude?: number;
  ShapeID?: string;
  OutOfService?: string;
  vehicleNumber?: string;
};

function parseTrainsJson(jsonStr: string): Train[] {
  const data = JSON.parse(jsonStr) as VehicleJson[];
  const trains: Train[] = [];
  const loopNames: Record<string, string> = {
    "OMN": "Omni",
    "BKL": "Brickell",
    "INN": "Inner"
  };

  for (const item of data) {
    if (item.OutOfService === "1") {
      continue;
    }
    const idVal = parseIntSafe(item.ID || "");
    if (!idVal || item.Latitude === undefined || item.Longitude === undefined) {
      continue;
    }
    trains.push({
      id: idVal,
      latitude: item.Latitude,
      longitude: item.Longitude,
      loopId: item.ShapeID || undefined,
      loopName: item.ShapeID ? (loopNames[item.ShapeID] || item.ShapeID) : undefined
    });
  }
  return trains;
}

type TrackerJson = {
  LoopID?: string;
  LoopName?: string;
  Time1Est?: number | string | null;
  Estimate1?: number | string | null;
  ArrivalTime1?: string | null;
  Time2Est?: number | string | null;
  Estimate2?: number | string | null;
  ArrivalTime2?: string | null;
  MessageAlert?: string | null;
};

function parseArrivalsJson(jsonStr: string): ArrivalGroup[] {
  const data = JSON.parse(jsonStr) as TrackerJson[];
  const arrivals: ArrivalGroup[] = [];
  
  for (const item of data) {
    const loopId = item.LoopID;
    const loopName = item.LoopName;
    if (!loopId || !loopName || loopName === "***") {
      continue;
    }
    
    const times: string[] = [];
    
    // Process First Arrival Estimate
    if (item.Estimate1 !== null && item.Estimate1 !== undefined && String(item.Estimate1).trim() !== "") {
      const est1 = String(item.Estimate1).trim();
      times.push(est1.toLowerCase().includes("min") ? est1 : `${est1} min`);
    } else if (item.Time1Est !== null && item.Time1Est !== undefined && String(item.Time1Est).trim() !== "") {
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
    
    // Process Second Arrival Estimate
    if (item.Estimate2 !== null && item.Estimate2 !== undefined && String(item.Estimate2).trim() !== "") {
      const est2 = String(item.Estimate2).trim();
      times.push(est2.toLowerCase().includes("min") ? est2 : `${est2} min`);
    } else if (item.Time2Est !== null && item.Time2Est !== undefined && String(item.Time2Est).trim() !== "") {
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
        arrivals: times
      });
    }
  }
  return arrivals;
}

function haversineKm(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const earthRadiusKm = 6371.0;
  const lat1Rad = toRadians(lat1);
  const lon1Rad = toRadians(lon1);
  const lat2Rad = toRadians(lat2);
  const lon2Rad = toRadians(lon2);

  const dLat = lat2Rad - lat1Rad;
  const dLon = lon2Rad - lon1Rad;

  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return earthRadiusKm * c;
}

function shapeToSvg(
  points: LoopShapePoint[],
  loopId: string,
  width: number,
  height: number,
  padding: number
): string {
  const coords = points
    .filter((point) => {
      const lon = Number(point.longitude);
      const lat = Number(point.latitude);
      return Number.isFinite(lon) && Number.isFinite(lat);
    })
    .map((point) => ({ lon: Number(point.longitude), lat: Number(point.latitude) }));

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

  const polyline = coords
    .map((item) => {
      const x = xOffset + (item.lon - minLon) * scale;
      const y = yOffset + (maxLat - item.lat) * scale;
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(" ");

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img" aria-label="MetroMover loop ${loopId}"><rect width="100%" height="100%" fill="white"/><polyline fill="none" stroke="#006600" stroke-width="2" points="${polyline}"/></svg>`;
}

function normalizeId(value: string): string {
  return (value || "").trim().toUpperCase();
}

function parseFloatSafe(value: string | number | null | undefined): number {
  if (value === null || value === undefined) {
    return 0;
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function parseIntSafe(value: string | number | null | undefined): number {
  if (value === null || value === undefined) return 0;
  if (typeof value === "number") {
    return Number.isFinite(value) ? Math.floor(value) : 0;
  }
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : 0;
}

function toRadians(value: number): number {
  return (value * Math.PI) / 180;
}

function round3(value: number): number {
  return Math.round(value * 1000) / 1000;
}

// Compile target registers these functions
registerExports("miamiMetromover", [
  "list_stations",
  "list_loops",
  "get_trains",
  "get_station_arrivals",
  "get_loop_svg",
  "find_nearest_station"
]);
