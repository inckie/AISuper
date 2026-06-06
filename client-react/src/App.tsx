import { useEffect, useMemo, useState } from 'react';
import { Bug, Settings } from 'lucide-react';
import { serverApi } from './api';
import { parseColorOrNull, parseJsonInput } from './layoutUtils';
import { useFocusRegistry, useFrameworkLabel, WidgetRenderer } from './WidgetRenderer';
import type { HeadlessSessionSnapshot, JsonValue } from './types';

interface SettingsState {
  baseUrl: string;
  manifestPath: string;
}

const DEFAULT_SETTINGS: SettingsState = {
  baseUrl: 'http://localhost:8080',
  manifestPath: 'files/applet.json'
};

function readStoredSettings(): SettingsState {
  try {
    const raw = localStorage.getItem('aisuper-web-settings');
    if (!raw) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw) as Partial<SettingsState>;
    return {
      baseUrl: parsed.baseUrl?.trim() || DEFAULT_SETTINGS.baseUrl,
      manifestPath: parsed.manifestPath?.trim() || DEFAULT_SETTINGS.manifestPath
    };
  } catch {
    return DEFAULT_SETTINGS;
  }
}

export default function App(): JSX.Element {
  const [settings, setSettings] = useState<SettingsState>(() => readStoredSettings());
  const [draftSettings, setDraftSettings] = useState<SettingsState>(() => readStoredSettings());
  const [snapshot, setSnapshot] = useState<HeadlessSessionSnapshot | null>(null);
  const [sessionId, setSessionId] = useState<string>('');
  const [status, setStatus] = useState<string>('Idle');
  const [error, setError] = useState<string>('');
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [debugOpen, setDebugOpen] = useState(false);

  const [debugAction, setDebugAction] = useState('');
  const [debugActionArgs, setDebugActionArgs] = useState('[]');
  const [debugValueId, setDebugValueId] = useState('');
  const [debugValue, setDebugValue] = useState('');
  const [debugModuleType, setDebugModuleType] = useState('audioPlayer');
  const [debugModuleTarget, setDebugModuleTarget] = useState('');
  const [debugModuleCommand, setDebugModuleCommand] = useState('');
  const [debugModuleArgs, setDebugModuleArgs] = useState('[]');

  const focusRegistry = useFocusRegistry();
  const frameworkLabel = useFrameworkLabel(snapshot?.framework);

  function parseArgsInput(input: string): JsonValue[] {
    const parsed = parseJsonInput(input);
    return Array.isArray(parsed) ? parsed : [];
  }

  useEffect(() => {
    localStorage.setItem('aisuper-web-settings', JSON.stringify(settings));
  }, [settings]);

  useEffect(() => {
    if (!sessionId) return;
    const streamUrl = `${settings.baseUrl.replace(/\/+$/, '')}/sessions/${sessionId}/events`;
    const source = new EventSource(streamUrl);
    source.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data) as HeadlessSessionSnapshot;
        setSnapshot(payload);
        setStatus(`Live (${payload.reason})`);
      } catch {
        setError('Failed to decode SSE payload.');
      }
    };
    source.addEventListener('state', (event) => {
      if (!(event instanceof MessageEvent)) return;
      try {
        const payload = JSON.parse(event.data) as HeadlessSessionSnapshot;
        setSnapshot(payload);
        setStatus(`Live (${payload.reason})`);
      } catch {
        setError('Failed to decode state event.');
      }
    });
    source.onerror = () => {
      setStatus('SSE disconnected');
      setError('SSE connection dropped.');
    };
    return () => source.close();
  }, [settings.baseUrl, sessionId]);

  const hasLayout = Boolean(snapshot?.layout?.layout);

  async function connectSession(): Promise<void> {
    setError('');
    setStatus('Creating session...');
    try {
      const created = await serverApi.createSession(settings.baseUrl, settings.manifestPath);
      setSessionId(created.id);
      let nextSnapshot = created.state;

      // Many applets publish layout after an initial action.
      if (!nextSnapshot.layout?.layout) {
        try {
          nextSnapshot = await serverApi.sendAction(settings.baseUrl, created.id, 'init', []);
        } catch {
          // Keep created state visible if init is not supported.
        }
      }

      setSnapshot(nextSnapshot);
      setStatus(`Connected (${created.id})`);
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setError(`Failed to create session: ${message}`);
      setStatus('Disconnected');
    }
  }

  async function onAction(action: string, args: JsonValue[]): Promise<void> {
    if (!sessionId || !action) return;
    try {
      const next = await serverApi.sendAction(settings.baseUrl, sessionId, action, args);
      setSnapshot(next);
      setStatus(`Action: ${action}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  async function onValueChange(id: string, value: string): Promise<void> {
    if (!sessionId) return;
    try {
      const next = await serverApi.setValue(settings.baseUrl, sessionId, id, parseJsonInput(value));
      setSnapshot(next);
      setStatus(`Value: ${id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  async function onModuleCommand(moduleType: string, target: string, command: string, args: JsonValue[]): Promise<void> {
    if (!sessionId) return;
    try {
      const next = await serverApi.moduleCommand(settings.baseUrl, sessionId, moduleType, target, command, args);
      setSnapshot(next);
      setStatus(`Module: ${moduleType}/${command}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  const headerMeta = useMemo(() => {
    return [`Framework: ${frameworkLabel}`, `Session: ${sessionId || 'none'}`, `Status: ${status}`].join(' • ');
  }, [frameworkLabel, sessionId, status]);

  // Derive layout area colors from the active applet style sheet so the
  // background and text color match the loaded theme (light / dark / custom).
  // The top-bar keeps its own fixed dark styling regardless of the theme.
  const layoutStyle = useMemo(() => {
    const sheet = snapshot?.styleSheet;
    if (!sheet) return {};
    const screenClass = sheet.classes?.['screen'];
    const bg = parseColorOrNull(screenClass?.backgroundColor);
    const textColor = parseColorOrNull(
      screenClass?.textColor ?? sheet.defaults?.['Text']?.textColor
    );
    return { background: bg, color: textColor };
  }, [snapshot?.styleSheet]);

  return (
    <div className="app-shell">
      <header className="top-bar">
        <div>
          <div className="title">AISuper Web</div>
          <div className="subtitle">{headerMeta}</div>
        </div>
        <div className="toolbar-actions">
          <button
              className={`icon-btn${debugOpen ? ' active' : ''}`}
              onClick={() => setDebugOpen((v) => !v)}
              title="Debug"
            >
              <Bug size={18} />
            </button>
            <button
              className={`icon-btn${settingsOpen ? ' active' : ''}`}
              onClick={() => setSettingsOpen((v) => !v)}
              title="Settings"
            >
              <Settings size={18} />
          </button>
        </div>
      </header>

      <main className="layout-host" style={layoutStyle}>
        {!hasLayout && (
          <div className="empty-state">
            <p>No layout loaded.</p>
            <button className="primary-btn" onClick={connectSession}>
              Create Session
            </button>
          </div>
        )}

        {hasLayout && snapshot?.layout && (
          <WidgetRenderer
            widget={snapshot.layout.layout}
            values={snapshot.values ?? {}}
            styleSheet={snapshot.styleSheet}
            onValueChange={onValueChange}
            onAction={onAction}
            onModuleCommand={onModuleCommand}
            focusRegistry={focusRegistry}
          />
        )}
      </main>

      {settingsOpen && (
        <aside className="panel right-panel">
          <h2>Settings</h2>
          <label>
            Server URL
            <input
              value={draftSettings.baseUrl}
              onChange={(event) => setDraftSettings((s) => ({ ...s, baseUrl: event.target.value }))}
            />
          </label>
          <label>
            Manifest Path
            <input
              value={draftSettings.manifestPath}
              onChange={(event) => setDraftSettings((s) => ({ ...s, manifestPath: event.target.value }))}
            />
          </label>
          <div className="panel-actions">
            <button
              className="primary-btn"
              onClick={() => {
                setSettings(draftSettings);
                connectSession();
              }}
            >
              Save + Connect
            </button>
            <button className="secondary-btn" onClick={() => setSettingsOpen(false)}>
              Close
            </button>
          </div>
        </aside>
      )}

      {debugOpen && (
        <aside className="panel left-panel">
          <h2>Debug</h2>

          <section>
            <h3>Action</h3>
            <input placeholder="action" value={debugAction} onChange={(e) => setDebugAction(e.target.value)} />
            <textarea value={debugActionArgs} onChange={(e) => setDebugActionArgs(e.target.value)} rows={3} />
            <button className="primary-btn" onClick={() => onAction(debugAction, parseArgsInput(debugActionArgs))}>
              Send Action
            </button>
          </section>

          <section>
            <h3>Set Value</h3>
            <input placeholder="id" value={debugValueId} onChange={(e) => setDebugValueId(e.target.value)} />
            <textarea value={debugValue} onChange={(e) => setDebugValue(e.target.value)} rows={2} />
            <button className="primary-btn" onClick={() => onValueChange(debugValueId, String(parseJsonInput(debugValue)))}>
              Set Value
            </button>
          </section>

          <section>
            <h3>Module Command</h3>
            <input
              placeholder="module type"
              value={debugModuleType}
              onChange={(e) => setDebugModuleType(e.target.value)}
            />
            <input
              placeholder="target"
              value={debugModuleTarget}
              onChange={(e) => setDebugModuleTarget(e.target.value)}
            />
            <input
              placeholder="command"
              value={debugModuleCommand}
              onChange={(e) => setDebugModuleCommand(e.target.value)}
            />
            <textarea value={debugModuleArgs} onChange={(e) => setDebugModuleArgs(e.target.value)} rows={3} />
            <button
              className="primary-btn"
              onClick={() =>
                onModuleCommand(debugModuleType, debugModuleTarget, debugModuleCommand, parseArgsInput(debugModuleArgs))
              }
            >
              Send Command
            </button>
          </section>

          <section>
            <h3>Live Snapshot</h3>
            <pre>{JSON.stringify(snapshot, null, 2)}</pre>
          </section>
        </aside>
      )}

      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}

