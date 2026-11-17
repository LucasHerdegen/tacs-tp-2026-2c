#!/usr/bin/env node
import { existsSync } from 'node:fs';
import { createConnection } from 'node:net';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '..', '..');

function checkTcp(host, port, timeoutMs = 200) {
  return new Promise((resolvePromise) => {
    const socket = createConnection({ host, port });
    let settled = false;
    const finish = (reachable) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      resolvePromise(reachable);
    };
    socket.setTimeout(timeoutMs);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
  });
}

async function main() {
  // Drain stdin without blocking if caller closes or sends payload
  if (!process.stdin.isTTY) {
    process.stdin.resume();
  }

  const hasEnv = existsSync(resolve(repoRoot, '.env'));
  const hasCorreccionesContexto = existsSync(
    resolve(repoRoot, 'correcciones', 'contexto'),
  );
  const mongoUp = await checkTcp('127.0.0.1', 27017, 200);
  const isWin = process.platform === 'win32';
  const mvnCmd = isWin ? '.\\mvnw.cmd' : './mvnw';

  const lines = [
    `[TACS Workspace Context] OS=${process.platform} (usar \`${mvnCmd}\` en \`backend/\`).`,
    `.env raíz: ${hasEnv ? 'presente' : 'ausente (revisar .env.example antes de levantar Docker)'}.`,
    `MongoDB local (127.0.0.1:27017): ${
      mongoUp
        ? 'accesible'
        : 'no detectado (tests @DataMongoTest usarán TestMongoFallbackConfig en memoria tras timeout; levantar con `docker compose up -d mongodb` si probás persistencia/concurrencia real)'
    }.`,
  ];

  if (hasCorreccionesContexto) {
    lines.push(
      'Enunciados y devoluciones locales detectados en `correcciones/` (recordar que están en .gitignore).',
    );
  }

  const output = {
    injectSteps: [
      {
        systemMessage: {
          systemMessage: lines.join(' '),
        },
      },
    ],
  };

  process.stdout.write(JSON.stringify(output));
}

main().catch(() => {
  process.stdout.write('{}');
});
