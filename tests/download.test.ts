import { afterEach, expect, test } from 'bun:test';
import { createHash } from 'node:crypto';
import { mkdir, mkdtemp, readFile, readdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { download } from '../src/main/net';

const originalFetch = globalThis.fetch;
let directory = '';
afterEach(async () => {
  globalThis.fetch = originalFetch;
  if (directory) await rm(directory, { recursive: true, force: true });
  directory = '';
});
async function destination(): Promise<string> {
  const root = path.join(os.tmpdir(), 'redsun');
  await mkdir(root, { recursive: true });
  directory = await mkdtemp(path.join(root, 'comet-download-'));
  return path.join(directory, 'artifact.jar');
}
const content = 'verified artifact';
const artifact = { url: 'https://libraries.minecraft.net/example.jar', sha1: createHash('sha1').update(content).digest('hex'), size: Buffer.byteLength(content) };

test('verified cache is reused without a network request', async () => {
  const file = await destination();
  await writeFile(file, content);
  globalThis.fetch = (() => { throw new Error('Network must not be used'); }) as typeof fetch;
  await download(artifact, file);
  expect(await readFile(file, 'utf8')).toBe(content);
});
test('a corrupted artifact is replaced only after verification', async () => {
  const file = await destination();
  await writeFile(file, 'corrupt');
  globalThis.fetch = (async () => new Response(content)) as typeof fetch;
  await download(artifact, file);
  expect(await readFile(file, 'utf8')).toBe(content);
  expect(await readdir(directory)).toEqual(['artifact.jar']);
});
test('checksum mismatch preserves the existing file and removes partial data', async () => {
  const file = await destination();
  await writeFile(file, 'existing');
  globalThis.fetch = (async () => new Response('bad download')) as typeof fetch;
  await expect(download(artifact, file)).rejects.toThrow('checksum mismatch');
  expect(await readFile(file, 'utf8')).toBe('existing');
  expect(await readdir(directory)).toEqual(['artifact.jar']);
});
test('duplicate asset hashes share one download instead of racing Windows renames', async () => {
  const file = await destination();
  let calls = 0;
  globalThis.fetch = (async () => { calls++; return new Response(content); }) as typeof fetch;
  await Promise.all(Array.from({ length: 12 }, () => download(artifact, file)));
  expect(calls).toBe(1);
  expect(await readFile(file, 'utf8')).toBe(content);
});
