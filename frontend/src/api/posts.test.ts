import { describe, expect, it } from 'vitest'
import { json, mockApi, post, request } from '../test/utils'
import { createPost, deletePost, getPost } from './posts'

describe('createPost', () => {
  it('本文と画像を multipart（FormData）で送る。Content-Type はブラウザに任せる', async () => {
    const created = post('p1', 'こんにちは')
    const fetchMock = mockApi({ '/api/posts': () => json(201, created) })
    const a = new File(['a'], 'a.png', { type: 'image/png' })
    const b = new File(['b'], 'b.jpg', { type: 'image/jpeg' })

    await expect(createPost({ body: 'こんにちは', images: [a, b] })).resolves.toEqual(created)

    const sent = request(fetchMock)
    expect(sent.method).toBe('POST')
    // boundary は fetch が付けるので、自分では指定しない
    expect(sent.headers.get('Content-Type')).toBeNull()
    const form = sent.body as FormData
    expect(form.get('body')).toBe('こんにちは')
    expect(form.getAll('images').map((f) => (f as File).name)).toEqual(['a.png', 'b.jpg'])
  })

  it('画像がなければ images パートを送らない', async () => {
    const fetchMock = mockApi({ '/api/posts': () => json(201, post('p1', '本文だけ')) })

    await createPost({ body: '本文だけ', images: [] })

    expect((request(fetchMock).body as FormData).has('images')).toBe(false)
  })

  it('サーバーの入力エラーを項目ごとに返す', async () => {
    mockApi({
      '/api/posts': () =>
        json(400, {
          code: 'VALIDATION_FAILED',
          detail: '入力内容に誤りがあります',
          errors: { body: '本文は 280 文字以内にしてください' },
        }),
    })

    await expect(createPost({ body: 'x', images: [] })).rejects.toMatchObject({
      errors: { body: '本文は 280 文字以内にしてください' },
    })
  })
})

describe('getPost', () => {
  it('ID を URL エンコードして取得する', async () => {
    const fetchMock = mockApi({ '/api/posts/a%2Fb': () => json(200, post('a/b', '本文')) })

    await expect(getPost('a/b')).resolves.toMatchObject({ id: 'a/b' })
    expect(request(fetchMock)).toMatchObject({ path: '/api/posts/a%2Fb', method: 'GET' })
  })

  it('存在しない投稿は 404 のエラーになる', async () => {
    mockApi({
      '/api/posts/none': () => json(404, { code: 'POST_NOT_FOUND', detail: 'この投稿は存在しないか、表示できません' }),
    })

    await expect(getPost('none')).rejects.toMatchObject({ status: 404, code: 'POST_NOT_FOUND' })
  })
})

describe('deletePost', () => {
  it('DELETE を送り、204 なら何も返さない', async () => {
    const fetchMock = mockApi({ '/api/posts/p1': () => new Response(null, { status: 204 }) })

    await expect(deletePost('p1')).resolves.toBeUndefined()
    expect(request(fetchMock).method).toBe('DELETE')
  })

  it('他人の投稿は 403 のエラーになる', async () => {
    mockApi({ '/api/posts/p1': () => json(403, { code: 'FORBIDDEN', detail: 'この操作を行う権限がありません' }) })

    await expect(deletePost('p1')).rejects.toMatchObject({ status: 403, message: 'この操作を行う権限がありません' })
  })
})
