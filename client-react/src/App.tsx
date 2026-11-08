import { useEffect, useMemo, useState } from 'react';
import { Bug, Settings, FolderOpen, FileArchive, UploadCloud } from 'lucide-react';
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
  const [status, setStatus] = useState<string>('Ready');
  const [error, setError] = useState<string>('');
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [debugOpen, setDebugOpen] = useState(false);
  const [isDragging, setIsDragging] = useState(false);

  const focusRegistry = useFocusRegistry();
  const frameworkLabel = useFrameworkLabel(snapshot?.framework);

  useEffect(() => {
    localStorage.setItem('aisuper-web-settings', JSON.stringify(settings));
  }, [settings]);

  // Setup TinyJS listeners
  useEffect(() => {
    const tiny = (typeof window !== 'undefined' ? (window as any).tiny : null);
    if (!tiny?.api) return;

    // Listen for sessions created via CLI arguments or drag-drop
    const stopSessionCreated = tiny.api.on('session_created', (data: { id: string; manifestPath: string; state: HeadlessSessionSnapshot }) => {
      setSessionId(data.id);
      setSnapshot(data.state);
      setStatus('Connected');
      setError('');
    });

    const stopState = tiny.api.on('state', (payload: HeadlessSessionSnapshot) => {
      setSnapshot(payload);
      setStatus(`Live (${payload.reason})`);
    });

    const stopError = tiny.api.on('error', (err: { message: string }) => {
      setError(err.message || 'Error occurred');
    });

    // Check if a session is already active (e.g. from CLI arg on cold start)
    serverApi.getState('', '').then((activeState) => {
      if (activeState && activeState.sessionId) {
        setSessionId(activeState.sessionId);
        setSnapshot(activeState);
        setStatus('Connected');
      }
    }).catch(() => {});

    // Listen for file drops via TinyJS app API
    if (tiny.app?.onOpenFiles) {
      try {
        tiny.app.onOpenFiles((paths: string[]) => {
          if (paths && paths.length > 0) {
            connectSession({ ...settings, manifestPath: paths[0] });
          }
        });
      } catch (_) {}
    }

    return () => {
      if (typeof stopSessionCreated === 'function') stopSessionCreated();
      if (typeof stopState === 'function') stopState();
      if (typeof stopError === 'function') stopError();
    };
  }, []);

  // SSE Fallback for standalone browser mode
  useEffect(() => {
    if (!sessionId) return;
    const tiny = (typeof window !== 'undefined' ? (window as any).tiny : null);
    if (tiny?.api) return; // TinyJS handles events via tiny.api.on('state')

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
    if (!activeSettings.manifestPath) return;

    setError('');
    setStatus('Loading applet...');
    try {
      const created = await serverApi.createSession(activeSettings.baseUrl, activeSettings.manifestPath);
      if (created.id) {
        setSessionId(created.id);
        setSnapshot(created.state);
        setStatus(`Connected (${created.id})`);
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : String(err);
      setError(`Failed to load applet: ${message}`);
      setStatus('Error');
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

  async function handleModalResponse(id: string, confirmed: boolean): Promise<void> {
    if (!sessionId) return;
    try {
      const next = await serverApi.respondModal(settings.baseUrl, sessionId, id, confirmed);
      setSnapshot(next);
      setStatus(confirmed ? 'Modal: Confirmed' : 'Modal: Cancelled');
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    }
  }

  async function handlePickFolder() {
    const tiny = (window as any).tiny;
    if (tiny?.dialog?.pickFolder) {
      try {
        const folder = await tiny.dialog.pickFolder();
        if (folder) {
          connectSession({ ...settings, manifestPath: folder });
        }
      } catch (err) {
        console.error('Folder picker error:', err);
      }
    }
  }

  async function handleOpenFile() {
    const tiny = (window as any).tiny;
    if (tiny?.dialog?.openFile) {
      try {
        const file = await tiny.dialog.openFile({ types: ['zip', 'json'] });
        if (file) {
          connectSession({ ...settings, manifestPath: file });
        }
      } catch (err) {
        console.error('File picker error:', err);
      }
    }
  }

  const headerMeta = useMemo(() => {
    return [`Framework: ${frameworkLabel}`, `Session: ${sessionId || 'none'}`, `Status: ${status}`].join(' • ');
  }, [frameworkLabel, sessionId, status]);

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
    <div
      className="app-shell"
      onDragOver={(e) => {
        e.preventDefault();
        setIsDragging(true);
      }}
      onDragLeave={(e) => {
        e.preventDefault();
        setIsDragging(false);
      }}
      onDrop={(e) => {
        e.preventDefault();
        setIsDragging(false);
        const files = e.dataTransfer.files;
        if (files && files.length > 0) {
          const filePath = (files[0] as any).path;
          if (filePath) {
            connectSession({ ...settings, manifestPath: filePath });
          }
        }
      }}
    >
      <header className="top-bar">
        <div>
          <div className="title">AISuper Pure JS</div>
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

      <main className="layout-host" style={hasLayout ? layoutStyle : {}}>
        {!hasLayout && (
          <div className="drop-landing">
            <div className={`drop-zone${isDragging ? ' dragging' : ''}`}>
              <UploadCloud className="drop-zone-icon" />
              <h2 className="drop-zone-title">Load an AISuper Applet</h2>
              <p className="drop-zone-desc">
                Drag and drop an Applet folder or a <strong>.zip</strong> archive directly onto this window.
              </p>
              <div className="drop-zone-buttons">
                <button className="secondary-btn" onClick={handlePickFolder}>
                  <FolderOpen size={18} />
                  Browse Folder...
                </button>
                <button className="secondary-btn" onClick={handleOpenFile}>
                  <FileArchive size={18} />
                  Browse .zip / .json...
                </button>
              </div>
            </div>
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

      {snapshot?.modal && (
        <div className="modal-overlay" role="dialog" aria-modal="true">
          <div className="modal-dialog">
            <h3 className="modal-title">{snapshot.modal.title || 'Подтверждение'}</h3>
            {snapshot.modal.message && (
              <p className="modal-message">{snapshot.modal.message}</p>
            )}
            <div className="modal-actions">
              <button
                type="button"
                className="modal-btn modal-cancel-btn"
                onClick={() => handleModalResponse(snapshot.modal!.id, false)}
              >
                {snapshot.modal.cancelText || 'Отмена'}
              </button>
              <button
                type="button"
                className={`modal-btn modal-confirm-btn${snapshot.modal.isDestructive ? ' destructive' : ''}`}
                onClick={() => handleModalResponse(snapshot.modal!.id, true)}
              >
                {snapshot.modal.confirmText || 'Подтвердить'}
              </button>
            </div>
          </div>
        </div>
      )}

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
