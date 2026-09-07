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
  const timeout = setTimeout(() => { console.error('Electron smoke test timed out'); app.exit(1); }, 45000);
  const created = new Promise(resolve => app.once('browser-window-created', (_event, window) => resolve(window)));
  await import('../dist/main/main.js');
  const window = await created;
  const errors = [];
  window.webContents.on('console-message', (_event, level, message) => { if (level >= 3) errors.push(message); });
  await new Promise(resolve => window.webContents.once('did-finish-load', resolve));
  const evaluate = expression => window.webContents.executeJavaScript(expression);
  const settle = () => evaluate('new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))');
  async function click(selector) {
    const bounds = await evaluate(`(() => { const element = document.querySelector(${JSON.stringify(selector)}); if (!element) throw new Error('Missing element'); const r = element.getBoundingClientRect(); return { x: Math.round(r.x + r.width / 2), y: Math.round(r.y + r.height / 2) }; })()`);
    window.webContents.sendInputEvent({ type: 'mouseDown', button: 'left', clickCount: 1, ...bounds });
    window.webContents.sendInputEvent({ type: 'mouseUp', button: 'left', clickCount: 1, ...bounds });
    await settle();
  }
  try {
    await settle();
    assert.equal(await evaluate('typeof window.comet.invoke'), 'function');
    assert.equal(await evaluate('document.querySelectorAll(".instance").length'), 3);
    assert.equal(await evaluate('document.documentElement.scrollWidth <= innerWidth'), true);
    await click('.instance.mcsr');
    assert.match(await evaluate('document.querySelector(".hero h2").textContent'), /Speedrunning/);
    await click('.tabs button:nth-child(2)');
    assert.equal(await evaluate('document.querySelectorAll(".instance").length'), 2);
    await click('.tabs button:first-child');
    await click('.instance:first-child');
    const screenshot = await window.webContents.capturePage();
    const screenshotPath = path.join(temporaryRoot, 'comet-launchpad.png');
    await writeFile(screenshotPath, screenshot.toPNG());
    await click('[aria-label="settings"]');
    assert.equal(await evaluate('document.querySelector("h1").textContent'), 'Launcher settings');
    await evaluate(`window.comet.invoke({ type: 'settings', settings: { clientId: '', javaPath: '', memoryMb: 3072, minimizeOnLaunch: false } })`);
    await settle();
    assert.equal(await evaluate('document.querySelector("input[type=checkbox]").checked'), false);
    assert.equal(await evaluate('(async () => (await window.comet.invoke({ type: "snapshot" })).settings.memoryMb)()'), 3072);
    const loginError = await evaluate(`window.comet.invoke({ type: 'login' }).then(() => '', error => error.message)`);
    assert.match(loginError, /approved Microsoft/);
    const badInstance = await evaluate(`window.comet.invoke({ type: 'folder', id: '../../outside' }).then(() => '', error => error.message)`);
    assert.match(badInstance, /Unknown instance/);
    await click('[aria-label="console"]');
    assert.match(await evaluate('document.querySelector("pre").textContent'), /No game output/);
    assert.deepEqual(errors, []);
    console.log(`Electron smoke passed: navigation, selection, filtering, settings IPC, auth setup error, traversal rejection, console. Screenshot: ${screenshotPath}`);
  } finally {
    clearTimeout(timeout);
    app.removeAllListeners('window-all-closed');
    for (const open of BrowserWindow.getAllWindows()) open.destroy();
  }
  app.exit(0);
}
main().catch(error => { console.error(error); app.exit(1); });
