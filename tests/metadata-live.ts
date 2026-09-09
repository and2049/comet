import { strict as assert } from 'node:assert';
import path from 'node:path';
import os from 'node:os';
import { rm } from 'node:fs/promises';
import { instances, defaults, allowed, platform } from '../src/main/core';
import { fetchFile, json, officialUrl, packHosts, trustedUrl } from '../src/main/net';
import { launchArguments, versionList, type Installation } from '../src/main/minecraft';
import { installLoader, loaderVersions } from '../src/main/loader';
import { readPack } from '../src/main/mrpack';
import { mcsrFiles, packUrl } from '../src/main/mcsr';
import { projectVersions, searchModpacks } from '../src/main/modrinth';

const manifest = (await json('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json')) as {
  versions: { id: string; url: string }[];
};
for (const instance of instances) {
  const entry = manifest.versions.find(v => v.id === instance.version);
  assert(entry);
  const metadata = (await json(officialUrl(entry.url))) as Installation['metadata'];
  assert.equal(metadata.id, instance.version);
  const target = platform();
  for (const library of metadata.libraries.filter(l => allowed(l.rules, target))) {
    if (library.downloads.artifact) officialUrl(library.downloads.artifact.url);
    const classifier = library.natives?.[target.os]?.replace('${arch}', '64');
    if (classifier) assert(library.downloads.classifiers?.[classifier]);
  }
  const args = launchArguments(
    { metadata, classpath: ['client.jar'], natives: 'natives', assets: 'assets', game: 'game', gameAssets: 'assets' },
    defaults,
    {
      account: { id: 'a'.repeat(32), name: 'TestPlayer' },
      clientId: 'test',
      accessToken: 'test',
      refreshToken: 'test',
      xuid: 'test',
    },
  );
  assert(!args.some(arg => arg.includes('${')));
  assert(args.includes(metadata.mainClass));
  assert(metadata.logging?.client, 'Official logging mitigation configuration is present');
  console.log(`${instance.version}: official metadata, ${target.os} natives and launch arguments verified`);
}
const versions = await versionList();
const latest = versions.find(version => version.type === 'release');
assert(latest && versions.some(version => version.id === '1.7.10'));
console.log(`Version catalog: ${versions.length} versions, latest release ${latest.id}`);
const scratch = path.join(os.tmpdir(), 'redsun', 'comet-loader-meta');
try {
  const archive = path.join(scratch, 'mcsr.mrpack');
  await fetchFile(trustedUrl(packUrl(platform()), packHosts), archive);
  const index = await readPack(archive);
  const files = mcsrFiles(index, '1.16.1');
  assert(files.length >= 10 && files.some(file => file.path.startsWith('mods/mcsrranked-')));
  const fabric = await installLoader(scratch, 'fabric', '1.16.1', index.dependencies['fabric-loader'], () => undefined);
  assert(
    fabric.classpath.some(entry => entry.includes('fabric-loader')) &&
      fabric.classpath.some(entry => entry.includes('intermediary')),
  );
  console.log(
    `MCSR pack ${index.versionId}: ${files.length} client mods, Fabric loader ${index.dependencies['fabric-loader']} resolved with ${fabric.classpath.length} libraries`,
  );
  for (const kind of ['fabric', 'quilt'] as const) {
    const list = await loaderVersions(kind, latest.id);
    assert(list.length > 0 && list.some(item => item.stable), `${kind} lists loaders for ${latest.id}`);
    console.log(
      `${kind} for ${latest.id}: ${list.length} loader versions, first stable ${list.find(v => v.stable)?.version}`,
    );
  }
  const quilt = await installLoader(
    scratch,
    'quilt',
    latest.id,
    (await loaderVersions('quilt', latest.id)).find(v => v.stable)!.version,
    () => undefined,
  );
  assert(quilt.mainClass.includes('quilt') && quilt.classpath.some(entry => entry.includes('quilt-loader')));
  console.log(`Quilt profile for ${latest.id}: ${quilt.mainClass} with ${quilt.classpath.length} libraries`);
} finally {
  await rm(scratch, { recursive: true, force: true });
}
const search = await searchModpacks('fabulously optimized', 0);
const hit = search.hits.find(item => item.slug === 'fabulously-optimized');
assert(hit && search.total > 0);
const packVersions = await projectVersions(hit.projectId);
assert(packVersions.some(version => version.supported));
console.log(
  `Modrinth: ${search.total} modpacks match, ${hit.title} has ${packVersions.length} versions (${packVersions.filter(v => v.supported).length} installable), icon ${hit.icon ? 'embedded' : 'missing'}`,
);
