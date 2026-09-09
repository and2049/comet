import { describe, expect, test } from 'bun:test';
import { loaderVersionsFrom, mavenPath, mergeLoader, metaUrl } from '../src/main/loader';
import { indexFrom, packFiles, packTarget, rawUrl, stalePaths, type PackIndex } from '../src/main/mrpack';
import { mcsrFiles, packUrl } from '../src/main/mcsr';
import { javaMatches, runtimeFiles, runtimeKeys, type Installation } from '../src/main/minecraft';
import { parallel, secureUrl, trustedUrl } from '../src/main/net';

const sha1 = 'a'.repeat(40);
const index: PackIndex = {
  formatVersion: 1,
  game: 'minecraft',
  versionId: 'v4+26.07.02-1.16.1',
  name: 'MCSR Ranked (for 1.16.1)',
  dependencies: { 'fabric-loader': '0.19.3', minecraft: '1.16.1' },
  files: [
    {
      path: 'mods/antigone-1.16.1-2.0.0.jar',
      hashes: { sha1 },
      env: { client: 'required' },
      downloads: [
        'https://github.com/Minecraft-Java-Edition-Speedrunning/legal-mods/raw/2ba63fc/legal-mods/antigone/1.16.1/antigone-1.16.1-2.0.0.jar',
      ],
      fileSize: 4359,
    },
    {
      path: 'mods/mcsrranked-5.8.1.jar',
      hashes: { sha1 },
      env: { client: 'optional' },
      downloads: ['https://cdn.modrinth.com/data/I9W1u5Ac/versions/k83cbnOa/mcsrranked-5.8.1.jar'],
      fileSize: 25414385,
    },
    {
      path: 'mods/server-only.jar',
      hashes: { sha1 },
      env: { client: 'unsupported' },
      downloads: ['https://cdn.modrinth.com/x.jar'],
      fileSize: 1,
    },
  ],
};
const installation: Installation = {
  metadata: {
    id: '1.16.1',
    type: 'release',
    mainClass: 'net.minecraft.client.main.Main',
    libraries: [],
    downloads: { client: { url: '', sha1: '' } },
    assetIndex: { id: '1.16', url: '', sha1: '' },
    arguments: { jvm: ['-cp', '${classpath}'], game: ['--username', '${auth_player_name}'] },
  },
  classpath: ['client.jar'],
  game: 'game',
  assets: 'assets',
  natives: 'natives',
  gameAssets: 'assets',
};

