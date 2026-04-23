# MCP HTTP Module + Weather Feature Plan

## Goal
Add MCP HTTP integration as a reusable feature module and ship a first `weather` feature that calls MCP tools from location buttons.

## Scope (Implemented)
1. Add `mcpHttp` feature module type with per-feature server configuration.
2. Support multiple tool groups on one server (`groups` allowlist), e.g. `weather`, `miami_metromover`.
3. Expose JS bridge functions:
   - `mcpServerInfo()`
   - `mcpListTools(group?)`
   - `mcpCall(group, tool, argsObject)`
4. Add `weather` feature with:
   - list of location buttons
   - `mcpCall("weather", "get_current_weather", ...)`
   - dynamic result rendering in widgets.
5. Add `metromover` feature with:
   - loop loading (`list_loops`) and station list (`list_stations`)
   - station rows with `Arrivals` action (`get_station_arrivals`)
   - optional loop map fetch (`get_loop_svg`) with human-readable status
   - safe text conversion helpers to avoid `[object Object]` output.
6. Add `geolocation` module integration for Metromover `Nearest` action:
   - `geoGetCurrent()` uses Android OS location on Android
   - GeoIP fallback on non-Android targets
   - nearest station resolved through `miami_metromover.find_nearest_station`.

## Config Shape
Module definition in `applet.json`:

```json
{
  "type": "mcpHttp",
  "name": "mcp-host-http",
  "config": {
    "url": "http://127.0.0.1:8000/mcp",
    "groups": "weather,miami_metromover"
  }
}
```

Additionally supported: `config.configJson` with the Copilot-style schema and the module `name` used as server key.

## Next Iterations
1. Add session-aware MCP init flow if server requires explicit `initialize`/session negotiation.
2. Add optional auth headers in module config.
3. Add auto-generated feature from `mcpListTools()` (dynamic tool browser).
4. Add periodic refresh for live arrivals and expose auto-refresh toggle in UI.

