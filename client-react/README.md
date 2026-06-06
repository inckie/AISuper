# AISuper React Client

React + TypeScript client for the headless server in `../server`.

## Features

- Health check against `GET /health`
- Session create/list/state APIs
- Action/value/module-command forms
- Live state updates via SSE (`/sessions/{id}/events`)
- PWA manifest + service worker for app-shell caching

## Quick start (Windows PowerShell)

```powershell
Set-Location "D:\Work\Mobile\Android\AISuper\client-react"
npm install
npm run dev
```

The client defaults to `http://localhost:8080` and can be changed in the UI.

## Build

```powershell
Set-Location "D:\Work\Mobile\Android\AISuper\client-react"
npm run build
npm run preview
```

## Run server (separate terminal)

```powershell
Set-Location "D:\Work\Mobile\Android\AISuper"
.\gradlew.bat :server:run
```

