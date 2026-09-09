import path from 'node:path';
import { inside, record, text, versionPattern } from './core.js';
import { download, json, loaderHosts, remoteText, trustedUrl } from './net.js';
import type { Installation } from './minecraft.js';
import type { LoaderVersion } from '../shared.js';

export type Loader = 'fabric' | 'quilt';
export const loaderNames = { vanilla: 'Vanilla', fabric: 'Fabric', quilt: 'Quilt' };
const meta = {
  fabric: 'https://meta.fabricmc.net/v2/versions/loader',
  quilt: 'https://meta.quiltmc.org/v3/versions/loader',
};
interface Profile {
  mainClass: string;
  arguments?: { jvm?: string[]; game?: string[] };
  libraries: { name: string; url: string; sha1?: string; size?: number }[];
}
export interface LoaderProfile {
  mainClass: string;
  jvm: string[];
  game: string[];
  classpath: string[];
}

export function mavenPath(name: string): string {
  const [group, artifact, version, classifier] = name.split(':');
  if (!group || !artifact || !version || name.split(':').length > 4)
    throw new Error(`Invalid Maven coordinate: ${name}`);
  return `${group.replace(/\./g, '/')}/${artifact}/${version}/${artifact}-${version}${classifier ? `-${classifier}` : ''}.jar`;
}
export function metaUrl(kind: Loader, game: string, loader?: string): string {
  if (!versionPattern.test(game) || (loader !== undefined && !versionPattern.test(loader)))
    throw new Error('Invalid version.');
  return loader ? `${meta[kind]}/${game}/${loader}/profile/json` : `${meta[kind]}/${game}`;
}
export function loaderVersionsFrom(value: unknown): LoaderVersion[] {
  if (!Array.isArray(value)) throw new Error('Unexpected loader metadata.');
  return value.map(item => {
    const loader = record(record(item).loader);
    const version = text(loader.version);
    if (!versionPattern.test(version)) throw new Error('Unexpected loader metadata.');
    return { version, stable: loader.stable === true || (loader.stable === undefined && !version.includes('-')) };
  });
}
export async function loaderVersions(kind: Loader, game: string): Promise<LoaderVersion[]> {
  return loaderVersionsFrom(await json(metaUrl(kind, game)));
}
async function library(root: string, item: Profile['libraries'][number]): Promise<string> {
  const relative = mavenPath(item.name);
  const url = trustedUrl(new URL(relative, item.url).href, loaderHosts);
  const sha1 = item.sha1 ?? (await remoteText(`${url}.sha1`)).trim();
  const destination = inside(path.join(root, 'cache', 'libraries'), relative);
  await download({ url, sha1, size: item.size }, destination, loaderHosts);
  return destination;
}
export async function installLoader(
  root: string,
  kind: Loader,
  game: string,
  loader: string,
  report: (message: string) => void,
): Promise<LoaderProfile> {
  report(`Resolving ${loaderNames[kind]} loader ${loader}`);
  const profile = (await json(metaUrl(kind, game, loader))) as Profile;
  if (!/^[\w.$]+$/.test(profile.mainClass) || !Array.isArray(profile.libraries))
    throw new Error('Unexpected loader metadata.');
  const classpath: string[] = [];
  for (const item of profile.libraries) {
    report(`Verifying ${item.name}`);
    classpath.push(await library(root, item));
  }
  return {
    mainClass: profile.mainClass,
    jvm: profile.arguments?.jvm ?? [],
    game: profile.arguments?.game ?? [],
    classpath,
  };
}
export function mergeLoader(installation: Installation, profile: LoaderProfile): Installation {
  const { metadata } = installation;
  const vanilla = metadata.arguments ?? {
    jvm: ['-Djava.library.path=${natives_directory}', '-cp', '${classpath}'],
    game: metadata.minecraftArguments?.split(/\s+/) ?? [],
  };
  return {
    ...installation,
    classpath: [...profile.classpath, ...installation.classpath],
    metadata: {
      ...metadata,
      mainClass: profile.mainClass,
      arguments: { jvm: [...profile.jvm, ...vanilla.jvm], game: [...vanilla.game, ...profile.game] },
    },
  };
}
