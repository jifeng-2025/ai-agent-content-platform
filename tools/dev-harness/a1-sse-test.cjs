const fs = require('node:fs')
const vm = require('node:vm')
const assert = require('node:assert/strict')
const ts = require(require.resolve('typescript', { paths: [process.cwd()] }))
const source = fs.readFileSync('src/utils/sse.ts', 'utf8')
const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText
let count = 0
for (const type of ['NEEDS_REVIEW', 'ALL_COMPLETE', 'ERROR', 'REVIEW_UPDATED']) {
  let completed = 0, received = 0
  class Source { closed = false; close() { this.closed = true } }
  const exports = {}
  vm.runInNewContext(js, { exports, EventSource: Source, console })
  const connection = exports.connectSSE('synthetic', { onMessage: () => received++, onComplete: () => completed++ })
  connection.onmessage({ data: JSON.stringify({ type }) })
  assert.equal(received, 1)
  assert.equal(connection.closed, type !== 'REVIEW_UPDATED')
  assert.equal(completed, type !== 'REVIEW_UPDATED' ? 1 : 0)
  count++
}
console.log(`SSE compatibility: ${count} tests, 0 failures; transpiled real sse.ts with simulated EventSource`)
