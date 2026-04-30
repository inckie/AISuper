/**
 * Polyfills for JS Module Development and Testing
 *
 * Provides xmlParse and httpGet functions for local development/testing
 * In production, these are provided by the Kotlin JS engine.
 */

/**
 * Simple XML to JSON parser - matches Kotlin XmlJsonParser output format
 *
 * Output contract:
 * - Root object wrapped with root tag name: { "RootTag": { ... } }
 * - Attributes stored under "@attributes"
 * - Text content stored under "#text"
 * - Repeated child tags become arrays
 */
function xmlParsePolyfill(xmlInput: string): Record<string, unknown> {
  const xml = xmlInput.trim();

  if (!xml) {
    throw new Error("XML input is empty");
  }

  interface Node {
    name: string;
    attributes: Record<string, string>;
    children: Node[];
    text: string[];
  }

  const stack: Node[] = [];
  let root: Node | null = null;
  let index = 0;

  // Parse tag name
  function readTagName(inner: string): string {
    let name = "";
    for (const ch of inner) {
      if (ch.match(/\s/) || ch === "/") break;
      name += ch;
    }
    return name;
  }

  // Parse attributes
  function parseAttributes(text: string): Record<string, string> {
    const attributes: Record<string, string> = {};
    let i = 0;

    function skipSpaces() {
      while (i < text.length && text[i].match(/\s/)) i++;
    }

    while (i < text.length) {
      skipSpaces();
      if (i >= text.length) break;

      let keyStart = i;
      while (i < text.length && !text[i].match(/\s/) && text[i] !== "=") i++;
      const key = text.substring(keyStart, i);
      if (!key) break;

      skipSpaces();
      if (i >= text.length || text[i] !== "=") continue;
      i++;
      skipSpaces();

      if (i >= text.length) continue;
      const quote = text[i];
      if (quote !== '"' && quote !== "'") continue;
      i++;

      let valueStart = i;
      while (i < text.length && text[i] !== quote) i++;
      if (i >= text.length) continue;

      const value = text.substring(valueStart, i);
      attributes[key] = decodeXmlEntities(value);
      i++;
    }

    return attributes;
  }

  // Decode XML entities
  function decodeXmlEntities(value: string): string {
    return value
      .replace(/&amp;/g, "&")
      .replace(/&lt;/g, "<")
      .replace(/&gt;/g, ">")
      .replace(/&quot;/g, '"')
      .replace(/&apos;/g, "'");
  }

  // Convert node to JSON
  function nodeToJson(node: Node): Record<string, unknown> {
    const out: Record<string, unknown> = {};

    if (Object.keys(node.attributes).length > 0) {
      out["@attributes"] = node.attributes;
    }

    const text = node.text.join("").trim();
    if (text) {
      out["#text"] = text;
    }

    if (node.children.length > 0) {
      const grouped: Record<string, Node[]> = {};
      for (const child of node.children) {
        if (!grouped[child.name]) {
          grouped[child.name] = [];
        }
        grouped[child.name].push(child);
      }

      for (const [name, siblings] of Object.entries(grouped)) {
        if (siblings.length === 1) {
          out[name] = nodeToJson(siblings[0]);
        } else {
          out[name] = siblings.map(sibling => nodeToJson(sibling));
        }
      }
    }

    return out;
  }

  // Main parsing loop
  while (index < xml.length) {
    if (xml[index] !== "<") {
      const nextTag = xml.indexOf("<", index);
      const end = nextTag === -1 ? xml.length : nextTag;
      const chunk = xml.substring(index, end);

      if (stack.length > 0 && chunk.trim()) {
        stack[stack.length - 1].text.push(decodeXmlEntities(chunk));
      }
      index = end;
      continue;
    }

    // Comment
    if (xml.startsWith("<!--", index)) {
      const end = xml.indexOf("-->", index + 4);
      if (end >= 0) {
        index = end + 3;
      } else {
        index++;
      }
      continue;
    }

    // CDATA
    if (xml.startsWith("<![CDATA[", index)) {
      const end = xml.indexOf("]]>", index + 9);
      if (end >= 0) {
        if (stack.length > 0) {
          stack[stack.length - 1].text.push(xml.substring(index + 9, end));
        }
        index = end + 3;
      } else {
        index++;
      }
      continue;
    }

    // Processing instruction
    if (xml.startsWith("<?", index)) {
      const end = xml.indexOf("?>", index + 2);
      if (end >= 0) {
        index = end + 2;
      } else {
        index++;
      }
      continue;
    }

    // Closing tag
    if (xml.startsWith("</", index)) {
      const end = xml.indexOf(">", index + 2);
      if (end >= 0) {
        const closeName = xml.substring(index + 2, end).trim();
        if (stack.length > 0) {
          const current = stack.pop()!;
          if (current.name !== closeName) {
            throw new Error(
              `Mismatched closing tag: expected </${current.name}> but found </${closeName}>`
            );
          }
        }
        index = end + 1;
      } else {
        index++;
      }
      continue;
    }

    // Opening tag
    const end = xml.indexOf(">", index + 1);
    if (end >= 0) {
      let inner = xml.substring(index + 1, end).trim();
      const selfClosing = inner.endsWith("/");
      if (selfClosing) {
        inner = inner.slice(0, -1).trim();
      }

      const name = readTagName(inner);
      if (!name) {
        index = end + 1;
        continue;
      }

      const attributesPart = inner.substring(name.length).trim();
      const node: Node = {
        name,
        attributes: parseAttributes(attributesPart),
        children: [],
        text: []
      };

      if (stack.length > 0) {
        stack[stack.length - 1].children.push(node);
      } else {
        root = node;
      }

      if (!selfClosing) {
        stack.push(node);
      }

      index = end + 1;
    } else {
      index++;
    }
  }

  if (!root) {
    throw new Error("No root XML node found");
  }

  return {
    [root.name]: nodeToJson(root)
  };
}

