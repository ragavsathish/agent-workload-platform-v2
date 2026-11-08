import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { chromium } from 'playwright';

const clientRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const bridgeRoot = path.join(clientRoot, 'web', 'voice-runtime');

test('@owner-client-009 generated MCP App iframe blocks parent credential access', async () => {
  const server = createServer(async (request, response) => {
    const pathname = new URL(request.url, 'http://localhost').pathname;
    if (pathname === '/') {
      response.writeHead(200, { 'content-type': 'text/html' });
      response.end(`<!doctype html><div id="host" style="width:400px;height:300px"></div><script type="module">import '/voice-runtime/voice_bridge.js';</script>`);
      return;
    }
    if (pathname === '/mermaid/index.html') {
      response.writeHead(200, { 'content-type': 'text/html' });
      response.end('<!doctype html><body>isolated MCP content</body><script>let secret; try { secret = parent.localStorage.getItem("private-token") ?? "empty"; } catch { secret = "blocked"; } parent.postMessage({ origin: location.origin, secret }, "*");</script>');
      return;
    }
    if (pathname.startsWith('/voice-runtime/')) {
      try {
        response.writeHead(200, { 'content-type': 'text/javascript' });
        response.end(await readFile(path.join(bridgeRoot, path.basename(pathname))));
      } catch {
        response.writeHead(404).end();
      }
      return;
    }
    response.writeHead(404).end();
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage();
    await page.goto(`http://127.0.0.1:${address.port}/`);
    await page.evaluate(() => localStorage.setItem('private-token', 'owner-secret'));
    await page.evaluate(() => window.addEventListener('message', ({ data, origin }) => { window.__childProbe = { ...data, messageOrigin: origin }; }));
    await page.waitForFunction(() => window.voiceBridge !== undefined);
    await page.evaluate(() => {
      window.pendingMount = window.voiceBridge.mountMcpApp(
        'host', JSON.stringify({ kind: 'flow', nodes: [] }), 'ui://mermaid/main',
      );
    });
    const frame = page.locator('#host iframe');
    await frame.waitFor();
    await page.waitForFunction(() => window.__childProbe !== undefined);

    const policy = await frame.evaluate((iframe) => ({
      sandbox: iframe.getAttribute('sandbox'),
      allow: iframe.getAttribute('allow'),
    }));
    assert.equal(policy.sandbox, 'allow-scripts');
    assert.equal(policy.sandbox.includes('allow-same-origin'), false);
    assert.equal(policy.allow, 'fullscreen');
    const isolation = await page.evaluate(() => window.__childProbe);
    assert.equal(isolation.messageOrigin, 'null');
    assert.equal(isolation.secret, 'blocked');
  } finally {
    await browser.close();
    await new Promise((resolve) => server.close(resolve));
  }
});
