import { mkdtemp, mkdir, readdir, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import { install } from '../src/main/minecraft';
import { instances } from '../src/main/core';

const temporaryRoot = path.join(os.tmpdir(), 'redsun');
await mkdir(temporaryRoot, { recursive: true });
const root = await mkdtemp(path.join(temporaryRoot, 'comet-install-'));
try {
  for (const instance of instances) {
    let last = '';
    const result = await install(root, instance, message => {
      const category = message.startsWith('Verifying assets') ? 'assets' : message;
      if (category !== last) console.log(message);
      last = category;
    });
    assert((await stat(result.classpath.at(-1)!)).size > 0);
    assert((await readdir(result.natives)).some(name => /^lwjgl(?:64)?\.dll$/i.test(name)));
    console.log(`${instance.version}: full client, libraries, assets, logging configuration and Windows native extraction passed`);
  }
} finally {
  await rm(root, { recursive: true, force: true });
}
