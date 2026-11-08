# AISuper Headless Server

JVM HTTP/SSE transport exposing headless runtime sessions from `:shared`.

Core abstractions (`HeadlessSessionManager`, snapshots, API DTOs, remote client) live in `com.damn.aisuper.headless` inside `:shared`.

## Endpoints

- `GET /health`
- `POST /sessions` with `{ "manifestPath": "..." }`
- `GET /sessions`
- `GET /sessions/{id}/state`
- `POST /sessions/{id}/action`
- `POST /sessions/{id}/value`
- `POST /sessions/{id}/module-command`
- `GET /sessions/{id}/events` (SSE)

## Run

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
./gradlew :server:run
```


