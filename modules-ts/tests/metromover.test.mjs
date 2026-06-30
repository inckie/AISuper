import assert from "node:assert";
import { test } from "node:test";

// Mock JSON responses (simplified versions of real API responses)
const MOCK_STATIONS_JSON = JSON.stringify([
  {
    "StationID": "DT",
    "Station": "Downtown",
    "Lat": 25.7617,
    "Long": -80.1918,
    "Address": "110 SE 6th St",
    "City": "Miami",
    "State": "FL",
    "Zip": "33131"
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
    "RouteID": "OMN",
    "Points": [
      {"Latitude": 25.3921, "Longitude": -80.2345},
      {"Latitude": 25.3925, "Longitude": -80.2340}
    ]
  },
  {
    "RouteID": "BKL",
    "Points": [
      {"Latitude": 25.7589, "Longitude": -80.1925}
    ]
  }
]);

const MOCK_TRACKER_JSON = JSON.stringify([
  {
    "LoopID": "OMN",
    "LoopName": "Omni",
    "Time1Est": "2 min",
    "Time2Est": "11 min"
  },
  {
    "LoopID": "BKL",
    "LoopName": "Brickell",
    "Time1Est": "5 min"
  }
]);

const MOCK_VEHICLES_JSON = JSON.stringify([
  {
    "ID": "101",
    "Latitude": 25.3925,
    "Longitude": -80.2340,
    "ShapeID": "OMN",
    "OutOfService": "0"
  },
  {
    "ID": "102",
    "Latitude": 25.7600,
    "Longitude": -80.1920,
    "ShapeID": "BKL",
    "OutOfService": "0"
  }
]);

// Helper functions (identical to modules-ts/modules/miami_metromover/index.ts)
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
      connectingOther: item.ConnectingOther || undefined,
      placesOfInterest: item.PlacesOfInterest || undefined,
      other: item.Other || undefined
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
  const route = data.find((item) => (item.RouteID || "").toUpperCase() === loopId.toUpperCase());
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

function shapeToSvg(points, loopId, width, height, padding) {
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

// Tests
test("JSON Parsing - parseStationsJson", () => {
  const stations = parseStationsJson(MOCK_STATIONS_JSON);
  assert.strictEqual(stations.length, 2, "Should parse 2 stations");
  assert.strictEqual(stations[0].id, "DT", "First station ID should be DT");
  assert.strictEqual(stations[0].title, "Downtown", "First station title should be Downtown");
  assert.strictEqual(stations[0].latitude, 25.7617, "Latitude should match");
  assert.strictEqual(stations[0].longitude, -80.1918, "Longitude should match");
  assert.strictEqual(stations[1].id, "BRI", "Second station ID should be BRI");
});

test("JSON Parsing - parseShapeLoopsJson", () => {
  const loops = parseShapeLoopsJson(MOCK_SHAPE_JSON);
  assert.strictEqual(loops.length, 2, "Should parse 2 loops");
  assert.deepEqual(loops, ["OMN", "BKL"], "Loop IDs should match");
});

test("JSON Parsing - parseArrivalsJson", () => {
  const arrivals = parseArrivalsJson(MOCK_TRACKER_JSON);
  assert.strictEqual(arrivals.length, 2, "Should parse 2 arrival groups");
  assert.strictEqual(arrivals[0].loopId, "OMN", "First loop ID should be OMN");
  assert.strictEqual(arrivals[0].loopName, "Omni", "First loop name should be Omni");
  assert.strictEqual(arrivals[0].arrivals.length, 2, "First group should have 2 times");
  assert.deepEqual(arrivals[0].arrivals, ["2 min", "11 min"], "Arrival times should match");
});

test("JSON Parsing - parseLoopShapeJson", () => {
  const points = parseLoopShapeJson(MOCK_SHAPE_JSON, "OMN");
  assert.strictEqual(points.length, 2, "Should parse 2 shape points");
  assert.strictEqual(points[0].order, 1, "First point order should be 1");
  assert.strictEqual(points[0].latitude, 25.3921, "First point latitude should match");
});

test("JSON Parsing - parseTrainsJson", () => {
  const trains = parseTrainsJson(MOCK_VEHICLES_JSON);
  assert.strictEqual(trains.length, 2, "Should parse 2 trains");
  assert.strictEqual(trains[0].id, 101, "First train ID should be 101");
  assert.strictEqual(trains[0].loopName, "Omni", "First train loop name should be Omni");
});

test("Geo - haversineKm distance calculation", () => {
  const dist = haversineKm(25.7617, -80.1918, 25.7589, -80.1925);
  assert(dist > 0.3 && dist < 1, `Distance should be ~0.5 km, got ${dist.toFixed(3)}`);
});

test("SVG Generation - shapeToSvg", () => {
  const points = parseLoopShapeJson(MOCK_SHAPE_JSON, "OMN");
  const svg = shapeToSvg(points, "OMN", 400, 400, 10);
  assert(svg.includes("<svg"), "SVG should contain <svg> tag");
  assert(svg.includes("polyline"), "SVG should contain polyline");
  assert(svg.includes("MetroMover loop OMN"), "SVG should have proper label");
  assert(svg.length > 100, "Generated SVG should have reasonable length");
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
