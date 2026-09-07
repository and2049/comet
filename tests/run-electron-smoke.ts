import { spawn } from 'node:child_process';
import { createRequire } from 'node:module';
import { mkdir, mkdtemp, rm } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';

const temporaryRoot = path.join(os.tmpdir(), 'redsun');
await mkdir(temporaryRoot, { recursive: true });
const data = await mkdtemp(path.join(temporaryRoot, 'comet-smoke-'));
try {
  const child = spawn(createRequire(import.meta.url)('electron') as string, ['tests/electron-smoke.cjs'], {
    stdio: 'inherit', env: { ...process.env, COMET_SMOKE_DATA: data },
  });
  const code = await new Promise<number>((resolve, reject) => {
    child.once('error', reject);
    child.once('close', code => resolve(code ?? 1));
  });
  process.exitCode = code;
} finally {
  await rm(data, { recursive: true, force: true, maxRetries: 10, retryDelay: 200 });
}
