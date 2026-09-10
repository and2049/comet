import { afterEach, describe, expect, test } from 'bun:test';
import { readFile, readdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { archiveKind, importArchive, importHint, prismComponents, prismName } from '../src/main/imports';
import { scratch, zip } from './helpers';

let root = '';
afterEach(async () => {
  if (root) await rm(root, { recursive: true, force: true });
  root = '';
});
const components = (...items: { uid: string; version?: string; cachedVersion?: string }[]) => ({ components: items });

describe('Prism export parsing', () => {
  test('reads Minecraft and loader components and ignores implied ones', () => {
    expect(
      prismComponents(
        components(
          { uid: 'org.lwjgl3', version: '3.3.3' },
          { uid: 'net.minecraft', version: '1.21.1' },
          { uid: 'net.fabricmc.intermediary', version: '1.21.1' },
          { uid: 'net.fabricmc.fabric-loader', version: '0.16.9' },
        ),
      ),
    ).toEqual({ version: '1.21.1', loader: 'fabric', loaderVersion: '0.16.9' });
    expect(
      prismComponents(
        components(
          { uid: 'net.minecraft', cachedVersion: '1.20.1' },
          { uid: 'org.quiltmc.hashed', version: '1.20.1' },
          { uid: 'org.quiltmc.quilt-loader', cachedVersion: '0.20.0' },
        ),
      ),
    ).toEqual({ version: '1.20.1', loader: 'quilt', loaderVersion: '0.20.0' });
    expect(
      prismComponents(components({ uid: 'org.lwjgl', version: '2.9.4' }, { uid: 'net.minecraft', version: '1.8.9' })),
    ).toEqual({ version: '1.8.9', loader: 'vanilla' });
    expect(() =>
      prismComponents(
        components({ uid: 'net.minecraft', version: '1.20.1' }, { uid: 'net.minecraftforge', version: '47.2.0' }),
      ),
    ).toThrow('net.minecraftforge is out of scope');
    expect(() => prismComponents(components({ uid: 'org.lwjgl3', version: '3.3.3' }))).toThrow('Minecraft version');
    expect(() =>
      prismComponents(components({ uid: 'net.minecraft', version: '1.20.1' }, { uid: 'net.fabricmc.fabric-loader' })),
    ).toThrow('loader version');
    expect(() => prismComponents({ components: 'x' })).toThrow();
  });
  test('reads the instance name and detects archive layouts', () => {
    expect(prismName('[General]\nConfigVersion=1.2\nname=My World\niconKey=default\n')).toBe('My World');
    expect(prismName('[General]\n')).toBeUndefined();
    expect(archiveKind(['modrinth.index.json', 'overrides/x'])).toEqual({ kind: 'mrpack' });
    expect(archiveKind(['mmc-pack.json', '.minecraft/options.txt'])).toEqual({ kind: 'prism', prefix: '' });
    expect(archiveKind(['Survival/instance.cfg', 'Survival/mmc-pack.json'])).toEqual({
      kind: 'prism',
      prefix: 'Survival/',
    });
    expect(() => archiveKind(['a/b/mmc-pack.json'])).toThrow(importHint);
    expect(() => archiveKind(['manifest.json'])).toThrow(importHint);
  });
});
describe('archive import', () => {
  test('imports a Prism export into an isolated custom instance', async () => {
    root = await scratch('comet-import-');
    const archive = path.join(root, 'export.zip');
    await writeFile(
      archive,
      zip([
        { name: 'Survival/instance.cfg', content: '[General]\nname=Survival World\n' },
        {
          name: 'Survival/mmc-pack.json',
          content: JSON.stringify(
            components({ uid: 'net.minecraft', version: '1.21.1' }, { uid: 'org.lwjgl3', version: '3.3.3' }),
          ),
        },
        { name: 'Survival/.minecraft/options.txt', content: 'fov:1.0', deflate: true },
        { name: 'Survival/.minecraft/saves/World/level.dat', content: 'level' },
      ]),
    );
    const messages: string[] = [];
    const instance = await importArchive(root, [], archive, message => messages.push(message));
    expect(instance).toMatchObject({
      id: 'survival-world',
      name: 'Survival World',
      profile: 'custom',
      version: '1.21.1',
      loader: 'vanilla',
      directory: 'isolated',
    });
    const game = path.join(root, 'instances', 'survival-world', '.minecraft');
    expect(await readFile(path.join(game, 'options.txt'), 'utf8')).toBe('fov:1.0');
    expect(await readFile(path.join(game, 'saves', 'World', 'level.dat'), 'utf8')).toBe('level');
    const stored = JSON.parse(await readFile(path.join(root, 'instances', 'survival-world', 'comet.json'), 'utf8'));
    expect(stored).toMatchObject({ id: 'survival-world', schemaVersion: 2 });
    expect(messages).toContain('Extracting instance files');
  });
  test('imports an mrpack with overrides and records the pack', async () => {
    root = await scratch('comet-import-');
    const archive = path.join(root, 'pack.mrpack');
    await writeFile(
      archive,
      zip([
        {
          name: 'modrinth.index.json',
          content: JSON.stringify({
            formatVersion: 1,
            game: 'minecraft',
            versionId: '1.2.0',
            name: 'Tiny Pack',
            dependencies: { minecraft: '1.20.1', 'quilt-loader': '0.20.0' },
            files: [],
          }),
        },
        { name: 'overrides/config/tiny.toml', content: 'enabled = true' },
        { name: 'client-overrides/options.txt', content: 'fov:0.5' },
      ]),
    );
    const instance = await importArchive(root, [], archive, () => undefined);
    expect(instance).toMatchObject({
      id: 'tiny-pack',
      loader: 'quilt',
      loaderVersion: '0.20.0',
      version: '1.20.1',
      pack: { source: 'import', name: 'Tiny Pack', versionId: '1.2.0' },
    });
    const game = path.join(root, 'instances', 'tiny-pack', '.minecraft');
    expect(await readFile(path.join(game, 'config', 'tiny.toml'), 'utf8')).toBe('enabled = true');
    expect(await readFile(path.join(game, 'options.txt'), 'utf8')).toBe('fov:0.5');
    expect(JSON.parse(await readFile(path.join(root, 'instances', 'tiny-pack', 'comet-pack.json'), 'utf8'))).toEqual({
      versionId: '1.2.0',
      loader: 'quilt',
      loaderVersion: '0.20.0',
      files: [],
    });
  });
  test('rejects unsupported archives without leaving folders behind', async () => {
    root = await scratch('comet-import-');
    const archive = path.join(root, 'forge.zip');
    await writeFile(
      archive,
      zip([
        {
          name: 'mmc-pack.json',
          content: JSON.stringify(
            components({ uid: 'net.minecraft', version: '1.20.1' }, { uid: 'net.minecraftforge', version: '47.2.0' }),
          ),
        },
      ]),
    );
    await expect(importArchive(root, [], archive, () => undefined)).rejects.toThrow('out of scope');
    await writeFile(archive, zip([{ name: 'manifest.json', content: '{}' }]));
    await expect(importArchive(root, [], archive, () => undefined)).rejects.toThrow(importHint);
    expect(await readdir(path.join(root, 'instances')).catch(() => [])).toEqual([]);
  });
});
