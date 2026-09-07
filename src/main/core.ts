import path from 'node:path';
import type { Instance, Settings } from '../shared.js';

export const instances: Instance[] = [
  { id: 'pvp-1.8.9', name: 'Classic combat', profile: 'pvp', version: '1.8.9' },
  { id: 'pvp-1.7.10', name: 'Legacy combat', profile: 'pvp', version: '1.7.10' },
  { id: 'mcsr-1.16.1', name: 'Speedrunning', profile: 'mcsr', version: '1.16.1' },
];
export const defaults: Settings = { clientId: '', javaPath: '', memoryMb: 4096, minimizeOnLaunch: true };

export function record(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('Expected an object.');
  return value as Record<string, unknown>;
}
export function text(value: unknown): string {
  if (typeof value !== 'string' || !value) throw new Error('Expected a non-empty string.');
  return value;
}
export function settingsFrom(value: unknown): Settings {
  const s = record(value);
  if (typeof s.clientId !== 'string' || (s.clientId !== '' && !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(s.clientId))) throw new Error('Client ID must be an application UUID.');
  if (typeof s.javaPath !== 'string' || (s.javaPath !== '' && (!path.isAbsolute(s.javaPath) || !/java(?:w)?\.exe$/i.test(s.javaPath)))) throw new Error('Choose an absolute path to java.exe.');
  if (!Number.isInteger(s.memoryMb) || Number(s.memoryMb) < 1024 || Number(s.memoryMb) > 16384) throw new Error('Memory must be between 1024 and 16384 MB.');
  if (typeof s.minimizeOnLaunch !== 'boolean') throw new Error('Invalid minimize setting.');
  return { clientId: s.clientId, javaPath: s.javaPath, memoryMb: Number(s.memoryMb), minimizeOnLaunch: s.minimizeOnLaunch };
}
export function inside(root: string, relative: string): string {
  if (!relative || relative.includes('\\') || relative.includes(':') || relative.startsWith('/') || relative.split('/').some(p => p === '..' || p === '.' || !p)) throw new Error('Unsafe artifact path.');
  return path.join(root, ...relative.split('/'));
}
export interface Rule {
  action: 'allow' | 'disallow';
  os?: { name?: string; arch?: string; version?: string };
  features?: Record<string, boolean>;
}
export function allowed(rules: Rule[] | undefined, osVersion: string): boolean {
  if (!rules) return true;
  let result = false;
  for (const rule of rules) {
    const matches = (!rule.os?.name || rule.os.name === 'windows')
      && (!rule.os?.arch || ['x86_64', 'amd64'].includes(rule.os.arch))
      && (!rule.os?.version || new RegExp(rule.os.version).test(osVersion))
      && Object.values(rule.features ?? {}).every(value => value === false);
    if (matches) result = rule.action === 'allow';
  }
  return result;
}
export type Argument = string | { rules: Rule[]; value: string | string[] };
export function expand(args: Argument[], values: Record<string, string>, osVersion: string): string[] {
  return args.flatMap(arg => typeof arg === 'string' ? [arg] : allowed(arg.rules, osVersion) ? [arg.value].flat() : [])
    .map(arg => arg.replace(/\$\{([^}]+)\}/g, (_, key: string) => {
      if (!(key in values)) throw new Error(`Unsupported launch argument: ${key}`);
      return values[key];
    }));
}
export function prismFiles(instance: Instance): { config: string; pack: string } {
  return {
    config: `[General]\nConfigVersion=1.2\nInstanceType=OneSix\nname=${instance.name}\niconKey=default\n`,
    pack: JSON.stringify({ formatVersion: 1, components: [{ uid: 'net.minecraft', version: instance.version, important: true }] }, null, 2),
  };
}
export function redact(line: string, secrets: string[]): string {
  let result = line;
  for (const secret of secrets) if (secret) result = result.split(secret).join('[redacted]');
  return result.replace(/(--accessToken[=\s]+)\S+/gi, '$1[redacted]');
}
