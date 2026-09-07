import { strict as assert } from 'node:assert';
import { instances, defaults, allowed } from '../src/main/core';
import { json, officialUrl } from '../src/main/net';
import { launchArguments, type Installation } from '../src/main/minecraft';

const manifest = await json('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json') as { versions: { id: string; url: string }[] };
for (const instance of instances) {
  const entry = manifest.versions.find(v => v.id === instance.version);
  assert(entry);
  const metadata = await json(officialUrl(entry.url)) as Installation['metadata'];
  assert.equal(metadata.id, instance.version);
  for (const library of metadata.libraries.filter(l => allowed(l.rules, '10.0'))) {
    if (library.downloads.artifact) officialUrl(library.downloads.artifact.url);
    const classifier = library.natives?.windows?.replace('${arch}', '64');
    if (classifier) assert(library.downloads.classifiers?.[classifier]);
  }
  const args = launchArguments({ metadata, classpath: ['client.jar'], natives: 'natives', assets: 'assets', game: 'game', gameAssets: 'assets' }, defaults, {
    account: { id: 'a'.repeat(32), name: 'TestPlayer' }, clientId: 'test', accessToken: 'test', refreshToken: 'test',
  });
  assert(!args.some(arg => arg.includes('${')));
  assert(args.includes(metadata.mainClass));
  assert(metadata.logging?.client, 'Official logging mitigation configuration is present');
  console.log(`${instance.version}: official metadata, Windows natives and launch arguments verified`);
}
