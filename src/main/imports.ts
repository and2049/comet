import { randomUUID } from 'node:crypto';
import { rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { cleanName, record, text, versionPattern } from './core.js';
import { fetchFile } from './net.js';
import { extractZip, listZip, readZipEntry } from './zip.js';
import { applyPack, packFiles, packTarget, readPack, type PackTarget } from './mrpack.js';
import { createInstance, instanceFolder, removeInstance } from './instances.js';
import { gameDirectory } from './minecraft.js';
import type { Instance, LoaderKind, Pack } from '../shared.js';

export const importHint =
  'Only Modrinth modpacks (.mrpack) and Prism Launcher, PolyMC or MultiMC exports (.zip) can be imported.';
const implied = ['org.lwjgl', 'org.lwjgl3', 'net.fabricmc.intermediary', 'org.quiltmc.hashed'];
const loaderUids: Record<string, LoaderKind> = {
  'net.fabricmc.fabric-loader': 'fabric',
  'org.quiltmc.quilt-loader': 'quilt',
};
export interface ImportOptions {
  name?: string;
  pack?: Pack;
  icon?: Buffer;
}

export function prismComponents(value: unknown): PackTarget {
  const pack = record(value);
  if (!Array.isArray(pack.components)) throw new Error('Unsupported instance export.');
  let version: string | undefined;
  let loader: LoaderKind = 'vanilla';
  let loaderVersion: string | undefined;
  for (const item of pack.components) {
    const component = record(item);
    const uid = text(component.uid);
    const declared = [component.version, component.cachedVersion].find(v => typeof v === 'string') as
      string | undefined;
    if (implied.includes(uid)) continue;
    if (uid === 'net.minecraft') version = declared;
    else if (uid in loaderUids) {
      loader = loaderUids[uid];
      loaderVersion = declared;
    } else throw new Error(`Component ${uid} is not supported yet.`);
  }
  if (!version || !versionPattern.test(version))
    throw new Error('The instance export does not declare a Minecraft version.');
  if (loader === 'vanilla') return { version, loader };
  if (!loaderVersion || !versionPattern.test(loaderVersion))
    throw new Error('The instance export does not declare a loader version.');
  return { version, loader, loaderVersion };
}
export function prismName(config: string): string | undefined {
  return /^name=(.+)$/m.exec(config)?.[1].trim() || undefined;
}
export function archiveKind(names: string[]): { kind: 'mrpack' } | { kind: 'prism'; prefix: string } {
  if (names.includes('modrinth.index.json')) return { kind: 'mrpack' };
  const pack = names.find(name => /^(?:[^/]+\/)?mmc-pack\.json$/.test(name));
  if (pack) return { kind: 'prism', prefix: pack.slice(0, -'mmc-pack.json'.length) };
  throw new Error(importHint);
}
export async function importArchive(
  root: string,
  existing: Instance[],
  archive: string,
  report: (message: string) => void,
  options: ImportOptions = {},
): Promise<Instance> {
  const names = await listZip(archive);
  const kind = archiveKind(names);
  let instance: Instance;
  if (kind.kind === 'mrpack') {
    const index = await readPack(archive);
    const target = packTarget(index);
    const files = packFiles(index);
    const name = cleanName(options.name ?? index.name, 'Imported modpack');
    instance = await createInstance(root, existing, {
      name,
      ...target,
      directory: 'isolated',
      pack: options.pack ?? { source: 'import', name: cleanName(index.name), versionId: index.versionId },
    });
    try {
      await applyPack(root, instance, index, files, archive, report);
    } catch (error) {
      await removeInstance(root, instance);
      throw error;
    }
  } else {
    const pack = await readZipEntry(archive, `${kind.prefix}mmc-pack.json`);
    if (!pack) throw new Error(importHint);
    const target = prismComponents(JSON.parse(pack.toString('utf8')));
    const config = await readZipEntry(archive, `${kind.prefix}instance.cfg`);
    const name = cleanName(options.name ?? (config && prismName(config.toString('utf8'))), 'Imported instance');
    const gameFolder = ['.minecraft/', 'minecraft/']
      .map(folder => kind.prefix + folder)
      .find(folder => names.some(entry => entry.startsWith(folder)));
    instance = await createInstance(root, existing, { name, ...target, directory: 'isolated' });
    try {
      report('Extracting instance files');
      if (gameFolder) await extractZip(archive, gameDirectory(root, instance), gameFolder);
    } catch (error) {
      await removeInstance(root, instance);
      throw error;
    }
  }
  if (options.icon) await writeFile(path.join(instanceFolder(root, instance.id), 'icon.png'), options.icon);
  return instance;
}
export async function importUrl(
  root: string,
  existing: Instance[],
  url: string,
  report: (message: string) => void,
): Promise<Instance> {
  const archive = path.join(root, 'cache', `import-${randomUUID()}.zip`);
  try {
    report('Downloading the archive');
    await fetchFile(url, archive);
    return await importArchive(root, existing, archive, report);
  } finally {
    await rm(archive, { force: true });
  }
}
