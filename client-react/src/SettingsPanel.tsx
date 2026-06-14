import { useState, useEffect } from 'react';

export interface SettingsState {
  baseUrl: string;
  manifestPath: string;
}

export const DEFAULT_SETTINGS: SettingsState = {
  baseUrl: 'http://localhost:8080',
  manifestPath: 'files/applet.json'
};

export function readStoredSettings(): SettingsState {
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

interface SettingsPanelProps {
  onClose: () => void;
  onSaveAndConnect: (settings: SettingsState) => void;
  initialSettings: SettingsState;
}

export function SettingsPanel({
  onClose,
  onSaveAndConnect,
  initialSettings
}: SettingsPanelProps) {
  const [draftSettings, setDraftSettings] = useState<SettingsState>(initialSettings);

  // Sync draft settings when initial settings change (e.g. on mount or if updated elsewhere)
  useEffect(() => {
    setDraftSettings(initialSettings);
  }, [initialSettings]);

  return (
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
            onSaveAndConnect(draftSettings);
          }}
        >
          Save + Connect
        </button>
        <button className="secondary-btn" onClick={onClose}>
          Close
        </button>
      </div>
    </aside>
  );
}
