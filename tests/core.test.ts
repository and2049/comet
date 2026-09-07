import { describe, expect, test } from 'bun:test';
import path from 'node:path';
import {
  allowed,
  defaults,
  expand,
  inside,
  instances,
  javaExecutable,
  prismFiles,
  redact,
  settingsFrom,
  type Platform,
} from '../src/main/core';

const windows: Platform = { os: 'windows', arch: 'x86_64', version: '10.0' };
const mac: Platform = { os: 'osx', arch: 'aarch64', version: '23.0' };
import { officialUrl } from '../src/main/net';
import { gameDirectory, launchArguments, type Installation } from '../src/main/minecraft';

describe('instance isolation and Prism serialization', () => {
  test('profile identity stays isolated even on the same version', () => {
    const pvp = { ...instances[0], version: '1.16.1' as const };
    expect(gameDirectory('root', pvp)).not.toBe(gameDirectory('root', instances[2]));
    expect(new Set(instances.map(i => gameDirectory('root', i))).size).toBe(3);
  });
  test('writes vanilla Prism components and config', () => {
    for (const instance of instances) {
      const files = prismFiles(instance);
      expect(files.config).toContain('InstanceType=OneSix');
      expect(JSON.parse(files.pack)).toEqual({
        formatVersion: 1,
        components: [{ uid: 'net.minecraft', version: instance.version, important: true }],
      });
      expect(files.pack).not.toContain('fabric');
    }
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
    });
    expect(args).toContain('C:\\With Spaces\\game');
    expect(args).toContain('-Xmx4096M');
    expect(args).toContain('{}');
    expect(args).not.toContain('--demo');
  });
});
