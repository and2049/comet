import { execFile, spawn, type ChildProcess } from 'node:child_process';
import { promisify } from 'node:util';
import { copyFile, mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import { allowed, expand, inside, type Argument, type Rule } from './core.js';
import { download, json, officialUrl, parallel, type Download } from './net.js';
import type { Instance, Settings } from '../shared.js';
import type { Session } from './auth.js';

const exec = promisify(execFile);
interface Library {
  name: string;
  rules?: Rule[];
  downloads: { artifact?: Download; classifiers?: Record<string, Download> };
  natives?: { windows?: string };
}
interface Metadata {
  id: string;
  type: string;
  mainClass: string;
  downloads: { client: Download };
  libraries: Library[];
  assetIndex: Download & { id: string };
  minecraftArguments?: string;
  arguments?: { jvm: Argument[]; game: Argument[] };
  logging?: { client: { argument: string; file: Download & { id: string } } };
  javaVersion?: { component: string; majorVersion: number };
}
interface AssetIndex {
  objects: Record<string, { hash: string; size: number }>;
  virtual?: boolean;
  map_to_resources?: boolean;
}
export interface Installation { metadata: Metadata; classpath: string[]; natives: string; game: string; assets: string; gameAssets: string }
export function gameDirectory(root: string, instance: Instance): string {
  return path.join(root, 'instances', instance.id, '.minecraft');
}
export async function extractZip(archive: string, destination: string): Promise<void> {
  const script = `
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::OpenRead($env:COMET_ARCHIVE)
try {
  $root = [IO.Path]::GetFullPath($env:COMET_NATIVES) + [IO.Path]::DirectorySeparatorChar
  foreach ($entry in $zip.Entries) {
    $name = $entry.FullName.Replace('\\', '/')
    if ($name.StartsWith('META-INF/') -or $name.EndsWith('/')) { continue }
    if ($name.Contains(':') -or $name.Split('/') -contains '..' -or $name.StartsWith('/')) { throw 'Unsafe native archive' }
    $target = [IO.Path]::GetFullPath([IO.Path]::Combine($root, $name))
    if (-not $target.StartsWith($root, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe native archive' }
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
    [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
  }
} finally { $zip.Dispose() }
`;
  await exec(path.join(process.env.SystemRoot ?? 'C:\\Windows', 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe'),
    ['-NoLogo', '-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')],
    { windowsHide: true, timeout: 60000, env: { ...process.env, COMET_ARCHIVE: archive, COMET_NATIVES: destination } });
}
export async function install(root: string, instance: Instance, report: (message: string) => void): Promise<Installation> {
  if (process.platform !== 'win32' || process.arch !== 'x64') throw new Error('This build supports Windows x64 only.');
  report(`Resolving Minecraft ${instance.version}`);
  const manifest = await json('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json') as { versions: { id: string; url: string; sha1: string }[] };
  const entry = manifest.versions.find(v => v.id === instance.version);
  if (!entry) throw new Error('Version is not in the official Minecraft manifest.');
  const versionRoot = path.join(root, 'cache', 'versions', instance.version);
  const metadataFile = path.join(versionRoot, `${instance.version}.json`);
  await download(entry, metadataFile);
  const metadata = JSON.parse(await readFile(metadataFile, 'utf8')) as Metadata;
  if (metadata.id !== instance.version || !/^[\w.$]+$/.test(metadata.mainClass)) throw new Error('Unexpected Minecraft metadata.');
  const game = gameDirectory(root, instance);
  const natives = path.join(root, 'instances', instance.id, 'natives');
  const assets = path.join(root, 'cache', 'assets');
  await mkdir(game, { recursive: true });
  await mkdir(natives, { recursive: true });
  const client = path.join(versionRoot, `${instance.version}.jar`);
  report('Verifying game client');
  await download(metadata.downloads.client, client);
  const classpath: string[] = [];
  for (const library of metadata.libraries) {
    if (!allowed(library.rules, os.release())) continue;
    report(`Verifying ${library.name}`);
    const artifact = library.downloads.artifact;
    if (artifact) {
      const destination = inside(path.join(root, 'cache', 'libraries'), artifact.path ?? '');
      await download(artifact, destination);
      classpath.push(destination);
    }
    const classifier = library.natives?.windows?.replace('${arch}', '64');
    if (classifier) {
      const native = library.downloads.classifiers?.[classifier];
      if (!native) throw new Error(`Missing Windows natives: ${library.name}`);
      const destination = inside(path.join(root, 'cache', 'libraries'), native.path ?? '');
      await download(native, destination);
      await extractZip(destination, natives);
    }
  }
  classpath.push(client);
  const indexFile = inside(path.join(assets, 'indexes'), `${metadata.assetIndex.id}.json`);
  await download(metadata.assetIndex, indexFile);
  const index = JSON.parse(await readFile(indexFile, 'utf8')) as AssetIndex;
  const entries = Object.entries(index.objects);
  const gameAssets = index.virtual ? inside(path.join(assets, 'virtual'), metadata.assetIndex.id) : assets;
  let done = 0;
  await parallel(entries, 12, async ([name, asset]) => {
    if (!/^[a-f0-9]{40}$/.test(asset.hash)) throw new Error('Invalid asset hash.');
    const relative = `${asset.hash.slice(0, 2)}/${asset.hash}`;
    const destination = inside(path.join(assets, 'objects'), relative);
    await download({ url: officialUrl(`https://resources.download.minecraft.net/${relative}`), sha1: asset.hash, size: asset.size }, destination);
    for (const mapping of [index.virtual ? gameAssets : null, index.map_to_resources ? path.join(game, 'resources') : null]) {
      if (!mapping) continue;
      const mapped = inside(mapping, name);
      await mkdir(path.dirname(mapped), { recursive: true });
      await copyFile(destination, mapped);
    }
    done++;
    if (done % 25 === 0 || done === entries.length) report(`Verifying assets ${done} / ${entries.length}`);
  });
  if (metadata.logging?.client) {
    const logging = metadata.logging.client;
    await download(logging.file, inside(path.join(assets, 'log_configs'), logging.file.id));
  }
  await writeFile(path.join(root, 'instances', instance.id, 'comet-install.json'), JSON.stringify({ version: instance.version, verifiedAt: new Date().toISOString() }));
  report(`Minecraft ${instance.version} is ready`);
  return { metadata, classpath, natives, game, assets, gameAssets };
}
interface RuntimeManifest { files: Record<string, { type: string; downloads?: { raw: Download } }> }
export function runtimeFiles(manifest: RuntimeManifest): { directories: string[]; files: (Download & { path: string })[] } {
  const directories: string[] = [];
  const files: (Download & { path: string })[] = [];
  for (const [relative, entry] of Object.entries(manifest.files)) {
    if (entry.type === 'directory') directories.push(relative);
    else if (entry.type === 'file' && entry.downloads) files.push({ ...entry.downloads.raw, path: relative });
    else throw new Error(`Unsupported runtime entry: ${relative}`);
  }
  return { directories, files };
}
export async function installRuntime(root: string, java: Metadata['javaVersion'], report: (message: string) => void): Promise<string> {
  const component = java?.component ?? 'jre-legacy';
  if (!/^[\w-]+$/.test(component)) throw new Error('Unexpected Java runtime component.');
  report(`Resolving Java runtime ${component}`);
  const all = await json('https://piston-meta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json') as Record<string, Record<string, { manifest: Download; version: { name: string } }[]>>;
  const entry = all['windows-x64']?.[component]?.[0];
  if (!entry) throw new Error(`Mojang does not publish ${component} for Windows x64.`);
  const home = path.join(root, 'cache', 'runtimes', component);
  const manifestFile = path.join(home, 'manifest.json');
  await download(entry.manifest, manifestFile);
  const { directories, files } = runtimeFiles(JSON.parse(await readFile(manifestFile, 'utf8')) as RuntimeManifest);
  for (const directory of directories) await mkdir(inside(home, directory), { recursive: true });
  let done = 0;
  await parallel(files, 12, async file => {
    await download(file, inside(home, file.path));
    done++;
    if (done % 25 === 0 || done === files.length) report(`Verifying Java runtime ${done} / ${files.length}`);
  });
  report(`Java runtime ${entry.version.name} is ready`);
  return path.join(home, 'bin', 'java.exe');
}
export function javaMatches(output: string, major: number): boolean {
  const version = /java\.specification\.version\s*=\s*(\S+)/.exec(output)?.[1];
  return version === (major === 8 ? '1.8' : String(major)) && /sun\.arch\.data\.model\s*=\s*64/.test(output);
}
export async function verifyJava(javaPath: string, major = 8): Promise<void> {
  const executable = javaPath.replace(/javaw\.exe$/i, 'java.exe');
  const result = await exec(executable, ['-XshowSettings:properties', '-version'], { windowsHide: true, timeout: 15000 });
  if (!javaMatches(result.stdout + result.stderr, major)) throw new Error(`This version requires a 64-bit Java ${major} runtime. Clear the Java override in Settings to use the managed runtime.`);
}
export function launchArguments(installation: Installation, settings: Settings, session: Session): string[] {
  const { metadata, game, assets, natives, gameAssets, classpath } = installation;
  const values: Record<string, string> = {
    auth_player_name: session.account.name, auth_uuid: session.account.id, auth_access_token: session.accessToken,
    auth_session: `token:${session.accessToken}:${session.account.id}`, user_type: 'msa', user_properties: '{}',
    version_name: metadata.id, version_type: metadata.type, game_directory: game, assets_root: assets,
    assets_index_name: metadata.assetIndex.id, game_assets: gameAssets, natives_directory: natives,
    launcher_name: 'Comet', launcher_version: '0.1.0', classpath: classpath.join(';'),
    resolution_width: '1280', resolution_height: '720',
  };
  const jvm = metadata.arguments?.jvm ?? ['-Djava.library.path=${natives_directory}', '-cp', '${classpath}'];
  const gameArgs = metadata.arguments?.game ?? metadata.minecraftArguments?.split(/\s+/);
  if (!gameArgs) throw new Error('Missing game arguments.');
  const logging = metadata.logging?.client;
  return [
    '-Xms512M', `-Xmx${settings.memoryMb}M`, '-Dlog4j2.formatMsgNoLookups=true',
    ...expand(jvm, values, os.release()),
    ...(logging ? [logging.argument.replace('${path}', inside(path.join(assets, 'log_configs'), logging.file.id))] : []),
    metadata.mainClass, ...expand(gameArgs, values, os.release()),
  ];
}
export function launchGame(installation: Installation, settings: Settings, session: Session, java: string): ChildProcess {
  return spawn(java.replace(/javaw\.exe$/i, 'java.exe'), launchArguments(installation, settings, session), {
    cwd: installation.game, windowsHide: true, shell: false, stdio: ['ignore', 'pipe', 'pipe'],
  });
}
