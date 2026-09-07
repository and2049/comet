import { contextBridge, ipcRenderer } from 'electron';
import type { Bridge, Snapshot } from '../shared.js';

const bridge: Bridge = {
  invoke: command => ipcRenderer.invoke('comet:command', command),
  subscribe: callback => {
    const listener = (_event: Electron.IpcRendererEvent, state: Snapshot) => callback(state);
    ipcRenderer.on('comet:state', listener);
    return () => ipcRenderer.removeListener('comet:state', listener);
  },
};
contextBridge.exposeInMainWorld('comet', bridge);
