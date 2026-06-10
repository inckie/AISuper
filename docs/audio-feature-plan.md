# Audio Feature Description and MVP Plan

## Feature Goal
Add searchable radio streaming with modular runtime integration.

## Search API
- Endpoint: `https://de1.api.radio-browser.info/json/stations/byname/{{name}}`
- Response: station array with `name`, `url`, `url_resolved`, `favicon`, metadata.

## Scope (Implemented)
1. Add `radio` feature modules: `http` + `audioPlayer` (`radioMain`).
2. Build search UI: query input + Find button + scrollable dynamic station list.
3. Fetch station list via `httpGet` and map into dynamic widgets.
4. Add per-item Play button with `actionArgs: [streamUrl, stationName]`.
5. On play, call `audioLoad/audioPlay` and render native `AudioPlayer` widget in dynamic `playerPanel`.
6. Keep audio state/event bridge active for future richer controls.

## Current Platform Support
- Android: basic stream playback via native `MediaPlayer`.
- JVM/iOS/JS/Wasm: `NoopAudioPlayer` fallback for API/state wiring and headless behavior.

## Next Iterations
1. Replace fallback player implementations with real playback per platform.
2. Add buffering state and better error reporting in values/events.
3. Add seek bar and stream metadata widget bindings.
4. Add lifecycle/background policy per platform.
5. Add tests for JS bridge + module event flow.

