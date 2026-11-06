const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const { unzipSync, zipSync } = require('fflate');

const REDACTED = '[REDACTED]';
const sensitiveKey = /^(?:password|token|csrfToken|csrf|_csrf|authorization|cookie|set-cookie|JSESSIONID|XSRF-TOKEN|X-XSRF-TOKEN|X-CSRF-TOKEN)$/i;
const embeddedZip = /data:application\/zip;base64,([A-Za-z0-9+/=]+)/g;
const decoder = new TextDecoder('utf-8', { fatal: true });
const isZip = bytes => bytes[0] === 0x50 && bytes[1] === 0x4b && bytes[2] === 3 && bytes[3] === 4;
function decode(bytes) { try { return decoder.decode(bytes); } catch { return null; } }
function parseJSON(text) { try { return { value: JSON.parse(text) }; } catch { return null; } }
function attachmentBytes(value) {
  if (!/^(?:text\/|application\/(?:[^;]*\+)?json)/i.test(String(value?.contentType || value?.mimeType || ''))
    || typeof value?.body !== 'string' || !/^[A-Za-z0-9+/]+={0,2}$/.test(value.body)) return null;
  const bytes = Buffer.from(value.body, 'base64');
  return bytes.toString('base64').replace(/=+$/, '') === value.body.replace(/=+$/, '') && decode(bytes) !== null ? bytes : null;
}

function createRedactor(initialSecrets = []) {
  const secrets = new Set(initialSecrets.filter(value => typeof value === 'string' && value));
  function add(value) {
    if (typeof value !== 'string' || value.length < 8 || value.includes(REDACTED)) return;
    secrets.add(value);
    // Header and cookie-list values can appear again in a DOM snapshot or JSON body.
    for (const part of value.split(';')) {
      const index = part.indexOf('=');
      if (index > 0 && /cookie|token|csrf|session/i.test(part.slice(0, index))) add(part.slice(index + 1).trim());
    }
  }
  function visit(value) {
    if (!value || typeof value !== 'object') return;
    if (sensitiveKey.test(String(value.name || value.key || ''))) add(value.value);
    for (const [key, item] of Object.entries(value)) {
      if (sensitiveKey.test(key)) add(item);
      if (item && typeof item === 'object') visit(item);
      else if (typeof item === 'string' && /^[\s]*[\[{]/.test(item)) {
        try { visit(JSON.parse(item)); } catch { /* A normal text string, not nested JSON. */ }
      }
    }
    const body = attachmentBytes(value);
    if (body) collect(body);
  }
  function collect(bytes) {
    if (isZip(bytes)) {
      for (const entry of Object.values(unzipSync(bytes))) collect(entry);
      return;
    }
    const text = decode(bytes);
    if (text === null) return;
    for (const match of text.matchAll(embeddedZip)) collect(Buffer.from(match[1], 'base64'));
    try { visit(JSON.parse(text)); }
    catch { for (const line of text.split(/\r?\n/)) { try { visit(JSON.parse(line)); } catch { /* Non-JSON diagnostics. */ } } }
  }
  function variants() {
    return [...new Set([...secrets].flatMap(value => [value, encodeURIComponent(value),
      new URLSearchParams({ value }).toString().slice(6), JSON.stringify(value).slice(1, -1),
      value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#x27;'),
      Buffer.from(value).toString('base64')]))].filter(Boolean).sort((a, b) => b.length - a.length);
  }
  function mask(text) {
    for (const value of variants()) text = text.split(value).join(REDACTED);
    return text;
  }
  function sanitize(bytes) {
    if (isZip(bytes)) {
      const entries = unzipSync(bytes);
      for (const name of Object.keys(entries)) entries[name] = sanitize(entries[name]);
      return Buffer.from(zipSync(entries, { level: 6 }));
    }
    let text = decode(bytes);
    if (text === null) return Buffer.from(bytes); // Keep screenshots/videos byte-for-byte.
    function rewriteAttachments(value) {
      if (!value || typeof value !== 'object') return;
      for (const item of Object.values(value)) if (item && typeof item === 'object') rewriteAttachments(item);
      const body = attachmentBytes(value);
      if (body) value.body = sanitize(body).toString('base64');
    }
    const parsed = parseJSON(text);
    if (parsed) {
      rewriteAttachments(parsed.value);
      text = JSON.stringify(parsed.value);
    } else {
      text = text.split('\n').map(line => {
        const entry = parseJSON(line);
        if (!entry) return line;
        rewriteAttachments(entry.value);
        return JSON.stringify(entry.value);
      }).join('\n');
    }
    let result = '', cursor = 0;
    for (const match of text.matchAll(embeddedZip)) {
      result += mask(text.slice(cursor, match.index));
      result += 'data:application/zip;base64,' + sanitize(Buffer.from(match[1], 'base64')).toString('base64');
      cursor = match.index + match[0].length;
    }
    return Buffer.from(result + mask(text.slice(cursor)));
  }
  function inspect(bytes) {
    if (isZip(bytes)) {
      for (const entry of Object.values(unzipSync(bytes))) inspect(entry);
      return;
    }
    const text = decode(bytes);
    if (text !== null) {
      let plain = '', cursor = 0;
      for (const match of text.matchAll(embeddedZip)) {
        plain += text.slice(cursor, match.index);
        inspect(Buffer.from(match[1], 'base64'));
        cursor = match.index + match[0].length;
      }
      plain += text.slice(cursor);
      for (const value of variants()) assert.ok(!plain.includes(value), 'Sensitive value detected: artifact publication blocked.');
      function inspectAttachments(value) {
        if (!value || typeof value !== 'object') return;
        const body = attachmentBytes(value);
        if (body) inspect(body);
        for (const item of Object.values(value)) if (item && typeof item === 'object') inspectAttachments(item);
      }
      const parsed = parseJSON(plain);
      if (parsed) inspectAttachments(parsed.value);
      else for (const line of plain.split('\n')) {
        const entry = parseJSON(line);
        if (entry) inspectAttachments(entry.value);
      }
    } else {
      for (const value of secrets) assert.ok(!Buffer.from(bytes).includes(Buffer.from(value)), 'Sensitive binary artifact blocked.');
    }
  }
  return { collect, sanitize, inspect, mask };
}

function runtimeSecrets(env = process.env) {
  return ['RITECH_E2E_PASSWORD', 'RITECH_DEMO_PASSWORD', 'RITECH_DB_PASSWORD', 'RITECH_TEST_DB_PASSWORD']
    .map(key => env[key]).filter(Boolean);
}

function filesIn(directory) {
  if (!fs.existsSync(directory)) return [];
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const file = path.join(directory, entry.name);
    assert.ok(!entry.isSymbolicLink(), 'Artifact symlinks are not permitted.');
    return entry.isDirectory() ? filesIn(file) : [file];
  });
}

