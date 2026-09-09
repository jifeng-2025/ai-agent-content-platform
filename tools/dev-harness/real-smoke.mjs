import { mkdirSync, writeFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const out = resolve(root, 'artifacts/a0/runs', `real-${new Date().toISOString().replace(/[:.]/g, '-')}`)
mkdirSync(out, { recursive: true })
const result = { kind: 'REAL_PROVIDER_TEXT_SMOKE', model: 'qwen-plus', calls: 0, imageCalls: 0, status: 'NOT_RUN', cost: 'not incurred' }
if (!process.argv.includes('--execute')) result.reason = 'Preflight only; use --execute with A0_DASHSCOPE_API_KEY to issue one bounded text request.'
else if (!process.env.A0_DASHSCOPE_API_KEY) {
  result.status = 'BLOCKED'; result.reason = 'A0_DASHSCOPE_API_KEY missing; never load the application .env automatically.'; process.exitCode = 2
} else {
  const started = Date.now()
  result.calls = 1
  result.cost = 'unknown; consult provider billing; usage is recorded, no price is guessed'
  try {
    const r = await fetch('https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions', {
      method: 'POST', signal: AbortSignal.timeout(45000),
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${process.env.A0_DASHSCOPE_API_KEY}` },
      body: JSON.stringify({ model: result.model, temperature: 0, max_tokens: 256, messages: [{ role: 'user', content: '为“普通人如何用 AI 提升工作效率”提供一个面向非技术新人的标题。只返回 JSON 数组，字段为 mainTitle 和 subTitle。不要编造数字或机构归因。' }] })
    })
    result.httpStatus = r.status
    const body = await r.json()
    result.usage = body.usage ?? null
    if (!r.ok) throw new Error(`Provider HTTP ${r.status}`)
    const titles = JSON.parse(body.choices?.[0]?.message?.content)
    if (!Array.isArray(titles) || !titles.length || !titles.every(t => typeof t.mainTitle === 'string' && t.mainTitle.trim() && typeof t.subTitle === 'string' && t.subTitle.trim())) throw new Error('Invalid title JSON contract')
    result.status = 'PASS'; result.titleCount = titles.length
  } catch (e) { result.status = 'FAIL'; result.reason = e.name === 'TimeoutError' ? '45 second timeout' : (e instanceof SyntaxError ? 'Invalid provider JSON' : e.message); process.exitCode = 1 }
  result.durationMs = Date.now() - started
}
// Never persist request headers, API keys or provider response text.
writeFileSync(resolve(out, 'summary.json'), JSON.stringify(result, null, 2))
console.log(JSON.stringify(result, null, 2)); console.log(`Evidence: ${out}`)
