import assert from "node:assert";
import { test } from "node:test";

// Mock XML responses (simplified versions of real API responses)
const MOCK_STATIONS_XML = `<?xml version="1.0"?>
<MoverStations>
  <Record>
    <StationID>DT</StationID>
    <Station>Downtown</Station>
    <Latitude>25.7617</Latitude>
    <Longitude>-80.1918</Longitude>
    <Address>110 SE 6th St</Address>
    <City>Miami</City>
    <State>FL</State>
    <Zip>33131</Zip>
  </Record>
  <Record>
    <StationID>BRI</StationID>
    <Station>Brickell</Station>
    <Latitude>25.7589</Latitude>
    <Longitude>-80.1925</Longitude>
    <Address>88 SW 8th St</Address>
    <City>Miami</City>
    <State>FL</State>
    <Zip>33130</Zip>
  </Record>
</MoverStations>`;

const MOCK_LOOPS_XML = `<?xml version="1.0"?>
<MoverMapShapeLoops>
  <Record><LoopID>OVR</LoopID></Record>
  <Record><LoopID>IRL</LoopID></Record>
  <Record><LoopID>ORL</LoopID></Record>
  <Record><LoopID>PRY</LoopID></Record>
</MoverMapShapeLoops>`;

const MOCK_ARRIVALS_XML = `<?xml version="1.0"?>
<MoverTracker>
  <Info>
    <firstLoopID>OVR</firstLoopID>
    <firstLoopName>Omni</firstLoopName>
    <firstTime1>2 min</firstTime1>
    <firstTime2>11 min</firstTime2>
    <secondLoopID>IRL</secondLoopID>
    <secondLoopName>Brickell</secondLoopName>
    <secondTime1>5 min</secondTime1>
  </Info>
</MoverTracker>`;

const MOCK_LOOP_SHAPE_XML = `<?xml version="1.0"?>
<MoverMapShape>
  <Record>
    <LoopID>OVR</LoopID>
    <OrderNum>1</OrderNum>
    <Latitude>25.3921</Latitude>
    <Longitude>-80.2345</Longitude>
  </Record>
  <Record>
    <LoopID>OVR</LoopID>
    <OrderNum>2</OrderNum>
    <Latitude>25.3925</Latitude>
    <Longitude>-80.2340</Longitude>
  </Record>
  <Record>
    <LoopID>OVR</LoopID>
    <OrderNum>3</OrderNum>
    <Latitude>25.3930</Latitude>
    <Longitude>-80.2335</Longitude>
  </Record>
</MoverMapShape>`;

const MOCK_TRAINS_XML = `<?xml version="1.0"?>
<MoverTrains>
  <Train>
    <TrainID>101</TrainID>
    <Latitude>25.3925</Latitude>
    <Longitude>-80.2340</Longitude>
    <LoopID>OVR</LoopID>
    <LoopName>Omni</LoopName>
    <vehDirection>North</vehDirection>
  </Train>
  <Train>
    <TrainID>102</TrainID>
    <Latitude>25.7600</Latitude>
    <Longitude>-80.1920</Longitude>
    <LoopID>IRL</LoopID>
    <LoopName>Brickell</LoopName>
    <vehDirection>South</vehDirection>
  </Train>
</MoverTrains>`;

// Helper functions extracted from the module for testing
function findBlocks(xml, tagName) {
  const escaped = escapeRegex(tagName);
  const pattern = new RegExp(`<${escaped}\\b[^>]*>([\\s\\S]*?)<\\/${escaped}>`, "gi");
  const blocks = [];
  let match = pattern.exec(xml);
  while (match) {
    blocks.push(match[1]);
    match = pattern.exec(xml);
  }
  return blocks;
}

function readTag(xmlBlock, tagName) {
  const escaped = escapeRegex(tagName);
  const pattern = new RegExp(`<${escaped}\\b[^>]*>([\\s\\S]*?)<\\/${escaped}>`, "i");
  const match = pattern.exec(xmlBlock);
  if (!match || !match[1]) {
    return null;
  }
  const text = decodeXmlEntities(match[1].trim());
  return text.length > 0 ? text : null;
}

function decodeXmlEntities(value) {
  return value
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'");
}

function escapeRegex(value) {
  return value.replace(/[.*+?^${}()|[\\]\\]/g, "\\$&");
}

function parseStationsXml(xml) {
  const stations = [];
  for (const block of findBlocks(xml, "Record")) {
    const stationId = readTag(block, "StationID");
    const title = readTag(block, "Station");
    const latitudeRaw = readTag(block, "Latitude");
    const longitudeRaw = readTag(block, "Longitude");

    if (!stationId || !title || !latitudeRaw || !longitudeRaw) continue;

    stations.push({
      id: stationId,
      title,
      latitude: parseFloatSafe(latitudeRaw),
      longitude: parseFloatSafe(longitudeRaw),
      address: readTag(block, "Address") || undefined,
      city: readTag(block, "City") || undefined,
      state: readTag(block, "State") || undefined,
      zip: readTag(block, "Zip") || undefined
    });
  }
  return stations;
}

function parseShapeLoopsXml(xml) {
  const loops = [];
  for (const block of findBlocks(xml, "Record")) {
    const loopId = readTag(block, "LoopID");
    if (loopId) loops.push(loopId);
  }
  return loops;
}

