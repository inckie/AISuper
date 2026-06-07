declare function registerExports(moduleName: string, functionNames: string[]): void;
declare function httpGet(url: string, headers?: Record<string, string>): Promise<string>;
declare function httpPost(url: string, body?: string, headers?: Record<string, string>): Promise<string>;
declare function httpRequestRaw(method: string, url: string, body?: string, headers?: Record<string, string>): Promise<string>;
declare function xmlParse(xmlString: string): Record<string, unknown>;

