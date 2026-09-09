import { mkdir, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { instanceFrom, instanceId, instances as predefined, prismFiles } from './core.js';
import { gameDirectory } from './minecraft.js';
import type { Instance } from '../shared.js';

export function instanceFolder(root: string, id: string): string {
  return path.join(root, 'instances', id);
}
async function writeNew(file: string, contents: string): Promise<void> {
  try {
    await writeFile(file, contents, { flag: 'wx' });
  } catch (error) {
    if ((error as NodeJS.ErrnoException).code !== 'EEXIST') throw error;
  }
}
export async function writeInstance(root: string, instance: Instance): Promise<void> {
  const folder = instanceFolder(root, instance.id);
  await mkdir(folder, { recursive: true });
  if (instance.directory !== 'official') await mkdir(gameDirectory(root, instance), { recursive: true });
  const files = prismFiles(instance);
  const { icon: _icon, ...stored } = instance;
  await writeNew(path.join(folder, 'instance.cfg'), files.config);
  await writeNew(path.join(folder, 'mmc-pack.json'), files.pack);
  await writeFile(path.join(folder, 'comet.json'), JSON.stringify({ ...stored, schemaVersion: 2 }, null, 2));
}
async function loadIcon(root: string, id: string): Promise<string | undefined> {
  try {
    const data = await readFile(path.join(instanceFolder(root, id), 'icon.png'));
    return data.length <= 200 * 1024 ? `data:image/png;base64,${data.toString('base64')}` : undefined;
  } catch {
    return undefined;
  }
}
export async function loadInstances(root: string, warn: (message: string) => void): Promise<Instance[]> {
  const result: Instance[] = [];
  for (const instance of predefined) {
    await writeInstance(root, instance);
    result.push(instance);
  }
  const folders = await readdir(path.join(root, 'instances'), { withFileTypes: true });
  for (const folder of folders.filter(entry => entry.isDirectory()).sort((a, b) => a.name.localeCompare(b.name))) {
    if (predefined.some(instance => instance.id === folder.name)) continue;
    try {
      const raw = await readFile(path.join(instanceFolder(root, folder.name), 'comet.json'), 'utf8');
      const instance = instanceFrom(JSON.parse(raw), folder.name);
      if (instance.profile !== 'custom') throw new Error('Only custom instances are loaded from disk.');
      const icon = await loadIcon(root, instance.id);
      result.push(icon ? { ...instance, icon } : instance);
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== 'ENOENT')
        warn(`Instance folder ${folder.name} was skipped: ${error instanceof Error ? error.message : 'invalid'}`);
    }
  }
  return result;
}
export async function createInstance(
  root: string,
  existing: Instance[],
  fields: Omit<Instance, 'id' | 'profile' | 'icon'>,
): Promise<Instance> {
  const folders = await readdir(path.join(root, 'instances')).catch(() => []);
  const instance: Instance = {
    id: instanceId(fields.name, [...existing.map(item => item.id), ...folders]),
    profile: 'custom',
    ...fields,
  };
  await writeInstance(root, instance);
  return instance;
}
export async function removeInstance(root: string, instance: Instance): Promise<void> {
  if (instance.profile !== 'custom') throw new Error('Built-in instances cannot be removed.');
  await rm(instanceFolder(root, instance.id), { recursive: true, force: true });
}
