import { describe, expect, test } from 'bun:test';
import { hitsFrom, imageType, modrinthId, versionsFrom } from '../src/main/modrinth';

describe('Modrinth responses', () => {
  test('validates ids', () => {
    expect(modrinthId('1KVo5zza')).toBe('1KVo5zza');
    for (const value of ['', '../x', 'a b', 'x'.repeat(17), 5]) expect(() => modrinthId(value)).toThrow();
  });
  test('parses search hits and keeps only loader categories', () => {
    expect(
      hitsFrom({
        total_hits: 2,
        hits: [
          {
            project_id: '1KVo5zza',
            slug: 'fabulously-optimized',
            title: 'Fabulously Optimized',
            description: 'Beautiful graphics, speedy performance',
            icon_url: 'https://cdn.modrinth.com/data/1KVo5zza/icon.png',
            downloads: 10,
            categories: ['fabric', 'quilt', 'optimization'],
          },
          { project_id: 'AbCdEf12', title: 'Forge Pack', categories: ['forge'] },
        ],
      }),
    ).toEqual({
      total: 2,
      hits: [
        {
          projectId: '1KVo5zza',
          slug: 'fabulously-optimized',
          title: 'Fabulously Optimized',
          description: 'Beautiful graphics, speedy performance',
          icon: 'https://cdn.modrinth.com/data/1KVo5zza/icon.png',
          downloads: 10,
          loaders: ['fabric', 'quilt'],
        },
        {
          projectId: 'AbCdEf12',
          slug: 'Instance',
          title: 'Forge Pack',
          description: '',
          icon: null,
          downloads: 0,
          loaders: ['forge'],
        },
      ],
    });
    expect(() => hitsFrom({ hits: 'x' })).toThrow();
    expect(() => hitsFrom({ hits: [{ project_id: '../x' }] })).toThrow();
  });
  test('parses versions, prefers the primary mrpack and marks Forge as unsupported', () => {
    const file = (filename: string, primary?: boolean) => ({
      url: `https://cdn.modrinth.com/data/x/${filename}`,
      filename,
      primary,
      size: 5,
      hashes: { sha1: 'b'.repeat(40), sha512: 'c' },
    });
    const versions = versionsFrom([
      {
        id: 'k83cbnOa',
        name: 'Release 6.0',
        version_number: '6.0.0',
        game_versions: ['1.21.1'],
        loaders: ['fabric'],
        date_published: '2026-01-01T00:00:00Z',
        files: [file('extra.zip'), file('pack.mrpack', true)],
      },
      { id: 'Forge1234', name: 'Forge', version_number: '1.0', loaders: ['forge'], files: [file('pack.mrpack')] },
      { id: 'NoFile123', name: 'No file', version_number: '1.0', loaders: ['fabric'], files: [file('mod.jar')] },
    ]);
    expect(versions).toEqual([
      {
        id: 'k83cbnOa',
        name: 'Release 6.0',
        versionNumber: '6.0.0',
        gameVersions: ['1.21.1'],
        loaders: ['fabric'],
        datePublished: '2026-01-01T00:00:00Z',
        supported: true,
        file: { url: 'https://cdn.modrinth.com/data/x/pack.mrpack', sha1: 'b'.repeat(40), size: 5 },
      },
      {
        id: 'Forge1234',
        name: 'Forge',
        versionNumber: '1.0',
        gameVersions: [],
        loaders: ['forge'],
        datePublished: '',
        supported: false,
        file: { url: 'https://cdn.modrinth.com/data/x/pack.mrpack', sha1: 'b'.repeat(40), size: 5 },
      },
    ]);
    expect(() => versionsFrom({})).toThrow();
  });
  test('recognizes icon formats by their magic bytes', () => {
    expect(imageType(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a]))).toBe('image/png');
    expect(imageType(Buffer.from([0xff, 0xd8, 0xff, 0xe0]))).toBe('image/jpeg');
    expect(imageType(Buffer.from('GIF89a'))).toBe('image/gif');
    expect(imageType(Buffer.from('RIFF0000WEBPVP8 '))).toBe('image/webp');
    expect(imageType(Buffer.from('<svg onload="x">'))).toBeNull();
    expect(imageType(Buffer.alloc(0))).toBeNull();
  });
});
