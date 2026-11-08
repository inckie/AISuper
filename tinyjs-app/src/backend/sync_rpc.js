// sync_rpc.js - Synchronous RPC bridge using SharedArrayBuffer & Atomics
// Enables worker isolates to synchronously block during host & cross-isolate calls.

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

  handleRpc() {
    const reqLen = Atomics.load(this.statusInt32, HEADER_REQ_LEN_IDX);
    const copy = new Uint8Array(reqLen);
    copy.set(this.dataUint8.subarray(0, reqLen));
    const reqStr = this.decoder.decode(copy);
    const { method, args } = JSON.parse(reqStr);

    try {
      const result = this.handler(method, args);
      const encoded = this.encoder.encode(JSON.stringify({ result }));

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
