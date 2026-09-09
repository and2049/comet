import { describe, expect, test } from 'bun:test';
import path from 'node:path';
import {
  allowed,
  cleanName,
  defaults,
  draftFrom,
  expand,
  inside,
  instanceFrom,
  instanceId,
  instances,
  javaExecutable,
  officialDirectory,
  prismFiles,
  redact,
  settingsFrom,
  type Platform,
} from '../src/main/core';

const windows: Platform = { os: 'windows', arch: 'x86_64', version: '10.0' };
const mac: Platform = { os: 'osx', arch: 'aarch64', version: '23.0' };
import { officialUrl } from '../src/main/net';
import { gameDirectory, launchArguments, type Installation } from '../src/main/minecraft';
import type { Instance } from '../src/shared';

const custom: Instance = {
  id: 'survival',
  name: 'Survival',
  profile: 'custom',
  version: '1.21.1',
  loader: 'fabric',
  loaderVersion: '0.16.9',
  directory: 'isolated',
};
describe('instance directories and Prism serialization', () => {
  test('Comet PvP versions share one folder while MCSR and custom instances stay isolated', () => {
    expect(gameDirectory('root', instances[0])).toBe(gameDirectory('root', instances[1]));
    expect(gameDirectory('root', instances[0])).toBe(path.join('root', 'shared', 'comet', '.minecraft'));
    const pvp = { ...instances[0], version: '1.16.1' };
    expect(gameDirectory('root', pvp)).not.toBe(gameDirectory('root', instances[2]));
    expect(gameDirectory('root', instances[2])).toBe(path.join('root', 'instances', 'mcsr-1.16.1', '.minecraft'));
    expect(gameDirectory('root', custom)).toBe(path.join('root', 'instances', 'survival', '.minecraft'));
    const official = gameDirectory('root', { ...custom, directory: 'official' });
    expect(official).not.toContain('root');
    expect(official.toLowerCase()).toContain('minecraft');
  });
  test('official launcher folders per platform', () => {
    expect(officialDirectory('windows', 'C:\\Users\\a', 'C:\\Users\\a\\AppData\\Roaming')).toBe(
      'C:\\Users\\a\\AppData\\Roaming\\.minecraft',
    );
    expect(officialDirectory('linux', '/home/a', '')).toBe(path.join('/home/a', '.minecraft'));
    expect(officialDirectory('osx', '/Users/a', '')).toBe(
      path.join('/Users/a', 'Library', 'Application Support', 'minecraft'),
    );
  });
  test('writes Prism components and config', () => {
    for (const instance of instances) {
      const files = prismFiles(instance);
      expect(files.config).toContain('InstanceType=OneSix');
      expect(JSON.parse(files.pack)).toEqual({
        formatVersion: 1,
        components: [{ uid: 'net.minecraft', version: instance.version, important: true }],
      });
    }
    expect(JSON.parse(prismFiles(custom).pack).components).toEqual([
      { uid: 'net.minecraft', version: '1.21.1', important: true },
      { uid: 'net.fabricmc.fabric-loader', version: '0.16.9' },
    ]);
    expect(JSON.parse(prismFiles({ ...custom, loader: 'quilt' }).pack).components[1].uid).toBe(
      'org.quiltmc.quilt-loader',
    );
  });
  test('validates stored instances and rejects mismatched or unsafe descriptors', () => {
    expect(instanceFrom({ ...custom, schemaVersion: 2, extra: 1 }, 'survival')).toEqual(custom);
    expect(
      instanceFrom({ ...custom, pack: { source: 'import', name: 'Pack', versionId: '1.0' } }, 'survival').pack,
    ).toEqual({ source: 'import', name: 'Pack', versionId: '1.0' });
    for (const [value, folder] of [
      [custom, 'other'],
      [{ ...custom, id: '../escape' }, '../escape'],
      [{ ...custom, id: 'Bad Id' }, 'Bad Id'],
      [{ ...custom, profile: 'admin' }, 'survival'],
      [{ ...custom, version: '1.21.1; rm' }, 'survival'],
      [{ ...custom, loader: 'forge' }, 'survival'],
      [{ ...custom, directory: '/tmp' }, 'survival'],
      [{ ...custom, loaderVersion: 42 }, 'survival'],
      [{ ...custom, directory: 'official', pack: { source: 'import', name: 'Pack' } }, 'survival'],
      [{ ...custom, pack: { source: 'curseforge', name: 'Pack' } }, 'survival'],
    ] as const)
      expect(() => instanceFrom(value, folder)).toThrow();
  });
  test('derives unique folder-safe ids and clean names', () => {
    expect(instanceId('My Survival World!', [])).toBe('my-survival-world');
    expect(instanceId('My Survival World!', ['my-survival-world', 'my-survival-world-2'])).toBe('my-survival-world-3');
    expect(instanceId('***', [])).toBe('instance');
    expect(instanceId('x'.repeat(80), [])).toHaveLength(32);
    expect(cleanName('  Line\nBreak\u0000 ')).toBe('Line Break');
    expect(cleanName('', 'Fallback')).toBe('Fallback');
    expect(cleanName(undefined)).toBe('Instance');
  });
  test('validates create drafts from the renderer', () => {
    expect(draftFrom({ name: ' Survival ', version: '1.21.1', loader: 'vanilla', directory: 'official' })).toEqual({
      name: 'Survival',
      version: '1.21.1',
      loader: 'vanilla',
      directory: 'official',
    });
    expect(
      draftFrom({ name: 'Mods', version: '1.21.1', loader: 'quilt', loaderVersion: '0.27.0', directory: 'isolated' }),
    ).toMatchObject({ loader: 'quilt', loaderVersion: '0.27.0' });
    for (const draft of [
      { name: '', version: '1.21.1', loader: 'vanilla', directory: 'isolated' },
      { name: 'x', version: '1.21.1', loader: 'fabric', directory: 'isolated' },
      { name: 'x', version: '1.21.1', loader: 'vanilla', directory: 'comet' },
      { name: 'x', version: '../1.21.1', loader: 'vanilla', directory: 'isolated' },
    ])
      expect(() => draftFrom(draft)).toThrow();
  });
});
describe('trust boundaries', () => {
  test('rejects path traversal, drive paths and alternate separators', () => {
    for (const value of [
      '../secret',
      '/absolute',
      'C:/secret',
      'x\\..\\secret',
      'x/../secret',
      'x//y',
      './secret',
      'x:stream',
    ])
      expect(() => inside('root', value)).toThrow();
    expect(inside('root', 'org/example/file.jar')).toBe(path.join('root', 'org', 'example', 'file.jar'));
  });
  test('only allows official HTTPS download hosts', () => {
    for (const url of [
      'http://libraries.minecraft.net/a',
      'https://evil.test/a',
      'https://libraries.minecraft.net.evil.test/a',
      'https://user@libraries.minecraft.net/a',
      'https://libraries.minecraft.net:8080/a',
    ])
      expect(() => officialUrl(url)).toThrow();
    expect(officialUrl('https://libraries.minecraft.net/a')).toBe('https://libraries.minecraft.net/a');
  });
  test('validates settings arriving from the renderer', () => {
    expect(settingsFrom(defaults)).toEqual(defaults);
    for (const patch of [
      { memoryMb: -1 },
      { memoryMb: 99999 },
      { memoryMb: 2048.5 },
      { clientId: 'prism' },
      { javaPath: 'java.exe' },
      { javaPath: '/usr/bin/javac' },
      { minimizeOnLaunch: 'yes' },
    ])
      expect(() => settingsFrom({ ...defaults, ...patch })).toThrow();
    for (const javaPath of ['C:\\Java\\bin\\javaw.exe', '/usr/lib/jvm/java-8/bin/java', '/Library/Java/Home/bin/javaw'])
      expect(settingsFrom({ ...defaults, javaPath }).javaPath).toBe(javaPath);
    expect(javaExecutable('C:\\Java\\bin\\javaw.exe')).toBe('C:\\Java\\bin\\java.exe');
    expect(javaExecutable('/opt/jre/bin/javaw')).toBe('/opt/jre/bin/java');
    expect(javaExecutable('/opt/jre/bin/java')).toBe('/opt/jre/bin/java');
    expect(javaExecutable('C:\\Java\\bin\\java.exe', true, 'windows')).toBe('C:\\Java\\bin\\javaw.exe');
    expect(javaExecutable('C:\\Java\\bin\\javaw.exe', true, 'windows')).toBe('C:\\Java\\bin\\javaw.exe');
    expect(javaExecutable('/opt/jre/bin/javaw', true, 'linux')).toBe('/opt/jre/bin/java');
  });
  test('removes known tokens and token argument values', () => {
    expect(redact('secret-token --accessToken other-token', ['secret-token'])).toBe(
      '[redacted] --accessToken [redacted]',
    );
  });
});
describe('Minecraft metadata rules', () => {
  test('ordered OS and feature rules', () => {
    expect(allowed(undefined, windows)).toBe(true);
    expect(allowed([], windows)).toBe(false);
    expect(allowed([{ action: 'allow' }, { action: 'disallow', os: { name: 'osx' } }], windows)).toBe(true);
    expect(allowed([{ action: 'allow' }, { action: 'disallow', os: { name: 'osx' } }], mac)).toBe(false);
    expect(allowed([{ action: 'allow', os: { name: 'linux' } }], windows)).toBe(false);
    expect(allowed([{ action: 'allow', os: { name: 'osx' } }], mac)).toBe(true);
    expect(allowed([{ action: 'allow', features: { is_demo_user: true } }], windows)).toBe(false);
    expect(allowed([{ action: 'allow', features: { is_demo_user: false } }], windows)).toBe(true);
    expect(allowed([{ action: 'allow', os: { name: 'windows', version: '^10\\.' } }], windows)).toBe(true);
    expect(allowed([{ action: 'allow', os: { name: 'windows', version: '^10\\.' } }], mac)).toBe(false);
    expect(allowed([{ action: 'allow', os: { arch: 'x86' } }], windows)).toBe(false);
    expect(allowed([{ action: 'allow', os: { arch: 'arm64' } }], mac)).toBe(true);
  });
  test('keeps spaces within substituted arguments and excludes demo flags', () => {
    expect(
      expand(
        ['${directory}', { rules: [{ action: 'allow', features: { is_demo_user: true } }], value: '--demo' }],
        { directory: 'C:\\Games With Spaces' },
        windows,
      ),
    ).toEqual(['C:\\Games With Spaces']);
    expect(
      expand([{ rules: [{ action: 'allow', os: { name: 'osx' } }], value: ['-XstartOnFirstThread'] }], {}, mac),
    ).toEqual(['-XstartOnFirstThread']);
    expect(() => expand(['${unknown}'], {}, windows)).toThrow();
  });
  test('builds legacy authenticated arguments without shell quoting', () => {
    const installation: Installation = {
      metadata: {
        id: '1.7.10',
        type: 'release',
        mainClass: 'net.minecraft.client.main.Main',
        libraries: [],
        downloads: { client: { url: '', sha1: '' } },
        assetIndex: { id: '1.7.10', url: '', sha1: '' },
        minecraftArguments:
          '--username ${auth_player_name} --gameDir ${game_directory} --accessToken ${auth_access_token} --userProperties ${user_properties}',
      },
      classpath: ['C:\\With Spaces\\client.jar'],
      game: 'C:\\With Spaces\\game',
      assets: 'assets',
      natives: 'natives',
      gameAssets: 'virtual',
    };
    const args = launchArguments(installation, defaults, {
      account: { name: 'Tester', id: '0'.repeat(32) },
      accessToken: 'token',
      refreshToken: 'refresh',
      clientId: 'id',
      xuid: '2535xuid',
    });
    expect(args).toContain('C:\\With Spaces\\game');
    expect(args).toContain('-Xmx4096M');
    expect(args).toContain('{}');
    expect(args).not.toContain('--demo');
  });
  test('supplies modern client id and Xbox user id arguments', () => {
    const installation: Installation = {
      metadata: {
        id: '1.21.1',
        type: 'release',
        mainClass: 'net.minecraft.client.main.Main',
        libraries: [],
        downloads: { client: { url: '', sha1: '' } },
        assetIndex: { id: '17', url: '', sha1: '' },
        arguments: {
          jvm: ['-cp', '${classpath}'],
          game: ['--clientId', '${clientid}', '--xuid', '${auth_xuid}', '--userType', '${user_type}'],
        },
      },
      classpath: ['client.jar'],
      game: 'game',
      assets: 'assets',
      natives: 'natives',
      gameAssets: 'assets',
    };
    const args = launchArguments(installation, defaults, {
      account: { name: 'Tester', id: '0'.repeat(32) },
      accessToken: 'token',
      refreshToken: 'refresh',
      clientId: 'client-uuid',
      xuid: '2535xuid',
    });
    expect(args[args.indexOf('--clientId') + 1]).toBe('client-uuid');
    expect(args[args.indexOf('--xuid') + 1]).toBe('2535xuid');
    expect(args).toContain('msa');
  });
});
