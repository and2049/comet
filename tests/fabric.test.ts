import { describe, expect, test } from 'bun:test';
import { mavenPath, mergeFabric, packFiles, rawUrl, stalePaths, type PackIndex } from '../src/main/fabric';
import { javaMatches, runtimeFiles, type Installation } from '../src/main/minecraft';
import { parallel, trustedUrl } from '../src/main/net';

const sha1 = 'a'.repeat(40);
const index: PackIndex = {
  formatVersion: 1, game: 'minecraft', versionId: 'v4+26.07.02-1.16.1', name: 'MCSR Ranked (for 1.16.1)',
  dependencies: { 'fabric-loader': '0.19.3', minecraft: '1.16.1' },
  files: [
    { path: 'mods/antigone-1.16.1-2.0.0.jar', hashes: { sha1 }, env: { client: 'required', server: 'unsupported' } as { client: string }, downloads: ['https://github.com/Minecraft-Java-Edition-Speedrunning/legal-mods/raw/2ba63fc/legal-mods/antigone/1.16.1/antigone-1.16.1-2.0.0.jar'], fileSize: 4359 },
    { path: 'mods/mcsrranked-5.8.1.jar', hashes: { sha1 }, env: { client: 'optional' }, downloads: ['https://cdn.modrinth.com/data/I9W1u5Ac/versions/k83cbnOa/mcsrranked-5.8.1.jar'], fileSize: 25414385 },
    { path: 'mods/server-only.jar', hashes: { sha1 }, env: { client: 'unsupported' }, downloads: ['https://cdn.modrinth.com/x.jar'], fileSize: 1 },
  ],
};

