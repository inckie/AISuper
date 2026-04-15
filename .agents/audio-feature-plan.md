# Audio Feature Description and MVP Plan

## Feature Goal
Add a basic radio streaming capability with two stations and modular runtime integration.

## Streams
- `plaza`: `https://radio.plaza.one/mp3https://plaza.one`
- `provodach`: `https://station.waveradio.org/provodach`

## Scope (Implemented)
1. Add an `audioPlayer` module type in feature manifest with explicit player name.
2. Add abstract `AudioPlayer` interface and `AudioPlayerModule` manager.
3. Add platform factory (`expect/actual`) for player creation.
4. Expose player control/state APIs to JS (`audioLoad/play/pause/stop/seek/...`).
5. Publish player state into feature values as `playerName.media.*` keys.
6. Add event subscription (`audioSubscribe`) that calls JS handlers when player state changes.
7. Add optional native `AudioPlayer` widget for direct controls bypassing JS.
8. Add `radio` feature with layout/script and module declaration.
9. Extract HTTP/audio wiring from `Feature` into generic module abstractions (`FeatureModule`, `FeatureModuleFactory`, `FeatureModuleHost`) and connect modules by manifest + platform factories.

## Current Platform Support
- Android: basic stream playback via native `MediaPlayer`.
- JVM/iOS/JS/Wasm: `NoopAudioPlayer` fallback for API/state wiring and headless behavior.

## Next Iterations
1. Replace fallback player implementations with real playback per platform.
2. Add buffering state and better error reporting in values/events.
3. Add seek bar and stream metadata widget bindings.
4. Add lifecycle/background policy per platform.
5. Add tests for JS bridge + module event flow.

