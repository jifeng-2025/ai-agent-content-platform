export interface DiffLine { kind: 'same' | 'removed' | 'added'; text: string }
// Linear bounded diff: retain equal prefix/suffix; show the changed middle without quadratic LCS memory.
export function reviewDiff(before: string, after: string): DiffLine[] {
  const a = before.split('\n'), b = after.split('\n')
  let start = 0, end = 0
  while (start < a.length && start < b.length && a[start] === b[start]) start++
  while (end < a.length - start && end < b.length - start && a[a.length - end - 1] === b[b.length - end - 1]) end++
  return [...a.slice(0, start).map(text => ({ kind: 'same' as const, text })),
    ...a.slice(start, a.length - end).map(text => ({ kind: 'removed' as const, text })),
    ...b.slice(start, b.length - end).map(text => ({ kind: 'added' as const, text })),
    ...a.slice(a.length - end).map(text => ({ kind: 'same' as const, text }))]
}
