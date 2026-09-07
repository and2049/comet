import { describe, expect, test } from 'bun:test';
import path from 'node:path';
import { allowed, defaults, expand, inside, instances, prismFiles, redact, settingsFrom } from '../src/main/core';
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
      expect(JSON.parse(files.pack)).toEqual({ formatVersion: 1, components: [{ uid: 'net.minecraft', version: instance.version, important: true }] });
      expect(files.pack).not.toContain('fabric');
    }
  });
});
describe('trust boundaries', () => {
  test('rejects path traversal, drive paths and alternate separators', () => {
    for (const value of ['../secret', '/absolute', 'C:/secret', 'x\\..\\secret', 'x/../secret', 'x//y', './secret', 'x:stream']) expect(() => inside('root', value)).toThrow();
    expect(inside('root', 'org/example/file.jar')).toBe(path.join('root', 'org', 'example', 'file.jar'));
  });
  test('only allows official HTTPS download hosts', () => {
    for (const url of ['http://libraries.minecraft.net/a', 'https://evil.test/a', 'https://libraries.minecraft.net.evil.test/a', 'https://user@libraries.minecraft.net/a', 'https://libraries.minecraft.net:8080/a']) expect(() => officialUrl(url)).toThrow();
    expect(officialUrl('https://libraries.minecraft.net/a')).toBe('https://libraries.minecraft.net/a');
  });
  test('validates settings arriving from the renderer', () => {
    expect(settingsFrom(defaults)).toEqual(defaults);
    for (const patch of [{ memoryMb: -1 }, { memoryMb: 99999 }, { memoryMb: 2048.5 }, { clientId: 'prism' }, { javaPath: 'java.exe' }, { minimizeOnLaunch: 'yes' }]) expect(() => settingsFrom({ ...defaults, ...patch })).toThrow();
  });
  test('removes known tokens and token argument values', () => {
    expect(redact('secret-token --accessToken other-token', ['secret-token'])).toBe('[redacted] --accessToken [redacted]');
  });
});
describe('Minecraft metadata rules', () => {
  test('ordered OS and feature rules', () => {
    expect(allowed(undefined, '10.0')).toBe(true);
    expect(allowed([], '10.0')).toBe(false);
    expect(allowed([{ action: 'allow' }, { action: 'disallow', os: { name: 'osx' } }], '10.0')).toBe(true);
    expect(allowed([{ action: 'allow', os: { name: 'linux' } }], '10.0')).toBe(false);
    expect(allowed([{ action: 'allow', features: { is_demo_user: true } }], '10.0')).toBe(false);
    expect(allowed([{ action: 'allow', features: { is_demo_user: false } }], '10.0')).toBe(true);
    expect(allowed([{ action: 'allow', os: { name: 'windows', version: '^10\\.' } }], '10.0')).toBe(true);
  });
  test('keeps spaces within substituted arguments and excludes demo flags', () => {
    expect(expand(['${directory}', { rules: [{ action: 'allow', features: { is_demo_user: true } }], value: '--demo' }], { directory: 'C:\\Games With Spaces' }, '10.0')).toEqual(['C:\\Games With Spaces']);
    expect(() => expand(['${unknown}'], {}, '10.0')).toThrow();
  });
  test('builds legacy authenticated arguments without shell quoting', () => {
    const installation: Installation = {
      metadata: { id: '1.7.10', type: 'release', mainClass: 'net.minecraft.client.main.Main', libraries: [], downloads: { client: { url: '', sha1: '' } }, assetIndex: { id: '1.7.10', url: '', sha1: '' }, minecraftArguments: '--username ${auth_player_name} --gameDir ${game_directory} --accessToken ${auth_access_token} --userProperties ${user_properties}' },
      classpath: ['C:\\With Spaces\\client.jar'], game: 'C:\\With Spaces\\game', assets: 'assets', natives: 'natives', gameAssets: 'virtual',
    };
    const args = launchArguments(installation, defaults, { account: { name: 'Tester', id: '0'.repeat(32) }, accessToken: 'token', refreshToken: 'refresh', clientId: 'id' });
    expect(args).toContain('C:\\With Spaces\\game');
    expect(args).toContain('-Xmx4096M');
    expect(args).toContain('{}');
    expect(args).not.toContain('--demo');
  });
});
