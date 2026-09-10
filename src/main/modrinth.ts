import { randomUUID } from 'node:crypto';
import { rm } from 'node:fs/promises';
import path from 'node:path';
import { cleanName, record, text } from './core.js';
import { download, json, modrinthHosts, parallel, remoteBytes, trustedUrl } from './net.js';
import { importArchive } from './imports.js';
import type { Instance, ModrinthHit, ModrinthVersion } from '../shared.js';

const api = 'https://api.modrinth.com/v2';
const known = ['fabric', 'quilt', 'forge', 'neoforge'];
const iconLimit = 200 * 1024;
export interface VersionFile extends ModrinthVersion {
  file: { url: string; sha1: string; size: number };
}

export function modrinthId(value: unknown): string {
  if (typeof value !== 'string' || !/^[A-Za-z0-9]{1,16}$/.test(value)) throw new Error('Invalid Modrinth id.');
  return value;
}
function strings(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
}
function optionalUrl(value: unknown): string | null {
  return typeof value === 'string' && value ? value : null;
}
export function hitsFrom(value: unknown): { hits: ModrinthHit[]; total: number } {
  const s = record(value);
  if (!Array.isArray(s.hits)) throw new Error('Unexpected Modrinth response.');
  return {
    total: typeof s.total_hits === 'number' ? s.total_hits : 0,
    hits: s.hits.map((item: unknown) => {
      const hit = record(item);
      return {
        projectId: modrinthId(hit.project_id),
        slug: cleanName(hit.slug),
        title: cleanName(hit.title),
        description: cleanName(hit.description, '').slice(0, 200),
        icon: optionalUrl(hit.icon_url),
        downloads: typeof hit.downloads === 'number' ? hit.downloads : 0,
        loaders: strings(hit.categories).filter(category => known.includes(category)),
      };
    }),
  };
}
export function versionsFrom(value: unknown): VersionFile[] {
  if (!Array.isArray(value)) throw new Error('Unexpected Modrinth response.');
  const result: VersionFile[] = [];
  for (const item of value) {
    const version = record(item);
    const files = Array.isArray(version.files) ? version.files.map((file: unknown) => record(file)) : [];
    const file = files.find(entry => entry.primary === true) ?? files[0];
    if (!file || typeof file.url !== 'string' || !/\.mrpack$/i.test(text(file.filename))) continue;
    const loaders = strings(version.loaders);
    result.push({
      id: modrinthId(version.id),
      name: cleanName(version.name),
      versionNumber: cleanName(version.version_number),
      gameVersions: strings(version.game_versions),
      loaders,
      datePublished: typeof version.date_published === 'string' ? version.date_published : '',
      supported: loaders.length > 0 && loaders.every(loader => loader === 'fabric' || loader === 'quilt'),
      file: {
        url: file.url,
        sha1: text(record(file.hashes).sha1),
        size: typeof file.size === 'number' ? file.size : 0,
      },
    });
  }
  return result;
}
export function imageType(bytes: Buffer): string | null {
  if (bytes.subarray(0, 4).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47]))) return 'image/png';
  if (bytes.subarray(0, 3).equals(Buffer.from([0xff, 0xd8, 0xff]))) return 'image/jpeg';
  if (bytes.subarray(0, 4).toString('latin1') === 'GIF8') return 'image/gif';
  if (bytes.subarray(0, 4).toString('latin1') === 'RIFF' && bytes.subarray(8, 12).toString('latin1') === 'WEBP')
    return 'image/webp';
  return null;
}
async function iconBytes(url: string | null): Promise<Buffer | null> {
  if (!url) return null;
  try {
    const bytes = Buffer.from(await remoteBytes(trustedUrl(url, ['cdn.modrinth.com']), iconLimit));
    return imageType(bytes) ? bytes : null;
  } catch {
    return null;
  }
}
async function iconData(url: string | null): Promise<string | null> {
  const bytes = await iconBytes(url);
  return bytes ? `data:${imageType(bytes)};base64,${bytes.toString('base64')}` : null;
}
export async function searchModpacks(query: string, offset: number): Promise<{ hits: ModrinthHit[]; total: number }> {
  const parameters = new URLSearchParams({
    query,
    facets: '[["project_type:modpack"]]',
    limit: '20',
    offset: String(offset),
    index: 'relevance',
  });
  const result = hitsFrom(await json(trustedUrl(`${api}/search?${parameters}`, modrinthHosts)));
  await parallel(result.hits, 6, async hit => {
    hit.icon = await iconData(hit.icon);
  });
  return result;
}
async function versions(projectId: string): Promise<VersionFile[]> {
  return versionsFrom(await json(trustedUrl(`${api}/project/${modrinthId(projectId)}/version`, modrinthHosts)));
}
export async function projectVersions(projectId: string): Promise<ModrinthVersion[]> {
  return (await versions(projectId)).map(({ file: _file, ...version }) => version);
}
export async function installModrinth(
  root: string,
  existing: Instance[],
  projectId: string,
  versionId: string,
  report: (message: string) => void,
): Promise<Instance> {
  const version = (await versions(projectId)).find(item => item.id === modrinthId(versionId));
  if (!version) throw new Error('Unknown modpack version.');
  if (!version.supported) throw new Error('Comet installs Fabric and Quilt modpacks only.');
  const project = record(await json(trustedUrl(`${api}/project/${modrinthId(projectId)}`, modrinthHosts)));
  const title = cleanName(project.title, 'Modrinth modpack');
  const archive = path.join(root, 'cache', `import-${randomUUID()}.mrpack`);
  try {
    report(`Downloading ${title} ${version.versionNumber}`);
    await download(version.file, archive, ['cdn.modrinth.com']);
    const icon = await iconBytes(optionalUrl(project.icon_url));
    return await importArchive(root, existing, archive, report, {
      name: title,
      pack: { source: 'modrinth', name: title, projectId, versionId: version.id },
      icon: icon ?? undefined,
    });
  } finally {
    await rm(archive, { force: true });
  }
}
