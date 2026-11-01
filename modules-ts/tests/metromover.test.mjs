import assert from "node:assert";
import { test } from "node:test";

// Mock JSON responses
const MOCK_STATIONS_JSON = JSON.stringify([
  {
    "StationID": "DT",
    "Station": "Downtown",
    "Lat": 25.7617,
    "Long": -80.1918,
    "Address": "110 SE 6th St",
    "City": "Miami",
    "State": "FL",
    "Zip": "33131",
    "Connecting": "3, 11",
    "ConnectingOther": "Metrorail",
    "PlacesOfInterest": "Courthouse"
  },
  {
    "StationID": "BRI",
    "Station": "Brickell",
    "Lat": 25.7589,
    "Long": -80.1925,
    "Address": "88 SW 8th St",
    "City": "Miami",
    "State": "FL",
    "Zip": "33130"
  }
]);

const MOCK_SHAPE_JSON = JSON.stringify([
  {
    "Id": "OTR",
    "Color": "#2A3A8D",
    "Shapes": [
      {"Latitude": 25.7718, "Longitude": -80.1914},
      {"Latitude": 25.7720, "Longitude": -80.1872}
    ]
  },
  {
    "Id": "OMN",
    "Color": "#0066FF",
    "Shapes": [
      {"Latitude": 25.3921, "Longitude": -80.2345},
      {"Latitude": 25.3925, "Longitude": -80.2340}
    ]
  },
  {
    "RouteID": "BKL",
    "Color": "#FF6600",
    "Points": [
      {"Latitude": 25.7589, "Longitude": -80.1925}
    ]
  }
]);

const MOCK_TRACKER_JSON = JSON.stringify([
  {
    "LoopID": "OMN",
    "LoopName": "Omni",
    "Direction": "SB",
    "Color": "0066FF",
    "Estimate1": "4",
    "Time1Est": 240,
    "Estimate2": "15",
    "Time2Est": 900,
    "MessageAlert": "Track maintenance on Omni loop"
  },
  {
    "LoopID": "BKL",
    "LoopName": "Brickell",
    "Direction": "NB",
    "Estimate1": "5 min"
  }
]);

const MOCK_VEHICLES_JSON = JSON.stringify([
  {
    "ID": "101",
    "Latitude": 25.3925,
    "Longitude": -80.2340,
    "ShapeID": "OMN",
    "OutOfService": "0",
    "vehicleNumber": "48",
    "RouteImage": "/DynamicMapImage?TextStr=OMN&TextStr2=&iconExt=solid-SB&iconColor=0066FF&iconType=Mover",
    "Info": "Omni Loop (SB) Cars: 48"
  },
  {
    "ID": "102",
    "Latitude": 25.7600,
    "Longitude": -80.1920,
    "ShapeID": "BKL",
    "OutOfService": "0",
    "vehicleNumber": "32"
  },
  {
    "ID": "103",
    "Latitude": 25.7600,
    "Longitude": -80.1920,
    "ShapeID": "BKL",
    "OutOfService": "1"
  }
]);