/**
 * Simple HTTP GET - matches Kotlin httpGet signature
 *
 * In Node.js test environment, uses node-fetch
 * In browser, uses fetch API
 *
 * Returns raw response body as string
 */
async function httpGetPolyfill(url: string): Promise<string> {
  try {
    // Try Node.js fetch (if available)
    if (typeof fetch !== "undefined") {
      const response = await fetch(url);
      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }
      return await response.text();
    }

    // Fallback for older Node.js versions
    if (typeof require !== "undefined") {
      const https = require("https");
      const http = require("http");

      return new Promise((resolve, reject) => {
        const client = url.startsWith("https") ? https : http;
        client.get(url, (response: any) => {
          let data = "";
          response.on("data", (chunk: any) => {
            data += chunk;
          });
          response.on("end", () => resolve(data));
          response.on("error", reject);
        }).on("error", reject);
      });
    }

    throw new Error("fetch is not available");
  } catch (error) {
    throw new Error(`httpGet failed for ${url}: ${error}`);
  }
}

/**
 * Mock implementation of registerExports for testing
 *
 * In production, this is provided by the Kotlin JS engine
 */
function registerExportsPolyfill(
  moduleName: string,
  functionNames: string[]
): void {
  console.log(`[Mock] Registered module "${moduleName}" with functions:`, functionNames);
}

// Export for use in tests and development
export { xmlParsePolyfill, httpGetPolyfill, registerExportsPolyfill };

// Make available globally if needed
if (typeof globalThis !== "undefined") {
  if (!globalThis.xmlParse) {
    (globalThis as any).xmlParse = xmlParsePolyfill;
  }
  if (!globalThis.httpGet) {
    (globalThis as any).httpGet = httpGetPolyfill;
  }
  if (!globalThis.registerExports) {
    (globalThis as any).registerExports = registerExportsPolyfill;
  }
}

