const { app, BrowserWindow } = require('electron');
const assert = require('node:assert/strict');
const { writeFile } = require('node:fs/promises');
const path = require('node:path');
const os = require('node:os');

async function main() {
  const temporaryRoot = path.join(os.tmpdir(), 'redsun');
  const testData = process.env.COMET_SMOKE_DATA;
  assert(testData, 'Run this test through bun run test:electron');
  app.setPath('userData', testData);
  app.setPath('sessionData', testData);
  const timeout = setTimeout(() => {
    console.error('Electron smoke test timed out');
    app.exit(1);
  }, 45000);
  const created = new Promise(resolve => app.once('browser-window-created', (_event, window) => resolve(window)));
  await import('../dist/main/main.js');
  const window = await created;
  window.webContents.setBackgroundThrottling(false);
  const errors = [];
  window.webContents.on('console-message', (_event, level, message) => {
    if (level >= 3) errors.push(message);
  });
  await new Promise(resolve => window.webContents.once('did-finish-load', resolve));
  const evaluate = expression => window.webContents.executeJavaScript(expression);
  const settle = () => evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))');
  async function until(expression, expected) {
    const deadline = Date.now() + 5000;
    let value = await evaluate(expression);
    while (value !== expected && Date.now() < deadline) {
      await settle();
      value = await evaluate(expression);
    }
    assert.equal(value, expected, expression);
  }
  async function click(selector) {
    const bounds = await evaluate(
      `(() => { const element = document.querySelector(${JSON.stringify(selector)}); if (!element) throw new Error('Missing element'); const r = element.getBoundingClientRect(); return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) }; })()`,
    );
    window.webContents.sendInputEvent({ type: 'mouseDown', button: 'left', clickCount: 1, ...bounds });
    window.webContents.sendInputEvent({ type: 'mouseUp', button: 'left', clickCount: 1, ...bounds });
    await settle();
  }
  try {
    await settle();
    assert.equal(await evaluate('typeof window.comet.invoke'), 'function');
    await until('document.querySelectorAll(".instance").length', 3);
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true);
    assert.equal(await evaluate('document.body.scrollHeight <= innerHeight'), true);
    assert.equal(
      await evaluate('document.querySelectorAll(".window-controls button").length'),
      process.platform === 'darwin' ? 0 : 3,
    );
    assert.equal(
      await evaluate(
        '[...document.querySelectorAll(".window-controls button")].every(b => { const r = b.getBoundingClientRect(); return r.width === r.height; })',
      ),
      true,
    );
    assert.equal(
      await evaluate(
        'document.body.textContent.includes("EARLY ACCESS") || document.body.textContent.includes("JAVA EDITION")',
      ),
      false,
    );
    await evaluate(`window.comet.invoke({ type: 'window', action: 'maximize' })`);
    assert.equal(window.isMaximized(), true);
    await evaluate(`window.comet.invoke({ type: 'window', action: 'maximize' })`);
    assert.equal(window.isMaximized(), false);
    const badAction = await evaluate(
      `window.comet.invoke({ type: 'window', action: 'destroy' }).then(() => '', error => error.message)`,
    );
    assert.match(badAction, /Unknown window action/);
    await click('.instance.mcsr');
    assert.match(await evaluate('document.querySelector(".hero h2").textContent'), /Speedrunning/);
    await click('.tabs button:nth-child(2)');
    await until('document.querySelectorAll(".instance").length', 3);
    await click('.tabs button:nth-child(3)');
    await until('document.querySelectorAll(".instance").length', 0);
    assert.match(await evaluate('document.querySelector(".empty").textContent'), /No custom instances/);
    await click('.tabs button:first-child');
    await click('.instance:first-child');
    assert.match(await evaluate('document.querySelector(".details dl").textContent'), /Comet shared folder/);
    await click('.new');
    await until('document.querySelector("h1")?.textContent', 'New instance');
    await click('.tabs button:nth-child(2)');
    assert.match(await evaluate('document.querySelector(".settings-section").textContent'), /Prism Launcher/);
    const badDraft = await evaluate(
      `window.comet.invoke({ type: 'create', draft: { name: 'x', version: '../1', loader: 'vanilla', directory: 'isolated' } }).then(() => '', error => error.message)`,
    );
    assert.match(badDraft, /Invalid Minecraft version/);
    const badQuery = await evaluate(
      `window.comet.query({ type: 'modrinthVersions', projectId: '../x' }).then(() => '', error => error.message)`,
    );
    assert.match(badQuery, /Invalid Modrinth id/);
    await click('[aria-label="library"]');
    await until('document.querySelectorAll(".instance").length', 3);
    const screenshot = await window.webContents.capturePage();
    const screenshotPath = path.join(temporaryRoot, 'comet-launchpad.png');
    await writeFile(screenshotPath, screenshot.toPNG());
    await click('[aria-label="settings"]');
    await until('document.querySelector("h1")?.textContent', 'Launcher settings');
    await evaluate(
      `window.comet.invoke({ type: 'settings', settings: { clientId: '', javaPath: '', memoryMb: 3072, minimizeOnLaunch: false } })`,
    );
    await settle();
    await until('document.querySelector("input[type=checkbox]").checked', false);
    assert.equal(
      await evaluate('(async () => (await window.comet.invoke({ type: "snapshot" })).settings.memoryMb)()'),
      3072,
    );
    const loginError = await evaluate(`window.comet.invoke({ type: 'login' }).then(() => '', error => error.message)`);
    assert.match(loginError, /approved Microsoft|secure storage/);
    const badInstance = await evaluate(
      `window.comet.invoke({ type: 'folder', id: '../../outside' }).then(() => '', error => error.message)`,
    );
    assert.match(badInstance, /Unknown instance/);
    await click('[aria-label="console"]');
    assert.match(await evaluate('document.querySelector("pre").textContent'), /No game output/);
    assert.deepEqual(errors, []);
    console.log(
      `Electron smoke passed: navigation, window controls, selection, filtering, new-instance page, draft and query validation, settings IPC, auth setup error, traversal rejection, console. Screenshot: ${screenshotPath}`,
    );
  } finally {
    clearTimeout(timeout);
    app.removeAllListeners('window-all-closed');
    for (const open of BrowserWindow.getAllWindows()) open.destroy();
  }
  app.exit(0);
}
main().catch(error => {
  console.error(error);
  app.exit(1);
});