function prepareArtifacts() {
  const root = path.resolve('target/ci-artifacts');
  // Verify the resolved deletion target is exactly the generated directory inside this workspace.
  assert.equal(path.dirname(root), path.resolve('target'));
  assert.equal(path.basename(root), 'ci-artifacts');
  assert.ok(!path.relative(process.cwd(), root).startsWith('..'));
  fs.rmSync(root, { recursive: true, force: true });
  const groups = [
    { output: 'playwright_report', source: 'playwright-report', name: 'playwright-report', entry: 'index.html' },
    { output: 'playwright_test_results', source: 'test-results', name: 'test-results' },
    { output: 'newman_report', source: 'target/ci/newman', name: 'newman-report', entry: 'index.html' },
  ];
  const redactor = createRedactor(runtimeSecrets());
  for (const group of groups) {
    group.files = filesIn(group.source).filter(file => path.basename(file) !== '.last-run.json');
    if (group.entry && !fs.existsSync(path.join(group.source, group.entry))) group.files = [];
    for (const file of group.files) redactor.collect(fs.readFileSync(file));
  }
  const outputs = {};
  for (const group of groups) {
    for (const file of group.files) {
      const data = redactor.sanitize(fs.readFileSync(file));
      redactor.inspect(data); // Recursively inspect the final bytes, including ZIPs embedded in HTML.
      const target = path.join(root, group.name, path.relative(group.source, file));
      fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.writeFileSync(target, data);
    }
    outputs[group.output] = group.files.length > 0;
  }
  // Write flags only after every output has passed inspection. No unverified artifact is uploaded.
  if (process.env.GITHUB_OUTPUT) {
    for (const [key, value] of Object.entries(outputs)) fs.appendFileSync(process.env.GITHUB_OUTPUT, `${key}=${value}\n`);
  }
  console.log(JSON.stringify({ inspected: true, ...outputs }));
  return outputs;
}

if (require.main === module) {
  if (process.argv[2] === 'log') {
    const redactor = createRedactor(runtimeSecrets());
    const bytes = fs.readFileSync(process.argv[3]);
    redactor.collect(bytes);
    const safe = redactor.sanitize(bytes);
    redactor.inspect(safe);
    console.log(safe.toString('utf8'));
  } else prepareArtifacts();
}
module.exports = { createRedactor, prepareArtifacts };
