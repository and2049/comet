import { mkdtemp, readFile, rm, unlink, writeFile } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { inside, platform, type Platform } from './core.js';
import { download, fabricHosts, json, packHosts, remoteBytes, remoteText, trustedUrl, type Download } from './net.js';
import { gameDirectory, type Installation } from './minecraft.js';
import { extractZip } from './zip.js';
import type { Instance } from '../shared.js';

export function packUrl(target: Platform): string {
  const names = { windows: 'Windows', osx: 'OSX', linux: 'Linux' };
  return `https://redlime.github.io/MCSRMods/modpacks/v4/MCSRRanked-${names[target.os]}-1.16.1-RSG.mrpack`;
}
export interface PackIndex {
  formatVersion: number;
  game: string;
  versionId: string;
  name: string;
  dependencies: Record<string, string>;
  files: { path: string; hashes: { sha1: string }; env?: { client?: string }; downloads: string[]; fileSize: number }[];
}
export interface PackFile extends Download { path: string }
interface FabricProfile {
  mainClass: string;
  arguments?: { jvm?: string[]; game?: string[] };
  libraries: { name: string; url: string; sha1?: string; size?: number }[];
}
export interface Fabric { mainClass: string; jvm: string[]; classpath: string[] }

export function mavenPath(name: string): string {
  const [group, artifact, version, classifier] = name.split(':');
  if (!group || !artifact || !version || name.split(':').length > 4) throw new Error(`Invalid Maven coordinate: ${name}`);
  return `${group.replace(/\./g, '/')}/${artifact}/${version}/${artifact}-${version}${classifier ? `-${classifier}` : ''}.jar`;
}
export function rawUrl(value: string): string {
  const match = /^https:\/\/github\.com\/([^/]+)\/([^/]+)\/raw\/(.+)$/.exec(value);
  return trustedUrl(match ? `https://raw.githubusercontent.com/${match[1]}/${match[2]}/${match[3]}` : value, packHosts);
}
export function packFiles(index: PackIndex, version: string): PackFile[] {
  if (index.formatVersion !== 1 || index.game !== 'minecraft' || index.dependencies.minecraft !== version) throw new Error('The MCSR pack does not target this instance version.');
  if (!/^\d+\.\d+\.\d+$/.test(index.dependencies['fabric-loader'] ?? '')) throw new Error('The MCSR pack does not declare a Fabric loader version.');
  return index.files.filter(file => file.env?.client !== 'unsupported').map(file => {
    if (!file.path.startsWith('mods/') || !/^[\w.+-]+\.jar$/.test(file.path.slice(5))) throw new Error(`Unsupported pack file: ${file.path}`);
    if (file.downloads.length !== 1) throw new Error(`Expected one download for ${file.path}`);
    return { path: file.path, sha1: file.hashes.sha1, size: file.fileSize, url: rawUrl(file.downloads[0]) };
  });
}
export function stalePaths(previous: string[], current: string[]): string[] {
  return previous.filter(item => !current.includes(item));
}
async function library(root: string, item: FabricProfile['libraries'][number]): Promise<string> {
  const relative = mavenPath(item.name);
  const url = trustedUrl(new URL(relative, item.url).href, fabricHosts);
  const sha1 = item.sha1 ?? (await remoteText(`${url}.sha1`)).trim();
  const destination = inside(path.join(root, 'cache', 'libraries'), relative);
  await download({ url, sha1, size: item.size }, destination, fabricHosts);
  return destination;
}
export async function installFabric(root: string, version: string, loader: string, report: (message: string) => void): Promise<Fabric> {
  report(`Resolving Fabric loader ${loader}`);
  const profile = await json(`https://meta.fabricmc.net/v2/versions/loader/${version}/${loader}/profile/json`) as FabricProfile;
  if (!/^[\w.$]+$/.test(profile.mainClass)) throw new Error('Unexpected Fabric metadata.');
  const classpath: string[] = [];
  for (const item of profile.libraries) {
    report(`Verifying ${item.name}`);
    classpath.push(await library(root, item));
  }
  return { mainClass: profile.mainClass, jvm: profile.arguments?.jvm ?? [], classpath };
}
export async function readPack(): Promise<PackIndex> {
  const staging = await mkdtemp(path.join(os.tmpdir(), 'comet-pack-'));
  try {
    const archive = path.join(staging, 'pack.mrpack');
    await writeFile(archive, Buffer.from(await remoteBytes(trustedUrl(packUrl(platform()), packHosts))));
    await extractZip(archive, staging);
    return JSON.parse(await readFile(path.join(staging, 'modrinth.index.json'), 'utf8')) as PackIndex;
  } finally {
    await rm(staging, { recursive: true, force: true });
  }
}
export async function installPack(root: string, instance: Instance, report: (message: string) => void): Promise<string> {
  report('Fetching the MCSR Ranked pack');
  const index = await readPack();
  const files = packFiles(index, instance.version);
  const game = gameDirectory(root, instance);
  const record = path.join(root, 'instances', instance.id, 'comet-pack.json');
  let previous: string[] = [];
  try { previous = (JSON.parse(await readFile(record, 'utf8')) as { files: string[] }).files; }
  catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error; }
  for (const file of files) {
    report(`Verifying ${path.basename(file.path)}`);
    await download(file, inside(game, file.path), packHosts);
  }
  for (const stale of stalePaths(previous, files.map(file => file.path))) await unlink(inside(game, stale)).catch(() => undefined);
  await writeFile(record, JSON.stringify({ versionId: index.versionId, loader: index.dependencies['fabric-loader'], files: files.map(file => file.path) }, null, 2));
  report(`${index.name} ${index.versionId} is ready`);
  return index.dependencies['fabric-loader'];
}
export function mergeFabric(installation: Installation, fabric: Fabric): Installation {
  const { metadata } = installation;
  return {
    ...installation,
    classpath: [...fabric.classpath, ...installation.classpath],
    metadata: { ...metadata, mainClass: fabric.mainClass, arguments: { jvm: [...fabric.jvm, ...(metadata.arguments?.jvm ?? [])], game: metadata.arguments?.game ?? [] } },
  };
}
export async function withFabric(root: string, instance: Instance, installation: Installation, report: (message: string) => void): Promise<Installation> {
  const loader = await installPack(root, instance, report);
  return mergeFabric(installation, await installFabric(root, instance.version, loader, report));
}
