import { copyFile, mkdir, stat } from 'node:fs/promises';
import path from 'node:path';
import { inside, record, text, versionPattern } from './core.js';
import { download, json, mojangHosts, optifineHosts, releaseHosts, remoteText, sha1, trustedUrl } from './net.js';
import { mavenPath, vanillaArguments } from './loader.js';
import type { Installation } from './minecraft.js';
import { optifineLabels } from '../shared.js';

export const launcherVersion = '0.1.0';
export const manifestUrl = 'https://github.com/and2049/comet/releases/latest/download/client.json';
export const tweakClasses = { optifine: 'optifine.OptiFineTweaker', comet: 'comet.launch.CometTweaker' };
export const optifineBuilds: Record<string, { file: string; sha1: string; size: number }> = {
  '1.8.9': { file: 'OptiFine_1.8.9_HD_U_M5.jar', sha1: 'd362d58a28f5373b141b9e426e8e160638bfafcd', size: 2585014 },
  '1.7.10': { file: 'OptiFine_1.7.10_HD_U_E7.jar', sha1: '2130d7266224da6a7325051e37444f7e7c19277c', size: 1774165 },
};
const launchwrapper = {
  name: 'net.minecraft:launchwrapper:1.12',
  url: 'https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar',
  sha1: '111e7bea9c968cdb3d06ef4632bf7ff0824d0f36',
  size: 32999,
};
export interface ClientFile {
  url: string;
  sha1: string;
  size: number;
}
export interface ClientManifest {
  launcher: string;
  clients: Record<string, ClientFile>;
}
export interface ClientParts {
  launchwrapper: string;
  optifine?: string;
  client?: string;
  warnings: string[];
}
const semver = /^\d+\.\d+\.\d+$/;

