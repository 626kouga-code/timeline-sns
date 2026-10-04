import type { Comment } from '../api/types'

export interface Thread {
  root: Comment
  /** ルートより下のすべての返信（深さによらず古い順） */
  replies: Comment[]
}

/**
 * 平らなコメント一覧をスレッドにする。API は親が子より前に並んだ古い順で返す。
 * 画面では 2 階層目までインデントし、それより深い返信も 2 階層目に並べる（F-32）。
 */
export function buildThreads(comments: Comment[]): Thread[] {
  const rootOf = new Map<string, string>()
  const threads = new Map<string, Thread>()
  for (const comment of comments) {
    const rootId = comment.parentId ? rootOf.get(comment.parentId) : comment.id
    // 親が見えない（API が除いた）返信は出さない
    if (!rootId) continue
    rootOf.set(comment.id, rootId)
    if (rootId === comment.id) threads.set(comment.id, { root: comment, replies: [] })
    else threads.get(rootId)?.replies.push(comment)
  }
  return [...threads.values()]
}
