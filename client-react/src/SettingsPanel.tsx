import { useState, useEffect } from 'react';

export interface SettingsState {
  baseUrl: string;
  manifestPath: string;
}

export const DEFAULT_SETTINGS: SettingsState = {
  baseUrl: 'http://localhost:8080',
  manifestPath: ''
};

export function readStoredSettings(): SettingsState {
  try {
    const raw = localStorage.getItem('aisuper-web-settings');
    if (!raw) return DEFAULT_SETTINGS;
    const parsed = JSON.parse(raw) as Partial<SettingsState>;
    const manifest = parsed.manifestPath?.trim();
    return {
      baseUrl: parsed.baseUrl?.trim() || DEFAULT_SETTINGS.baseUrl,
      manifestPath: (manifest && manifest !== 'files/applet.json' && !manifest.includes('d:/Work')) ? manifest : ''
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
          type="text"
          value={draftSettings.baseUrl}
          onChange={(e) => setDraftSettings((s) => ({ ...s, baseUrl: e.target.value }))}
          placeholder="http://localhost:8080"
        />
      </label>
      <label>
        Manifest Path
        <input
          type="text"
          value={draftSettings.manifestPath}
          onChange={(e) => setDraftSettings((s) => ({ ...s, manifestPath: e.target.value }))}
          placeholder="/path/to/applet or applet.zip"
        />
      </label>
      <div className="panel-actions">
        <button className="primary-btn" onClick={() => onSaveAndConnect(draftSettings)}>
          Save & Connect
        </button>
        <button className="ghost-btn" onClick={onClose}>
          Cancel
        </button>
      </div>
    </aside>
  );
}
