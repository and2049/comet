import { afterEach, expect, test } from 'bun:test';
import { login, refresh, sessionFrom } from '../src/main/auth';

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test('missing client ID fails before sending a request', async () => {
  let called = false;
  globalThis.fetch = (() => { called = true; throw new Error('Unexpected request'); }) as typeof fetch;
  await expect(login('', () => {}, new AbortController().signal)).rejects.toThrow('approved Microsoft');
  expect(called).toBe(false);
});
test('cancels device-code polling without exchanging tokens', async () => {
  globalThis.fetch = (async () => Response.json({ user_code: 'TEST', device_code: 'private', interval: 5, expires_in: 900 })) as typeof fetch;
  const controller = new AbortController();
  await expect(login('id', () => controller.abort(), controller.signal)).rejects.toThrow();
});
test('refresh performs Xbox, XSTS, Minecraft and entitlement/profile checks', async () => {
  const responses = [
    { access_token: 'msa', refresh_token: 'rotated' },
    { Token: 'xbox' },
    { Token: 'xsts', DisplayClaims: { xui: [{ uhs: 'hash' }] } },
    { access_token: 'minecraft' },
    { items: [{ name: 'game_minecraft' }] },
    { id: 'a'.repeat(32), name: 'Tester' },
  ];
  const requests: string[] = [];
  globalThis.fetch = (async (input: string | URL | Request) => {
    requests.push(String(input));
    return Response.json(responses.shift());
  }) as typeof fetch;
  const result = await refresh({ account: { id: 'a'.repeat(32), name: 'Tester' }, clientId: 'id', refreshToken: 'old', accessToken: 'expired' });
  expect(result.refreshToken).toBe('rotated');
  expect(result.accessToken).toBe('minecraft');
  expect(requests).toHaveLength(6);
  expect(requests[4]).toContain('entitlements');
});
test('approval failures do not leak response bodies', async () => {
  globalThis.fetch = (async () => new Response('private token details', { status: 403 })) as typeof fetch;
  await expect(refresh({ account: { id: 'a'.repeat(32), name: 'Tester' }, clientId: 'id', refreshToken: 'old', accessToken: 'expired' })).rejects.toThrow('HTTP 403');
});
test('encrypted account payload must have complete fields', () => {
  expect(() => sessionFrom({ account: { name: 'Tester' } })).toThrow();
});
