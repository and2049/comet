import { mkdtemp, mkdir, readdir, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import { install, installRuntime, verifyJava } from '../src/main/minecraft';
import { installLoader, mergeLoader } from '../src/main/loader';
import { installMcsr } from '../src/main/mcsr';
import { instances } from '../src/main/core';
import { loadInstances } from '../src/main/instances';

const temporaryRoot = path.join(os.tmpdir(), 'redsun');
await mkdir(temporaryRoot, { recursive: true });
const root = await mkdtemp(path.join(temporaryRoot, 'comet-install-'));
try {
  await loadInstances(root, message => console.log(message));
  for (const instance of instances) {
    let last = '';
    const report = (message: string) => {
      const category = message.startsWith('Verifying') ? 'verifying' : message;
      if (category !== last) console.log(message);
      last = category;
    };
    let result = await install(root, instance, report);
    assert((await stat(result.classpath.at(-1)!)).size > 0);
    assert((await readdir(result.natives)).some(name => /^lib(?:lwjgl|jemalloc)|^lwjgl(?:64)?\.dll$/i.test(name)));
    console.log(
      `${instance.version}: full client, libraries, assets, logging configuration and native extraction passed on ${process.platform}`,
    );
    if (instance.pack?.source === 'mcsr') {
      const loader = await installMcsr(root, instance, report);
      result = mergeLoader(result, await installLoader(root, 'fabric', instance.version, loader, report));
      assert(result.classpath.some(entry => /fabric-loader-[\d.]+\.jar$/.test(entry)));
      assert.equal(result.metadata.mainClass, 'net.fabricmc.loader.impl.launch.knot.KnotClient');
      const mods = await readdir(path.join(result.game, 'mods'));
      assert(mods.some(name => /^mcsrranked-/.test(name)) && mods.length >= 10);
      const game = (await readdir(result.game)).filter(name => name !== 'mods');
      console.log(
        `${instance.version}: Fabric loader, libraries and ${mods.length} MCSR pack mods installed; other game entries: ${game.join(', ') || 'none'}`,
      );
    }
    assert.equal(result.metadata.javaVersion?.majorVersion, 8);
  }
  assert.equal(
    (await stat(path.join(root, 'shared', 'comet', '.minecraft'))).isDirectory(),
    true,
    'Comet PvP versions share one game folder',
  );
  const java = await installRuntime(root, { component: 'jre-legacy', majorVersion: 8 }, message =>
    console.log(message),
  );
  await verifyJava(java, 8);
  console.log('Managed jre-legacy runtime downloaded and verified as 64-bit Java 8');
} finally {
  await rm(root, { recursive: true, force: true });
}
