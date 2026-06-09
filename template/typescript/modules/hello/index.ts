declare function registerExports(id: string, methods: string[]): void;

export function sayHello(name: string): string {
  return "Hello, " + name + " from TypeScript module!";
}

registerExports("hello", ["sayHello"]);
