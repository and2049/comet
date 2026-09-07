import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { inflateRawSync } from 'node:zlib';
import { inside } from './core.js';

export async function extractZip(archive: string, destination: string): Promise<void> {
  const data = await readFile(archive);
  let end = data.length - 22;
  while (end >= 0 && data.readUInt32LE(end) !== 0x06054b50) end--;
  if (end < 0) throw new Error('Invalid ZIP archive.');
  const count = data.readUInt16LE(end + 10);
  let offset = data.readUInt32LE(end + 16);
  for (let index = 0; index < count; index++) {
    if (offset + 46 > data.length || data.readUInt32LE(offset) !== 0x02014b50) throw new Error('Invalid ZIP archive.');
    const method = data.readUInt16LE(offset + 10);
    const compressed = data.readUInt32LE(offset + 20);
    const nameLength = data.readUInt16LE(offset + 28);
    const local = data.readUInt32LE(offset + 42);
    const name = data.toString('utf8', offset + 46, offset + 46 + nameLength);
    offset += 46 + nameLength + data.readUInt16LE(offset + 30) + data.readUInt16LE(offset + 32);
    if (name.endsWith('/') || name.startsWith('META-INF/')) continue;
    if (compressed === 0xffffffff || ![0, 8].includes(method)) throw new Error(`Unsupported ZIP entry: ${name}`);
    const start = local + 30 + data.readUInt16LE(local + 26) + data.readUInt16LE(local + 28);
    if (start + compressed > data.length) throw new Error('Invalid ZIP archive.');
    const raw = data.subarray(start, start + compressed);
    const target = inside(destination, name);
    await mkdir(path.dirname(target), { recursive: true });
    await writeFile(target, method === 0 ? raw : inflateRawSync(raw));
  }
}
