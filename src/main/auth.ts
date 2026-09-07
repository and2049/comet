import { setTimeout as delay } from 'node:timers/promises';
import { record, text } from './core.js';
import { json } from './net.js';
import type { Account } from '../shared.js';

const authority = 'https://login.microsoftonline.com/consumers/oauth2/v2.0';
const scope = 'XboxLive.signin offline_access';
export interface Session {
  account: Account;
  accessToken: string;
  refreshToken: string;
  clientId: string;
}
export function sessionFrom(value: unknown): Session {
  const s = record(value);
  const a = record(s.account);
  return {
    account: { id: text(a.id), name: text(a.name) },
    accessToken: text(s.accessToken),
    refreshToken: text(s.refreshToken),
    clientId: text(s.clientId),
  };
}
async function exchange(msa: Record<string, unknown>, clientId: string, signal?: AbortSignal): Promise<Session> {
  const post = async (url: string, body: unknown) =>
    record(
      await json(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal,
      }),
    );
  const xbox = await post('https://user.auth.xboxlive.com/user/authenticate', {
    Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: `d=${text(msa.access_token)}` },
    RelyingParty: 'http://auth.xboxlive.com',
    TokenType: 'JWT',
  });
  const xsts = await post('https://xsts.auth.xboxlive.com/xsts/authorize', {
    Properties: { SandboxId: 'RETAIL', UserTokens: [text(xbox.Token)] },
    RelyingParty: 'rp://api.minecraftservices.com/',
    TokenType: 'JWT',
  });
  const claims = record(xsts.DisplayClaims).xui;
  if (!Array.isArray(claims) || !claims.length) throw new Error('Xbox profile is missing.');
  const minecraft = await post('https://api.minecraftservices.com/authentication/login_with_xbox', {
    identityToken: `XBL3.0 x=${text(record(claims[0]).uhs)};${text(xsts.Token)}`,
  });
  const accessToken = text(minecraft.access_token);
  const headers = { Authorization: `Bearer ${accessToken}` };
  const entitlements = record(
    await json('https://api.minecraftservices.com/entitlements/mcstore', { headers, signal }),
  );
  if (!Array.isArray(entitlements.items) || entitlements.items.length === 0)
    throw new Error('This account has no Minecraft Java entitlement.');
  const profile = record(await json('https://api.minecraftservices.com/minecraft/profile', { headers, signal }));
  const account = { id: text(profile.id), name: text(profile.name) };
  if (!/^[a-f0-9]{32}$/i.test(account.id) || !/^\w{1,16}$/.test(account.name))
    throw new Error('Invalid Minecraft profile.');
  return { account, accessToken, refreshToken: text(msa.refresh_token), clientId };
}
export async function login(clientId: string, showCode: (code: string) => void, signal: AbortSignal): Promise<Session> {
  if (!clientId) throw new Error('Add Comet’s approved Microsoft application ID in Settings first.');
  const device = record(
    await json(`${authority}/devicecode`, {
      method: 'POST',
      body: new URLSearchParams({ client_id: clientId, scope }),
      signal,
    }),
  );
  showCode(text(device.user_code));
  const expires = Number(device.expires_in);
  let interval = Math.max(5, Number(device.interval) || 5);
  if (!Number.isFinite(expires) || expires <= 0 || expires > 1800) throw new Error('Invalid device code expiry.');
  const deadline = Date.now() + expires * 1000;
  while (Date.now() < deadline) {
    await delay(interval * 1000, undefined, { signal });
    const response = await fetch(`${authority}/token`, {
      method: 'POST',
      body: new URLSearchParams({
        client_id: clientId,
        device_code: text(device.device_code),
        grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
      }),
      signal: AbortSignal.any([signal, AbortSignal.timeout(30000)]),
      redirect: 'error',
    });
    const result = record(await response.json());
    if (response.ok) return exchange(result, clientId, signal);
    if (result.error === 'authorization_pending') continue;
    if (result.error === 'slow_down') {
      interval += 5;
      continue;
    }
    throw new Error(`Microsoft sign-in failed: ${typeof result.error === 'string' ? result.error : response.status}`);
  }
  throw new Error('Sign-in code expired. Try again.');
}
export async function refresh(session: Session): Promise<Session> {
  const msa = record(
    await json(`${authority}/token`, {
      method: 'POST',
      body: new URLSearchParams({
        client_id: session.clientId,
        refresh_token: session.refreshToken,
        grant_type: 'refresh_token',
        scope,
      }),
    }),
  );
  return exchange({ ...msa, refresh_token: msa.refresh_token ?? session.refreshToken }, session.clientId);
}
