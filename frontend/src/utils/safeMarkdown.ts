import { marked } from 'marked'
// A small allowlist for article Markdown, including user-edited drafts. No raw active HTML.
export function safeMarkdown(source: string): string {
  const template = document.createElement('template')
  template.innerHTML = marked.parse(source, { async: false })
  const tags = new Set(['P','BR','HR','H1','H2','H3','H4','H5','H6','BLOCKQUOTE','UL','OL','LI','PRE','CODE','STRONG','EM','DEL','TABLE','THEAD','TBODY','TR','TH','TD','A','IMG'])
  for (const element of Array.from(template.content.querySelectorAll('*'))) {
    if (!tags.has(element.tagName)) { element.replaceWith(document.createTextNode(element.textContent || '')); continue }
    for (const attr of Array.from(element.attributes)) {
      const allowed = (element.tagName === 'A' && attr.name === 'href') || (element.tagName === 'IMG' && ['src','alt'].includes(attr.name)) || attr.name === 'title'
      if (!allowed) element.removeAttribute(attr.name)
    }
    for (const attr of ['href','src']) if (element.hasAttribute(attr)) {
      try { const raw = element.getAttribute(attr) || ''; if (attr === 'src' && raw.startsWith('/') && !/^\/api\/images\/[a-f0-9]{32}$/.test(raw)) { element.removeAttribute(attr); continue } const url = new URL(raw, window.location.href); if (!['http:','https:'].includes(url.protocol)) element.removeAttribute(attr) }
      catch { element.removeAttribute(attr) }
    }
    if (element.tagName === 'A') element.setAttribute('rel','noopener noreferrer')
  }
  return template.innerHTML
}
