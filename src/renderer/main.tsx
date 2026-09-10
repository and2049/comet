import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import type {
  Bridge,
  Command,
  Draft,
  Instance,
  LoaderKind,
  LoaderVersion,
  ModrinthHit,
  ModrinthVersion,
  Query,
  QueryResult,
  Settings,
  Snapshot,
  VersionInfo,
} from '../shared';
import { optifineLabels } from '../shared';
import './style.css';

declare global {
  interface Window {
    comet?: Bridge;
  }
}
const preview: Snapshot = {
  instances: [
    {
      id: 'pvp-1.8.9',
      name: 'Classic combat',
      profile: 'pvp',
      version: '1.8.9',
      loader: 'vanilla',
      directory: 'comet',
    },
    {
      id: 'pvp-1.7.10',
      name: 'Legacy combat',
      profile: 'pvp',
      version: '1.7.10',
      loader: 'vanilla',
      directory: 'comet',
    },
    {
      id: 'mcsr-1.16.1',
      name: 'Speedrunning',
      profile: 'mcsr',
      version: '1.16.1',
      loader: 'fabric',
      directory: 'isolated',
      pack: { source: 'mcsr', name: 'MCSR Ranked RSG pack' },
    },
  ],
  selected: null,
  optifine: [],
  client: [],
  settings: { clientId: '', javaPath: '', memoryMb: 4096, minimizeOnLaunch: true },
  account: null,
  running: null,
  busy: false,
  status: 'Desktop preview · launch with bun start to use Minecraft',
  logs: [],
  deviceCode: null,
  maximized: false,
  platform: 'windows',
};
const previewMessage = 'This is a browser preview. Run bun start for the desktop launcher.';
const loaderNames: Record<LoaderKind, string> = { vanilla: 'Vanilla', fabric: 'Fabric', quilt: 'Quilt' };
const folderNames = { isolated: 'Own folder', comet: 'Comet shared folder', official: 'Official .minecraft' };
const createFolders: { value: 'isolated' | 'official'; label: string; hint: string }[] = [
  { value: 'isolated', label: 'Separate', hint: 'Worlds, options and mods stay inside this instance.' },
  {
    value: 'official',
    label: 'Shared',
    hint: 'Uses the official .minecraft folder, so worlds and options continue from the launcher and across versions.',
  },
];
const known = ['1.7.10', '1.8.9', '1.16.1'];
const emptyForm: Draft = { name: '', version: '', loader: 'vanilla', directory: 'isolated' };
function art(instance: Instance): string | null {
  return !instance.icon && known.includes(instance.version) ? `art/${instance.version}.jpg` : null;
}
function family(instance: Instance): string {
  if (instance.profile === 'pvp') return 'COMET / PVP';
  if (instance.profile === 'mcsr') return 'COMET / MCSR';
  return `CUSTOM / ${loaderNames[instance.loader].toUpperCase()}`;
}
function eyebrow(instance: Instance): string {
  return instance.profile === 'pvp' ? 'COMET PVP' : instance.profile === 'mcsr' ? 'SPEEDRUNNING' : 'CUSTOM';
}
function loaderLabel(instance: Instance): string {
  const name = loaderNames[instance.loader];
  if (instance.pack?.source === 'mcsr') return `${name} · MCSR Ranked pack`;
  return instance.loaderVersion ? `${name} ${instance.loaderVersion}` : name;
}
function note(instance: Instance): string {
  if (instance.profile === 'mcsr') return 'Mods follow the upstream MCSR Ranked pack. Mods you add are kept.';
  if (instance.profile === 'pvp')
    return 'Launches with OptiFine, fetched from optifine.net onto this computer, and the Comet client from GitHub releases. Settings and worlds are shared between the Comet 1.7.10 and 1.8.9 instances.';
  if (instance.pack)
    return `Installed from ${instance.pack.name}${instance.pack.versionId ? ` ${instance.pack.versionId}` : ''}.`;
  if (instance.directory === 'official')
    return 'Uses the official launcher folder. Worlds, options and servers are shared with it.';
  return 'Uses its own folder inside Comet.';
}
function clean(e: unknown): string {
  return e instanceof Error ? e.message.replace(/^Error invoking remote method '[^']+': Error: /, '') : String(e);
}
type Page = 'library' | 'settings' | 'console' | 'add';
type Mode = 'create' | 'import' | 'modrinth';
const pageNames: Record<Page, string> = {
  library: 'Launchpad',
  settings: 'Settings',
  console: 'Game console',
  add: 'New instance',
};
function ramp(axis: 'x' | 'y'): string {
  const [dir, on, mid] = axis === 'x' ? ['x2="1" y2="0"', '#f00', '#800'] : ['x2="0" y2="1"', '#0f0', '#080'];
  const stops = [
    [0, on],
    [0.2, mid],
    [0.8, mid],
    [1, '#000'],
  ]
    .map(([offset, color]) => `<stop offset="${offset}" stop-color="${color}"/>`)
    .join('');
  return `data:image/svg+xml,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1" preserveAspectRatio="none"><linearGradient id="g" ${dir}>${stops}</linearGradient><rect width="1" height="1" fill="url(#g)"/></svg>`,
  )}`;
}
function Backdrop(): React.JSX.Element {
  return (
    <>
      <video className="backdrop" src="background.mp4" autoPlay muted loop playsInline aria-hidden="true" />
      <div className="frost" aria-hidden="true">
        <i />
        <i />
        <i />
        <i />
      </div>
      <svg className="defs" aria-hidden="true">
        <filter id="lens" x="0" y="0" width="100%" height="100%" colorInterpolationFilters="sRGB">
          <feImage href={ramp('x')} preserveAspectRatio="none" result="rx" />
          <feImage href={ramp('y')} preserveAspectRatio="none" result="ry" />
          <feComposite in="rx" in2="ry" operator="arithmetic" k2="1" k3="1" result="map" />
          <feGaussianBlur in="SourceGraphic" stdDeviation="12" result="blur" />
          <feDisplacementMap in="blur" in2="map" scale="40" xChannelSelector="R" yChannelSelector="G" result="warp" />
          <feColorMatrix in="warp" type="saturate" values="1.5" />
        </filter>
      </svg>
    </>
  );
}
function Icon({
  name,
}: {
  name: 'library' | 'settings' | 'console' | 'play' | 'comet' | 'minimize' | 'maximize' | 'restore' | 'close';
}): React.JSX.Element {
  const paths = {
    library: 'M4 4h6v6H4z M14 4h6v6h-6z M4 14h6v6H4z M14 14h6v6h-6z',
    settings: 'M4 7h16 M4 17h16 M8 4v6 M16 14v6',
    console: 'm5 6 6 6-6 6 M13 18h6',
    play: 'm8 5 11 7-11 7z',
    comet: 'M19 3 9 7a7 7 0 1 0 8 8l4-10 M8 11a3 3 0 1 0 4 4',
    minimize: 'M5 12h14',
    maximize: 'M5 5h14v14H5z',
    restore: 'M8 8h11v11H8z M5 16V5h11',
    close: 'M6 6l12 12 M18 6 6 18',
  };
  return (
    <svg
      width="22"
      height="22"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  );
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
  const [confirmRemove, setConfirmRemove] = useState(false);
  const [mode, setMode] = useState<Mode>('create');
  const [versions, setVersions] = useState<VersionInfo[]>([]);
  const [snapshots, setSnapshots] = useState(false);
  const [form, setForm] = useState<Draft>(emptyForm);
  const [loaderOptions, setLoaderOptions] = useState<LoaderVersion[] | null>(null);
  const [url, setUrl] = useState('');
  const [query, setQuery] = useState('');
  const [hits, setHits] = useState<ModrinthHit[]>([]);
  const [total, setTotal] = useState(0);
  const [hit, setHit] = useState<ModrinthHit | null>(null);
  const [packVersions, setPackVersions] = useState<ModrinthVersion[]>([]);
  const [packVersion, setPackVersion] = useState('');
  const selected = state.instances.find(i => i.id === selectedId) ?? state.instances[0];
  const locked = state.busy || pending;
  useEffect(() => {
    const bridge = window.comet;
    if (!bridge) return;
    const unsubscribe = bridge.subscribe(setState);
    void bridge
      .invoke({ type: 'snapshot' })
      .then(setState)
      .catch(e => setError(String(e)));
    return unsubscribe;
  }, []);
  useEffect(() => {
    setDraft(state.settings);
  }, [state.settings.clientId, state.settings.javaPath, state.settings.memoryMb, state.settings.minimizeOnLaunch]);
  useEffect(() => {
    if (state.selected) select(state.selected);
  }, [state.selected]);
  useEffect(() => setConfirmRemove(false), [selectedId]);
  useEffect(() => {
    if (page !== 'add' || versions.length) return;
    void ask({ type: 'versions' }).then(list => {
      if (!list) return;
      setVersions(list);
      const release = list.find(v => v.type === 'release');
      if (release) setForm(current => (current.version ? current : { ...current, version: release.id }));
    });
  }, [page]);
  useEffect(() => {
    if (form.loader === 'vanilla' || !form.version) return;
    setLoaderOptions(null);
    void ask({ type: 'loaders', loader: form.loader, version: form.version }).then(list => {
      if (!list) return;
      setLoaderOptions(list);
      setForm(current => ({ ...current, loaderVersion: (list.find(v => v.stable) ?? list[0])?.version }));
    });
  }, [form.loader, form.version]);
  async function run(command: Command): Promise<boolean> {
    setError('');
    if (!window.comet) {
      setError(previewMessage);
      return false;
    }
    if (command.type !== 'cancelLogin') setPending(true);
    try {
      setState(await window.comet.invoke(command));
      return true;
    } catch (e) {
      setError(clean(e));
      return false;
    } finally {
      setPending(false);
    }
  }
  async function ask<Q extends Query>(q: Q): Promise<QueryResult<Q> | null> {
    setError('');
    if (!window.comet) {
      setError(previewMessage);
      return null;
    }
    try {
      return await window.comet.query(q);
    } catch (e) {
      setError(clean(e));
      return null;
    }
  }
  async function finish(command: Command): Promise<void> {
    if (await run(command)) setPage('library');
  }
  async function searchPacks(offset: number): Promise<void> {
    const result = await ask({ type: 'modrinthSearch', query, offset });
    if (!result) return;
    setHits(offset ? [...hits, ...result.hits] : result.hits);
    setTotal(result.total);
  }
  async function choosePack(item: ModrinthHit): Promise<void> {
    setHit(item);
    setPackVersions([]);
    setPackVersion('');
    const list = await ask({ type: 'modrinthVersions', projectId: item.projectId });
    if (!list) return;
    setPackVersions(list);
    setPackVersion(list.find(v => v.supported)?.id ?? '');
  }
  const filtered = state.instances.filter(
    i =>
      (filter === 'all' || (filter === 'comet' ? i.profile !== 'custom' : i.profile === 'custom')) &&
      `${i.name} ${i.version}`.toLowerCase().includes(search.toLowerCase()),
  );
  const versionOptions = versions.filter(v => snapshots || v.type === 'release');
  const heroArt = selected ? art(selected) : null;
  return (
    <div className={`app ${state.platform} ${state.maximized ? 'maximized' : ''}`}>
      <Backdrop />
      <aside className="rail">
        <div className="brand-mark" title="Comet">
          <Icon name="comet" />
        </div>
        <nav aria-label="Main navigation">
          {(['library', 'console', 'settings'] as const).map(item => (
            <button
              key={item}
              title={item[0].toUpperCase() + item.slice(1)}
              aria-label={item}
              aria-current={page === item ? 'page' : undefined}
              className={page === item ? 'nav active' : 'nav'}
              onClick={() => setPage(item)}
            >
              <Icon name={item} />
            </button>
          ))}
        </nav>
      </aside>
      <div className="workspace">
        <header className="topbar">
          <div className="wordmark">
            COMET <span>/</span> <small>{pageNames[page]}</small>
          </div>
          <div className="top-actions">
            <span className={`running ${state.running ? 'live' : ''}`}>
              <i />
              {state.running ? '1 instance running' : 'No instances running'}
            </span>
            <button
              className="account"
              disabled={locked}
              onClick={() => (state.account ? setPage('settings') : void run({ type: 'login' }))}
            >
              <span className="avatar">{state.account?.name.slice(0, 2).toUpperCase() ?? 'MS'}</span>
              {state.account?.name ?? 'Sign in with Microsoft'}
            </button>
            {state.platform !== 'osx' && (
              <div className="window-controls">
                {(
                  [
                    ['minimize', 'Minimize'],
                    [state.maximized ? 'restore' : 'maximize', state.maximized ? 'Restore' : 'Maximize'],
                    ['close', 'Close'],
                  ] as const
                ).map(([icon, label]) => (
                  <button
                    key={label}
                    aria-label={label}
                    title={label}
                    className={icon === 'close' ? 'close' : ''}
                    onClick={() => void run({ type: 'window', action: icon === 'restore' ? 'maximize' : icon })}
                  >
                    <Icon name={icon} />
                  </button>
                ))}
              </div>
            )}
          </div>
        </header>
        <main>
          {error && (
            <div className="error" role="alert">
              <span>{error}</span>
              <button aria-label="Dismiss error" onClick={() => setError('')}>
                ×
              </button>
            </div>
          )}
          {state.deviceCode && (
            <section className="login-panel" aria-live="polite">
              <div>
                <span className="eyebrow">MICROSOFT SIGN-IN</span>
                <h2>Your next stop: microsoft.com/link</h2>
                <p>Enter this code in your browser. Never share it with anyone.</p>
              </div>
              <strong>{state.deviceCode}</strong>
              <button onClick={() => void run({ type: 'cancelLogin' })}>Cancel</button>
            </section>
          )}
          {page === 'library' && selected && (
            <>
              <section className={`hero ${selected.profile}`}>
                {heroArt && <img className="art" src={heroArt} alt="" />}
                <div className="hero-copy">
                  <span className="eyebrow">{eyebrow(selected)}</span>
                  <h2>
                    {selected.name}
                    <span>Minecraft {selected.version}</span>
                  </h2>
                  <div className="hero-actions">
                    <button
                      className="primary"
                      disabled={locked || !!state.running}
                      onClick={() => void run({ type: 'launch', id: selected.id })}
                    >
                      <Icon name="play" />
                      {state.running === selected.id ? 'Game running' : locked ? 'Working…' : 'Launch game'}
                    </button>
                    <span>{loaderNames[selected.loader]}</span>
                  </div>
                </div>
                <div className="version-art" aria-hidden="true">
                  <strong>{selected.version}</strong>
                </div>
              </section>
              <div className="library-layout">
                <section className="library">
                  <div className="section-heading">
                    <h2>
                      Your instances <span>{state.instances.length.toString().padStart(2, '0')}</span>
                    </h2>
                    <input
                      aria-label="Search instances"
                      placeholder="Search instances…"
                      value={search}
                      onChange={e => setSearch(e.target.value)}
                    />
                  </div>
                  <div className="tab-row">
                    <div className="tabs" aria-label="Filter instances">
                      {[
                        ['all', 'All instances'],
                        ['comet', 'Comet'],
                        ['custom', 'Custom'],
                      ].map(([value, label]) => (
                        <button
                          key={value}
                          aria-pressed={filter === value}
                          className={filter === value ? 'selected' : ''}
                          onClick={() => setFilter(value)}
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                    <button className="new" onClick={() => setPage('add')}>
                      New instance
                    </button>
                  </div>
                  <div className="cards">
                    {filtered.map(instance => {
                      const cover = art(instance);
                      return (
                        <button
                          className={`instance ${instance.profile} ${instance.id === selectedId ? 'chosen' : ''}`}
                          key={instance.id}
                          onClick={() => select(instance.id)}
                          aria-pressed={instance.id === selectedId}
                        >
                          <div className={`cover ${cover ? '' : 'generic'}`}>
                            {cover && <img className="art" src={cover} alt="" />}
                            {instance.profile === 'mcsr' && (
                              <img className="badge" src="art/mcsr-ranked.png" alt="MCSR Ranked" />
                            )}
                            {instance.icon && <img className="badge" src={instance.icon} alt="" />}
                            <span>{family(instance)}</span>
                            <strong>{instance.version}</strong>
                          </div>
                          <div className="card-caption">
                            <div>
                              <h3>{instance.name}</h3>
                              <p>{loaderNames[instance.loader]}</p>
                            </div>
                            <Icon name="play" />
                          </div>
                        </button>
                      );
                    })}
                  </div>
                  {filtered.length === 0 && (
                    <p className="empty">
                      {filter === 'custom' && !search ? 'No custom instances yet.' : 'No instances match your search.'}
                    </p>
                  )}
                </section>
                <aside className="details">
                  <h2>{selected.name}</h2>
                  <dl>
                    <div>
                      <dt>Version</dt>
                      <dd>{selected.version}</dd>
                    </div>
                    <div>
                      <dt>Mod loader</dt>
                      <dd>{loaderLabel(selected)}</dd>
                    </div>
                    <div>
                      <dt>Game folder</dt>
                      <dd>{folderNames[selected.directory]}</dd>
                    </div>
                    {selected.profile === 'pvp' && (
                      <>
                        <div>
                          <dt>OptiFine</dt>
                          <dd>
                            {state.optifine.includes(selected.version)
                              ? `${optifineLabels[selected.version]} ready`
                              : 'Not downloaded'}
                          </dd>
                        </div>
                        <div>
                          <dt>Comet client</dt>
                          <dd>{state.client.includes(selected.version) ? 'Ready' : 'Not downloaded'}</dd>
                        </div>
                      </>
                    )}
                    <div>
                      <dt>Memory</dt>
                      <dd>{state.settings.memoryMb / 1024} GB</dd>
                    </div>
                    <div>
                      <dt>Java runtime</dt>
                      <dd>{state.settings.javaPath ? 'Custom override' : 'Managed by Comet'}</dd>
                    </div>
                  </dl>
                  <p className="phase-note">{note(selected)}</p>
                  <div className="detail-buttons">
                    <button
                      disabled={locked || !!state.running}
                      onClick={() => void run({ type: 'install', id: selected.id })}
                    >
                      Install / verify files
                    </button>
                    <button disabled={locked} onClick={() => void run({ type: 'folder', id: selected.id })}>
                      Open game folder <span>↗</span>
                    </button>
                    {selected.profile === 'pvp' && (
                      <>
                        <button
                          disabled={locked || !!state.running}
                          onClick={() => void run({ type: 'optifine', id: selected.id })}
                        >
                          Get OptiFine
                        </button>
                        <button disabled={locked} onClick={() => void run({ type: 'optifineFile', id: selected.id })}>
                          Add OptiFine jar
                        </button>
                      </>
                    )}
                    {selected.profile === 'custom' && (
                      <button
                        className={confirmRemove ? 'danger' : ''}
                        disabled={locked || state.running === selected.id}
                        onClick={() =>
                          confirmRemove ? void run({ type: 'remove', id: selected.id }) : setConfirmRemove(true)
                        }
                      >
                        {confirmRemove ? 'Confirm remove' : 'Remove instance'}
                      </button>
                    )}
                  </div>
                </aside>
              </div>
            </>
          )}
          {page === 'add' && (
            <>
              <div className="page-heading">
                <h1>New instance</h1>
              </div>
              <section className="settings-panel">
                <div className="tabs" aria-label="Instance source">
                  {(
                    [
                      ['create', 'Create'],
                      ['import', 'Import'],
                      ['modrinth', 'Modrinth'],
                    ] as const
                  ).map(([value, label]) => (
                    <button
                      key={value}
                      aria-pressed={mode === value}
                      className={mode === value ? 'selected' : ''}
                      onClick={() => setMode(value)}
                    >
                      {label}
                    </button>
                  ))}
                </div>
                {mode === 'create' && (
                  <div className="settings-section">
                    <label>
                      Name
                      <input
                        value={form.name}
                        placeholder="Survival"
                        onChange={e => setForm({ ...form, name: e.target.value })}
                      />
                    </label>
                    <label>
                      Minecraft version
                      <select value={form.version} onChange={e => setForm({ ...form, version: e.target.value })}>
                        {versionOptions.map(v => (
                          <option key={v.id} value={v.id}>
                            {v.id}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label className="checkbox">
                      <input type="checkbox" checked={snapshots} onChange={e => setSnapshots(e.target.checked)} />
                      Show snapshots
                    </label>
                    <label>
                      Mod loader
                      <div className="choices">
                        {(['vanilla', 'fabric', 'quilt'] as const).map(kind => (
                          <label className="checkbox" key={kind}>
                            <input
                              type="radio"
                              name="loader"
                              checked={form.loader === kind}
                              onChange={() => setForm({ ...form, loader: kind })}
                            />
                            {loaderNames[kind]}
                          </label>
                        ))}
                      </div>
                    </label>
                    {form.loader !== 'vanilla' && (
                      <label>
                        {loaderNames[form.loader]} version
                        <select
                          value={form.loaderVersion ?? ''}
                          onChange={e => setForm({ ...form, loaderVersion: e.target.value })}
                        >
                          {(loaderOptions ?? []).map(v => (
                            <option key={v.version} value={v.version}>
                              {v.version}
                              {v.stable ? '' : ' (beta)'}
                            </option>
                          ))}
                        </select>
                      </label>
                    )}
                    {form.loader !== 'vanilla' && loaderOptions?.length === 0 && (
                      <p className="hint">
                        {loaderNames[form.loader]} is not available for Minecraft {form.version}.
                      </p>
                    )}
                    <label>
                      Game folder
                      <div className="choices">
                        {createFolders.map(option => (
                          <label className="checkbox" key={option.value}>
                            <input
                              type="radio"
                              name="directory"
                              checked={form.directory === option.value}
                              onChange={() => setForm({ ...form, directory: option.value })}
                            />
                            {option.label}
                          </label>
                        ))}
                      </div>
                    </label>
                    <p className="hint">{createFolders.find(option => option.value === form.directory)?.hint}</p>
                    <button
                      className="primary"
                      disabled={
                        locked ||
                        !form.name.trim() ||
                        !form.version ||
                        (form.loader !== 'vanilla' && !form.loaderVersion)
                      }
                      onClick={() =>
                        void finish({
                          type: 'create',
                          draft: { ...form, loaderVersion: form.loader === 'vanilla' ? undefined : form.loaderVersion },
                        })
                      }
                    >
                      Create instance
                    </button>
                  </div>
                )}
                {mode === 'import' && (
                  <div className="settings-section">
                    <p>
                      Modrinth modpacks (.mrpack) and Prism Launcher, PolyMC or MultiMC exports (.zip). Comet uses
                      Modrinth only; CurseForge and Technic are not supported.
                    </p>
                    <label>
                      Local file
                      <div className="path-row">
                        <button disabled={locked} onClick={() => void finish({ type: 'importFile' })}>
                          Browse…
                        </button>
                      </div>
                    </label>
                    <label>
                      Direct download link
                      <div className="path-row">
                        <input value={url} placeholder="https://" onChange={e => setUrl(e.target.value)} />
                        <button
                          className="primary"
                          disabled={locked || !url.trim()}
                          onClick={() => void finish({ type: 'importUrl', url: url.trim() })}
                        >
                          Import
                        </button>
                      </div>
                    </label>
                  </div>
                )}
                {mode === 'modrinth' && (
                  <div className="settings-section">
                    <form
                      className="path-row"
                      onSubmit={e => {
                        e.preventDefault();
                        void searchPacks(0);
                      }}
                    >
                      <input
                        aria-label="Search Modrinth modpacks"
                        placeholder="Search modpacks…"
                        value={query}
                        onChange={e => setQuery(e.target.value)}
                      />
                      <button type="submit" disabled={locked}>
                        Search
                      </button>
                    </form>
                    <div className="results">
                      {hits.map(item => (
                        <button
                          key={item.projectId}
                          className={`result ${hit?.projectId === item.projectId ? 'selected' : ''}`}
                          onClick={() => void choosePack(item)}
                        >
                          {item.icon ? <img src={item.icon} alt="" /> : <span className="result-icon" />}
                          <div>
                            <h3>{item.title}</h3>
                            <p>{item.description}</p>
                          </div>
                        </button>
                      ))}
                    </div>
                    {hits.length > 0 && hits.length < total && (
                      <button onClick={() => void searchPacks(hits.length)}>Load more</button>
                    )}
                    {hit && (
                      <>
                        <label>
                          {hit.title} version
                          <select value={packVersion} onChange={e => setPackVersion(e.target.value)}>
                            {packVersions.map(v => (
                              <option key={v.id} value={v.id} disabled={!v.supported}>
                                {v.name} · {v.gameVersions.join(', ')} · {v.loaders.join(' / ')}
                              </option>
                            ))}
                          </select>
                        </label>
                        <p className="hint">Comet installs Fabric and Quilt packs only.</p>
                        <button
                          className="primary"
                          disabled={locked || !packVersion}
                          onClick={() =>
                            void finish({ type: 'modrinthInstall', projectId: hit.projectId, versionId: packVersion })
                          }
                        >
                          Install modpack
                        </button>
                      </>
                    )}
                  </div>
                )}
              </section>
            </>
          )}
          {page === 'settings' && (
            <>
              <div className="page-heading">
                <h1>Launcher settings</h1>
              </div>
              <section className="settings-panel">
                <div className="settings-section">
                  <h2>Microsoft account</h2>
                  <p>
                    {state.account
                      ? `Signed in as ${state.account.name}. Tokens are encrypted using your system's secure storage.`
                      : 'Sign in with an account that owns Minecraft: Java Edition.'}
                  </p>
                  <label>
                    Comet application (client) ID
                    <input
                      value={draft.clientId}
                      placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
                      onChange={e => setDraft({ ...draft, clientId: e.target.value.trim() })}
                    />
                  </label>
                  <p className="hint">
                    Requires your own Entra public-client registration and Minecraft Services approval. No client secret
                    is needed.
                  </p>
                  <button disabled={locked} onClick={() => void run({ type: state.account ? 'logout' : 'login' })}>
                    {state.account ? 'Sign out' : 'Sign in with Microsoft'}
                  </button>
                </div>
                <div className="settings-section">
                  <h2>Java & memory</h2>
                  <label>
                    Java executable override (optional)
                    <div className="path-row">
                      <input
                        readOnly
                        value={state.settings.javaPath}
                        placeholder="Empty: Comet downloads Mojang's Java runtime"
                      />
                      <button disabled={locked} onClick={() => void run({ type: 'java' })}>
                        Browse…
                      </button>
                      <button
                        disabled={locked || !state.settings.javaPath}
                        onClick={() => void run({ type: 'settings', settings: { ...state.settings, javaPath: '' } })}
                      >
                        Clear
                      </button>
                    </div>
                  </label>
                  <p className="hint">
                    Comet downloads the Java runtime Mojang declares for each version. An override must be 64-bit and
                    match the Java version each instance needs.
                  </p>
                  <label>
                    Maximum memory <span className="memory-value">{draft.memoryMb} MB</span>
                    <input
                      type="range"
                      min="1024"
                      max="16384"
                      step="512"
                      value={draft.memoryMb}
                      onChange={e => setDraft({ ...draft, memoryMb: Number(e.target.value) })}
                    />
                  </label>
                </div>
                <div className="settings-section">
                  <h2>Launch behavior</h2>
                  <label className="checkbox">
                    <input
                      type="checkbox"
                      checked={draft.minimizeOnLaunch}
                      onChange={e => setDraft({ ...draft, minimizeOnLaunch: e.target.checked })}
                    />
                    Minimize Comet when the game process starts
                  </label>
                  <p className="hint">
                    Comet returns when the game exits. Closing the launcher while a game or operation is active
                    minimizes it instead.
                  </p>
                </div>
                <button
                  className="primary"
                  disabled={locked}
                  onClick={() => void run({ type: 'settings', settings: draft })}
                >
                  Save settings
                </button>
              </section>
            </>
          )}
          {page === 'console' && (
            <>
              <div className="page-heading">
                <div>
                  <h1>Game console</h1>
                  <p>Access tokens are redacted before display.</p>
                </div>
              </div>
              <pre className="console" aria-label="Game output">
                {state.logs.length
                  ? state.logs.join('\n')
                  : 'No game output yet. Launch an instance to see its log here.'}
              </pre>
            </>
          )}
        </main>
      </div>
    </div>
  );
}

createRoot(document.getElementById('root')!).render(<App />);
