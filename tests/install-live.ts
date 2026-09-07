import { mkdtemp, mkdir, readdir, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import { install, installRuntime, verifyJava } from '../src/main/minecraft';
import { withFabric } from '../src/main/fabric';
import { readdir as list } from 'node:fs/promises';
import { instances } from '../src/main/core';

const temporaryRoot = path.join(os.tmpdir(), 'redsun');
await mkdir(temporaryRoot, { recursive: true });
const root = await mkdtemp(path.join(temporaryRoot, 'comet-install-'));
try {
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
    console.log(`${instance.version}: full client, libraries, assets, logging configuration and native extraction passed on ${process.platform}`);
    if (instance.profile === 'mcsr') {
      result = await withFabric(root, instance, result, report);
      assert(result.classpath.some(entry => /fabric-loader-[\d.]+\.jar$/.test(entry)));
      assert.equal(result.metadata.mainClass, 'net.fabricmc.loader.impl.launch.knot.KnotClient');
      const mods = await list(path.join(result.game, 'mods'));
      assert(mods.some(name => /^mcsrranked-/.test(name)) && mods.length >= 10);
      console.log(`${instance.version}: Fabric loader, libraries and ${mods.length} MCSR pack mods installed`);
    }
    assert.equal(result.metadata.javaVersion?.majorVersion, 8);
  }
  const java = await installRuntime(root, { component: 'jre-legacy', majorVersion: 8 }, message => console.log(message));
  await verifyJava(java, 8);
  console.log('Managed jre-legacy runtime downloaded and verified as 64-bit Java 8');
} finally {
  await rm(root, { recursive: true, force: true });
}
