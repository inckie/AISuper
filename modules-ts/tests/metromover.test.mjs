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

  const color = strokeColor || (loopId === "OMN" ? "#0066FF" : loopId === "BKL" ? "#FF6600" : "#339900");

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img" aria-label="MetroMover loop ${loopId}"><rect width="100%" height="100%" fill="white"/><polyline fill="none" stroke="${color}" stroke-width="3" points="${polyline}"/></svg>`;
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
  const svg = shapeToSvg(result.points, "OMN", 400, 400, 10, result.color);
  assert(svg.includes("<svg"), "SVG should contain <svg> tag");
  assert(svg.includes("polyline"), "SVG should contain polyline");
  assert(svg.includes("stroke=\"#0066FF\""), "SVG should use loop color");
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
