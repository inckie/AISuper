/**
 * Quick test to verify find_nearest_station coordinate order
 *
 * Run with: npm test -- find-nearest-station
 */

import { describe, it, expect, beforeEach } from "vitest";
import { setupTestEnvironment, MetroMoverTestData } from "../test-setup";
import { find_nearest_station } from "../modules/miami_metromover/index";

describe("find_nearest_station - Coordinate Order Verification", () => {
  beforeEach(() => {
    setupTestEnvironment();
    MetroMoverTestData.setupMocks();
  });

  it("should find nearest station from exact Downtown location", async () => {
    // Downtown Miami: 25.7617, -80.1918
    const result = await find_nearest_station(25.7617, -80.1918, 5);

    expect(result.station.id).toBe("DT");
    expect(result.station.title).toBe("Downtown");
    expect(result.distanceKm).toBeLessThan(0.1); // Should be very close
  });

  it("should find Brickell when searching from Downtown", async () => {
    // Downtown: 25.7617, -80.1918
    // Brickell: 25.7589, -80.1925 (about 0.6 km away)
    const result = await find_nearest_station(25.7617, -80.1918, 5);

    // Should find Downtown (closer)
    expect(result.station.id).toBe("DT");
  });

  it("should find Brickell when searching from Brickell location", async () => {
    // Brickell: 25.7589, -80.1925
    const result = await find_nearest_station(25.7589, -80.1925, 5);

    expect(result.station.id).toBe("BRI");
    expect(result.station.title).toBe("Brickell");
    expect(result.distanceKm).toBeLessThan(0.1);
  });

  it("should respect max_distance_km parameter", async () => {
    // From Downtown but very small radius
    await expect(
      find_nearest_station(25.7617, -80.1918, 0.01)
    ).rejects.toThrow("No station found within");
  });

  it("should fail with swapped coordinates (wrong parameter order)", async () => {
    // SWAPPED: passing longitude first, latitude second
    // This will search at -80.1918 as latitude (not valid for Miami)
    const result = await find_nearest_station(-80.1918, 25.7617, 10000);

    // Distance will be very large (wrong location)
    expect(result.distanceKm).toBeGreaterThan(1000);
  });

  it("should demonstrate the coordinate order problem", () => {
    // For reference:
    // CORRECT:   find_nearest_station(latitude=25.7617, longitude=-80.1918)
    // INCORRECT: find_nearest_station(longitude=-80.1918, latitude=25.7617)

    const correctLat = 25.7617;   // Miami latitude
    const correctLon = -80.1918;  // Miami longitude

    expect(correctLat).toBeGreaterThan(0);      // Miami is north of equator
    expect(correctLat).toBeLessThan(90);        // Valid latitude range
    expect(correctLon).toBeLessThan(0);         // Miami is west of prime meridian
    expect(correctLon).toBeGreaterThan(-180);   // Valid longitude range
  });
});