describe('Fabric and pack resolution', () => {
  test('maps Maven coordinates to repository paths', () => {
    expect(mavenPath('net.fabricmc:fabric-loader:0.19.3')).toBe('net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar');
    expect(mavenPath('org.lwjgl:lwjgl:3.2.2:natives-windows')).toBe('org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2-natives-windows.jar');
    for (const name of ['net.fabricmc', 'a:b:c:d:e', '']) expect(() => mavenPath(name)).toThrow();
  });
  test('rewrites GitHub raw links and rejects untrusted mod hosts', () => {
    expect(rawUrl('https://github.com/owner/repo/raw/abc/dir/mod.jar')).toBe('https://raw.githubusercontent.com/owner/repo/abc/dir/mod.jar');
    expect(rawUrl('https://cdn.modrinth.com/data/x/versions/y/mod.jar')).toBe('https://cdn.modrinth.com/data/x/versions/y/mod.jar');
    for (const url of ['https://github.com/owner/repo/releases/download/v1/mod.jar', 'http://cdn.modrinth.com/mod.jar', 'https://evil.test/mod.jar', 'https://libraries.minecraft.net/mod.jar']) expect(() => rawUrl(url)).toThrow();
  });
  test('accepts only client mods inside the mods folder', () => {
    const files = packFiles(index, '1.16.1');
    expect(files.map(file => file.path)).toEqual(['mods/antigone-1.16.1-2.0.0.jar', 'mods/mcsrranked-5.8.1.jar']);
    expect(files[0].url).toStartWith('https://raw.githubusercontent.com/');
    expect(files[1]).toMatchObject({ sha1, size: 25414385 });
    expect(() => packFiles(index, '1.16.5')).toThrow('does not target');
    expect(() => packFiles({ ...index, dependencies: { minecraft: '1.16.1' } }, '1.16.1')).toThrow('Fabric loader');
    for (const path of ['config/options.txt', 'mods/../options.txt', 'mods/sub/mod.jar', 'mods/mod.zip', 'options.txt']) {
      expect(() => packFiles({ ...index, files: [{ ...index.files[1], path }] }, '1.16.1')).toThrow('Unsupported pack file');
    }
    expect(() => packFiles({ ...index, files: [{ ...index.files[1], downloads: [] }] }, '1.16.1')).toThrow('one download');
  });
  test('removes only previously managed files that left the pack', () => {
    expect(stalePaths(['mods/old.jar', 'mods/kept.jar'], ['mods/kept.jar', 'mods/new.jar'])).toEqual(['mods/old.jar']);
  });
  test('puts Fabric ahead of the vanilla classpath and arguments', () => {
    const installation: Installation = {
      metadata: { id: '1.16.1', type: 'release', mainClass: 'net.minecraft.client.main.Main', libraries: [], downloads: { client: { url: '', sha1: '' } }, assetIndex: { id: '1.16', url: '', sha1: '' }, arguments: { jvm: ['-cp', '${classpath}'], game: ['--username', '${auth_player_name}'] } },
      classpath: ['client.jar'], game: 'game', assets: 'assets', natives: 'natives', gameAssets: 'assets',
    };
    const merged = mergeFabric(installation, { mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient', jvm: ['-DFabricMcEmu= net.minecraft.client.main.Main '], classpath: ['loader.jar'] });
    expect(merged.classpath).toEqual(['loader.jar', 'client.jar']);
    expect(merged.metadata.mainClass).toBe('net.fabricmc.loader.impl.launch.knot.KnotClient');
    expect(merged.metadata.arguments).toEqual({ jvm: ['-DFabricMcEmu= net.minecraft.client.main.Main ', '-cp', '${classpath}'], game: ['--username', '${auth_player_name}'] });
    expect(installation.classpath).toEqual(['client.jar']);
  });
});
describe('Java runtime handling', () => {
  test('selects raw runtime files and directories, rejecting links', () => {
    const raw = { url: 'https://piston-data.mojang.com/x', sha1, size: 1 };
    const manifest = { files: { bin: { type: 'directory' }, 'bin/java.exe': { type: 'file', downloads: { raw } }, COPYRIGHT: { type: 'file', downloads: { raw, lzma: raw } } } };
    expect(runtimeFiles(manifest)).toEqual({ directories: ['bin'], files: [{ ...raw, path: 'bin/java.exe' }, { ...raw, path: 'COPYRIGHT' }] });
    expect(() => runtimeFiles({ files: { link: { type: 'link' } } })).toThrow('Unsupported');
  });
  test('checks the requested major version and 64-bit architecture', () => {
    const output = (specification: string, bits: string) => `    java.specification.version = ${specification}\n    java.version = 1.8.0_51\n    sun.arch.data.model = ${bits}\n`;
    expect(javaMatches(output('1.8', '64'), 8)).toBe(true);
    expect(javaMatches(output('17', '64'), 17)).toBe(true);
    expect(javaMatches(output('1.8', '32'), 8)).toBe(false);
    expect(javaMatches(output('17', '64'), 8)).toBe(false);
    expect(javaMatches(output('1.8', '64'), 17)).toBe(false);
    expect(javaMatches(output('11', '64'), 1)).toBe(false);
  });
});
describe('shared download helpers', () => {
  test('host allowlists are exact and HTTPS only', () => {
    expect(trustedUrl('https://maven.fabricmc.net/a.jar', ['maven.fabricmc.net'])).toBe('https://maven.fabricmc.net/a.jar');
    for (const url of ['https://maven.fabricmc.net.evil.test/a', 'http://maven.fabricmc.net/a', 'https://maven.fabricmc.net:8443/a']) expect(() => trustedUrl(url, ['maven.fabricmc.net'])).toThrow();
  });
  test('parallel workers stop scheduling after the first failure and propagate it', async () => {
    const started: number[] = [];
    await expect(parallel([1, 2, 3, 4, 5, 6], 2, async item => {
      started.push(item);
      await new Promise(resolve => setTimeout(resolve, 5));
      if (item === 2) throw new Error('boom');
    })).rejects.toThrow('boom');
    expect(started.length).toBeLessThan(6);
    const done: number[] = [];
    await parallel([1, 2, 3], 8, async item => { done.push(item); });
    expect(done).toEqual([1, 2, 3]);
  });
});
