import { spawn } from 'node:child_process';
import { mkdtemp, mkdir, readFile, readdir, rm } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import assert from 'node:assert/strict';
import { install, installRuntime, launchArguments, verifyJava } from '../src/main/minecraft';
import { mergeClient, prepareClient } from '../src/main/client';
import { instances, javaExecutable } from '../src/main/core';
import { loadInstances } from '../src/main/instances';

const devDir = process.env.COMET_CLIENT_DIR ?? path.join(import.meta.dirname, '..', 'client', 'build', 'dist');
const wanted = process.env.COMET_LIVE_VERSIONS?.split(',') ?? ['1.8.9', '1.7.10'];
const reuse = process.env.COMET_LIVE_ROOT;
const root = reuse ?? (await mkdtemp(path.join(await ensure(path.join(os.tmpdir(), 'redsun')), 'comet-client-')));
const session = {
  account: { id: '00000000000000000000000000000000', name: 'CometTest' },
  accessToken: 'offline',
  refreshToken: '',
  clientId: 'test',
  xuid: '',
};
const markers = ['OptiFine ZIP file:', 'SpongePowered MIXIN Subsystem', 'Comet client ready (OptiFine)'];
const menu = /Sound engine started/;

async function ensure(directory: string): Promise<string> {
  await mkdir(directory, { recursive: true });
  return directory;
}
function runGame(java: string, args: string[], cwd: string): Promise<string[]> {
  return new Promise((resolve, reject) => {
    const lines: string[] = [];
    const child = spawn(javaExecutable(java), args, { cwd, shell: false, stdio: ['ignore', 'pipe', 'pipe'] });
    const timer = setTimeout(() => child.kill(), 150000);
    const seen = () => markers.every(marker => lines.some(line => line.includes(marker)));
    const collect = (chunk: Buffer) => {
      for (const line of chunk.toString().split(/\r?\n/)) {
        if (!line) continue;
        lines.push(line);
        console.log(`  | ${line}`);
      }
      if (seen() && lines.some(line => menu.test(line))) setTimeout(() => child.kill(), 3000);
    };
    child.stdout.on('data', collect);
    child.stderr.on('data', collect);
    child.once('error', reject);
    child.once('close', () => {
      clearTimeout(timer);
      resolve(lines);
    });
  });
}
try {
  await loadInstances(root, message => console.log(message));
  const java = await installRuntime(root, { component: 'jre-legacy', majorVersion: 8 }, message =>
    console.log(message),
  );
  await verifyJava(java, 8);
  for (const instance of instances.filter(item => item.profile === 'pvp' && wanted.includes(item.version))) {
    let last = '';
    const report = (message: string) => {
      const category = message.startsWith('Verifying') ? 'verifying' : message;
      if (category !== last) console.log(message);
      last = category;
    };
    const vanilla = await install(root, instance, report);
    const parts = await prepareClient(root, instance.version, report, devDir);
    assert.deepEqual(parts.warnings, [], 'OptiFine and the Comet client must both be available');
    assert(parts.optifine && parts.client);
    const installation = mergeClient(vanilla, parts);
    const args = launchArguments(
      installation,
      { clientId: '', javaPath: '', memoryMb: 2048, minimizeOnLaunch: false },
      session,
    );
    const lines = await runGame(java, ['-Dmixin.debug.export=true', ...args], installation.game);
    for (const marker of markers)
      assert(
        lines.some(line => line.includes(marker)),
        `Missing log marker: ${marker}`,
      );
    assert(!lines.some(line => /MixinApplyError|Mixin apply failed|InvalidMixinException/.test(line)), 'Mixin errors');
    assert(!lines.some(line => /Unable to launch|Exception in thread|Caused by:/.test(line)), 'The game crashed');
    assert(
      lines.some(line => menu.test(line)),
      'The game did not reach the main menu',
    );
    const exported = path.join(installation.game, '.mixin.out');
    const transformed = (await readdir(exported, { recursive: true }).catch(() => [] as string[])).filter(
      name => name.endsWith('.class') && !name.includes('comet'),
    );
    assert(
      transformed.length >= 2,
      `Mixin exported ${transformed.length} transformed game classes, expected at least 2`,
    );
    const classes = await Promise.all(transformed.map(name => readFile(path.join(exported, name))));
    const hooks = ['comet$state', 'comet$held', 'comet$release'];
    if (instance.version === '1.8.9') hooks.push('comet$oldSwing', 'comet$useSwing');
    for (const hook of hooks)
      assert(
        classes.some(bytes => bytes.includes(Buffer.from(hook))),
        `${instance.version}: missing transformed hook ${hook}`,
      );
    console.log(`${instance.version}: Mixin transformed ${transformed.map(name => path.basename(name)).join(', ')}`);
    await rm(exported, { recursive: true, force: true });
    console.log(`${instance.version}: OptiFine, Mixin and the Comet client loaded together`);
  }
} finally {
  if (!reuse) await rm(root, { recursive: true, force: true });
}
