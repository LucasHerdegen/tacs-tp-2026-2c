#!/usr/bin/env node
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..', '..', '..', '..');
const backendDir = resolve(repoRoot, 'backend');
const frontendDir = resolve(repoRoot, 'frontend');

const args = process.argv.slice(2);
let runBackend = args.includes('--backend');
let runFrontend = args.includes('--frontend');
const skipSpotless = args.includes('--skip-spotless');
const onlySpotless = args.includes('--spotless-only');
const testFlagIdx = args.indexOf('--test');
const testFilter = testFlagIdx !== -1 ? args[testFlagIdx + 1] : null;

if (testFlagIdx !== -1 && !testFilter) {
  console.error('Error: --test requiere el nombre de la clase o patrón de test.');
  process.exit(2);
}

if (!runBackend && !runFrontend) {
  runBackend = true;
  runFrontend = !onlySpotless;
}

const isWin = process.platform === 'win32';
const mvnwCmd = isWin ? 'mvnw.cmd' : './mvnw';
const npmCmd = isWin ? 'npm.cmd' : 'npm';

const results = [];

function runStep(label, command, cmdArgs, cwd) {
  console.log(`\n=== ${label} (${cwd}) ===`);
  const res = spawnSync(command, cmdArgs, {
    cwd,
    stdio: 'inherit',
    shell: isWin,
    env: {
      ...process.env,
      JWT_SECRET:
        process.env.JWT_SECRET ||
        'dummy-jwt-secret-key-needs-to-be-long-enough-32bytes',
      WEATHERAPI_API_KEY:
        process.env.WEATHERAPI_API_KEY || 'dummy-weather-api-key',
      TELEGRAM_BOT_TOKEN:
        process.env.TELEGRAM_BOT_TOKEN || 'dummy-bot-token',
      TELEGRAM_BOT_USERNAME:
        process.env.TELEGRAM_BOT_USERNAME || 'dummybotusername',
    },
  });
  const ok = res.status === 0;
  results.push({ label, ok, code: res.status ?? 1 });
  return ok;
}

if (runBackend) {
  if (onlySpotless) {
    runStep('Backend: Spotless Check', mvnwCmd, ['spotless:check'], backendDir);
  } else {
    const compiled = runStep(
      'Backend: Maven Clean Compile',
      mvnwCmd,
      ['clean', 'compile'],
      backendDir,
    );
    if (compiled) {
      const testArgs = testFilter ? ['test', `-Dtest=${testFilter}`] : ['test'];
      runStep('Backend: Maven Test', mvnwCmd, testArgs, backendDir);
    }
    if (!skipSpotless) {
      runStep('Backend: Spotless Check', mvnwCmd, ['spotless:check'], backendDir);
    }
  }
}

if (runFrontend) {
  runStep('Frontend: ESLint', npmCmd, ['run', 'lint'], frontendDir);
  runStep('Frontend: TypeCheck & Build', npmCmd, ['run', 'build'], frontendDir);
}

console.log('\n=== Resumen de Verificación ===');
let anyFailed = false;
for (const r of results) {
  const badge = r.ok ? 'PASS' : `FAIL (exit ${r.code})`;
  console.log(`- ${r.label}: ${badge}`);
  if (!r.ok) anyFailed = true;
}

process.exit(anyFailed ? 1 : 0);
