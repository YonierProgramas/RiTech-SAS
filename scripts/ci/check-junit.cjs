const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

function checkJUnit(directory = 'target/surefire-reports') {
  const reports = fs.readdirSync(directory).filter(file => /^TEST-.*\.xml$/.test(file));
  assert.ok(reports.length, 'No Surefire XML reports found.');
  const totals = { tests: 0, failures: 0, errors: 0, skipped: 0 };
  let integration = false;
  for (const file of reports) {
    const header = fs.readFileSync(path.join(directory, file), 'utf8').match(/<testsuite\s[^>]*>/)?.[0];
    assert.ok(header, `Invalid Surefire report: ${file}`);
    const counts = {};
    for (const key of Object.keys(totals)) {
      const match = new RegExp(`\\b${key}="(\\d+)"`).exec(header);
      assert.ok(match, `Missing ${key} in ${file}`);
      counts[key] = Number(match[1]);
      totals[key] += counts[key];
    }
    if (file.includes('ExportacionMySqlRepositoryTest')) {
      integration = counts.tests > 0 && counts.skipped === 0 && counts.errors === 0 && counts.failures === 0;
    }
  }
  assert.ok(integration, 'The real MySQL integration test must execute successfully, not skip.');
  assert.ok(totals.tests >= 161, 'Expected at least the current 161 JUnit tests.');
  for (const key of ['failures', 'errors', 'skipped']) assert.equal(totals[key], 0, `JUnit ${key}`);
  return totals;
}
if (require.main === module) console.log(JSON.stringify(checkJUnit()));
module.exports = { checkJUnit };
