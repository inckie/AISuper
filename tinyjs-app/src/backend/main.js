// main.js - TinyJS backend entry point for AISuper
import { AppletSession } from './applet_session.js';
import { resolveAppletManifest } from './applet_resolver.js';

let appInstance = null;
let currentSession = null;
const sessions = new Map();

async function loadAppletFromPath(targetPath) {
  console.log(`[Backend] Loading applet from path: ${targetPath}`);
  const manifestPath = await resolveAppletManifest(targetPath);
  console.log(`[Backend] Resolved manifest: ${manifestPath}`);

  // Close previous session if any
  if (currentSession) {
    try {
      currentSession.close();
    } catch (_) {}
    currentSession = null;
  }

  const sessionId = `sess_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`;
  const session = new AppletSession(sessionId, manifestPath, (snapshot) => {
    if (appInstance) {
      appInstance.push('state', snapshot);
    }
  });

  await session.load();
  currentSession = session;
  sessions.set(sessionId, session);

  const state = session.getSnapshot('initial');
  if (appInstance) {
    appInstance.push('session_created', {
      id: sessionId,
      manifestPath,
      state
    });
    appInstance.push('state', state);
  }

  return { id: sessionId, manifestPath, state };
}

export const api = {
  async health() {
    return { ok: true, runtime: 'txiki.js', time: new Date().toISOString() };
  },

  async listSessions() {
    return Array.from(sessions.keys());
  },

  async createSession({ manifestPath }) {
    if (!manifestPath || manifestPath.trim() === '') {
      // Empty: return no session, prompting drag-and-drop landing screen
      return { id: '', manifestPath: '', state: null };
    }
    return await loadAppletFromPath(manifestPath);
  },

  async getState({ sessionId }) {
    const session = sessions.get(sessionId) || currentSession;
    if (!session) {
      return null;
    }
    return session.getSnapshot('poll');
  },

  async sendAction({ sessionId, action, args }) {
    const session = sessions.get(sessionId) || currentSession;
    if (!session) {
      throw new Error(`Session '${sessionId}' not found.`);
    }
    const state = await session.sendAction(action, args || []);
    if (appInstance) {
      appInstance.push('state', state);
    }
    return state;
  },

  async setValue({ sessionId, id, value }) {
    const session = sessions.get(sessionId) || currentSession;
    if (!session) {
      throw new Error(`Session '${sessionId}' not found.`);
    }
    const state = await session.setValue(id, value);
    if (appInstance) {
      appInstance.push('state', state);
    }
    return state;
  },

  async moduleCommand({ sessionId, moduleType, target, command, args }) {
    console.log(`[Backend] Module command: ${moduleType}/${target}/${command}`, args);
    const session = sessions.get(sessionId) || currentSession;
    if (!session) {
      throw new Error(`Session '${sessionId}' not found.`);
    }
    return session.getSnapshot('moduleCommand');
  },

  async respondModal({ sessionId, id, confirmed }) {
    const session = sessions.get(sessionId) || currentSession;
    if (!session) {
      throw new Error(`Session '${sessionId}' not found.`);
    }
    session.respondToModal(id, confirmed);
    const state = session.getSnapshot('modal_response');
    if (appInstance) {
      appInstance.push('state', state);
    }
    return state;
  }
};

// Called by TinyJS when files/folders are passed via CLI or dragged & dropped onto window
export async function onOpenFiles(paths, app) {
  console.log('[Backend] onOpenFiles received:', paths);
  if (paths && paths.length > 0) {
    try {
      await loadAppletFromPath(paths[0]);
    } catch (err) {
      console.error('[Backend] Failed to load opened path:', err);
      if (app) {
        app.push('error', { message: err.message });
      }
    }
  }
}

export function init(app) {
  appInstance = app;
  console.log('[Backend] AISuper Pure JS backend initialized.');

  // Check if CLI arguments were passed directly
  if (typeof tjs !== 'undefined' && Array.isArray(tjs.args)) {
    const candidateArgs = tjs.args.slice(1).filter(a => a && !a.startsWith('-') && !a.endsWith('tjs.exe'));
    if (candidateArgs.length > 0) {
      const cliPath = candidateArgs[candidateArgs.length - 1];
      console.log('[Backend] Discovered CLI argument path:', cliPath);
      loadAppletFromPath(cliPath).catch(err => {
        console.error('[Backend] Failed to load CLI argument:', err);
      });
    }
  }
}
