import { mkdir, open, writeFile, type FileHandle } from 'node:fs/promises';
import path from 'node:path';
import { inflateRawSync } from 'node:zlib';
import { inside } from './core.js';

interface Entry {
  name: string;
  method: number;
  compressed: number;
  local: number;
}
const invalid = () => new Error('Invalid ZIP archive.');
async function directory(handle: FileHandle): Promise<{ entries: Entry[]; size: number }> {
  const { size } = await handle.stat();
  const tailLength = Math.min(size, 65557);
  const tail = Buffer.alloc(tailLength);
  await handle.read(tail, 0, tailLength, size - tailLength);
  let end = tailLength - 22;
  while (end >= 0 && tail.readUInt32LE(end) !== 0x06054b50) end--;
  if (end < 0) throw invalid();
  const count = tail.readUInt16LE(end + 10);
  const length = tail.readUInt32LE(end + 12);
  const start = tail.readUInt32LE(end + 16);
  if (start + length > size) throw invalid();
  const data = Buffer.alloc(length);
  await handle.read(data, 0, length, start);
  const entries: Entry[] = [];
  let offset = 0;
  for (let index = 0; index < count; index++) {
    if (offset + 46 > data.length || data.readUInt32LE(offset) !== 0x02014b50) throw invalid();
    const nameLength = data.readUInt16LE(offset + 28);
    entries.push({
      name: data.toString('utf8', offset + 46, offset + 46 + nameLength),
      method: data.readUInt16LE(offset + 10),
      compressed: data.readUInt32LE(offset + 20),
      local: data.readUInt32LE(offset + 42),
    });
    offset += 46 + nameLength + data.readUInt16LE(offset + 30) + data.readUInt16LE(offset + 32);
  }
  return { entries, size };
}
async function content(handle: FileHandle, entry: Entry, size: number): Promise<Buffer> {
  if (entry.compressed === 0xffffffff || ![0, 8].includes(entry.method))
    throw new Error(`Unsupported ZIP entry: ${entry.name}`);
  const header = Buffer.alloc(30);
  if (entry.local + 30 > size) throw invalid();
  await handle.read(header, 0, 30, entry.local);
  if (header.readUInt32LE(0) !== 0x04034b50) throw invalid();
  const start = entry.local + 30 + header.readUInt16LE(26) + header.readUInt16LE(28);
  if (start + entry.compressed > size) throw invalid();
  const raw = Buffer.alloc(entry.compressed);
  await handle.read(raw, 0, entry.compressed, start);
  return entry.method === 0 ? raw : inflateRawSync(raw);
}
async function withArchive<T>(archive: string, work: (handle: FileHandle) => Promise<T>): Promise<T> {
  const handle = await open(archive, 'r');
  try {
    return await work(handle);
  } finally {
    await handle.close();
  }
}
export async function listZip(archive: string): Promise<string[]> {
  return withArchive(archive, async handle => (await directory(handle)).entries.map(entry => entry.name));
}
export async function readZipEntry(archive: string, name: string): Promise<Buffer | null> {
  return withArchive(archive, async handle => {
    const { entries, size } = await directory(handle);
    const entry = entries.find(item => item.name === name);
    return entry ? content(handle, entry, size) : null;
  });
}
export async function extractZip(archive: string, destination: string, prefix = ''): Promise<void> {
  await withArchive(archive, async handle => {
    const { entries, size } = await directory(handle);
    for (const entry of entries) {
      if (entry.name.endsWith('/') || entry.name.startsWith('META-INF/') || !entry.name.startsWith(prefix)) continue;
      const target = inside(destination, entry.name.slice(prefix.length));
      const data = await content(handle, entry, size);
      await mkdir(path.dirname(target), { recursive: true });
      await writeFile(target, data);
    }
  });
}