// Parser functions matching modules-ts/modules/miami_metromover/index.ts
function parseStationsJson(jsonStr) {
  const data = JSON.parse(jsonStr);
  const stations = [];
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
      connecting: item.Connecting || undefined,
      connectingOther: item.ConnectingOther || undefined,
      placesOfInterest: item.PlacesOfInterest || undefined,
      other: item.Other || undefined,
      directionsLink: item.DirectionsLink || undefined,
      streetViewLink: item.StreetViewLink || undefined
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
  const route = data.find((item) => (item.Id || item.RouteID || "").toUpperCase() === loopId.toUpperCase());
  if (route) {
    color = route.Color;
    const pointsList = route.Shapes || route.Points || [];
    pointsList.forEach((pt, index) => {
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
    if (!idVal || item.Latitude === undefined || item.Longitude === undefined) {
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
      loopId: item.ShapeID || undefined,
      loopName: item.ShapeID ? (loopNames[item.ShapeID] || item.ShapeID) : undefined,
      direction,
      cars: item.vehicleNumber || undefined,
      updated: item.CarsUpdated || undefined
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

    const direction = item.Direction ? item.Direction.trim() : undefined;
    const color = item.Color ? item.Color.trim() : undefined;
    const loopName = direction ? `${baseLoopName} (${direction})` : baseLoopName;
    
    const times = [];
    
    // Process First Arrival Estimate
    if (item.Estimate1 !== null && item.Estimate1 !== undefined && String(item.Estimate1).trim() !== "" && String(item.Estimate1).trim() !== "-") {
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
    if (item.Estimate2 !== null && item.Estimate2 !== undefined && String(item.Estimate2).trim() !== "" && String(item.Estimate2).trim() !== "-") {
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

function parseFloatSafe(value) {
  if (value === null || value === undefined) {
    return 0;
  }
  if (typeof value === "number") {
    return Number.isFinite(value) ? value : 0;
  }
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function parseIntSafe(value) {
  if (value === null || value === undefined) return 0;
  if (typeof value === "number") {
    return Number.isFinite(value) ? Math.floor(value) : 0;
  }
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) ? parsed : 0;
}

function haversineKm(lat1, lon1, lat2, lon2) {
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

function toRadians(value) {
  return (value * Math.PI) / 180;
}

function toBase64(input) {
  const chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=";
  let output = "";
  const bytes = [];
  for (let i = 0; i < input.length; i++) {
    const c = input.charCodeAt(i);
    if (c < 128) {
      bytes.push(c);
    } else if (c < 2048) {
      bytes.push((c >> 6) | 192);
      bytes.push((c & 63) | 128);
    } else {
      bytes.push((c >> 12) | 224);
      bytes.push(((c >> 6) & 63) | 128);
      bytes.push((c & 63) | 128);
    }
  }

  let i = 0;
  while (i < bytes.length) {
    const b1 = bytes[i++];
    const b2 = i < bytes.length ? bytes[i++] : NaN;
    const b3 = i < bytes.length ? bytes[i++] : NaN;

    const enc1 = b1 >> 2;
    const enc2 = ((b1 & 3) << 4) | (isNaN(b2) ? 0 : b2 >> 4);
    const enc3 = isNaN(b2) ? 64 : ((b2 & 15) << 2) | (isNaN(b3) ? 0 : b3 >> 6);
    const enc4 = isNaN(b3) ? 64 : b3 & 63;

    output += chars.charAt(enc1) + chars.charAt(enc2) + chars.charAt(enc3) + chars.charAt(enc4);
  }
  return output;
}

function shapeToSvg(points, loopId, width, height, padding, strokeColor) {
  const coords = points
    .filter((point) => Number.isFinite(point.longitude) && Number.isFinite(point.latitude))
    .map((point) => ({ lon: point.longitude, lat: point.latitude }));

  if (coords.length < 2) {
    throw new Error(`Loop '${loopId}' has no drawable coordinates.`);
  }

  const longitudes = coords.map((item) => item.lon);
  const latitudes = coords.map((item) => item.lat);

  const minLon = Math.min(...longitudes);
  const maxLon = Math.max(...longitudes);
  const minLat = Math.min(...latitudes);
  const maxLat = Math.max(...latitudes);

  const avgLatRad = ((minLat + maxLat) / 2) * (Math.PI / 180);
  const cosLat = Math.cos(avgLatRad);

  const lonSpan = Math.max((maxLon - minLon) * cosLat, 1e-9);
  const latSpan = Math.max(maxLat - minLat, 1e-9);

  const pad = Math.max(padding, 16);
  const drawableW = Math.max(width - 2 * pad, 1);
  const drawableH = Math.max(height - 2 * pad, 1);
  const scale = Math.min(drawableW / lonSpan, drawableH / latSpan);

  const viewWidth = Math.round(lonSpan * scale + 2 * pad);
  const viewHeight = Math.round(latSpan * scale + 2 * pad);

  const polyline = coords
    .map((item) => {
      const x = pad + (item.lon - minLon) * cosLat * scale;
      const y = pad + (maxLat - item.lat) * scale;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(" ");

  const color = strokeColor || (loopId === "OMN" ? "#0066FF" : loopId === "BKL" ? "#FF6600" : "#009933");
  const strokeWidth = 14;
  const casingWidth = 22;

  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${viewWidth}" height="${viewHeight}" viewBox="0 0 ${viewWidth} ${viewHeight}" role="img" aria-label="MetroMover loop ${loopId}"><polyline fill="none" stroke="white" stroke-width="${casingWidth}" stroke-linecap="round" stroke-linejoin="round" points="${polyline}"/><polyline fill="none" stroke="${color}" stroke-width="${strokeWidth}" stroke-linecap="round" stroke-linejoin="round" points="${polyline}"/></svg>`;

  const svgDataUri = `data:image/svg+xml;base64,${toBase64(svg)}`;

  return { svg, svgDataUri };
}

// Tests
test("JSON Parsing - parseStationsJson", () => {
  const stations = parseStationsJson(MOCK_STATIONS_JSON);
  assert.strictEqual(stations.length, 2, "Should parse 2 stations");
  assert.strictEqual(stations[0].id, "DT", "First station ID should be DT");
  assert.strictEqual(stations[0].title, "Downtown", "First station title should be Downtown");
  assert.strictEqual(stations[0].latitude, 25.7617, "Latitude should match");
  assert.strictEqual(stations[0].longitude, -80.1918, "Longitude should match");
  assert.strictEqual(stations[0].connecting, "3, 11", "Connecting bus should match");
  assert.strictEqual(stations[1].id, "BRI", "Second station ID should be BRI");
});

test("JSON Parsing - parseShapeLoopsJson", () => {
  const loops = parseShapeLoopsJson(MOCK_SHAPE_JSON);
  assert.strictEqual(loops.length, 3, "Should parse 3 loops");
  assert.strictEqual(loops[0], "OMN", "Passenger loop OMN should be prioritized first");
  assert.strictEqual(loops[1], "BKL", "Passenger loop BKL should be prioritized second");
  assert.strictEqual(loops[2], "OTR", "OTR loop should be third");
});

test("JSON Parsing - parseArrivalsJson", () => {
  const result = parseArrivalsJson(MOCK_TRACKER_JSON);
  assert.strictEqual(result.arrivals.length, 2, "Should parse 2 arrival groups");
  assert.strictEqual(result.arrivals[0].loopId, "OMN", "First loop ID should be OMN");
  assert.strictEqual(result.arrivals[0].loopName, "Omni (SB)", "Formatted loop name should include direction");
  assert.strictEqual(result.arrivals[0].direction, "SB", "Direction should be SB");
  assert.strictEqual(result.arrivals[0].color, "0066FF", "Color should be 0066FF");
  assert.strictEqual(result.arrivals[0].arrivals.length, 2, "First group should have 2 times");
  assert.deepEqual(result.arrivals[0].arrivals, ["4 min", "15 min"], "Arrival times should match");
  assert.strictEqual(result.messageAlert, "Track maintenance on Omni loop");
});

test("JSON Parsing - parseLoopShapeJson", () => {
  const result = parseLoopShapeJson(MOCK_SHAPE_JSON, "OMN");
  assert.strictEqual(result.points.length, 2, "Should parse 2 shape points");
  assert.strictEqual(result.points[0].order, 1, "First point order should be 1");
  assert.strictEqual(result.points[0].latitude, 25.3921, "First point latitude should match");
  assert.strictEqual(result.color, "#0066FF", "Color should be #0066FF");
});

test("JSON Parsing - parseTrainsJson", () => {
  const trains = parseTrainsJson(MOCK_VEHICLES_JSON);
  assert.strictEqual(trains.length, 2, "Should parse 2 active trains (ignoring out of service)");
  assert.strictEqual(trains[0].id, 101, "First train ID should be 101");
  assert.strictEqual(trains[0].loopName, "Omni", "First train loop name should be Omni");
  assert.strictEqual(trains[0].direction, "SB", "First train direction should be SB");
  assert.strictEqual(trains[0].cars, "48", "First train cars should be 48");
});

test("Geo - haversineKm distance calculation", () => {
  const dist = haversineKm(25.7617, -80.1918, 25.7589, -80.1925);
  assert(dist > 0.3 && dist < 1, `Distance should be ~0.5 km, got ${dist.toFixed(3)}`);
});

test("SVG Generation - shapeToSvg", () => {
  const result = parseLoopShapeJson(MOCK_SHAPE_JSON, "OMN");
  const { svg, svgDataUri } = shapeToSvg(result.points, "OMN", 400, 400, 10, result.color);
  assert(svg.includes("<svg"), "SVG should contain <svg> tag");
  assert(svg.includes("polyline"), "SVG should contain polyline");
  assert(svg.includes("stroke=\"#0066FF\""), "SVG should use loop color");
  assert(svg.includes("stroke-width=\"14\""), "SVG should use bold stroke width");
  assert(svg.includes("stroke=\"white\" stroke-width=\"22\""), "SVG should include white casing");
  assert(svg.includes("MetroMover loop OMN"), "SVG should have proper label");
  assert(svg.length > 100, "Generated SVG should have reasonable length");
  assert(svgDataUri.startsWith("data:image/svg+xml;base64,"), "Should return valid base64 data URI");
  const decoded = Buffer.from(svgDataUri.replace("data:image/svg+xml;base64,", ""), "base64").toString("utf8");
  assert.strictEqual(decoded, svg, "Base64 should decode to exact SVG string");
});

test("Number Parsing - parseFloatSafe", () => {
  assert.strictEqual(parseFloatSafe("25.7617"), 25.7617, "Should parse float");
  assert.strictEqual(parseFloatSafe(""), 0, "Should return 0 for empty string");
  assert.strictEqual(parseFloatSafe(null), 0, "Should return 0 for null");
  assert.strictEqual(parseFloatSafe("invalid"), 0, "Should return 0 for invalid");
});

test("Number Parsing - parseIntSafe", () => {
  assert.strictEqual(parseIntSafe("101"), 101, "Should parse int");
  assert.strictEqual(parseIntSafe(""), 0, "Should return 0 for empty string");
  assert.strictEqual(parseIntSafe(null), 0, "Should return 0 for null");
});


function escapeXml(str) {
  return str
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&apos;");
}

function synthesizeNetworkSvg(allShapes, stations, activeLoops, highlightStationId, width = 720, height = 720, padding = 24) {
  const defaultColors = {
    "OMN": "#0066FF",
    "BKL": "#FF6600",
    "INN": "#009933",
    "FUL": "#9900FF",
    "OTR": "#2A3A8D"
  };

  const routeMap = new Map();
  for (const route of allShapes) {
    const id = (route.Id || route.RouteID || "").trim().toUpperCase();
    if (!id) continue;
    const rawColor = route.Color || defaultColors[id] || "#0066FF";
    const color = rawColor.startsWith("#") ? rawColor : "#" + rawColor;
    const shapesList = route.Shapes || route.Points || [];
    const points = shapesList
      .filter((pt) => Number.isFinite(pt.Latitude) && Number.isFinite(pt.Longitude))
      .map((pt) => ({ lat: Number(pt.Latitude), lon: Number(pt.Longitude) }));
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

  const minLon = Math.min(...allLons);
  const maxLon = Math.max(...allLons);
  const minLat = Math.min(...allLats);
  const maxLat = Math.max(...allLats);

  const avgLatRad = ((minLat + maxLat) / 2) * (Math.PI / 180);
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

  const activeSet = new Set((activeLoops || ["OMN", "BKL", "INN"]).map((s) => (s || "").trim().toUpperCase()));

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

  const normHighlightId = (highlightStationId || "").trim().toUpperCase();
  let highlightedStationItem = null;

  for (const station of stations) {
    const lat = Number(station.latitude);
    const lon = Number(station.longitude);
    if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue;
    const [sx, sy] = project(lat, lon);
    if (normHighlightId && (station.id || "").trim().toUpperCase() === normHighlightId) {
      highlightedStationItem = { title: station.title, x: sx, y: sy };
    } else {
      svgParts.push(`<circle cx="${sx}" cy="${sy}" r="4" fill="white" stroke="#334155" stroke-width="2"/>`);
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

  return { activeLoops, svg, svgDataUri, viewBox: `0 0 ${viewWidth} ${viewHeight}` };
}


test("SVG Generation - synthesizeNetworkSvg with multiple loops and trails", () => {
  const allShapes = JSON.parse(MOCK_SHAPE_JSON);
  const stations = parseStationsJson(MOCK_STATIONS_JSON);
  const result = synthesizeNetworkSvg(allShapes, stations, ["OMN", "BKL"], "DT", 720, 720, 24);

  assert(result.svg.includes("<svg"), "SVG should have <svg> root element");
  assert(result.svg.includes("Miami Metromover Network Map"), "SVG should have network map label");
  assert(result.svg.includes("#CBD5E1"), "SVG should include base guideway trail");
  assert(result.svg.includes("#0066FF"), "SVG should include active Omni loop (#0066FF)");
  assert(result.svg.includes("#FF6600"), "SVG should include active Brickell loop (#FF6600)");
  assert(result.svg.includes("Downtown"), "SVG should include highlighted station title Downtown");
  assert(result.svgDataUri.startsWith("data:image/svg+xml;base64,"), "Should produce valid base64 data URI");
  const decoded = Buffer.from(result.svgDataUri.replace("data:image/svg+xml;base64,", ""), "base64").toString("utf8");
  assert.strictEqual(decoded, result.svg, "Base64 should decode cleanly");
});
