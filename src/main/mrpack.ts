import { mkdir, readFile, unlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { inside, record, text, versionPattern } from './core.js';
import { download, packHosts, parallel, trustedUrl, type Download } from './net.js';
import { gameDirectory } from './minecraft.js';
import { extractZip, readZipEntry } from './zip.js';
import type { Instance, LoaderKind } from '../shared.js';

export interface PackIndex {
  formatVersion: number;
  game: string;
  versionId: string;
  name: string;
  dependencies: Record<string, string>;
  files: { path: string; hashes: { sha1: string }; env?: { client?: string }; downloads: string[]; fileSize: number }[];
}
export interface PackFile extends Download {
  path: string;
}
export interface PackTarget {
  version: string;
  loader: LoaderKind;
  loaderVersion?: string;
}
interface PackRecord {
  versionId?: string;
  files: string[];
}

export function indexFrom(value: unknown): PackIndex {
  const s = record(value);
  if (s.formatVersion !== 1 || s.game !== 'minecraft' || !Array.isArray(s.files))
    throw new Error('Unsupported modpack index.');
  const dependencies: Record<string, string> = {};
  for (const [key, version] of Object.entries(record(s.dependencies))) {
    if (typeof version !== 'string' || !versionPattern.test(version)) throw new Error('Unsupported modpack index.');
    dependencies[key] = version;
  }
  const files = s.files.map((item: unknown) => {
    const file = record(item);
    const env = file.env === undefined ? undefined : record(file.env);
    if (!Array.isArray(file.downloads) || !file.downloads.every(url => typeof url === 'string'))
      throw new Error('Unsupported modpack index.');
    if (typeof file.fileSize !== 'number') throw new Error('Unsupported modpack index.');
    return {
      path: text(file.path),
      hashes: { sha1: text(record(file.hashes).sha1) },
      env: typeof env?.client === 'string' ? { client: env.client } : undefined,
      downloads: file.downloads as string[],
      fileSize: file.fileSize,
    };
  });
  return { formatVersion: 1, game: 'minecraft', versionId: text(s.versionId), name: text(s.name), dependencies, files };
}
export function packTarget(index: PackIndex): PackTarget {
  const version = index.dependencies.minecraft;
  if (!version) throw new Error('The modpack does not declare a Minecraft version.');
  if (index.dependencies.forge || index.dependencies.neoforge)
    throw new Error('Comet installs Fabric and Quilt modpacks only.');
  const fabric = index.dependencies['fabric-loader'];
  const quilt = index.dependencies['quilt-loader'];
  if (fabric) return { version, loader: 'fabric', loaderVersion: fabric };
  if (quilt) return { version, loader: 'quilt', loaderVersion: quilt };
  return { version, loader: 'vanilla' };
}
export function rawUrl(value: string): string {
  const match = /^https:\/\/github\.com\/([^/]+)\/([^/]+)\/raw\/(.+)$/.exec(value);
  return trustedUrl(match ? `https://raw.githubusercontent.com/${match[1]}/${match[2]}/${match[3]}` : value, packHosts);
}
export function packFiles(index: PackIndex, options: { strictMods?: boolean } = {}): PackFile[] {
  return index.files
    .filter(file => file.env?.client !== 'unsupported')
    .map(file => {
      if (options.strictMods && (!file.path.startsWith('mods/') || !/^[\w.+-]+\.jar$/.test(file.path.slice(5))))
        throw new Error(`Unsupported pack file: ${file.path}`);
      inside('.', file.path);
      if (!file.downloads[0]) throw new Error(`Expected a download for ${file.path}`);
      return { path: file.path, sha1: file.hashes.sha1, size: file.fileSize, url: rawUrl(file.downloads[0]) };
    });
}
export function stalePaths(previous: string[], current: string[]): string[] {
  return previous.filter(item => !current.includes(item));
}
export async function readPack(archive: string): Promise<PackIndex> {
  const entry = await readZipEntry(archive, 'modrinth.index.json');
  if (!entry) throw new Error('The archive is not a Modrinth modpack.');
  return indexFrom(JSON.parse(entry.toString('utf8')));
}
export async function applyPack(
  root: string,
  instance: Instance,
  index: PackIndex,
  files: PackFile[],
  archive: string,
  report: (message: string) => void,
): Promise<void> {
  const game = gameDirectory(root, instance);
  const recordFile = path.join(root, 'instances', instance.id, 'comet-pack.json');
  let previous: PackRecord = { files: [] };
  try {
    previous = JSON.parse(await readFile(recordFile, 'utf8')) as PackRecord;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
  }
  await mkdir(game, { recursive: true });
  let done = 0;
  await parallel(files, 12, async file => {
    await download(file, inside(game, file.path), packHosts, 'follow');
    done++;
    report(`Verifying pack files ${done} / ${files.length}`);
  });
  for (const stale of stalePaths(
    previous.files,
    files.map(file => file.path),
  ))
    await unlink(inside(game, stale)).catch(() => undefined);
  if (previous.versionId !== index.versionId) {
    report('Applying pack overrides');
    await extractZip(archive, game, 'overrides/');
    await extractZip(archive, game, 'client-overrides/');
  }
  const target = packTarget(index);
  await writeFile(
    recordFile,
    JSON.stringify(
      {
        versionId: index.versionId,
        loader: target.loader,
        loaderVersion: target.loaderVersion,
        files: files.map(file => file.path),
      },
      null,
      2,
    ),
  );
  report(`${index.name} ${index.versionId} is ready`);
}
