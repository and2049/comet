import { afterEach, expect, test } from 'bun:test';
import { mkdir, readFile, readdir, rm, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { instances as predefined } from '../src/main/core';
import { createInstance, loadInstances, removeInstance } from '../src/main/instances';
import { scratch } from './helpers';

let root = '';
afterEach(async () => {
  if (root) await rm(root, { recursive: true, force: true });
  root = '';
});
test('creates predefined folders, discovers valid custom instances and skips broken ones', async () => {
  root = await scratch('comet-instances-');
  const warnings: string[] = [];
  const first = await loadInstances(root, message => warnings.push(message));
  expect(first.map(instance => instance.id)).toEqual(predefined.map(instance => instance.id));
  expect((await stat(path.join(root, 'shared', 'comet', '.minecraft'))).isDirectory()).toBe(true);
  expect((await stat(path.join(root, 'instances', 'mcsr-1.16.1', '.minecraft'))).isDirectory()).toBe(true);
  const stored = JSON.parse(await readFile(path.join(root, 'instances', 'pvp-1.8.9', 'comet.json'), 'utf8'));
  expect(stored).toMatchObject({ id: 'pvp-1.8.9', directory: 'comet', schemaVersion: 2 });
  await mkdir(path.join(root, 'instances', 'broken'), { recursive: true });
  await writeFile(path.join(root, 'instances', 'broken', 'comet.json'), '{"id":"other"}');
  await mkdir(path.join(root, 'instances', 'stray'), { recursive: true });
  await mkdir(path.join(root, 'instances', 'sneaky'), { recursive: true });
  await writeFile(
    path.join(root, 'instances', 'sneaky', 'comet.json'),
    JSON.stringify({ ...predefined[0], id: 'sneaky', profile: 'pvp' }),
  );
  const created = await createInstance(root, first, {
    name: 'Survival',
    version: '1.21.1',
    loader: 'vanilla',
    directory: 'isolated',
  });
  expect(created).toMatchObject({ id: 'survival', profile: 'custom' });
  const second = await loadInstances(root, message => warnings.push(message));
  expect(second.map(instance => instance.id)).toEqual([...predefined.map(instance => instance.id), 'survival']);
  expect(warnings.some(message => message.includes('broken'))).toBe(true);
  expect(warnings.some(message => message.includes('sneaky'))).toBe(true);
  expect(warnings.some(message => message.includes('stray'))).toBe(false);
});
test('avoids id collisions with existing folders and only removes custom instances', async () => {
  root = await scratch('comet-instances-');
  await mkdir(path.join(root, 'instances', 'survival'), { recursive: true });
  const created = await createInstance(root, [], {
    name: 'Survival',
    version: '1.21.1',
    loader: 'fabric',
    loaderVersion: '0.16.9',
    directory: 'official',
  });
  expect(created.id).toBe('survival-2');
  expect(
    JSON.parse(await readFile(path.join(root, 'instances', 'survival-2', 'mmc-pack.json'), 'utf8')).components,
  ).toHaveLength(2);
  await expect(removeInstance(root, predefined[0])).rejects.toThrow('cannot be removed');
  await removeInstance(root, created);
  expect((await readdir(path.join(root, 'instances'))).sort()).toEqual(['survival']);
});
