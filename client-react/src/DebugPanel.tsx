import { useState } from 'react';
import { parseJsonInput } from './layoutUtils';
import type { HeadlessSessionSnapshot, JsonValue } from './types';

interface DebugPanelProps {
  snapshot: HeadlessSessionSnapshot | null;
  onAction: (action: string, args: JsonValue[]) => Promise<void>;
  onValueChange: (id: string, value: string) => Promise<void>;
  onModuleCommand: (
    moduleType: string,
    target: string,
    command: string,
    args: JsonValue[]
  ) => Promise<void>;
}

type DebugTab = 'actions' | 'values' | 'layout';

export function DebugPanel({
  snapshot,
  onAction,
  onValueChange,
  onModuleCommand
}: DebugPanelProps) {
  const [activeTab, setActiveTab] = useState<DebugTab>('actions');

  const [debugAction, setDebugAction] = useState('');
  const [debugActionArgs, setDebugActionArgs] = useState('[]');
  const [debugValueId, setDebugValueId] = useState('');
  const [debugValue, setDebugValue] = useState('');
  const [debugModuleType, setDebugModuleType] = useState('audioPlayer');
  const [debugModuleTarget, setDebugModuleTarget] = useState('');
  const [debugModuleCommand, setDebugModuleCommand] = useState('');
  const [debugModuleArgs, setDebugModuleArgs] = useState('[]');

  function parseArgsInput(input: string): JsonValue[] {
    const parsed = parseJsonInput(input);
    return Array.isArray(parsed) ? parsed : [];
  }

  return (
    <aside className="panel left-panel">
      <h2>Debug</h2>

      <div className="tab-bar">
        <button
          className={`tab-btn${activeTab === 'actions' ? ' active' : ''}`}
          onClick={() => setActiveTab('actions')}
        >
          Actions
        </button>
        <button
          className={`tab-btn${activeTab === 'values' ? ' active' : ''}`}
          onClick={() => setActiveTab('values')}
        >
          Values
        </button>
        <button
          className={`tab-btn${activeTab === 'layout' ? ' active' : ''}`}
          onClick={() => setActiveTab('layout')}
        >
          Layout
        </button>
      </div>

      <div className="tab-content">
        {activeTab === 'actions' && (
          <>
            <section>
              <h3>Session Info</h3>
              {snapshot ? (
                <div className="value-data">
                  {(() => {
                    const { values, layout, styleSheet, ...other } = snapshot;
                    return <pre>{JSON.stringify(other, null, 2)}</pre>;
                  })()}
                </div>
              ) : (
                <p>No active session.</p>
              )}
            </section>

            <section>
              <h3>Action</h3>
              <input
                placeholder="action"
                value={debugAction}
                onChange={(e) => setDebugAction(e.target.value)}
              />
              <textarea
                value={debugActionArgs}
                onChange={(e) => setDebugActionArgs(e.target.value)}
                rows={3}
              />
              <button
                className="primary-btn"
                onClick={() => onAction(debugAction, parseArgsInput(debugActionArgs))}
              >
                Send Action
              </button>
            </section>

            <section>
              <h3>Set Value</h3>
              <input
                placeholder="id"
                value={debugValueId}
                onChange={(e) => setDebugValueId(e.target.value)}
              />
              <textarea
                value={debugValue}
                onChange={(e) => setDebugValue(e.target.value)}
                rows={2}
              />
              <button
                className="primary-btn"
                onClick={() =>
                  onValueChange(debugValueId, String(parseJsonInput(debugValue)))
                }
              >
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
              <textarea
                value={debugModuleArgs}
                onChange={(e) => setDebugModuleArgs(e.target.value)}
                rows={3}
              />
              <button
                className="primary-btn"
                onClick={() =>
                  onModuleCommand(
                    debugModuleType,
                    debugModuleTarget,
                    debugModuleCommand,
                    parseArgsInput(debugModuleArgs)
                  )
                }
              >
                Send Command
              </button>
            </section>
          </>
        )}

        {activeTab === 'values' && (
          <div className="values-list">
            {snapshot?.values && Object.keys(snapshot.values).length > 0 ? (
              Object.entries(snapshot.values).map(([id, val]) => (
                <div key={id} className="value-item">
                  <div className="value-id">{id}</div>
                  <div className="value-data">
                    {typeof val === 'object' ? (
                      <pre>{JSON.stringify(val, null, 2)}</pre>
                    ) : (
                      String(val)
                    )}
                  </div>
                </div>
              ))
            ) : (
              <p>No values in snapshot.</p>
            )}
          </div>
        )}

        {activeTab === 'layout' && (
          <div className="snapshot-container">
            <h3>Layout Tree</h3>
            <pre>{JSON.stringify(snapshot?.layout, null, 2)}</pre>
          </div>
        )}
      </div>
    </aside>
  );
}
