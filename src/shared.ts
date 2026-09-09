export type Profile = 'pvp' | 'mcsr' | 'custom';
export type LoaderKind = 'vanilla' | 'fabric' | 'quilt';
export type Directory = 'isolated' | 'comet' | 'official';
export interface Pack {
  source: 'mcsr' | 'modrinth' | 'import';
  name: string;
  versionId?: string;
  projectId?: string;
}
export interface Instance {
  id: string;
  name: string;
  profile: Profile;
  version: string;
  loader: LoaderKind;
  loaderVersion?: string;
  directory: Directory;
  pack?: Pack;
  icon?: string;
}
export interface Draft {
  name: string;
  version: string;
  loader: LoaderKind;
  loaderVersion?: string;
  directory: 'isolated' | 'official';
}
export interface Settings {
  clientId: string;
  javaPath: string;
  memoryMb: number;
  minimizeOnLaunch: boolean;
}
export interface Account {
  id: string;
  name: string;
}
export interface Snapshot {
  instances: Instance[];
  selected: string | null;
  settings: Settings;
  account: Account | null;
  running: string | null;
  busy: boolean;
  status: string;
  logs: string[];
  deviceCode: string | null;
  maximized: boolean;
  platform: 'windows' | 'osx' | 'linux';
}
export type Command =
  | { type: 'snapshot' }
  | { type: 'settings'; settings: Settings }
  | { type: 'java' }
  | { type: 'login' }
  | { type: 'cancelLogin' }
  | { type: 'logout' }
  | { type: 'install'; id: string }
  | { type: 'launch'; id: string }
  | { type: 'folder'; id: string }
  | { type: 'remove'; id: string }
  | { type: 'create'; draft: Draft }
  | { type: 'importFile' }
  | { type: 'importUrl'; url: string }
  | { type: 'modrinthInstall'; projectId: string; versionId: string }
  | { type: 'window'; action: 'minimize' | 'maximize' | 'close' };
export interface VersionInfo {
  id: string;
  type: string;
  releaseTime: string;
}
export interface LoaderVersion {
  version: string;
  stable: boolean;
}
export interface ModrinthHit {
  projectId: string;
  slug: string;
  title: string;
  description: string;
  icon: string | null;
  downloads: number;
  loaders: string[];
}
export interface ModrinthVersion {
  id: string;
  name: string;
  versionNumber: string;
  gameVersions: string[];
  loaders: string[];
  datePublished: string;
  supported: boolean;
}
export type Query =
  | { type: 'versions' }
  | { type: 'loaders'; loader: 'fabric' | 'quilt'; version: string }
  | { type: 'modrinthSearch'; query: string; offset: number }
  | { type: 'modrinthVersions'; projectId: string };
export type QueryResult<Q extends Query> = Q extends { type: 'versions' }
  ? VersionInfo[]
  : Q extends { type: 'loaders' }
    ? LoaderVersion[]
    : Q extends { type: 'modrinthSearch' }
      ? { hits: ModrinthHit[]; total: number }
      : ModrinthVersion[];
export interface Bridge {
  invoke(command: Command): Promise<Snapshot>;
  query<Q extends Query>(query: Q): Promise<QueryResult<Q>>;
  subscribe(callback: (state: Snapshot) => void): () => void;
}
