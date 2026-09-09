import { afterEach, expect, test } from 'bun:test';
import { readFile, readdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { extractZip, listZip, readZipEntry } from '../src/main/zip';
import { scratch, zip } from './helpers';

let directory = '';
afterEach(async () => {
  if (directory) await rm(directory, { recursive: true, force: true });
  directory = '';
});
test('extracts stored and deflated entries, skipping directories and META-INF', async () => {
  directory = await scratch('comet-zip-');
  const archive = path.join(directory, 'natives.jar');
  await writeFile(
    archive,
    zip([
      { name: 'META-INF/MANIFEST.MF', content: 'ignored' },
      { name: 'sub/', content: '' },
      { name: 'lwjgl64.dll', content: 'stored'.repeat(10) },
      { name: 'sub/nested.so', content: 'deflated'.repeat(200), deflate: true },
    ]),
  );
  await extractZip(archive, path.join(directory, 'out'));
  expect((await readdir(path.join(directory, 'out'))).sort()).toEqual(['lwjgl64.dll', 'sub']);
  expect(await readFile(path.join(directory, 'out', 'lwjgl64.dll'), 'utf8')).toBe('stored'.repeat(10));
  expect(await readFile(path.join(directory, 'out', 'sub', 'nested.so'), 'utf8')).toBe('deflated'.repeat(200));
});
test('lists entries, reads single entries and extracts a prefix only', async () => {
  directory = await scratch('comet-zip-');
  const archive = path.join(directory, 'pack.mrpack');
  await writeFile(
    archive,
    zip([
      { name: 'modrinth.index.json', content: '{"a":1}', deflate: true },
      { name: 'overrides/config/x.toml', content: 'x = 1' },
      { name: 'overrides/mods/', content: '' },
      { name: 'client-overrides/options.txt', content: 'fov:1' },
    ]),
  );
  expect(await listZip(archive)).toEqual([
    'modrinth.index.json',
    'overrides/config/x.toml',
    'overrides/mods/',
    'client-overrides/options.txt',
  ]);
  expect((await readZipEntry(archive, 'modrinth.index.json'))?.toString()).toBe('{"a":1}');
  expect(await readZipEntry(archive, 'missing.json')).toBeNull();
  await extractZip(archive, path.join(directory, 'game'), 'overrides/');
  expect((await readdir(path.join(directory, 'game'))).sort()).toEqual(['config']);
  expect(await readFile(path.join(directory, 'game', 'config', 'x.toml'), 'utf8')).toBe('x = 1');
  await extractZip(archive, path.join(directory, 'game'), 'client-overrides/');
  expect(await readFile(path.join(directory, 'game', 'options.txt'), 'utf8')).toBe('fov:1');
});
test('rejects traversal entries and corrupt archives', async () => {
  directory = await scratch('comet-zip-');
  const archive = path.join(directory, 'bad.jar');
  await writeFile(archive, zip([{ name: '../escape.dll', content: 'x' }]));
  await expect(extractZip(archive, path.join(directory, 'out'))).rejects.toThrow('Unsafe');
  await writeFile(archive, zip([{ name: 'overrides/../escape.txt', content: 'x' }]));
  await expect(extractZip(archive, path.join(directory, 'out'), 'overrides/')).rejects.toThrow('Unsafe');
  await writeFile(archive, Buffer.from('not a zip at all'));
  await expect(extractZip(archive, path.join(directory, 'out'))).rejects.toThrow('Invalid ZIP');
  await writeFile(archive, Buffer.alloc(0));
  await expect(listZip(archive)).rejects.toThrow('Invalid ZIP');
  expect(await readdir(directory)).toEqual(['bad.jar']);
});
