// http_service.js - Centralized HTTP client with automatic Cookie Jar matching Kotlin HttpComponent

const KNOWN_COOKIE_ATTRS = new Set([
  'path',
  'expires',
  'max-age',
  'domain',
  'secure',
  'httponly',
  'samesite',
  'priority',
  'version',
  'comment'
]);

export class CookieJar {
  constructor() {
    this.cookies = []; // Array<{ name, value, domain, path, secure, httpOnly }>
  }

  setCookiesFromHeader(urlStr, setCookieHeader) {
    if (!setCookieHeader) return;
    try {
      const url = new URL(urlStr);
      const defaultDomain = url.hostname.toLowerCase();

      const lines = Array.isArray(setCookieHeader) ? setCookieHeader : String(setCookieHeader).split(/[\r\n]+/);
      for (const line of lines) {
        if (!line.trim()) continue;

        const tokens = line.split(';').map(t => t.trim()).filter(Boolean);
        let currentCookie = null;

        for (const token of tokens) {
          const eqIdx = token.indexOf('=');
          const key = (eqIdx >= 0 ? token.substring(0, eqIdx) : token).trim();
          const val = (eqIdx >= 0 ? token.substring(eqIdx + 1) : '').trim();
          const lowerKey = key.toLowerCase();

          if (KNOWN_COOKIE_ATTRS.has(lowerKey)) {
            if (currentCookie) {
              if (lowerKey === 'path') currentCookie.path = val || '/';
              else if (lowerKey === 'domain') currentCookie.domain = val.toLowerCase().replace(/^\./, '');
              else if (lowerKey === 'expires') currentCookie.expires = val;
              else if (lowerKey === 'secure') currentCookie.secure = true;
              else if (lowerKey === 'httponly') currentCookie.httpOnly = true;
            }
          } else if (eqIdx > 0) {
            // New cookie starts here
            currentCookie = {
              name: key,
              value: val,
              domain: defaultDomain,
              path: '/'
            };
            // Replace existing cookie with same name and domain if present
            const existingIdx = this.cookies.findIndex(c => c.name === key && c.domain === currentCookie.domain);
            if (existingIdx >= 0) {
              this.cookies[existingIdx] = currentCookie;
            } else {
              this.cookies.push(currentCookie);
            }
          }
        }
      }
    } catch (e) {
      console.warn('[CookieJar] Failed to parse set-cookie header:', e);
    }
  }

  getCookieHeader(urlStr) {
    try {
      const url = new URL(urlStr);
      const requestHost = url.hostname.toLowerCase();
      const requestPath = url.pathname || '/';

      const matching = [];
      for (const c of this.cookies) {
        // Domain match: exact hostname or parent domain
        const cDomain = (c.domain || '').toLowerCase().replace(/^\./, '');
        const domainMatches = requestHost === cDomain || requestHost.endsWith('.' + cDomain);
        if (!domainMatches) continue;

        // Path match
        if (c.path && !requestPath.startsWith(c.path)) continue;

        matching.push(`${c.name}=${c.value}`);
      }

      return matching.join('; ');
    } catch (_) {
      return '';
    }
  }

  clear() {
    this.cookies = [];
  }
}

export class HttpService {
  constructor() {
    this.cookieJar = new CookieJar();
  }

  async requestRaw(method, url, body = '', headers = {}) {
    try {
      const reqMethod = (method || 'GET').toUpperCase();
      const requestHeaders = { ...(headers || {}) };

      // Ensure standard User-Agent if none specified
      if (!requestHeaders['User-Agent'] && !requestHeaders['user-agent']) {
        requestHeaders['User-Agent'] = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36';
      }

      // Attach cookies from jar if not explicitly provided
      const cookieHeader = this.cookieJar.getCookieHeader(url);
      if (cookieHeader && !requestHeaders['Cookie'] && !requestHeaders['cookie']) {
        requestHeaders['Cookie'] = cookieHeader;
      }

      const init = {
        method: reqMethod,
        headers: requestHeaders
      };

      // Attach body for non-GET/HEAD methods, or set Content-Length: 0 if empty
      if (reqMethod !== 'GET' && reqMethod !== 'HEAD') {
        const bodyStr = body !== undefined && body !== null ? String(body) : '';
        if (bodyStr.length > 0) {
          init.body = bodyStr;
        } else {
          // Explicit Content-Length: 0 prevents HTTP 411 Length Required on strict servers (ASP.NET/IIS)
          if (!requestHeaders['Content-Length'] && !requestHeaders['content-length']) {
            requestHeaders['Content-Length'] = '0';
          }
        }
      }

      const res = await fetch(url, init);

      // Record any Set-Cookie headers into jar
      const setCookie = res.headers.get('set-cookie');
      if (setCookie) {
        this.cookieJar.setCookiesFromHeader(url, setCookie);
      }

      const text = await res.text();
      const resHeaders = {};
      if (res.headers && typeof res.headers.forEach === 'function') {
        res.headers.forEach((val, key) => {
          resHeaders[key.toLowerCase()] = val;
        });
      }

      return JSON.stringify({
        status: res.status,
        headers: resHeaders,
        body: text
      });
    } catch (e) {
      console.error('[HttpService] requestRaw failed:', url, e);
      return JSON.stringify({
        status: 0,
        error: e instanceof Error ? e.message : String(e)
      });
    }
  }

  async get(url, headers = {}) {
    const raw = await this.requestRaw('GET', url, '', headers);
    try {
      const parsed = JSON.parse(raw);
      if (parsed.status >= 200 && parsed.status <= 299) {
        return parsed.body;
      }
      return `Error: HTTP ${parsed.status}`;
    } catch (e) {
      return `Error: ${e.message}`;
    }
  }

  async post(url, body = '', headers = {}) {
    const raw = await this.requestRaw('POST', url, body, headers);
    try {
      const parsed = JSON.parse(raw);
      if (parsed.status >= 200 && parsed.status <= 299) {
        return parsed.body;
      }
      return `Error: HTTP ${parsed.status}`;
    } catch (e) {
      return `Error: ${e.message}`;
    }
  }
}
