import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import type { Bridge, Command, Instance, Settings, Snapshot } from '../shared';
import './style.css';

declare global { interface Window { comet?: Bridge } }
const preview: Snapshot = {
  instances: [
    { id: 'pvp-1.8.9', name: 'Classic combat', profile: 'pvp', version: '1.8.9' },
    { id: 'pvp-1.7.10', name: 'Legacy combat', profile: 'pvp', version: '1.7.10' },
    { id: 'mcsr-1.16.1', name: 'Speedrunning', profile: 'mcsr', version: '1.16.1' },
  ],
  settings: { clientId: '', javaPath: '', memoryMb: 4096, minimizeOnLaunch: true },
  account: null, running: null, busy: false, status: 'Desktop preview · launch with bun start to use Minecraft', logs: [], deviceCode: null,
};
type Page = 'library' | 'settings' | 'console';
function Icon({ name }: { name: 'library' | 'settings' | 'console' | 'play' | 'comet' }): React.JSX.Element {
  const paths = {
    library: 'M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z',
    settings: 'M4 7h16 M4 17h16 M8 4v6 M16 14v6',
    console: 'm5 6 6 6-6 6 M13 18h6',
    play: 'm8 5 11 7-11 7z',
    comet: 'M19 3 9 7a7 7 0 1 0 8 8l4-10 M8 11a3 3 0 1 0 4 4',
  };
  return <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>;
}
function App(): React.JSX.Element {
  const [state, setState] = useState<Snapshot>(preview);
  const [page, setPage] = useState<Page>('library');
  const [selectedId, select] = useState('pvp-1.8.9');
  const [filter, setFilter] = useState('all');
  const [search, setSearch] = useState('');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const [draft, setDraft] = useState<Settings>(preview.settings);
  const selected = state.instances.find(i => i.id === selectedId) ?? state.instances[0];
  const locked = state.busy || pending;
  useEffect(() => {
    const bridge = window.comet;
    if (!bridge) return;
    const unsubscribe = bridge.subscribe(setState);
    void bridge.invoke({ type: 'snapshot' }).then(setState).catch(e => setError(String(e)));
    return unsubscribe;
  }, []);
  useEffect(() => { setDraft(state.settings); }, [state.settings.clientId, state.settings.javaPath, state.settings.memoryMb, state.settings.minimizeOnLaunch]);
  async function run(command: Command): Promise<void> {
    setError('');
    if (!window.comet) { setError('This is a browser preview. Run bun start for the desktop launcher.'); return; }
    if (command.type !== 'cancelLogin') setPending(true);
    try { setState(await window.comet.invoke(command)); }
    catch (e) { setError(e instanceof Error ? e.message.replace(/^Error invoking remote method '[^']+': Error: /, '') : String(e)); }
    finally { setPending(false); }
  }
  function choose(instance: Instance): void { select(instance.id); }
  const filtered = state.instances.filter(i => (filter === 'all' || i.profile === filter) && `${i.name} ${i.version}`.toLowerCase().includes(search.toLowerCase()));
  return <div className="app">
    <aside className="rail">
      <div className="brand-mark" title="Comet"><Icon name="comet" /></div>
      <nav aria-label="Main navigation">
        {(['library', 'console', 'settings'] as const).map(item => <button key={item} title={item[0].toUpperCase() + item.slice(1)} aria-label={item} aria-current={page === item ? 'page' : undefined} className={page === item ? 'nav active' : 'nav'} onClick={() => setPage(item)}><Icon name={item} /></button>)}
      </nav>
      <span className="rail-version">0.1</span>
    </aside>
    <div className="workspace">
      <header className="topbar"><div className="wordmark">COMET <span>/</span> <small>{page === 'library' ? 'Launchpad' : page === 'console' ? 'Game console' : 'Settings'}</small></div><div className="top-actions"><span className={`running ${state.running ? 'live' : ''}`}><i />{state.running ? '1 instance running' : 'No instances running'}</span><button className="account" disabled={locked} onClick={() => state.account ? setPage('settings') : void run({ type: 'login' })}><span className="avatar">{state.account?.name.slice(0, 2).toUpperCase() ?? 'MS'}</span>{state.account?.name ?? 'Sign in with Microsoft'}</button></div></header>
      <main>
        {error && <div className="error" role="alert"><span>{error}</span><button aria-label="Dismiss error" onClick={() => setError('')}>×</button></div>}
        {state.deviceCode && <section className="login-panel" aria-live="polite"><div><span className="eyebrow">MICROSOFT SIGN-IN</span><h2>Your next stop: microsoft.com/link</h2><p>Enter this code in your browser. Never share it with anyone.</p></div><strong>{state.deviceCode}</strong><button onClick={() => void run({ type: 'cancelLogin' })}>Cancel</button></section>}
        {page === 'library' && <>
          <div className="page-heading"><div><span className="eyebrow">YOUR GAME. YOUR SPACE.</span><h1>Ready when you are.</h1><p>One launcher. A separate home for every way you play.</p></div><span className="build-label">EARLY ACCESS <i /></span></div>
          <section className={`hero ${selected.profile}`}>
            <div className="hero-copy"><span className="eyebrow">{selected.profile === 'mcsr' ? 'SPEEDRUNNING / ISOLATED INSTANCE' : 'COMET PVP / ISOLATED INSTANCE'}</span><h2>{selected.name}<span>Minecraft {selected.version}</span></h2><p>{selected.profile === 'mcsr' ? 'Fabric and the upstream MCSR Ranked RSG pack are installed for you. Nothing else touches this instance.' : 'Back to the versions that made every hit count. Your worlds, your settings, your game.'}</p><div className="hero-actions"><button className="primary" disabled={locked || !!state.running} onClick={() => void run({ type: 'launch', id: selected.id })}><Icon name="play" />{state.running === selected.id ? 'Game running' : locked ? 'Working…' : 'Launch game'}</button><span>Java Edition <b>·</b> {selected.profile === 'mcsr' ? 'Fabric' : 'Vanilla'}</span></div></div>
            <div className="version-art" aria-hidden="true"><span>JAVA EDITION</span><strong>{selected.version}</strong><div className="art-rule" /><small>{selected.profile === 'mcsr' ? 'THE RUN STARTS HERE' : 'THE CLASSICS NEVER LEAVE'}</small></div>
          </section>
          <div className="library-layout"><section className="library"><div className="section-heading"><h2>Your instances <span>{state.instances.length.toString().padStart(2, '0')}</span></h2><input aria-label="Search instances" placeholder="Search instances…" value={search} onChange={e => setSearch(e.target.value)} /></div><div className="tabs" aria-label="Filter instances">{[['all', 'All instances'], ['pvp', 'Comet PvP'], ['mcsr', 'Speedrunning']].map(([value, label]) => <button key={value} aria-pressed={filter === value} className={filter === value ? 'selected' : ''} onClick={() => setFilter(value)}>{label}</button>)}</div><div className="cards">{filtered.map(instance => <button className={`instance ${instance.profile} ${instance.id === selectedId ? 'chosen' : ''}`} key={instance.id} onClick={() => choose(instance)} aria-pressed={instance.id === selectedId}><div className="cover"><span>{instance.profile === 'pvp' ? 'COMET / PVP' : 'COMET / MCSR'}</span><strong>{instance.version}</strong><small>JAVA EDITION</small></div><div className="card-caption"><div><h3>{instance.name}</h3><p>Vanilla <span>·</span> {instance.version}</p></div><Icon name="play" /></div></button>)}</div>{filtered.length === 0 && <p className="empty">No instances match your search.</p>}</section>
          <aside className="details"><span className="eyebrow">INSTANCE DETAILS</span><h2>{selected.name}</h2><dl><div><dt>Version</dt><dd>{selected.version}</dd></div><div><dt>Mod loader</dt><dd>{selected.profile === 'mcsr' ? 'Fabric · MCSR Ranked pack' : 'None / vanilla'}</dd></div><div><dt>Memory</dt><dd>{state.settings.memoryMb / 1024} GB</dd></div><div><dt>Java runtime</dt><dd>{state.settings.javaPath ? 'Java 8 · custom' : 'Java 8 · managed by Comet'}</dd></div></dl><div className="isolation"><span className="isolation-dot" /><div><strong>A space of its own</strong><p>Mods, worlds, and settings never share a game directory with another instance.</p></div></div><p className="phase-note">{selected.profile === 'mcsr' ? 'The RSG pack follows the upstream MCSR Ranked mod list on every install. Mods you add yourself are kept.' : 'Built-in PvP mods and the right-shift menu are not included in this milestone.'}</p><div className="detail-buttons"><button disabled={locked || !!state.running} onClick={() => void run({ type: 'install', id: selected.id })}>Install / verify files</button><button disabled={locked} onClick={() => void run({ type: 'folder', id: selected.id })}>Open game folder <span>↗</span></button></div></aside></div>
        </>}
        {page === 'settings' && <><div className="page-heading"><div><span className="eyebrow">MAKE YOURSELF AT HOME</span><h1>Launcher settings</h1><p>Global defaults for every instance.</p></div></div><section className="settings-panel"><div className="settings-section"><h2>Microsoft account</h2><p>{state.account ? `Signed in as ${state.account.name}. Tokens are encrypted using Windows secure storage.` : 'Sign in with an account that owns Minecraft: Java Edition.'}</p><label>Comet application (client) ID<input value={draft.clientId} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" onChange={e => setDraft({ ...draft, clientId: e.target.value.trim() })} /></label><p className="hint">Requires your own Entra public-client registration and Minecraft Services approval. No client secret is needed.</p><button disabled={locked} onClick={() => void run({ type: state.account ? 'logout' : 'login' })}>{state.account ? 'Sign out' : 'Sign in with Microsoft'}</button></div><div className="settings-section"><h2>Java & memory</h2><label>Java executable override (optional)<div className="path-row"><input readOnly value={state.settings.javaPath} placeholder="Empty: Comet downloads Mojang's Java runtime" /><button disabled={locked} onClick={() => void run({ type: 'java' })}>Browse…</button><button disabled={locked || !state.settings.javaPath} onClick={() => void run({ type: 'settings', settings: { ...state.settings, javaPath: '' } })}>Clear</button></div></label><p className="hint">Comet downloads the Java runtime Mojang declares for each version. Choose a 64-bit Java 8 java.exe only to override it.</p><label>Maximum memory <span className="memory-value">{draft.memoryMb} MB</span><input type="range" min="1024" max="16384" step="512" value={draft.memoryMb} onChange={e => setDraft({ ...draft, memoryMb: Number(e.target.value) })} /></label></div><div className="settings-section"><h2>Launch behavior</h2><label className="checkbox"><input type="checkbox" checked={draft.minimizeOnLaunch} onChange={e => setDraft({ ...draft, minimizeOnLaunch: e.target.checked })} />Minimize Comet when the game process starts</label><p className="hint">Comet returns when the game exits. Closing the launcher while a game or operation is active minimizes it instead.</p></div><button className="primary" disabled={locked} onClick={() => void run({ type: 'settings', settings: draft })}>Save settings</button></section></>}
        {page === 'console' && <><div className="page-heading"><div><span className="eyebrow">UNDER THE HOOD</span><h1>Game console</h1><p>Recent game output. Access tokens are redacted before display.</p></div></div><pre className="console" aria-label="Game output">{state.logs.length ? state.logs.join('\n') : 'No game output yet. Launch an instance to see its log here.'}</pre></>}
      </main>
      <footer><span className={locked ? 'status-dot working' : 'status-dot'} /><span role="status">{state.status}</span><span className="footer-end">COMET <b>EARLY ACCESS</b></span></footer>
    </div>
  </div>;
}

createRoot(document.getElementById('root')!).render(<App />);
