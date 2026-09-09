import path from 'node:path';
import os from 'node:os';
import type { Draft, Instance, LoaderKind, Pack, Settings } from '../shared.js';

export const instances: Instance[] = [
  { id: 'pvp-1.8.9', name: 'Classic combat', profile: 'pvp', version: '1.8.9', loader: 'vanilla', directory: 'comet' },
  { id: 'pvp-1.7.10', name: 'Legacy combat', profile: 'pvp', version: '1.7.10', loader: 'vanilla', directory: 'comet' },
  {
    id: 'mcsr-1.16.1',
    name: 'Speedrunning',
    profile: 'mcsr',
    version: '1.16.1',
    loader: 'fabric',
    directory: 'isolated',
    pack: { source: 'mcsr', name: 'MCSR Ranked RSG pack' },
  },
];
export const defaults: Settings = { clientId: '', javaPath: '', memoryMb: 4096, minimizeOnLaunch: true };
export const versionPattern = /^[\w.+-]{1,40}$/;
const idPattern = /^[a-z0-9][a-z0-9-]{0,40}$/;
const loaders: readonly LoaderKind[] = ['vanilla', 'fabric', 'quilt'];

export function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Expected an object.');
  return value as Record<string, unknown>;
}
export function text(value: unknown): string {
  if (typeof value !== 'string' || !value) throw new Error('Expected a non-empty string.');
  return value;
}
export interface Platform {
  os: 'windows' | 'osx' | 'linux';
  arch: 'x86_64' | 'aarch64';
  version: string;
}
export function platform(): Platform {
  const names = { win32: 'windows', darwin: 'osx', linux: 'linux' } as const;
  const os_ = names[process.platform as keyof typeof names];
  const arch = process.arch === 'x64' ? 'x86_64' : process.arch === 'arm64' ? 'aarch64' : undefined;
  if (!os_ || !arch || (arch === 'aarch64' && os_ !== 'osx'))
    throw new Error('This build supports Windows x64, Linux x64 and macOS only.');
  return { os: os_, arch, version: os.release() };
}
export function javaExecutable(javaPath: string, gui = false, os = platform().os): string {
  const name = gui && os === 'windows' ? 'javaw' : 'java';
  return javaPath.replace(/javaw?(\.exe)?$/i, (_, exe?: string) => name + (exe ?? ''));
}
export function officialDirectory(os: Platform['os'], home: string, appData: string): string {
  if (os === 'windows') return path.join(appData, '.minecraft');
  if (os === 'osx') return path.join(home, 'Library', 'Application Support', 'minecraft');
  return path.join(home, '.minecraft');
}
function oneOf<T extends string>(value: unknown, options: readonly T[], label: string): T {
  if (typeof value !== 'string' || !options.includes(value as T)) throw new Error(`Invalid instance ${label}.`);
  return value as T;
}
function version(value: unknown, label: string): string {
  if (typeof value !== 'string' || !versionPattern.test(value)) throw new Error(`Invalid ${label} version.`);
  return value;
}
export function cleanName(value: unknown, fallback = 'Instance'): string {
  const name =
    typeof value === 'string'
      ? value
          .replace(/[\p{Cc}]+/gu, ' ')
          .trim()
          .slice(0, 80)
      : '';
  return name || fallback;
}
export function instanceId(name: string, taken: Iterable<string>): string {
  const existing = new Set(taken);
  const base =
    name
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, 32) || 'instance';
  let id = base;
  for (let n = 2; existing.has(id); n++) id = `${base}-${n}`;
  return id;
}
export function draftFrom(value: unknown): Draft {
  const s = record(value);
  const loader = oneOf(s.loader, loaders, 'loader');
  const draft: Draft = {
    name: cleanName(s.name, ''),
    version: version(s.version, 'Minecraft'),
    loader,
    directory: oneOf(s.directory, ['isolated', 'official'] as const, 'folder'),
  };
  if (!draft.name) throw new Error('Give the instance a name.');
  if (loader !== 'vanilla') draft.loaderVersion = version(s.loaderVersion, 'loader');
  return draft;
}
export function instanceFrom(value: unknown, folder: string): Instance {
  const s = record(value);
  const id = text(s.id);
  if (id !== folder || !idPattern.test(id)) throw new Error('Instance id does not match its folder.');
  const instance: Instance = {
    id,
    name: cleanName(s.name),
    profile: oneOf(s.profile, ['pvp', 'mcsr', 'custom'] as const, 'profile'),
    version: version(s.version, 'Minecraft'),
    loader: oneOf(s.loader, loaders, 'loader'),
    directory: oneOf(s.directory, ['isolated', 'comet', 'official'] as const, 'folder'),
  };
  if (s.loaderVersion !== undefined) instance.loaderVersion = version(s.loaderVersion, 'loader');
  if (s.pack !== undefined) {
    const p = record(s.pack);
    if (instance.directory !== 'isolated') throw new Error('Pack instances must use their own folder.');
    const pack: Pack = {
      source: oneOf(p.source, ['mcsr', 'modrinth', 'import'] as const, 'pack'),
      name: cleanName(p.name),
    };
    if (p.versionId !== undefined) pack.versionId = text(p.versionId).slice(0, 80);
    if (p.projectId !== undefined) pack.projectId = text(p.projectId).slice(0, 80);
    instance.pack = pack;
  }
  return instance;
}
export function settingsFrom(value: unknown): Settings {
  const s = record(value);
  if (
    typeof s.clientId !== 'string' ||
    (s.clientId !== '' && !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(s.clientId))
  )
    throw new Error('Client ID must be an application UUID.');
  if (
    typeof s.javaPath !== 'string' ||
    (s.javaPath !== '' && (!path.isAbsolute(s.javaPath) || !/(^|[\\/])javaw?(\.exe)?$/i.test(s.javaPath)))
  )
    throw new Error('Choose an absolute path to a java executable.');
  if (!Number.isInteger(s.memoryMb) || Number(s.memoryMb) < 1024 || Number(s.memoryMb) > 16384)
    throw new Error('Memory must be between 1024 and 16384 MB.');
  if (typeof s.minimizeOnLaunch !== 'boolean') throw new Error('Invalid minimize setting.');
  return {
    clientId: s.clientId,
    javaPath: s.javaPath,
    memoryMb: Number(s.memoryMb),
    minimizeOnLaunch: s.minimizeOnLaunch,
  };
}
export function inside(root: string, relative: string): string {
  if (
    !relative ||
    relative.includes('\\') ||
    relative.includes(':') ||
    relative.startsWith('/') ||
    relative.split('/').some(p => p === '..' || p === '.' || !p)
  )
    throw new Error('Unsafe artifact path.');
  return path.join(root, ...relative.split('/'));
}
export interface Rule {
  action: 'allow' | 'disallow';
  os?: { name?: string; arch?: string; version?: string };
  features?: Record<string, boolean>;
}
export function allowed(rules: Rule[] | undefined, target: Platform): boolean {
  if (!rules) return true;
  let result = false;
  const arches = target.arch === 'x86_64' ? ['x86_64', 'amd64'] : ['aarch64', 'arm64'];
  for (const rule of rules) {
    const matches =
      (!rule.os?.name || rule.os.name === target.os) &&
      (!rule.os?.arch || arches.includes(rule.os.arch)) &&
      (!rule.os?.version || new RegExp(rule.os.version).test(target.version)) &&
      Object.values(rule.features ?? {}).every(value => value === false);
    if (matches) result = rule.action === 'allow';
  }
  return result;
}
export type Argument = string | { rules: Rule[]; value: string | string[] };
export function expand(args: Argument[], values: Record<string, string>, target: Platform): string[] {
  return args
    .flatMap(arg => (typeof arg === 'string' ? [arg] : allowed(arg.rules, target) ? [arg.value].flat() : []))
    .map(arg =>
      arg.replace(/\$\{([^}]+)\}/g, (_, key: string) => {
        if (!(key in values)) throw new Error(`Unsupported launch argument: ${key}`);
        return values[key];
      }),
    );
}
export function prismFiles(instance: Instance): { config: string; pack: string } {
  const uids = { fabric: 'net.fabricmc.fabric-loader', quilt: 'org.quiltmc.quilt-loader' };
  const components: { uid: string; version: string; important?: boolean }[] = [
    { uid: 'net.minecraft', version: instance.version, important: true },
  ];
  if (instance.loader !== 'vanilla' && instance.loaderVersion)
    components.push({ uid: uids[instance.loader], version: instance.loaderVersion });
  return {
    config: `[General]\nConfigVersion=1.2\nInstanceType=OneSix\nname=${instance.name}\niconKey=default\n`,
    pack: JSON.stringify({ formatVersion: 1, components }, null, 2),
  };
}
export function redact(line: string, secrets: string[]): string {
  let result = line;
  for (const secret of secrets) if (secret) result = result.split(secret).join('[redacted]');
  return result.replace(/(--accessToken[=\s]+)\S+/gi, '$1[redacted]');
}
