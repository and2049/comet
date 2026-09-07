export type Profile = 'pvp' | 'mcsr';
export type Version = '1.7.10' | '1.8.9' | '1.16.1';
export interface Instance {
  id: string;
  name: string;
  profile: Profile;
  version: Version;
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
  | { type: 'window'; action: 'minimize' | 'maximize' | 'close' };
export interface Bridge {
  invoke(command: Command): Promise<Snapshot>;
  subscribe(callback: (state: Snapshot) => void): () => void;
}
