import { useEffect, useMemo, useState } from 'react';
import { Bug, Settings } from 'lucide-react';
import { serverApi } from './api';
import { parseColorOrNull, parseJsonInput } from './layoutUtils';
import { useFocusRegistry, useFrameworkLabel, WidgetRenderer } from './WidgetRenderer';
import type { HeadlessSessionSnapshot, JsonValue } from './types';
import { SettingsPanel, SettingsState, readStoredSettings } from './SettingsPanel';
import { DebugPanel } from './DebugPanel';

export default function App(): JSX.Element {
  const [settings, setSettings] = useState<SettingsState>(() => readStoredSettings());
  const [snapshot, setSnapshot] = useState<HeadlessSessionSnapshot | null>(null);
  const [sessionId, setSessionId] = useState<string>('');
  const [status, setStatus] = useState<string>('Idle');
  const [error, setError] = useState<string>('');
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [debugOpen, setDebugOpen] = useState(false);

  const focusRegistry = useFocusRegistry();
  const frameworkLabel = useFrameworkLabel(snapshot?.framework);

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

  async function connectSession(overrideSettings?: SettingsState): Promise<void> {
    const activeSettings = overrideSettings || settings;
    setError('');
    setStatus('Creating session...');
    try {
      const created = await serverApi.createSession(activeSettings.baseUrl, activeSettings.manifestPath);
      setSessionId(created.id);
      let nextSnapshot = created.state;

      // Many applets publish layout after an initial action.
      if (!nextSnapshot.layout?.layout) {
        try {
          nextSnapshot = await serverApi.sendAction(activeSettings.baseUrl, created.id, 'init', []);
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
            <button className="primary-btn" onClick={() => connectSession()}>
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
        <SettingsPanel
          onClose={() => setSettingsOpen(false)}
          initialSettings={settings}
          onSaveAndConnect={(newSettings) => {
            setSettings(newSettings);
            connectSession(newSettings);
          }}
        />
      )}

      {debugOpen && (
        <DebugPanel
          snapshot={snapshot}
          onAction={onAction}
          onValueChange={onValueChange}
          onModuleCommand={onModuleCommand}
        />
      )}

      {error && <div className="error-banner">{error}</div>}
    </div>
  );
}
