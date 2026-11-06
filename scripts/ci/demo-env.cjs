const fs = require('node:fs');
const crypto = require('node:crypto');

function demoEnvironment(env = process.env) {
  let password = env.RITECH_E2E_PASSWORD;
  if (!password) {
    const login = fs.readFileSync('src/main/resources/static/login.html', 'utf8');
    const match = /Contraseña para las 6 cuentas:\s*<code>([^<]+)<\/code>/.exec(login);
    if (!match || !fs.readFileSync('README.md', 'utf8').includes(match[1])) {
      throw new Error('No public DEMO password found; configure the GitHub secret RITECH_E2E_PASSWORD.');
    }
    password = match[1];
  }
  return { RITECH_E2E_PASSWORD: password, RITECH_DEMO_PASSWORD: password };
}

if (require.main === module) {
  if (!process.env.GITHUB_ENV) throw new Error('This bootstrap requires the GitHub Actions GITHUB_ENV file.');
  const values = demoEnvironment();
  const password = values.RITECH_E2E_PASSWORD;
  if (process.env.GITHUB_ACTIONS === 'true') {
    console.log(`::add-mask::${password.replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A')}`);
  }
  for (const [key, value] of Object.entries(values)) {
    const delimiter = crypto.randomUUID();
    fs.appendFileSync(process.env.GITHUB_ENV, `${key}<<${delimiter}\n${value}\n${delimiter}\n`);
  }
  console.log('DEMO credentials configured for Spring Boot and the test clients; values are not logged.');
}

module.exports = { demoEnvironment };