function build(version: string): (typeof optifineBuilds)[string] {
  const item = optifineBuilds[version];
  if (!item) throw new Error(`OptiFine is not pinned for Minecraft ${version}.`);
  return item;
}
export function optifineLink(page: string, file: string): string {
  const match = new RegExp(`downloadx\\?f=${file.replace(/[.]/g, '\\.')}&x=([0-9a-f]{32})`).exec(page);
  if (!match) throw new Error('optifine.net did not offer a download link.');
  return `https://optifine.net/downloadx?f=${file}&x=${match[1]}`;
}
export function optifinePath(root: string, version: string): string {
  return path.join(root, 'optifine', build(version).file);
}
async function matches(file: string, item: { sha1: string; size: number }): Promise<boolean> {
  try {
    return (await stat(file)).size === item.size && (await sha1(file)) === item.sha1;
  } catch {
    return false;
  }
}
export async function fetchOptifine(root: string, version: string, report: (message: string) => void): Promise<string> {
  const item = build(version);
  const destination = optifinePath(root, version);
  if (await matches(destination, item)) return destination;
  report(`Fetching OptiFine ${optifineLabels[version]} from optifine.net`);
  const page = await remoteText(trustedUrl(`https://optifine.net/adloadx?f=${item.file}`, optifineHosts));
  await download({ ...item, url: optifineLink(page, item.file) }, destination, optifineHosts);
  return destination;
}
export async function addOptifineFile(root: string, version: string, source: string): Promise<string> {
  const item = build(version);
  if (!(await matches(source, item)))
    throw new Error(`This is not OptiFine ${optifineLabels[version]} for ${version} (${item.file}).`);
  const destination = optifinePath(root, version);
  await mkdir(path.dirname(destination), { recursive: true });
  await copyFile(source, destination);
  return destination;
}
export function newerLauncher(required: string, current = launcherVersion): boolean {
  const [a, b] = [required, current].map(value => value.split('.').map(Number));
  for (let i = 0; i < 3; i++) if (a[i] !== b[i]) return a[i] > b[i];
  return false;
}
export function clientFrom(value: unknown): ClientManifest {
  const s = record(value);
  const launcher = text(s.launcher);
  if (!semver.test(launcher)) throw new Error('Unexpected client manifest.');
  const clients: Record<string, ClientFile> = {};
  for (const [version, entry] of Object.entries(record(s.clients))) {
    const e = record(entry);
    const file = { url: trustedUrl(text(e.url), releaseHosts), sha1: text(e.sha1), size: e.size };
    if (!versionPattern.test(version) || !/^[a-f0-9]{40}$/.test(file.sha1) || !Number.isInteger(file.size))
      throw new Error('Unexpected client manifest.');
    clients[version] = { ...file, size: file.size as number };
  }
  return { launcher, clients };
}
export function clientPath(root: string, version: string): string {
  if (!versionPattern.test(version)) throw new Error('Invalid version.');
  return inside(path.join(root, 'client'), `comet-client-${version}.jar`);
}
export async function devClient(devDir: string | undefined, version: string): Promise<string | undefined> {
  if (!devDir) return undefined;
  const file = path.join(devDir, `comet-client-${version}.jar`);
  return (await stat(file).catch(() => null))?.isFile() ? file : undefined;
}
export async function fetchClient(
  root: string,
  version: string,
  report: (message: string) => void,
  devDir?: string,
): Promise<string> {
  const dev = await devClient(devDir, version);
  if (dev) return dev;
  const destination = clientPath(root, version);
  const cached = (await stat(destination).catch(() => null))?.isFile();
  let manifest: ClientManifest;
  try {
    report('Checking for Comet client updates');
    manifest = clientFrom(await json(trustedUrl(manifestUrl, releaseHosts), undefined, 'follow'));
  } catch (error) {
    if (cached) {
      report('Comet client update check failed; using the cached client.');
      return destination;
    }
    throw error;
  }
  const item = manifest.clients[version];
  if (!item) throw new Error(`No Comet client is published for Minecraft ${version}.`);
  if (newerLauncher(manifest.launcher)) {
    if (cached) {
      report(`Comet ${manifest.launcher} is required for the latest client; using the cached client.`);
      return destination;
    }
    throw new Error(`Update Comet to ${manifest.launcher} to get the Comet client.`);
  }
  report(`Verifying Comet client for ${version}`);
  await download(item, destination, releaseHosts, 'follow');
  return destination;
}
export async function installLaunchwrapper(root: string, report: (message: string) => void): Promise<string> {
  report(`Verifying ${launchwrapper.name}`);
  const destination = inside(path.join(root, 'cache', 'libraries'), mavenPath(launchwrapper.name));
  await download(launchwrapper, destination, mojangHosts);
  return destination;
}
export async function prepareClient(
  root: string,
  version: string,
  report: (message: string) => void,
  devDir?: string,
): Promise<ClientParts> {
  const parts: ClientParts = { launchwrapper: await installLaunchwrapper(root, report), warnings: [] };
  try {
    parts.optifine = await fetchOptifine(root, version, report);
  } catch (error) {
    parts.warnings.push(
      `OptiFine could not be downloaded from optifine.net (${error instanceof Error ? error.message : 'unknown error'}); launching without it. Add the jar from optifine.net through the instance page.`,
    );
  }
  try {
    parts.client = await fetchClient(root, version, report, devDir);
  } catch (error) {
    parts.warnings.push(
      `The Comet client is unavailable (${error instanceof Error ? error.message : 'unknown error'}); launching without it.`,
    );
  }
  return parts;
}
export function mergeClient(installation: Installation, parts: Omit<ClientParts, 'warnings'>): Installation {
  if (!parts.client && !parts.optifine) return installation;
  const { metadata } = installation;
  const vanilla = vanillaArguments(metadata);
  const tweaks = ['--tweakClass', parts.client ? tweakClasses.comet : tweakClasses.optifine];
  return {
    ...installation,
    classpath: [
      ...(parts.client ? [parts.client] : []),
      ...(parts.optifine ? [parts.optifine] : []),
      parts.launchwrapper,
      ...installation.classpath,
    ],
    metadata: {
      ...metadata,
      mainClass: 'net.minecraft.launchwrapper.Launch',
      arguments: { jvm: vanilla.jvm, game: [...vanilla.game, ...tweaks] },
    },
  };
}
export async function availability(
  root: string,
  versions: string[],
  devDir?: string,
): Promise<{ optifine: string[]; client: string[] }> {
  const optifine: string[] = [];
  const client: string[] = [];
  for (const version of versions) {
    if (optifineBuilds[version] && (await matches(optifinePath(root, version), optifineBuilds[version])))
      optifine.push(version);
    if ((await devClient(devDir, version)) || (await stat(clientPath(root, version)).catch(() => null))?.isFile())
      client.push(version);
  }
  return { optifine, client };
}
