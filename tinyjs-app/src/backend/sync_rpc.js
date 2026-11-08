/**
 * @wk-id tinyjs/sync-rpc
 * @wk-title TinyJS Synchronous RPC Bridge (sync_rpc.js)
 * @wk-tags tinyjs, rpc, atomics, sharedarraybuffer, workers, isolation
 * @wk-categories tinyjs-runtime
 *
 * # Synchronous RPC Bridge
 *
 * Enables Web Worker JavaScript isolates to execute synchronous blocking host and
 * cross-module RPC calls without burning CPU, preserving full VM isolation while maintaining
 * backward compatibility with synchronous applet APIs like `getValue(key)`, `setValue(key, val)`,
 * `storageGet(scope, key)`, and module proxy methods like `security_getPromoCards()`.
 *
 * ## Architecture & Mechanism
 * 1. **Shared Memory Layout**: A `SharedArrayBuffer` (typically 512 KB) is shared between the host and worker.
 *    The first 16 bytes serve as an atomic status and length header (`Int32Array`):
 *    - Index 0 (`HEADER_STATUS_IDX`): Status flag (`0: IDLE`, `1: REQ_PENDING`, `2: RESP_READY`, `3: ERROR`).
 *    - Index 1 (`HEADER_REQ_LEN_IDX`): Request payload length in bytes.
 *    - Index 2 (`HEADER_RESP_LEN_IDX`): Response payload length in bytes.
 *    - Index 3 (`HEADER_SEQ_IDX`): Sequence counter.
 *    - Bytes 16+ (`PAYLOAD_OFFSET`): JSON UTF-8 payload buffer.
 * 2. **Client Invocation (`SyncRpcClient.call`)**:
 *    - Serializes `{ method, args }` into the payload buffer.
 *    - Posts `{ type: '__sync_rpc__' }` to the host thread via message channel.
 *    - Calls `Atomics.wait(statusInt32, 0, 0)` which puts the calling isolate thread to sleep efficiently.
 * 3. **Host Handling (`SyncRpcHost.handleRpc`)**:
 *    - Reads the request JSON, executes the registered handler (can be async/Promise).
 *    - Serializes the result or error into the payload buffer.
 *    - Updates `HEADER_RESP_LEN_IDX` and sets status to `STATUS_RESP_READY` or `STATUS_ERROR`.
 *    - Calls `Atomics.notify(statusInt32, 0, 1)` to wake up the sleeping worker thread.
 * 4. **Worker Resumes**:
 *    - Worker wakes up from `Atomics.wait`, decodes the response string, and resets status to `STATUS_IDLE`.
 *    - Returns the unwrapped result directly or throws on error.
 */

const STATUS_IDLE = 0;
const STATUS_REQ_PENDING = 1;
const STATUS_RESP_READY = 2;
const STATUS_ERROR = 3;

const HEADER_STATUS_IDX = 0;
const HEADER_REQ_LEN_IDX = 1;
const HEADER_RESP_LEN_IDX = 2;
const HEADER_SEQ_IDX = 3;
const PAYLOAD_OFFSET = 16;

export class SyncRpcBuffer {
  static create(size = 64 * 1024) {
    return new SharedArrayBuffer(size);
  }
}

export class SyncRpcClient {
  constructor(sab) {
    this.sab = sab;
    this.statusInt32 = new Int32Array(sab, 0, 4);
    this.dataUint8 = new Uint8Array(sab, PAYLOAD_OFFSET);
    this.maxPayload = sab.byteLength - PAYLOAD_OFFSET;
    this.encoder = new TextEncoder();
    this.decoder = new TextDecoder();
  }

  call(method, args = []) {
    const payload = JSON.stringify({ method, args });
    const encoded = this.encoder.encode(payload);

    if (encoded.length > this.maxPayload) {
      throw new Error(`Sync RPC payload exceeds maximum buffer size (${encoded.length} > ${this.maxPayload})`);
    }

    // Write request into SharedArrayBuffer
    this.dataUint8.set(encoded, 0);
    Atomics.store(this.statusInt32, HEADER_REQ_LEN_IDX, encoded.length);

    // Notify host that request is ready
    globalThis.postMessage({ type: '__sync_rpc__' });

    // Block until status changes from 0 (waiting for host response)
    while (Atomics.load(this.statusInt32, HEADER_STATUS_IDX) === STATUS_IDLE) {
      Atomics.wait(this.statusInt32, HEADER_STATUS_IDX, STATUS_IDLE);
    }

    const status = Atomics.load(this.statusInt32, HEADER_STATUS_IDX);
    const respLen = Atomics.load(this.statusInt32, HEADER_RESP_LEN_IDX);

    // Copy slice from SharedArrayBuffer for safe TextDecoder compatibility
    const copy = new Uint8Array(respLen);
    copy.set(this.dataUint8.subarray(0, respLen));
    const respStr = this.decoder.decode(copy);

    // Reset status back to IDLE for subsequent calls
    Atomics.store(this.statusInt32, HEADER_STATUS_IDX, STATUS_IDLE);

    const parsed = JSON.parse(respStr);
    if (status === STATUS_ERROR) {
      throw new Error(parsed.error || `Sync RPC Error in ${method}`);
    }

    return parsed.result;
  }
}

export class SyncRpcHost {
  constructor(sab, handler) {
    this.sab = sab;
    this.statusInt32 = new Int32Array(sab, 0, 4);
    this.dataUint8 = new Uint8Array(sab, PAYLOAD_OFFSET);
    this.maxPayload = sab.byteLength - PAYLOAD_OFFSET;
    this.handler = handler;
    this.encoder = new TextEncoder();
    this.decoder = new TextDecoder();
  }

  async handleRpc() {
    const reqLen = Atomics.load(this.statusInt32, HEADER_REQ_LEN_IDX);
    const copy = new Uint8Array(reqLen);
    copy.set(this.dataUint8.subarray(0, reqLen));
    const reqStr = this.decoder.decode(copy);
    const { method, args } = JSON.parse(reqStr);

    try {
      const result = await this.handler(method, args);
      const encoded = this.encoder.encode(JSON.stringify({ result: result !== undefined ? result : null }));

      if (encoded.length > this.maxPayload) {
        throw new Error(`Sync RPC response exceeds buffer capacity (${encoded.length} > ${this.maxPayload})`);
      }

      this.dataUint8.set(encoded, 0);
      Atomics.store(this.statusInt32, HEADER_RESP_LEN_IDX, encoded.length);
      Atomics.store(this.statusInt32, HEADER_STATUS_IDX, STATUS_RESP_READY);
      Atomics.notify(this.statusInt32, HEADER_STATUS_IDX, 1);
    } catch (err) {
      const errorMsg = err instanceof Error ? err.message : String(err);
      const encoded = this.encoder.encode(JSON.stringify({ error: errorMsg }));

      this.dataUint8.set(encoded, 0);
      Atomics.store(this.statusInt32, HEADER_RESP_LEN_IDX, encoded.length);
      Atomics.store(this.statusInt32, HEADER_STATUS_IDX, STATUS_ERROR);
      Atomics.notify(this.statusInt32, HEADER_STATUS_IDX, 1);
    }
  }
}
