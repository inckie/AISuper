/**
 * @wk-id tinyjs/zip-extractor
 * @wk-title Pure JS ZIP Extractor (zip_extractor.js)
 * @wk-tags tinyjs, zip, decompression, streams
 * @wk-categories tinyjs-runtime
 *
 * # Pure JS ZIP Extractor
 *
 * Zero-dependency ZIP file reader and decompressor leveraging standard Web Streams
 * `new DecompressionStream('deflate-raw')`. Parses local file headers (`0x04034b50`),
 * supports uncompressed (`method 0`) and Deflate (`method 8`) streams, and writes
 * extracted file trees recursively without external binary tools.
 */

export async function extractZip(zipFilePath, destDir) {
  const bytes = await tjs.readFile(zipFilePath);
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let offset = 0;
  let count = 0;

  await tjs.makeDir(destDir, { recursive: true });

  while (offset + 30 <= bytes.length) {
    const sig = view.getUint32(offset, true);
    if (sig !== 0x04034b50) {
      break; // Reached central directory or end of archive
    }

    const method = view.getUint16(offset + 8, true);
    const compSize = view.getUint32(offset + 18, true);
    const nameLen = view.getUint16(offset + 26, true);
    const extraLen = view.getUint16(offset + 28, true);

    const nameBytes = bytes.subarray(offset + 30, offset + 30 + nameLen);
    let name = new TextDecoder().decode(nameBytes).replace(/\\/g, '/');

    const dataStart = offset + 30 + nameLen + extraLen;
    const compressedData = bytes.subarray(dataStart, dataStart + compSize);
    offset = dataStart + compSize;

    if (name.endsWith('/')) {
      await tjs.makeDir(`${destDir}/${name}`, { recursive: true });
      continue;
    }

    const lastSlash = name.lastIndexOf('/');
    if (lastSlash > 0) {
      const parentDir = `${destDir}/${name.substring(0, lastSlash)}`;
      await tjs.makeDir(parentDir, { recursive: true });
    }

    let fileData;
    if (method === 0) {
      // Stored (uncompressed)
      fileData = compressedData;
    } else if (method === 8) {
      // Deflated (standard zip compression)
      const ds = new DecompressionStream('deflate-raw');
      const writer = ds.writable.getWriter();
      writer.write(compressedData);
      writer.close();
      const resp = new Response(ds.readable);
      fileData = new Uint8Array(await resp.arrayBuffer());
    } else {
      console.warn(`[ZipExtractor] Unsupported compression method ${method} for '${name}'`);
      continue;
    }

    await tjs.writeFile(`${destDir}/${name}`, fileData);
    count++;
  }

  return count;
}
