// パフォーマンステストで使う API 呼び出し（docs/08 の「パフォーマンステスト」）。
// setup() でのテストデータ作成と、VU の本体の両方から使う。
import http from 'k6/http';
import { check } from 'k6';

export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');

const JSON_HEADERS = { 'Content-Type': 'application/json' };
const BOUNDARY = 'k6PerfBoundary7MA4YWxkTrZu0gW';

function auth(token) {
  return { Authorization: `Bearer ${token}` };
}

/** ユーザー登録。成功すればアクセストークンを返す。 */
export function signup({ email, password, handle, displayName }) {
  const res = http.post(
    `${BASE_URL}/api/auth/signup`,
    JSON.stringify({ email, password, handle, displayName, agreedToTerms: true }),
    { headers: JSON_HEADERS, tags: { name: 'setup_signup' } },
  );
  if (!check(res, { 'signup 201': (r) => r.status === 201 })) {
    throw new Error(`signup に失敗しました (${res.status}): ${res.body}`);
  }
  return res.json('accessToken');
}

/**
 * 本文だけの投稿を作る。投稿 API は multipart/form-data しか受け付けないが、
 * k6 はファイルを含まないと multipart にしないため、本文を自分で組み立てる。
 */
export function createPost(token, body) {
  const payload =
    `--${BOUNDARY}\r\n` +
    'Content-Disposition: form-data; name="body"\r\n' +
    'Content-Type: text/plain; charset=utf-8\r\n\r\n' +
    `${body}\r\n` +
    `--${BOUNDARY}--\r\n`;
  const res = http.post(`${BASE_URL}/api/posts`, payload, {
    headers: { ...auth(token), 'Content-Type': `multipart/form-data; boundary=${BOUNDARY}` },
    tags: { name: 'setup_post' },
  });
  if (!check(res, { 'post 201': (r) => r.status === 201 })) {
    throw new Error(`投稿に失敗しました (${res.status}): ${res.body}`);
  }
}

/** フォローする。 */
export function follow(token, handle) {
  const res = http.put(`${BASE_URL}/api/users/${handle}/follow`, null, {
    headers: auth(token),
    tags: { name: 'setup_follow' },
  });
  if (!check(res, { 'follow 2xx': (r) => r.status >= 200 && r.status < 300 })) {
    throw new Error(`フォローに失敗しました (${res.status}): ${res.body}`);
  }
}

/** ホームタイムライン（ログイン必須）。cursor を渡すと次のページ。 */
export function homeTimeline(token, cursor, name = 'home_timeline') {
  const query = cursor ? `?cursor=${cursor}` : '';
  return http.get(`${BASE_URL}/api/timeline/home${query}`, { headers: auth(token), tags: { name } });
}

/** ホームタイムラインの新着件数（画面は 30 秒ごとに呼ぶ）。 */
export function homeNewCount(token, since) {
  return http.get(`${BASE_URL}/api/timeline/home/new-count?since=${since}`, {
    headers: auth(token),
    tags: { name: 'home_new_count' },
  });
}

/** 全体タイムライン（ゲストでも見られる）。 */
export function globalTimeline() {
  return http.get(`${BASE_URL}/api/timeline/global`, { tags: { name: 'global_timeline' } });
}
