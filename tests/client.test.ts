import { afterEach, describe, expect, test } from 'bun:test';
import { mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import {
  addOptifineFile,
  availability,
  clientFrom,
  clientPath,
  devClient,
  mergeClient,
  newerLauncher,
  optifineLink,
  optifinePath,
} from '../src/main/client';
import type { Installation } from '../src/main/minecraft';
import { scratch } from './helpers';

let root = '';
afterEach(async () => {
  if (root) await rm(root, { recursive: true, force: true });
  root = '';
});
const sha1 = 'b'.repeat(40);
const file = 'OptiFine_1.8.9_HD_U_M5.jar';
const manifest = {
  launcher: '0.1.0',
  clients: {
    '1.8.9': { url: 'https://github.com/and2049/comet/releases/download/v0.1.0/comet-client-1.8.9.jar', sha1, size: 5 },
  },
};
const installation: Installation = {
  metadata: {
    id: '1.8.9',
    type: 'release',
    mainClass: 'net.minecraft.client.main.Main',
    libraries: [],
    downloads: { client: { url: '', sha1: '' } },
    assetIndex: { id: '1.8', url: '', sha1: '' },
    arguments: { jvm: ['-cp', '${classpath}'], game: ['--username', '${auth_player_name}'] },
  },
  classpath: ['client.jar'],
  game: 'game',
  assets: 'assets',
  natives: 'natives',
  gameAssets: 'assets',
};

describe('OptiFine acquisition', () => {
  test('parses the mirror page link for the pinned file only', () => {
    const token = '5150b2efb38c1ae16307f02ee07a68b8';
    const page = `<a href='downloadx?f=${file}&x=${token}'>Download</a>`;
    expect(optifineLink(page, file)).toBe(`https://optifine.net/downloadx?f=${file}&x=${token}`);
    expect(() => optifineLink('<html>no link</html>', file)).toThrow('download link');
    expect(() => optifineLink(page, 'OptiFine_1.7.10_HD_U_E7.jar')).toThrow('download link');
    expect(() => optifineLink(`downloadx?f=${file}&x=zz`, file)).toThrow('download link');
  });
  test('rejects jars that do not match the pinned build', async () => {
    root = await scratch('comet-client-');
    const source = path.join(root, 'fake.jar');
    await writeFile(source, 'not optifine');
    await expect(addOptifineFile(root, '1.8.9', source)).rejects.toThrow('HD U M5');
    await expect(addOptifineFile(root, '1.16.1', source)).rejects.toThrow('not pinned');
    expect(optifinePath(root, '1.7.10')).toBe(path.join(root, 'optifine', 'OptiFine_1.7.10_HD_U_E7.jar'));
    expect((await availability(root, ['1.8.9', '1.7.10'])).optifine).toEqual([]);
  });
});
describe('Comet client manifest', () => {
  test('validates the release manifest', () => {
    expect(clientFrom(JSON.parse(JSON.stringify(manifest)))).toEqual(manifest);
    for (const broken of [
      { ...manifest, launcher: 'latest' },
      { ...manifest, clients: { '1.8.9': { ...manifest.clients['1.8.9'], sha1: 'abc' } } },
      { ...manifest, clients: { '1.8.9': { ...manifest.clients['1.8.9'], size: 1.5 } } },
      { ...manifest, clients: { '1.8.9': { ...manifest.clients['1.8.9'], url: 'https://example.com/x.jar' } } },
      { ...manifest, clients: { '1.8.9': { ...manifest.clients['1.8.9'], url: 'http://github.com/x.jar' } } },
      { ...manifest, clients: { '../x': manifest.clients['1.8.9'] } },
      { ...manifest, clients: { '1.8.9': { sha1, size: 5 } } },
    ])
      expect(() => clientFrom(broken)).toThrow();
  });
  test('compares launcher versions numerically', () => {
    expect(newerLauncher('0.1.0', '0.1.0')).toBe(false);
    expect(newerLauncher('0.1.1', '0.1.0')).toBe(true);
    expect(newerLauncher('0.10.0', '0.9.0')).toBe(true);
    expect(newerLauncher('0.9.0', '0.10.0')).toBe(false);
    expect(newerLauncher('1.0.0', '0.99.99')).toBe(true);
  });
  test('resolves development and cached client jars', async () => {
    root = await scratch('comet-client-');
    expect(await devClient(undefined, '1.8.9')).toBeUndefined();
    expect(await devClient(root, '1.8.9')).toBeUndefined();
    const dev = path.join(root, 'comet-client-1.8.9.jar');
    await writeFile(dev, 'jar');
    expect(await devClient(root, '1.8.9')).toBe(dev);
    expect(clientPath(root, '1.7.10')).toBe(path.join(root, 'client', 'comet-client-1.7.10.jar'));
    expect(() => clientPath(root, '../x')).toThrow();
    await mkdir(path.join(root, 'client'));
    await writeFile(clientPath(root, '1.7.10'), 'jar');
    expect(await availability(root, ['1.8.9', '1.7.10'], root)).toEqual({ optifine: [], client: ['1.8.9', '1.7.10'] });
    expect(await availability(root, ['1.8.9', '1.7.10'])).toEqual({ optifine: [], client: ['1.7.10'] });
  });
});
describe('launch composition', () => {
  test('puts the client, OptiFine and LaunchWrapper ahead of vanilla with a single tweaker', () => {
    const merged = mergeClient(installation, { client: 'comet.jar', optifine: 'of.jar', launchwrapper: 'lw.jar' });
    expect(merged.classpath).toEqual(['comet.jar', 'of.jar', 'lw.jar', 'client.jar']);
    expect(merged.metadata.mainClass).toBe('net.minecraft.launchwrapper.Launch');
    expect(merged.metadata.arguments).toEqual({
      jvm: ['-cp', '${classpath}'],
      game: ['--username', '${auth_player_name}', '--tweakClass', 'comet.launch.CometTweaker'],
    });
    expect(installation.classpath).toEqual(['client.jar']);
    expect(installation.metadata.mainClass).toBe('net.minecraft.client.main.Main');
  });
  test('handles a missing part and legacy metadata', () => {
    expect(mergeClient(installation, { launchwrapper: 'lw.jar' })).toBe(installation);
    const legacy: Installation = {
      ...installation,
      metadata: {
        ...installation.metadata,
        arguments: undefined,
        minecraftArguments: '--username ${auth_player_name}',
      },
    };
    const merged = mergeClient(legacy, { optifine: 'of.jar', launchwrapper: 'lw.jar' });
    expect(merged.classpath).toEqual(['of.jar', 'lw.jar', 'client.jar']);
    expect(merged.metadata.arguments).toEqual({
      jvm: ['-Djava.library.path=${natives_directory}', '-cp', '${classpath}'],
      game: ['--username', '${auth_player_name}', '--tweakClass', 'optifine.OptiFineTweaker'],
    });
    const clientOnly = mergeClient(installation, { client: 'comet.jar', launchwrapper: 'lw.jar' });
    expect(clientOnly.classpath).toEqual(['comet.jar', 'lw.jar', 'client.jar']);
    expect(clientOnly.metadata.arguments?.game.slice(-2)).toEqual(['--tweakClass', 'comet.launch.CometTweaker']);
  });
});
