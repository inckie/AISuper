import type { CreateSessionResponse, HeadlessSessionSnapshot, JsonValue } from './types';

const parseResponse = async <T>(response: Response): Promise<T> => {
  const text = await response.text();

  if (!response.ok) {
    throw new Error(text || `Request failed with ${response.status}`);
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (!contentType.includes('application/json')) {
    const preview = text.slice(0, 120).replace(/\s+/g, ' ').trim();
    throw new Error(`Expected JSON but got '${contentType || 'unknown'}': ${preview}`);
  }

  try {
    return JSON.parse(text) as T;
  } catch {
    const preview = text.slice(0, 120).replace(/\s+/g, ' ').trim();
    throw new Error(`Invalid JSON response: ${preview}`);
  }
};

const postJson = async <T>(url: string, body: unknown): Promise<T> => {
  const response = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  return parseResponse<T>(response);
};

export const serverApi = {
  async health(baseUrl: string): Promise<{ ok: boolean }> {
    if (typeof window !== 'undefined' && (window as any).tiny?.api) {
      return (window as any).tiny.api.call('health');
    }
    const response = await fetch(`${baseUrl}/health`);
    return parseResponse<{ ok: boolean }>(response);
  },

  async listSessions(baseUrl: string): Promise<string[]> {
    if (typeof window !== 'undefined' && (window as any).tiny?.api) {
      return (window as any).tiny.api.call('listSessions');
    }
    const response = await fetch(`${baseUrl}/sessions`);
    return parseResponse<string[]>(response);
  },

  async createSession(baseUrl: string, manifestPath: string): Promise<CreateSessionResponse> {
    if (typeof window !== 'undefined' && (window as any).tiny?.api) {
      return (window as any).tiny.api.call('createSession', { manifestPath });
    }
    return postJson<CreateSessionResponse>(`${baseUrl}/sessions`, { manifestPath });
  },

  async getState(baseUrl: string, sessionId: string): Promise<HeadlessSessionSnapshot> {
    if (typeof window !== 'undefined' && (window as any).tiny?.api) {
      return (window as any).tiny.api.call('getState', { sessionId });
    }
    const response = await fetch(`${baseUrl}/sessions/${sessionId}/state`);
    return parseResponse<HeadlessSessionSnapshot>(response);
  },

  async sendAction(
    baseUrl: string,
    sessionId: string,
    action: string,
    args: JsonValue[]
  ): Promise<HeadlessSessionSnapshot> {
    if (typeof window !== 'undefined' && (window as any).tiny?.api) {
      return (window as any).tiny.api.call('sendAction', { sessionId, action, args });
    }
    return postJson<HeadlessSessionSnapshot>(`${baseUrl}/sessions/${sessionId}/action`, {
      action,
      args
    });
  },

  async setValue(
    baseUrl: string,
    sessionId: string,
    id: string,
    value: JsonValue
  ): Promise<HeadlessSessionSnapshot> {
    if (typeof window !== 'undefined' && (window as any).tiny?.api) {
      return (window as any).tiny.api.call('setValue', { sessionId, id, value });
    }
    return postJson<HeadlessSessionSnapshot>(`${baseUrl}/sessions/${sessionId}/value`, {
      id,
      value
    });
  },

  async moduleCommand(
    baseUrl: string,
    sessionId: string,
    moduleType: string,
    target: string,
    command: string,
    args: JsonValue[]
  ): Promise<HeadlessSessionSnapshot> {
    if (typeof window !== 'undefined' && (window as any).tiny?.api) {
      return (window as any).tiny.api.call('moduleCommand', { sessionId, moduleType, target, command, args });
    }
    return postJson<HeadlessSessionSnapshot>(`${baseUrl}/sessions/${sessionId}/module-command`, {
      moduleType,
      target,
      command,
      args
    });
  },

  async respondModal(
    baseUrl: string,
    sessionId: string,
    id: string,
    confirmed: boolean
  ): Promise<HeadlessSessionSnapshot> {
    if (typeof window !== 'undefined' && (window as any).tiny?.api) {
      return (window as any).tiny.api.call('respondModal', { sessionId, id, confirmed });
    }
    return postJson<HeadlessSessionSnapshot>(`${baseUrl}/sessions/${sessionId}/modal-response`, {
      id,
      confirmed
    });
  }
};
