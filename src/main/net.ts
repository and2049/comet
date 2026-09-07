import { createHash, randomUUID } from 'node:crypto';
import { createReadStream, createWriteStream } from 'node:fs';
import { mkdir, rename, rm, stat } from 'node:fs/promises';
import path from 'node:path';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';

export function officialUrl(value: string): string {
  const url = new URL(value);
  if (url.protocol !== 'https:' || url.username || url.password || url.port || ![
    'piston-meta.mojang.com', 'piston-data.mojang.com', 'launchermeta.mojang.com',
    'launcher.mojang.com', 'libraries.minecraft.net', 'resources.download.minecraft.net',
  ].includes(url.hostname)) throw new Error('Untrusted Minecraft download URL.');
  return url.href;
}
export async function json(url: string, init?: RequestInit): Promise<unknown> {
  const timeout = AbortSignal.timeout(30000);
  const response = await fetch(url, { ...init, redirect: 'error', signal: init?.signal ? AbortSignal.any([init.signal, timeout]) : timeout });
  if (!response.ok) throw new Error(`${new URL(url).hostname}: HTTP ${response.status}${response.status === 403 && url.includes('minecraftservices') ? ' — application approval or account permissions may be required.' : ''}`);
  return response.json();
}
export async function sha1(file: string): Promise<string> {
  const hash = createHash('sha1');
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest('hex');
}
export interface Download { url: string; sha1: string; size?: number; path?: string }
const inflight = new Map<string, { hash: string; promise: Promise<void> }>();
export async function download(item: Download, destination: string): Promise<void> {
  const key = path.resolve(destination).toLowerCase();
  const existing = inflight.get(key);
  if (existing) {
    if (existing.hash !== item.sha1) throw new Error('Conflicting artifact checksums for the same path.');
    return existing.promise;
  }
  const promise = downloadFile(item, destination);
  inflight.set(key, { hash: item.sha1, promise });
  try { await promise; }
  finally { inflight.delete(key); }
}
async function downloadFile(item: Download, destination: string): Promise<void> {
  officialUrl(item.url);
  if (!/^[a-f0-9]{40}$/.test(item.sha1)) throw new Error('Missing artifact checksum.');
  try {
    if ((item.size === undefined || (await stat(destination)).size === item.size) && await sha1(destination) === item.sha1) return;
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
  }
  await mkdir(path.dirname(destination), { recursive: true });
  const temporary = `${destination}.${randomUUID()}.part`;
  try {
    const response = await fetch(item.url, { redirect: 'error', signal: AbortSignal.timeout(180000) });
    if (!response.ok || !response.body) throw new Error(`Download failed: HTTP ${response.status}`);
    await pipeline(Readable.fromWeb(response.body as import('node:stream/web').ReadableStream), createWriteStream(temporary, { flags: 'wx' }));
    if ((item.size !== undefined && (await stat(temporary)).size !== item.size) || await sha1(temporary) !== item.sha1) throw new Error('Artifact checksum mismatch. Retry installation.');
    await rename(temporary, destination);
  } finally {
    await rm(temporary, { force: true });
  }
}
