import { createHash } from 'node:crypto'
import { spawnSync } from 'node:child_process'
import { mkdirSync, writeFileSync, copyFileSync, existsSync, readFileSync, readdirSync, realpathSync } from 'node:fs'
import { dirname, resolve, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const mode = process.argv[2] ?? 'baseline'
if (!['baseline', 'mock', 'health', 'dev', 'a1', 'a1-mock', 'a2', 'a2-mock'].includes(mode)) throw new Error('Use baseline | mock | a1 | a1-mock | a2 | a2-mock | health | dev <snapshot>; real API uses real-smoke.mjs')
const stamp = new Date().toISOString().replace(/[:.]/g, '-')
const out = resolve(root, mode.startsWith('a2') ? 'artifacts/a2/runs' : mode.startsWith('a1') ? 'artifacts/a1/runs' : 'artifacts/a0/runs', stamp)
const testSelector = mode.startsWith('a2') ? 'A0BaselineTest,A1*Test,A2*Test' : mode.startsWith('a1') ? 'A0BaselineTest,A1*Test' : 'A0BaselineTest'
const workBase = resolve(root, 'tools/dev-harness/.work')
const work = mode === 'dev' ? realpathSync(resolve(process.argv[3] ?? '')) : resolve(workBase, stamp)
if (mode === 'dev' && (!/^\d{4}-\d{2}-\d{2}T[\d-]+Z$/.test(relative(realpathSync(workBase), work)) || !existsSync(resolve(work, 'frontend/node_modules')))) {
  throw new Error('dev requires an existing timestamped harness snapshot with installed dependencies')
}
const cache = resolve(root, 'tools/dev-harness/.cache/m2')
for (const p of [out, work, cache]) mkdirSync(p, { recursive: true })
const maven = 'maven@sha256:65353f527c86cb23187c8233475713e15067e8d36220d18863c379680698fe85'
const node = 'node@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32'
const results = []
const redact = text => text.replace(/sk-[\w-]+/g, '[REDACTED]').replace(/(Authorization:\s*Bearer\s+)\S+/gi, '$1[REDACTED]')
function command(id, exe, args, timeout = 600000) {
  const start = Date.now()
  const r = spawnSync(exe, args, { cwd: root, encoding: 'utf8', timeout, maxBuffer: 32 * 1024 * 1024, windowsHide: true })
  writeFileSync(resolve(out, `${id}.txt`), redact((r.stdout ?? '') + (r.stderr ?? '') + (r.error ? `\n${r.error.message}` : '')))
  const row = { id, command: [exe, ...args], exitCode: r.status, signal: r.signal, durationMs: Date.now() - start, status: r.status === 0 ? 'PASS' : 'FAIL' }
  results.push(row)
  console.log(`${id}: ${row.status} (${row.durationMs} ms)`)
  return r.status === 0
}
function docker(id, image, args, { offline = false, frontend = false } = {}) {
  // Only allowlisted source snapshot; no user .env, local config, database or Docker socket.
  const name = `a0-${stamp.toLowerCase()}-${id}`
  if (image === maven && args[0] === 'mvn') args = ['mvn', '-s', '/work/a0-maven-settings.xml', ...args.slice(1)]
  const ok = command(id, 'docker', ['run', '--rm', '--name', name, '--cpus=2', '--memory=3g', ...(offline ? ['--network=none'] : []),
    '--mount', `type=bind,source=${work},target=/work`, '--mount', `type=bind,source=${cache},target=/root/.m2`,
    '-w', frontend ? '/work/frontend' : '/work', image, ...args])
  if (!ok) spawnSync('docker', ['rm', '-f', name], { windowsHide: true, stdio: 'ignore' })
  if (['mock', 'existing-test', 'test-dependencies'].includes(id)) {
    const reports = resolve(work, 'target/surefire-reports')
    if (existsSync(reports)) for (const file of readdirSync(reports).filter(f => f.endsWith('.txt') || f.endsWith('.xml'))) {
      writeFileSync(resolve(out, `${id}-${file}`), redact(readFileSync(resolve(reports, file), 'utf8')))
    }
  }
  return ok
}
async function devStartup() {
  const name = `a0-${stamp.toLowerCase()}-dev`
  try {
    const started = command('dev-start', 'docker', ['run', '-d', '--name', name, '--cpus=2', '--memory=1g',
      '-p', '127.0.0.1::5173', '--mount', `type=bind,source=${work},target=/work`, '-w', '/work/frontend',
      node, 'npm', 'run', 'dev', '--', '--host', '0.0.0.0'])
    if (!started) return
    const port = spawnSync('docker', ['port', name, '5173/tcp'], { encoding: 'utf8', windowsHide: true }).stdout.trim()
    if (!/^127\.0\.0\.1:\d+$/.test(port)) throw new Error('Unexpected dev port')
    let ready = false
    const probeStarted = Date.now()
    const deadline = probeStarted + 90000
    while (Date.now() < deadline) {
      try {
        const response = await fetch(`http://${port}/`, { signal: AbortSignal.timeout(1000) })
        if (response.ok && (await response.text()).includes('<html')) { ready = true; break }
      } catch { /* bounded startup wait */ }
      await new Promise(r => setTimeout(r, 1000))
    }
    results.push({ id: 'isolated-dev-health', status: ready ? 'PASS' : 'FAIL', url: `http://${port}/`, durationMs: Date.now() - probeStarted, timeoutMs: 90000, scope: 'Vite startup only, no browser or API generation' })
    command('dev-log', 'docker', ['logs', name])
  } catch (e) { results.push({ id: 'isolated-dev-health', status: 'FAIL', error: e.message }) }
  finally { command('dev-stop', 'docker', ['rm', '-f', name]) }
}
async function health(id, url, json = false) {
  const start = Date.now()
  try {
    const r = await fetch(url, { signal: AbortSignal.timeout(10000), redirect: 'error' })
    const body = await r.text()
    const valid = json ? (() => { const v = JSON.parse(body); return v.code === 0 && v.data === 'ok' })() : /<html/i.test(body)
    results.push({ id, command: ['GET', url], status: r.ok && valid ? 'PASS' : 'FAIL', httpStatus: r.status, durationMs: Date.now() - start })
  } catch (e) { results.push({ id, command: ['GET', url], status: 'FAIL', error: e.message, durationMs: Date.now() - start }) }
}
command('git-head', 'git', ['rev-parse', 'HEAD'])
command('git-status', 'git', ['status', '--short'])
command('git-diff', 'git', ['diff', '--', 'frontend/package.json', '.gitignore'])
if (mode === 'dev') await devStartup()
if (mode !== 'health' && mode !== 'dev') {
  const listing = spawnSync('git', ['ls-files', '--cached', '--others', '--exclude-standard', '-z', '--', 'pom.xml', 'src', 'frontend'], { cwd: root, encoding: 'utf8', windowsHide: true })
  if (listing.status !== 0) throw new Error('Cannot enumerate source snapshot')
  copyFileSync(resolve(root, 'tools/dev-harness/maven-settings.xml'), resolve(work, 'a0-maven-settings.xml'))
  const manifest = []
  for (const p of new Set(listing.stdout.split('\0').filter(Boolean))) {
    if (/application-local|(^|\/)\.env|(^|\/)env\.ts$|(^|\/)(node_modules|target|dist)\//.test(p)) continue
    const dest = resolve(work, p)
    if (relative(work, dest).startsWith('..')) throw new Error('Unsafe source path')
    if (!existsSync(resolve(root, p))) continue
    mkdirSync(dirname(dest), { recursive: true }); copyFileSync(resolve(root, p), dest); manifest.push({ path: p, sha256: createHash('sha256').update(readFileSync(dest)).digest('hex') })
  }
  writeFileSync(resolve(work, 'frontend/src/config/env.ts'), "export const API_BASE_URL = '/api'\n")
  writeFileSync(resolve(out, 'source-manifest.json'), JSON.stringify(manifest, null, 2))
  if (mode.startsWith('a1') || mode.startsWith('a2')) copyFileSync(resolve(root, 'tools/dev-harness/a1-sse-test.cjs'), resolve(work, 'a1-sse-test.cjs'))
  command('maven-version', 'docker', ['run', '--rm', maven, 'mvn', '-version'])
  const compiled = docker('backend-package', maven, ['mvn', '-B', '-DskipTests', 'package'])
  if (compiled) {
    docker('test-dependencies', maven, ['mvn', '-B', `-Dtest=${testSelector}`, 'test'])
    docker('mock', maven, ['mvn', '-o', '-B', `-Dtest=${testSelector}`, 'test'], { offline: true })
    if (['baseline', 'a1', 'a2'].includes(mode)) docker('existing-test', maven, ['mvn', '-o', '-B', '-Dtest=MainApplicationTests', 'test'], { offline: true })
  } else results.push({ id: 'mock', status: 'BLOCKED', reason: 'backend-package failed' })
  if (['baseline', 'a1', 'a2'].includes(mode)) {
    command('node-version', 'docker', ['run', '--rm', node, 'node', '--version'])
    if (docker('npm-ci', node, ['npm', 'ci', '--no-audit', '--no-fund'], { frontend: true })) {
      if (mode === 'a1' || mode === 'a2') docker('sse-compatibility', node, ['node', '../a1-sse-test.cjs'], { frontend: true, offline: true })
      await devStartup()
      for (const task of ['type-check', 'build', 'lint:check']) docker(task.replace(':', '-'), node, ['npm', 'run', task], { frontend: true, offline: true })
    }
  }
}
if (mode.startsWith('a1') && existsSync(resolve(work, 'target/a1-example.json'))) copyFileSync(resolve(work, 'target/a1-example.json'), resolve(out, 'a1-example.json'))
await health('deployed-backend-health', 'http://localhost:8123/api/health', true)
await health('deployed-frontend-health', 'http://localhost/')
results.push({ id: 'real-api', status: 'NOT_RUN', reason: 'Separate opt-in provider smoke; health and mocks do not demonstrate real generation.' })
writeFileSync(resolve(out, 'summary.json'), JSON.stringify({ mode, generatedAt: new Date().toISOString(), work, images: { maven, node }, results }, null, 2))
console.log(`Evidence: ${out}`)
process.exitCode = results.some(r => ['FAIL', 'BLOCKED'].includes(r.status)) ? 1 : 0
