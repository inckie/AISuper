declare function registerExports(moduleName: string, functionNames: string[]): void;
declare function httpGet(url: string): Promise<string>;
declare function xmlParse(xmlString: string): Record<string, unknown>;