function parseArrivalsXml(xml) {
  const infoBlock = findBlocks(xml, "Info")[0];
  if (!infoBlock) return [];

  const PREFIXES = ["first", "second", "third", "forth", "fifth"];
  const arrivals = [];

  for (const prefix of PREFIXES) {
    const loopId = readTag(infoBlock, `${prefix}LoopID`);
    if (!loopId) break;

    const loopName = readTag(infoBlock, `${prefix}LoopName`);
    if (!loopName || loopName === "*****") break;

    const time1 = readTag(infoBlock, `${prefix}Time1`);
    const time2 = readTag(infoBlock, `${prefix}Time2`);
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

function parseLoopShapeXml(xml) {
  const points = [];

  for (const block of findBlocks(xml, "Record")) {
    const loopId = readTag(block, "LoopID");
    const orderRaw = readTag(block, "OrderNum");
    const latitudeRaw = readTag(block, "Latitude");
    const longitudeRaw = readTag(block, "Longitude");

    if (!loopId || !orderRaw || !latitudeRaw || !longitudeRaw) continue;

    points.push({
      loopId,
      order: parseIntSafe(orderRaw),
      latitude: parseFloatSafe(latitudeRaw),
      longitude: parseFloatSafe(longitudeRaw)
    });
  }

  points.sort((left, right) => left.order - right.order);
  return points;
}

function parseTrainsXml(xml) {
  const trains = [];
  for (const block of findBlocks(xml, "Train")) {
    const trainIdText = readTag(block, "TrainID");
    if (!trainIdText) continue;

    trains.push({
      id: parseIntSafe(trainIdText),
      latitude: parseFloatSafe(readTag(block, "Latitude")),
      longitude: parseFloatSafe(readTag(block, "Longitude")),
      loopId: readTag(block, "LoopID") || undefined,
      loopName: readTag(block, "LoopName") || undefined,
      direction: readTag(block, "vehDirection") || undefined
    });
  }
  return trains;
}

function parseFloatSafe(value) {
  if (!value) return 0;
  const parsed = Number.parseFloat(value);
  return Number.isFinite(parsed) ? parsed : 0;
}

function parseIntSafe(value) {
  if (!value) return 0;
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
test("XML Parsing - parseStationsXml", () => {
  const stations = parseStationsXml(MOCK_STATIONS_XML);
  assert.strictEqual(stations.length, 2, "Should parse 2 stations");
  assert.strictEqual(stations[0].id, "DT", "First station ID should be DT");
  assert.strictEqual(stations[0].title, "Downtown", "First station title should be Downtown");
  assert.strictEqual(stations[0].latitude, 25.7617, "Latitude should match");
  assert.strictEqual(stations[0].longitude, -80.1918, "Longitude should match");
  assert.strictEqual(stations[1].id, "BRI", "Second station ID should be BRI");
});

test("XML Parsing - parseShapeLoopsXml", () => {
  const loops = parseShapeLoopsXml(MOCK_LOOPS_XML);
  assert.strictEqual(loops.length, 4, "Should parse 4 loops");
  assert.deepEqual(loops, ["OVR", "IRL", "ORL", "PRY"], "Loop IDs should match");
});

test("XML Parsing - parseArrivalsXml", () => {
  const arrivals = parseArrivalsXml(MOCK_ARRIVALS_XML);
  assert.strictEqual(arrivals.length, 2, "Should parse 2 arrival groups");
  assert.strictEqual(arrivals[0].loopId, "OVR", "First loop ID should be OVR");
  assert.strictEqual(arrivals[0].loopName, "Omni", "First loop name should be Omni");
  assert.strictEqual(arrivals[0].arrivals.length, 2, "First group should have 2 times");
  assert.deepEqual(arrivals[0].arrivals, ["2 min", "11 min"], "Arrival times should match");
});

test("XML Parsing - parseLoopShapeXml", () => {
  const points = parseLoopShapeXml(MOCK_LOOP_SHAPE_XML);
  assert.strictEqual(points.length, 3, "Should parse 3 shape points");
  assert.strictEqual(points[0].order, 1, "First point order should be 1");
  assert.strictEqual(points[0].latitude, 25.3921, "First point latitude should match");
  assert.strictEqual(points[2].order, 3, "Third point order should be 3");
});

test("XML Parsing - parseTrainsXml", () => {
  const trains = parseTrainsXml(MOCK_TRAINS_XML);
  assert.strictEqual(trains.length, 2, "Should parse 2 trains");
  assert.strictEqual(trains[0].id, 101, "First train ID should be 101");
  assert.strictEqual(trains[0].loopName, "Omni", "First train loop name should be Omni");
  assert.strictEqual(trains[1].direction, "South", "Second train direction should be South");
});

test("Geo - haversineKm distance calculation", () => {
  // Distance from Downtown Miami to Brickell (roughly 1-2 km)
  const dist = haversineKm(25.7617, -80.1918, 25.7589, -80.1925);
  assert(dist > 0.3 && dist < 1, `Distance should be ~0.5 km, got ${dist.toFixed(3)}`);
});

test("SVG Generation - shapeToSvg", () => {
  const points = parseLoopShapeXml(MOCK_LOOP_SHAPE_XML);
  const svg = shapeToSvg(points, "OVR", 400, 400, 10);
  assert(svg.includes("<svg"), "SVG should contain <svg> tag");
  assert(svg.includes("polyline"), "SVG should contain polyline");
  assert(svg.includes("MetroMover loop OVR"), "SVG should have proper label");
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

