import { afterEach, expect, test } from 'bun:test';
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { crc32, deflateRawSync } from 'node:zlib';
import { extractZip } from '../src/main/zip';

let directory = '';
afterEach(async () => {
  if (directory) await rm(directory, { recursive: true, force: true });
  directory = '';
});
function zip(entries: { name: string; content: string; deflate?: boolean }[]): Buffer {
  const locals: Buffer[] = [];
  const central: Buffer[] = [];
  let offset = 0;
  for (const entry of entries) {
    const name = Buffer.from(entry.name);
    const raw = Buffer.from(entry.content);
    const data = entry.deflate ? deflateRawSync(raw) : raw;
    const header = Buffer.alloc(30);
    header.writeUInt32LE(0x04034b50, 0);
    header.writeUInt16LE(entry.deflate ? 8 : 0, 8);
    header.writeUInt32LE(crc32(raw), 14);
    header.writeUInt32LE(data.length, 18);
    header.writeUInt32LE(raw.length, 22);
    header.writeUInt16LE(name.length, 26);
    const record = Buffer.alloc(46);
    record.writeUInt32LE(0x02014b50, 0);
    record.writeUInt16LE(entry.deflate ? 8 : 0, 10);
    record.writeUInt32LE(crc32(raw), 16);
    record.writeUInt32LE(data.length, 20);
    record.writeUInt32LE(raw.length, 24);
    record.writeUInt16LE(name.length, 28);
    record.writeUInt32LE(offset, 42);
    locals.push(header, name, data);
    central.push(record, name);
    offset += header.length + name.length + data.length;
  }
  const directorySize = central.reduce((sum, part) => sum + part.length, 0);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(entries.length, 8);
  end.writeUInt16LE(entries.length, 10);
  end.writeUInt32LE(directorySize, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, ...central, end]);
}
async function scratch(): Promise<string> {
  const root = path.join(os.tmpdir(), 'redsun');
  await mkdir(root, { recursive: true });
  directory = await mkdtemp(path.join(root, 'comet-zip-'));
  return directory;
}
test('extracts stored and deflated entries, skipping directories and META-INF', async () => {
  const root = await scratch();
  const archive = path.join(root, 'natives.jar');
  await writeFile(
    archive,
    zip([
      { name: 'META-INF/MANIFEST.MF', content: 'ignored' },
      { name: 'sub/', content: '' },
      { name: 'lwjgl64.dll', content: 'stored'.repeat(10) },
      { name: 'sub/nested.so', content: 'deflated'.repeat(200), deflate: true },
    ]),
  );
  await extractZip(archive, path.join(root, 'out'));
  expect((await readdir(path.join(root, 'out'))).sort()).toEqual(['lwjgl64.dll', 'sub']);
  expect(await readFile(path.join(root, 'out', 'lwjgl64.dll'), 'utf8')).toBe('stored'.repeat(10));
  expect(await readFile(path.join(root, 'out', 'sub', 'nested.so'), 'utf8')).toBe('deflated'.repeat(200));
});
test('rejects traversal entries and corrupt archives', async () => {
  const root = await scratch();
  const archive = path.join(root, 'bad.jar');
  await writeFile(archive, zip([{ name: '../escape.dll', content: 'x' }]));
  await expect(extractZip(archive, path.join(root, 'out'))).rejects.toThrow('Unsafe');
  await writeFile(archive, Buffer.from('not a zip at all'));
  await expect(extractZip(archive, path.join(root, 'out'))).rejects.toThrow('Invalid ZIP');
  expect(await readdir(root)).toEqual(['bad.jar']);
});
