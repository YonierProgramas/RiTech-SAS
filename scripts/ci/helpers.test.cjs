const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { zipSync, unzipSync } = require('fflate');
const { createRedactor } = require('./sanitize-artifacts.cjs');
const { demoEnvironment } = require('./demo-env.cjs');
const { checkJUnit } = require('./check-junit.cjs');
const { newmanOptions } = require('./run-newman.cjs');

test('Artifact sanitization removes raw, form, JSON and HTML representations of a password', () => {
  const secret = 'CI-test-only<&"credential';
  const redactor = createRedactor([secret]);
  const escaped = secret.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  const bytes = Buffer.from([secret, encodeURIComponent(secret), JSON.stringify(secret), escaped].join('\n'));
  const safe = redactor.sanitize(bytes);
  redactor.inspect(safe);
  assert.ok(!safe.toString().includes(secret));
});

test('Trace JSONL discovers cookie and CSRF values, including their reuse in action snapshots', () => {
  const cookie = 'ci-cookie-0123456789', token = 'ci-csrf-0123456789';
  const bytes = Buffer.from([
    JSON.stringify({ request: { headers: [{ name: 'Cookie', value: `JSESSIONID=${cookie}` }] } }),
    JSON.stringify({ token, headerName: 'X-XSRF-TOKEN', parameterName: '_csrf' }),
    JSON.stringify({ params: { value: cookie }, snapshot: ['INPUT', { name: '_csrf', value: token }] }),
  ].join('\n'));
  const redactor = createRedactor();
  redactor.collect(bytes);
  const safe = redactor.sanitize(bytes);
  redactor.inspect(safe);
  assert.ok(!safe.toString().includes(cookie));
  assert.ok(!safe.toString().includes(token));
  for (const line of safe.toString().split('\n')) JSON.parse(line);
});

test('Nested Playwright report ZIP and trace ZIP are sanitized and remain readable', () => {
  const secret = 'ci-artifact-fixture-password';
  const trace = zipSync({ 'test.trace': Buffer.from(JSON.stringify({ params: { value: secret } })) });
  const report = zipSync({ 'report.json': Buffer.from(JSON.stringify({ title: `Fill ${secret}` })), 'trace.zip': trace });
  const html = Buffer.from(`<script>data:application/zip;base64,${Buffer.from(report).toString('base64')}</script>`);
  const redactor = createRedactor([secret]);
  redactor.collect(html);
  const safe = redactor.sanitize(html);
  redactor.inspect(safe);
  const payload = safe.toString().match(/base64,([A-Za-z0-9+/=]+)/)[1];
  const entries = unzipSync(Buffer.from(payload, 'base64'));
  assert.equal(JSON.parse(Buffer.from(entries['report.json']).toString()).title, 'Fill [REDACTED]');
  assert.equal(JSON.parse(Buffer.from(unzipSync(entries['trace.zip'])['test.trace']).toString()).params.value, '[REDACTED]');
});

test('Base64 JSON report attachments cannot conceal a CSRF token', () => {
  const bytes = Buffer.from(JSON.stringify({ contentType: 'application/json',
    body: Buffer.from(JSON.stringify({ token: 'ci-base64-csrf-fixture' })).toString('base64') }));
  const redactor = createRedactor();
  redactor.collect(bytes);
  const safe = redactor.sanitize(bytes);
  redactor.inspect(safe);
  const body = JSON.parse(Buffer.from(JSON.parse(safe.toString()).body, 'base64').toString());
  assert.equal(body.token, '[REDACTED]');
});

test('Binary diagnostics are preserved; inspection blocks an unsanitized ZIP', () => {
  const bytes = Buffer.from([0xff, 0xfe, 0x80, 0, 1, 2, 3]);
  const redactor = createRedactor(['ci-fixture-sensitive']);
  assert.deepEqual(redactor.sanitize(bytes), bytes);
  redactor.inspect(bytes);
  assert.throws(() => redactor.inspect(zipSync({ 'secret.txt': Buffer.from('ci-fixture-sensitive') })), /publication blocked/);
});

test('Base64 attachments inside JSONL traces are inspected and redacted', () => {
  const attachment = { contentType: 'application/json', body: Buffer.from(JSON.stringify({ token: 'ci-jsonl-csrf-fixture' })).toString('base64') };
  const bytes = Buffer.from(JSON.stringify({ type: 'before' }) + '\n' + JSON.stringify(attachment));
  const redactor = createRedactor();
  redactor.collect(bytes);
  assert.throws(() => redactor.inspect(bytes), /publication blocked/);
  const safe = redactor.sanitize(bytes);
  redactor.inspect(safe);
  const body = JSON.parse(Buffer.from(JSON.parse(safe.toString().split('\n')[1]).body, 'base64').toString());
  assert.equal(body.token, '[REDACTED]');
});

test('DEMO bootstrap prefers the injected secret and uses only the documented public fallback', () => {
  const env = demoEnvironment({ RITECH_E2E_PASSWORD: 'ci-fixture-custom-password' });
  assert.equal(env.RITECH_E2E_PASSWORD, 'ci-fixture-custom-password');
  assert.equal(env.RITECH_DEMO_PASSWORD, env.RITECH_E2E_PASSWORD);
  const fallback = demoEnvironment({});
  assert.ok(fs.readFileSync('src/main/resources/static/login.html', 'utf8').includes(fallback.RITECH_E2E_PASSWORD));
  assert.ok(fs.readFileSync('README.md', 'utf8').includes(fallback.RITECH_E2E_PASSWORD));
});

test('JUnit gate rejects an omitted MySQL integration test', t => {
  fs.mkdirSync('target', { recursive: true });
  const directory = fs.mkdtempSync(path.resolve('target/ci-helper-tests-'));
  t.after(() => {
    assert.equal(path.dirname(directory), path.resolve('target'));
    assert.ok(path.basename(directory).startsWith('ci-helper-tests-'));
    fs.rmSync(directory, { recursive: true, force: true });
  });
  fs.writeFileSync(path.join(directory, 'TEST-Units.xml'), '<testsuite tests="160" failures="0" errors="0" skipped="0"></testsuite>');
  const integration = path.join(directory, 'TEST-ExportacionMySqlRepositoryTest.xml');
  fs.writeFileSync(integration, '<testsuite tests="1" failures="0" errors="0" skipped="1"></testsuite>');
  assert.throws(() => checkJUnit(directory), /must execute/);
  fs.writeFileSync(integration, '<testsuite tests="1" failures="0" errors="0" skipped="0"></testsuite>');
  assert.deepEqual(checkJUnit(directory), { tests: 161, failures: 0, errors: 0, skipped: 0 });
});

test('Newman injects credentials in memory and suppresses sensitive report sections', () => {
  const original = fs.readFileSync('postman/RiTech_Local.postman_environment.json', 'utf8');
  const options = newmanOptions({ RITECH_E2E_PASSWORD: 'ci-fixture-custom-password',
    RITECH_E2E_EXPORTACIONES_USERNAME: 'exportaciones@ritech.example' });
  assert.equal(options.environment.values.find(v => v.key === 'password').value, 'ci-fixture-custom-password');
  assert.equal(options.environment.values.find(v => v.key === 'uploadFile').value, '');
  assert.equal(options.reporter.htmlextra.skipSensitiveData, true);
  assert.equal(options.reporter.htmlextra.showEnvironmentData, false);
  assert.equal(fs.readFileSync('postman/RiTech_Local.postman_environment.json', 'utf8'), original);
  assert.throws(() => newmanOptions({}), /Set RITECH/);
});
