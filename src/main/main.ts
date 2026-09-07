import { app, BrowserWindow, dialog, ipcMain, safeStorage, shell } from 'electron';
import { mkdir, readFile, rename, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { randomUUID } from 'node:crypto';
import { createInterface } from 'node:readline';
import { defaults, instances, platform, prismFiles, record, redact, settingsFrom, text } from './core.js';
import { login, refresh, sessionFrom, type Session } from './auth.js';
import { gameDirectory, install, installRuntime, launchGame, verifyJava } from './minecraft.js';
import { withFabric } from './fabric.js';
import type { Snapshot } from '../shared.js';

app.setName('Comet');
const here = path.dirname(fileURLToPath(import.meta.url));
let window: BrowserWindow;
let root: string;
let session: Session | null = null;
let loginController: AbortController | null = null;
const storageHint = process.platform === 'linux' ? 'System secure storage is unavailable. Install and unlock a keyring such as gnome-keyring or KWallet.' : 'System secure storage is unavailable.';
const state: Snapshot = { instances, settings: defaults, account: null, running: null, busy: false, status: 'Choose an instance to get started', logs: [], deviceCode: null, maximized: false, platform: platform().os };
function publish(): Snapshot {
  if (window && !window.isDestroyed()) window.webContents.send('comet:state', state);
  return state;
}
function status(message: string): void { state.status = message; publish(); }
function log(message: string): void {
  state.logs = [...state.logs, message.slice(0, 4000)].slice(-400);
  publish();
}
async function atomic(file: string, contents: string | Buffer): Promise<void> {
  const temporary = `${file}.${randomUUID()}.tmp`;
  try { await writeFile(temporary, contents); await rename(temporary, file); }
  finally { await rm(temporary, { force: true }); }
}
async function saveSession(next: Session): Promise<void> {
  if (!safeStorage.isEncryptionAvailable()) throw new Error(`${storageHint} Sign-in was not saved.`);
  await atomic(path.join(root, 'account.bin'), safeStorage.encryptString(JSON.stringify(next)));
  session = next;
  state.account = next.account;
}
async function initialize(): Promise<void> {
  root = app.getPath('userData');
  await mkdir(root, { recursive: true });
  try { state.settings = settingsFrom(JSON.parse(await readFile(path.join(root, 'settings.json'), 'utf8'))); }
  catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') log('Settings could not be read. Defaults are active; the original file was preserved.'); }
  try {
    if (safeStorage.isEncryptionAvailable()) {
      session = sessionFrom(JSON.parse(safeStorage.decryptString(await readFile(path.join(root, 'account.bin')))));
      if (session.clientId === state.settings.clientId) state.account = session.account;
      else session = null;
    }
  } catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') log('Saved account could not be unlocked. Sign in again.'); }
  for (const instance of instances) {
    const folder = path.join(root, 'instances', instance.id);
    await mkdir(gameDirectory(root, instance), { recursive: true });
    const files = prismFiles(instance);
    for (const [name, contents] of Object.entries({ 'instance.cfg': files.config, 'mmc-pack.json': files.pack, 'comet.json': JSON.stringify({ profile: instance.profile, schemaVersion: 1 }) })) {
      try { await writeFile(path.join(folder, name), contents, { flag: 'wx' }); }
      catch (error) { if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error; }
    }
  }
}
async function command(input: unknown): Promise<Snapshot> {
  const request = record(input);
  const type = text(request.type);
  if (type === 'snapshot') return state;
  if (type === 'cancelLogin') { loginController?.abort(); return state; }
  if (type === 'window') {
    const action = text(request.action);
    if (action === 'minimize') window.minimize();
    else if (action === 'maximize') window.isMaximized() ? window.unmaximize() : window.maximize();
    else if (action === 'close') window.close();
    else throw new Error('Unknown window action.');
    return state;
  }
  if (state.busy) throw new Error('Wait for the current operation to finish.');
  if (type === 'settings') {
    const next = settingsFrom(request.settings);
    if (next.clientId !== state.settings.clientId) {
      await rm(path.join(root, 'account.bin'), { force: true });
      session = null; state.account = null;
    }
    await atomic(path.join(root, 'settings.json'), JSON.stringify(next, null, 2));
    state.settings = next;
    status('Settings saved');
    return state;
  }
  if (type === 'java') {
    const result = await dialog.showOpenDialog(window, { title: 'Choose a Java executable', filters: process.platform === 'win32' ? [{ name: 'Java executable', extensions: ['exe'] }] : [], properties: ['openFile'] });
    if (result.filePaths[0]) {
      const next = settingsFrom({ ...state.settings, javaPath: result.filePaths[0] });
      await verifyJava(next.javaPath);
      await atomic(path.join(root, 'settings.json'), JSON.stringify(next, null, 2));
      state.settings = next;
      status('64-bit Java 8 override saved');
    }
    return publish();
  }
  if (type === 'logout') {
    await rm(path.join(root, 'account.bin'), { force: true });
    session = null; state.account = null;
    status('Signed out');
    return state;
  }
  const instance = instances.find(item => item.id === request.id);
  if (type === 'folder') {
    if (!instance) throw new Error('Unknown instance.');
    const error = await shell.openPath(gameDirectory(root, instance));
    if (error) throw new Error(error);
    return state;
  }
  if (!['login', 'install', 'launch'].includes(type)) throw new Error('Unknown command.');
  if (type !== 'login' && !instance) throw new Error('Unknown instance.');
  if (state.running && type !== 'login') throw new Error('Close the running game before installing or launching another instance.');
  state.busy = true; publish();
  try {
    if (type === 'login') {
      if (!safeStorage.isEncryptionAvailable()) throw new Error(storageHint);
      loginController = new AbortController();
      status('Waiting for Microsoft sign-in');
      const next = await login(state.settings.clientId, code => {
        state.deviceCode = code; publish();
        void shell.openExternal('https://www.microsoft.com/link').catch(() => status('Open microsoft.com/link and enter the displayed code.'));
      }, loginController.signal);
      await saveSession(next);
      status(`Signed in as ${next.account.name}`);
    } else if (instance) {
      if (type === 'launch' && (!session || session.clientId !== state.settings.clientId)) throw new Error('Sign in with Microsoft before launching.');
      let installation = await install(root, instance, status);
      if (instance.profile === 'mcsr') installation = await withFabric(root, instance, installation, status);
      const major = installation.metadata.javaVersion?.majorVersion ?? 8;
      const java = state.settings.javaPath || await installRuntime(root, installation.metadata.javaVersion, status);
      await verifyJava(java, major);
      if (type === 'launch' && session) {
        status('Refreshing Minecraft session');
        await saveSession(await refresh(session));
        const secrets = [session.accessToken, session.refreshToken];
        const child = launchGame(installation, state.settings, session, java);
        await new Promise<void>((resolve, reject) => { child.once('spawn', resolve); child.once('error', reject); });
        state.running = instance.id;
        state.logs = [];
        status(`Minecraft ${instance.version} is running`);
        for (const stream of [child.stdout, child.stderr]) {
          if (stream) createInterface({ input: stream }).on('line', line => log(redact(line, secrets)));
        }
        child.on('error', () => log('Game process error. Check the Java runtime.'));
        child.once('close', code => {
          state.running = null;
          status(code === 0 ? 'Game closed' : `Game exited with code ${code ?? 'unknown'}. Check the console and instance crash reports.`);
          if (!window.isDestroyed()) { window.restore(); window.show(); window.focus(); }
        });
        if (state.settings.minimizeOnLaunch) window.minimize();
      }
    }
  } catch (error) {
    status(loginController?.signal.aborted ? 'Sign-in cancelled' : error instanceof Error ? error.message : 'Operation failed');
    throw new Error(state.status);
  } finally {
    state.busy = false; state.deviceCode = null; loginController = null; publish();
  }
  return state;
}
if (!app.requestSingleInstanceLock()) app.quit();
else {
  app.on('second-instance', () => { if (window && !window.isDestroyed()) { window.restore(); window.focus(); } });
  app.whenReady().then(async () => {
    await initialize();
    window = new BrowserWindow({
      width: 1320, height: 850, minWidth: 1000, minHeight: 700, title: 'Comet',
      ...(process.platform === 'linux' ? { transparent: true, backgroundColor: '#00000000' } : { backgroundColor: '#101114' }),
      frame: false, titleBarStyle: 'hidden', trafficLightPosition: { x: 14, y: 18 },
      webPreferences: { preload: path.join(here, 'preload.cjs'), contextIsolation: true, nodeIntegration: false, sandbox: true },
    });
    window.setMenuBarVisibility(false);
    const maximized = () => { state.maximized = window.isMaximized(); publish(); };
    window.on('maximize', maximized);
    window.on('unmaximize', maximized);
    window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
    window.webContents.on('will-navigate', event => event.preventDefault());
    window.webContents.session.setPermissionRequestHandler((_contents, _permission, callback) => callback(false));
    ipcMain.handle('comet:command', (event, input: unknown) => {
      if (event.sender !== window.webContents || event.senderFrame !== window.webContents.mainFrame) throw new Error('Untrusted command sender.');
      return command(input);
    });
    window.on('close', event => {
      if (state.running || state.busy) { event.preventDefault(); window.minimize(); }
    });
    await window.loadFile(path.join(here, '../renderer/index.html'));
  }).catch(error => { dialog.showErrorBox('Comet could not start', error instanceof Error ? error.message : 'Unknown error'); app.quit(); });
  app.on('window-all-closed', () => app.quit());
}
