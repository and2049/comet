import { createHash } from 'node:crypto';
import { readFile, stat, writeFile } from 'node:fs/promises';
import path from 'node:path';

const [tag, output = 'client.json'] = process.argv.slice(2);
if (!/^v\d+\.\d+\.\d+$/.test(tag ?? '')) throw new Error('Usage: bun scripts/client-manifest.ts v1.2.3 [output]');
const dist = path.join(import.meta.dirname, '..', 'client', 'build', 'dist');
const launcher = JSON.parse(await readFile(path.join(import.meta.dirname, '..', 'package.json'), 'utf8')).version;
const clients: Record<string, { url: string; sha1: string; size: number }> = {};
for (const version of ['1.8.9', '1.7.10']) {
  const name = `comet-client-${version}.jar`;
  const file = path.join(dist, name);
  clients[version] = {
    url: `https://github.com/and2049/comet/releases/download/${tag}/${name}`,
    sha1: createHash('sha1')
      .update(await readFile(file))
      .digest('hex'),
    size: (await stat(file)).size,
  };
}
await writeFile(output, `${JSON.stringify({ launcher, clients }, null, 2)}\n`);
console.log(`Wrote ${output} for ${tag}`);
