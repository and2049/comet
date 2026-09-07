import { strict as assert } from 'node:assert';
import path from 'node:path';
import os from 'node:os';
import { rm } from 'node:fs/promises';
import { instances, defaults, allowed, platform } from '../src/main/core';
import { json, officialUrl } from '../src/main/net';
import { launchArguments, type Installation } from '../src/main/minecraft';
import { installFabric, packFiles, readPack } from '../src/main/fabric';

const manifest = await json('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json') as { versions: { id: string; url: string }[] };
for (const instance of instances) {
  const entry = manifest.versions.find(v => v.id === instance.version);
  assert(entry);
  const metadata = await json(officialUrl(entry.url)) as Installation['metadata'];
  assert.equal(metadata.id, instance.version);
  const target = platform();
  for (const library of metadata.libraries.filter(l => allowed(l.rules, target))) {
    if (library.downloads.artifact) officialUrl(library.downloads.artifact.url);
    const classifier = library.natives?.[target.os]?.replace('${arch}', '64');
    if (classifier) assert(library.downloads.classifiers?.[classifier]);
  }
  const args = launchArguments({ metadata, classpath: ['client.jar'], natives: 'natives', assets: 'assets', game: 'game', gameAssets: 'assets' }, defaults, {
    account: { id: 'a'.repeat(32), name: 'TestPlayer' }, clientId: 'test', accessToken: 'test', refreshToken: 'test',
  });
  assert(!args.some(arg => arg.includes('${')));
  assert(args.includes(metadata.mainClass));
  assert(metadata.logging?.client, 'Official logging mitigation configuration is present');
  console.log(`${instance.version}: official metadata, ${target.os} natives and launch arguments verified`);
}
const index = await readPack();
const files = packFiles(index, '1.16.1');
assert(files.length >= 10 && files.some(file => file.path.startsWith('mods/mcsrranked-')));
const scratch = path.join(os.tmpdir(), 'redsun', 'comet-fabric-meta');
const fabric = await installFabric(scratch, '1.16.1', index.dependencies['fabric-loader'], () => undefined).finally(() => rm(scratch, { recursive: true, force: true }));
assert(fabric.classpath.some(entry => entry.includes('fabric-loader')) && fabric.classpath.some(entry => entry.includes('intermediary')));
console.log(`MCSR pack ${index.versionId}: ${files.length} client mods, Fabric loader ${index.dependencies['fabric-loader']} resolved with ${fabric.classpath.length} libraries`);
