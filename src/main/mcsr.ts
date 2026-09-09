import { randomUUID } from 'node:crypto';
import { rm } from 'node:fs/promises';
import path from 'node:path';
import { platform, type Platform } from './core.js';
import { fetchFile, packHosts, trustedUrl } from './net.js';
import { applyPack, packFiles, readPack, type PackFile, type PackIndex } from './mrpack.js';
import type { Instance } from '../shared.js';

export function packUrl(target: Platform): string {
  const names = { windows: 'Windows', osx: 'OSX', linux: 'Linux' };
  return `https://redlime.github.io/MCSRMods/modpacks/v4/MCSRRanked-${names[target.os]}-1.16.1-RSG.mrpack`;
}
export function mcsrFiles(index: PackIndex, version: string): PackFile[] {
  if (index.dependencies.minecraft !== version) throw new Error('The MCSR pack does not target this instance version.');
  if (!/^\d+\.\d+\.\d+$/.test(index.dependencies['fabric-loader'] ?? ''))
    throw new Error('The MCSR pack does not declare a Fabric loader version.');
  return packFiles(index, { strictMods: true });
}
export async function installMcsr(
  root: string,
  instance: Instance,
  report: (message: string) => void,
): Promise<string> {
  report('Fetching the MCSR Ranked pack');
  const archive = path.join(root, 'cache', `import-${randomUUID()}.mrpack`);
  try {
    await fetchFile(trustedUrl(packUrl(platform()), packHosts), archive);
    const index = await readPack(archive);
    await applyPack(root, instance, index, mcsrFiles(index, instance.version), archive, report);
    return index.dependencies['fabric-loader'];
  } finally {
    await rm(archive, { force: true });
  }
}