describe('modpack index handling', () => {
  test('validates the Modrinth index shape', () => {
    expect(indexFrom(JSON.parse(JSON.stringify(index)))).toEqual(index);
    for (const broken of [
      { ...index, formatVersion: 2 },
      { ...index, game: 'other' },
      { ...index, files: 'none' },
      { ...index, dependencies: { minecraft: '1.16.1 && evil' } },
      { ...index, files: [{ ...index.files[0], hashes: {} }] },
      { ...index, files: [{ ...index.files[0], downloads: 'x' }] },
      { ...index, files: [{ ...index.files[0], fileSize: '1' }] },
    ])
      expect(() => indexFrom(broken)).toThrow();
  });
  test('derives the loader from pack dependencies and rejects Forge', () => {
    expect(packTarget(index)).toEqual({ version: '1.16.1', loader: 'fabric', loaderVersion: '0.19.3' });
    expect(packTarget({ ...index, dependencies: { minecraft: '1.20.1', 'quilt-loader': '0.20.0' } })).toEqual({
      version: '1.20.1',
      loader: 'quilt',
      loaderVersion: '0.20.0',
    });
    expect(packTarget({ ...index, dependencies: { minecraft: '1.20.1' } })).toEqual({
      version: '1.20.1',
      loader: 'vanilla',
    });
    expect(() => packTarget({ ...index, dependencies: { minecraft: '1.20.1', forge: '47.2.0' } })).toThrow('Forge');
    expect(() => packTarget({ ...index, dependencies: { minecraft: '1.21', neoforge: '21.0.1' } })).toThrow('Forge');
    expect(() => packTarget({ ...index, dependencies: {} })).toThrow('Minecraft version');
  });
  test('rewrites GitHub raw links and rejects untrusted mod hosts', () => {
    expect(rawUrl('https://github.com/owner/repo/raw/abc/dir/mod.jar')).toBe(
      'https://raw.githubusercontent.com/owner/repo/abc/dir/mod.jar',
    );
    expect(rawUrl('https://github.com/owner/repo/releases/download/v1/mod.jar')).toBe(
      'https://github.com/owner/repo/releases/download/v1/mod.jar',
    );
    for (const url of [
      'http://cdn.modrinth.com/mod.jar',
      'https://evil.test/mod.jar',
      'https://libraries.minecraft.net/mod.jar',
    ])
      expect(() => rawUrl(url)).toThrow();
  });
  test('accepts any safe path for generic packs and only mods for the MCSR pack', () => {
    const files = packFiles(index);
    expect(files.map(file => file.path)).toEqual(['mods/antigone-1.16.1-2.0.0.jar', 'mods/mcsrranked-5.8.1.jar']);
    expect(files[0].url).toStartWith('https://raw.githubusercontent.com/');
    expect(files[1]).toMatchObject({ sha1, size: 25414385 });
    const withPath = (path: string) => ({ ...index, files: [{ ...index.files[1], path }] });
    for (const path of ['config/options.txt', 'resourcepacks/pack.zip', 'shaderpacks/x.zip'])
      expect(packFiles(withPath(path))[0].path).toBe(path);
    for (const path of ['mods/../options.txt', '/etc/passwd', 'C:/x.jar', 'mods\\x.jar'])
      expect(() => packFiles(withPath(path))).toThrow('Unsafe');
    for (const path of ['config/options.txt', 'mods/sub/mod.jar', 'mods/mod.zip', 'options.txt'])
      expect(() => mcsrFiles(withPath(path), '1.16.1')).toThrow('Unsupported pack file');
    expect(mcsrFiles(index, '1.16.1')).toHaveLength(2);
    expect(() => mcsrFiles(index, '1.16.5')).toThrow('does not target');
    expect(() => mcsrFiles({ ...index, dependencies: { minecraft: '1.16.1' } }, '1.16.1')).toThrow('Fabric loader');
    expect(() => packFiles({ ...index, files: [{ ...index.files[1], downloads: [] }] })).toThrow('download');
  });
  test('removes only previously managed files that left the pack', () => {
    expect(stalePaths(['mods/old.jar', 'mods/kept.jar'], ['mods/kept.jar', 'mods/new.jar'])).toEqual(['mods/old.jar']);
  });
});
describe('loader resolution', () => {
  test('maps Maven coordinates to repository paths', () => {
    expect(mavenPath('net.fabricmc:fabric-loader:0.19.3')).toBe(
      'net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar',
    );
    expect(mavenPath('org.lwjgl:lwjgl:3.2.2:natives-windows')).toBe(
      'org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2-natives-windows.jar',
    );
    for (const name of ['net.fabricmc', 'a:b:c:d:e', '']) expect(() => mavenPath(name)).toThrow();
  });
  test('builds Fabric and Quilt meta URLs from validated versions', () => {
    expect(metaUrl('fabric', '1.21.1')).toBe('https://meta.fabricmc.net/v2/versions/loader/1.21.1');
    expect(metaUrl('quilt', '1.21.1', '0.27.0')).toBe(
      'https://meta.quiltmc.org/v3/versions/loader/1.21.1/0.27.0/profile/json',
    );
    expect(() => metaUrl('fabric', '../x')).toThrow();
    expect(() => metaUrl('fabric', '1.21.1', 'a/b')).toThrow();
  });
  test('parses loader lists and marks stable builds', () => {
    expect(
      loaderVersionsFrom([
        { loader: { version: '0.16.9', stable: true } },
        { loader: { version: '0.17.0-beta.1', stable: false } },
        { loader: { version: '0.27.0' } },
        { loader: { version: '0.28.0-beta.1' } },
      ]),
    ).toEqual([
      { version: '0.16.9', stable: true },
      { version: '0.17.0-beta.1', stable: false },
      { version: '0.27.0', stable: true },
      { version: '0.28.0-beta.1', stable: false },
    ]);
    expect(() => loaderVersionsFrom({})).toThrow();
    expect(() => loaderVersionsFrom([{ loader: { version: 'x y' } }])).toThrow();
  });
  test('puts the loader ahead of the vanilla classpath and arguments', () => {
    const merged = mergeLoader(installation, {
      mainClass: 'net.fabricmc.loader.impl.launch.knot.KnotClient',
      jvm: ['-DFabricMcEmu= net.minecraft.client.main.Main '],
      game: [],
      classpath: ['loader.jar'],
    });
    expect(merged.classpath).toEqual(['loader.jar', 'client.jar']);
    expect(merged.metadata.mainClass).toBe('net.fabricmc.loader.impl.launch.knot.KnotClient');
    expect(merged.metadata.arguments).toEqual({
      jvm: ['-DFabricMcEmu= net.minecraft.client.main.Main ', '-cp', '${classpath}'],
      game: ['--username', '${auth_player_name}'],
    });
    expect(installation.classpath).toEqual(['client.jar']);
  });
  test('synthesizes modern arguments for legacy metadata', () => {
    const legacy: Installation = {
      ...installation,
      metadata: {
        ...installation.metadata,
        arguments: undefined,
        minecraftArguments: '--username ${auth_player_name}',
      },
    };
    const merged = mergeLoader(legacy, { mainClass: 'a.b.C', jvm: ['-Dx=1'], game: ['--extra'], classpath: [] });
    expect(merged.metadata.arguments).toEqual({
      jvm: ['-Dx=1', '-Djava.library.path=${natives_directory}', '-cp', '${classpath}'],
      game: ['--username', '${auth_player_name}', '--extra'],
    });
  });
});
describe('Java runtime handling', () => {
  test('selects raw runtime files, directories, links and the java executable', () => {
    const raw = { url: 'https://piston-data.mojang.com/x', sha1, size: 1 };
    const manifest = {
      files: {
        bin: { type: 'directory' },
        'bin/java.exe': { type: 'file', executable: true, downloads: { raw } },
        COPYRIGHT: { type: 'file', downloads: { raw, lzma: raw } },
      },
    };
    expect(runtimeFiles(manifest)).toEqual({
      directories: ['bin'],
      files: [
        { ...raw, path: 'bin/java.exe', executable: true },
        { ...raw, path: 'COPYRIGHT', executable: false },
      ],
      links: [],
      java: 'bin/java.exe',
    });
    const mac = {
      files: {
        'jre.bundle/Contents/Home/bin/java': { type: 'file', executable: true, downloads: { raw } },
        'lib/libjsig.so': { type: 'link', target: '../libjsig.so' },
      },
    };
    expect(runtimeFiles(mac)).toMatchObject({
      links: [{ path: 'lib/libjsig.so', target: '../libjsig.so' }],
      java: 'jre.bundle/Contents/Home/bin/java',
    });
    expect(() => runtimeFiles({ files: { link: { type: 'link' } } })).toThrow('Unsupported');
    expect(() => runtimeFiles({ files: { COPYRIGHT: { type: 'file', downloads: { raw } } } })).toThrow(
      'no java executable',
    );
    expect(runtimeKeys({ os: 'windows', arch: 'x86_64', version: '' })).toEqual(['windows-x64']);
    expect(runtimeKeys({ os: 'linux', arch: 'x86_64', version: '' })).toEqual(['linux']);
    expect(runtimeKeys({ os: 'osx', arch: 'aarch64', version: '' })).toEqual(['mac-os-arm64', 'mac-os']);
    expect(packUrl({ os: 'osx', arch: 'aarch64', version: '' })).toEndWith('MCSRRanked-OSX-1.16.1-RSG.mrpack');
    expect(packUrl({ os: 'linux', arch: 'x86_64', version: '' })).toEndWith('MCSRRanked-Linux-1.16.1-RSG.mrpack');
  });
  test('checks the requested major version and 64-bit architecture', () => {
    const output = (specification: string, bits: string) =>
      `    java.specification.version = ${specification}\n    java.version = 1.8.0_51\n    sun.arch.data.model = ${bits}\n`;
    expect(javaMatches(output('1.8', '64'), 8)).toBe(true);
    expect(javaMatches(output('17', '64'), 17)).toBe(true);
    expect(javaMatches(output('1.8', '32'), 8)).toBe(false);
    expect(javaMatches(output('17', '64'), 8)).toBe(false);
    expect(javaMatches(output('1.8', '64'), 17)).toBe(false);
    expect(javaMatches(output('11', '64'), 1)).toBe(false);
    expect(javaMatches(output('21', '64'))).toBe(true);
    expect(javaMatches(output('21', '32'))).toBe(false);
  });
});
describe('shared download helpers', () => {
  test('host allowlists are exact and HTTPS only', () => {
    expect(trustedUrl('https://maven.fabricmc.net/a.jar', ['maven.fabricmc.net'])).toBe(
      'https://maven.fabricmc.net/a.jar',
    );
    for (const url of [
      'https://maven.fabricmc.net.evil.test/a',
      'http://maven.fabricmc.net/a',
      'https://maven.fabricmc.net:8443/a',
    ])
      expect(() => trustedUrl(url, ['maven.fabricmc.net'])).toThrow();
  });
  test('user-typed import links must be https without credentials', () => {
    expect(secureUrl('https://example.test/pack.mrpack')).toBe('https://example.test/pack.mrpack');
    for (const url of [
      'http://example.test/pack.zip',
      'https://user:pw@example.test/pack.zip',
      'file:///C:/pack.zip',
      'x',
    ])
      expect(() => secureUrl(url)).toThrow();
  });
  test('parallel workers stop scheduling after the first failure and propagate it', async () => {
    const started: number[] = [];
    await expect(
      parallel([1, 2, 3, 4, 5, 6], 2, async item => {
        started.push(item);
        await new Promise(resolve => setTimeout(resolve, 5));
        if (item === 2) throw new Error('boom');
      }),
    ).rejects.toThrow('boom');
    expect(started.length).toBeLessThan(6);
    const done: number[] = [];
    await parallel([1, 2, 3], 8, async item => {
      done.push(item);
    });
    expect(done).toEqual([1, 2, 3]);
  });
});
