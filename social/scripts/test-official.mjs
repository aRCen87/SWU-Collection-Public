// Run the complete community suite against the official rules, exclusively in local emulators.
import { mkdtempSync, mkdirSync, readFileSync, writeFileSync, readdirSync, rmSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
const root = resolve(import.meta.dirname, '..');
const temp = mkdtempSync(join(root, '.official-security-'));
try {
  mkdirSync(join(temp, 'tests'));
  writeFileSync(join(temp, 'package.json'), '{"type":"module"}');
  writeFileSync(join(temp, 'firestore.rules'), readFileSync(join(root, 'official/firestore.rules')));
  writeFileSync(join(temp, 'firestore.indexes.json'), readFileSync(join(root, 'firestore.indexes.json')));
  writeFileSync(join(temp, 'firebase.json'), readFileSync(join(root, 'firebase.json')));
  for (const name of readdirSync(join(root, 'tests'))) {
    if (!name.endsWith('.test.mjs')) continue;
    const source = readFileSync(join(root, 'tests', name), 'utf8')
      .replaceAll('demo-swu-community-test', 'demo-swu-community-official')
      .replaceAll('testMembers', 'members');
    writeFileSync(join(temp, 'tests', name), source);
  }
  const cli = join(root, 'node_modules/firebase-tools/lib/bin/firebase.js');
  const result = spawnSync(process.execPath, [cli, 'emulators:exec', '--project', 'demo-swu-community-official', '--only', 'auth,firestore', 'node --test --test-concurrency=1 tests/*.test.mjs'], { cwd: temp, stdio: 'inherit', env: process.env });
  if (result.error) throw result.error;
  process.exitCode = result.status ?? 1;
} finally { rmSync(temp, { recursive: true, force: true }); }
