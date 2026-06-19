// Core Module Registration
declare function registerExports(moduleName: string, functionNames: string[]): void;

// HTTP Module (Native)
declare function httpGet(url: string, headers?: Record<string, string>): Promise<string>;
declare function httpPost(url: string, body?: string, headers?: Record<string, string>): Promise<string>;
declare function httpRequestRaw(method: string, url: string, body?: string, headers?: Record<string, string>): Promise<string>;

// Core Runtime Utilities
declare function jsonParse(json: string): unknown;
declare function xmlParse(xmlString: string): Record<string, unknown>;
declare function encodeURIComponent(s: string): string;
declare function consoleLog(...args: any[]): void;
declare function consoleError(...args: any[]): void;

// UI State Management
declare function getValue(key: string): any;
declare function setValue(key: string, value: any): void;
declare function subscribeValue(key: string, callbackName: string): number;
declare function unsubscribeValue(subscriptionId: number): void;
declare function setLayout(layoutPath: string): Promise<void>;

// Navigation
declare function launchFeature(featureId: string): Promise<void>;
declare function getFeatures(): string[];

// UI Theme/Framework Management
declare function getAvailableThemes(): string[];
declare function getCurrentTheme(): string;
declare function setCurrentTheme(themeId: string): Promise<void>;
declare function getAvailableFrameworks(): string[];
declare function getCurrentFramework(): string;
declare function setCurrentFramework(frameworkId: string): Promise<void>;

// Storage API
type StorageScope = "applet" | "feature" | "module" | "module.global";

// Transient Storage (In-Memory)
declare function storageGet(scope: StorageScope, key: string): Promise<string | null>;
declare function storagePut(scope: StorageScope, key: string, value: string): Promise<string>;
declare function storageDelete(scope: StorageScope, key: string): Promise<void>;
declare function storageGetObject(scope: StorageScope, key: string): Promise<any | null>;
declare function storagePutObject(scope: StorageScope, key: string, value: any): Promise<any>;

// Persistent Storage (Disk)
declare function persistentStorageGet(scope: StorageScope, key: string): Promise<string | null>;
declare function persistentStoragePut(scope: StorageScope, key: string, value: string): Promise<string>;
declare function persistentStorageDelete(scope: StorageScope, key: string): Promise<void>;
declare function persistentStorageGetObject(scope: StorageScope, key: string): Promise<any | null>;
declare function persistentStoragePutObject(scope: StorageScope, key: string, value: any): Promise<any>;

// Geolocation Module (Native)
declare function geoRequestPermission(): Promise<boolean>;
declare function geoGetCurrent(): Promise<{
    success: boolean;
    latitude?: number;
    longitude?: number;
    source?: string;
    error?: string;
}>;

// Audio Player Module (Native)
declare function getAudioPlayers(): string[];
declare function audioGetState(playerName: string): any;
declare function audioLoad(playerName: string, url: string): void;
declare function audioPlay(playerName: string): void;
declare function audioPause(playerName: string): void;
declare function audioStop(playerName: string): void;
declare function audioSeek(playerName: string, positionMs: number): void;
declare function audioSubscribe(playerName: string, handlerName: string): void;
declare function audioUnsubscribe(playerName: string, handlerName?: string): void;

// MCP Module (Native)
declare function mcpServerInfo(): { name: string; url: string; groups: string[] };
declare function mcpListTools(group?: string): Promise<any[]>;
declare function mcpCall(group: string, tool: string, args: Record<string, any>): Promise<any>;
