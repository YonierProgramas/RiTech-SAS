const fs = require('node:fs');
const path = require('node:path');
const newman = require('newman');

function newmanOptions(env = process.env) {
  if (!env.RITECH_E2E_PASSWORD || !env.RITECH_E2E_EXPORTACIONES_USERNAME) {
    throw new Error('Set RITECH_E2E_EXPORTACIONES_USERNAME and RITECH_E2E_PASSWORD; values are never written to the collection.');
  }
  const collection = JSON.parse(fs.readFileSync('postman/RiTech_SAS_API_Tests.postman_collection.json', 'utf8'));
  const environment = JSON.parse(fs.readFileSync('postman/RiTech_Local.postman_environment.json', 'utf8'));
  const replace = (key, value) => {
    const entry = environment.values.find(variable => variable.key === key);
    if (!entry) throw new Error(`Missing Postman environment variable: ${key}`);
    Object.assign(entry, { value, enabled: true });
  };
  replace('username', env.RITECH_E2E_EXPORTACIONES_USERNAME);
  replace('password', env.RITECH_E2E_PASSWORD);
  replace('uploadFile', ''); // CI exercises the existing negative multipart request, never a physical upload.
  return {
    collection, environment, ignoreRedirects: true, timeoutRequest: 10000,
    reporters: ['cli', 'htmlextra'],
    reporter: {
      cli: { noConsole: true },
      htmlextra: { export: path.resolve('target/ci/newman/index.html'), title: 'RiTech SAS - API CI',
        skipSensitiveData: true, showEnvironmentData: false, showGlobalData: false, logs: false },
    },
  };
}

if (require.main === module) {
  fs.mkdirSync('target/ci/newman', { recursive: true });
  newman.run(newmanOptions(), (error, summary) => {
    if (error || !summary) {
      console.error('Newman execution failed; no environment or cookie jar is exported.');
      process.exitCode = 1;
      return;
    }
    const stats = summary.run.stats;
    const complete = stats.requests.total === 38 && stats.assertions.total === 148
      && stats.requests.failed === 0 && stats.assertions.failed === 0
      && summary.run.failures.length === 0 && summary.environment.get('logoutSessionVerified') === 'true';
    const safeSummary = { requests: stats.requests.total, assertions: stats.assertions.total,
      failedRequests: stats.requests.failed, failedAssertions: stats.assertions.failed,
      sessionClosed: summary.environment.get('logoutSessionVerified') === 'true', complete };
    fs.writeFileSync('target/ci/newman-summary.json', JSON.stringify(safeSummary, null, 2));
    console.log(JSON.stringify(safeSummary));
    if (!complete) process.exitCode = 1;
  });
}

module.exports = { newmanOptions };
