// タイムラインの負荷テスト（docs/08 の「パフォーマンステスト」）。
// 要件: タイムラインの初回表示は 2 秒以内（100 ユーザー同時接続時）— docs/03
//
// 実行例:
//   docker compose run --rm k6 run /perf/timeline.ts                  # smoke（動作確認）
//   docker compose run --rm k6 run -e SCENARIO=load /perf/timeline.ts # load（100 VU）
//
// 環境変数:
//   BASE_URL        対象の API（既定 http://localhost:8080）
//   SCENARIO        smoke | load（既定 smoke）
//   USERS           setup で作るユーザー数（既定 smoke 10 / load 100）
//   POSTS_PER_USER  1 人あたりの投稿数（既定 smoke 5 / load 20）
//   FOLLOWS         1 人あたりのフォロー数（既定 smoke 3 / load 20）
import { check, sleep } from 'k6';
import type { Options, Scenario } from 'k6/options';
import { textSummary } from 'https://jslib.k6.io/k6-summary/0.1.0/index.js';
import { createPost, follow, globalTimeline, homeNewCount, homeTimeline, signup } from './lib/api.ts';

interface Preset {
  users: number;
  postsPerUser: number;
  follows: number;
  executor: Scenario;
}

interface PerfUser {
  handle: string;
  token: string;
}

interface SetupData {
  users: PerfUser[];
}

const PRESETS: Record<string, Preset> = {
  smoke: {
    users: 10,
    postsPerUser: 5,
    follows: 3,
    executor: { executor: 'constant-vus', vus: 5, duration: '30s' },
  },
  load: {
    users: 100,
    postsPerUser: 20,
    follows: 20,
    executor: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '1m', target: 100 },
        { duration: '3m', target: 100 },
        { duration: '30s', target: 0 },
      ],
      gracefulRampDown: '10s',
    },
  },
};

const SCENARIO = __ENV.SCENARIO || 'smoke';
const preset = PRESETS[SCENARIO];
if (!preset) {
  throw new Error(`SCENARIO は ${Object.keys(PRESETS).join(' / ')} のどれかにしてください: ${SCENARIO}`);
}

const USERS = Number(__ENV.USERS || preset.users);
const POSTS_PER_USER = Number(__ENV.POSTS_PER_USER || preset.postsPerUser);
const FOLLOWS = Math.min(Number(__ENV.FOLLOWS || preset.follows), USERS - 1);

export const options: Options = {
  scenarios: { [SCENARIO]: preset.executor },
  // setup は ユーザー数 ×（登録 + 投稿 + フォロー）回 API を呼ぶので長めにとる
  setupTimeout: '10m',
  thresholds: {
    // 要件（docs/03）: タイムラインの初回表示は 2 秒以内
    'http_req_duration{name:home_timeline}': ['p(95)<2000'],
    'http_req_duration{name:global_timeline}': ['p(95)<2000'],
    // setup のリクエストも含めて、失敗は 1% 未満
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
  },
  // 集計に p(99) も出す
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

/** テストデータを API で作る。実行ごとに別の接頭辞を付け、前回のデータとぶつからないようにする。 */
export function setup(): SetupData {
  // handle は ^[A-Za-z0-9_]{3,15}$。"pf" + 英数字 5 桁 + "_" + 連番（最大 4 桁）で 15 文字以内に収める
  const runId = Date.now().toString(36).slice(-5);
  const users: PerfUser[] = [];
  for (let i = 0; i < USERS; i++) {
    const handle = `pf${runId}_${i}`;
    const token = signup({
      email: `perf+${runId}-${i}@example.com`,
      password: 'perf-password',
      handle,
      displayName: `perf ${i}`,
    });
    users.push({ handle, token });
  }
  for (const user of users) {
    for (let p = 0; p < POSTS_PER_USER; p++) {
      createPost(user.token, `パフォーマンステストの投稿 ${user.handle} #${p}`);
    }
  }
  // i 番目のユーザーは、続く FOLLOWS 人をフォローする（ホームタイムラインに他人の投稿が混ざるように）
  users.forEach((user, i) => {
    for (let f = 1; f <= FOLLOWS; f++) {
      follow(user.token, users[(i + f) % users.length].handle);
    }
  });
  console.log(`setup: runId=${runId} users=${USERS} posts=${USERS * POSTS_PER_USER} follows=${USERS * FOLLOWS}`);
  return { users };
}

/** 1 反復 = 1 人がタイムラインを開いて少し読み進め、新着を確かめ、全体タイムラインも見る。 */
export default function ({ users }: SetupData): void {
  const { token } = users[(__VU - 1) % users.length];

  const first = homeTimeline(token);
  const ok = check(first, {
    'home 200': (r) => r.status === 200,
    'home has items': (r) => r.status === 200 && (r.json('items') as unknown[]).length > 0,
  });
  sleep(1);

  if (ok) {
    const nextCursor = first.json('nextCursor') as string | null;
    if (nextCursor) {
      check(homeTimeline(token, nextCursor, 'home_timeline_next'), { 'home next 200': (r) => r.status === 200 });
      sleep(1);
    }
    const newest = first.json('items.0.id') as string;
    check(homeNewCount(token, newest), { 'new-count 200': (r) => r.status === 200 });
    sleep(1);
  }

  check(globalTimeline(), { 'global 200': (r) => r.status === 200 });
  sleep(2);
}

/** 標準出力の要約に加えて、結果を perf/results/ に JSON で残す。 */
export function handleSummary(data: Record<string, unknown>): Record<string, string> {
  const dir = __ENV.RESULTS_DIR || 'perf/results';
  return {
    stdout: textSummary(data, { indent: ' ', enableColors: true }),
    [`${dir}/summary-${SCENARIO}.json`]: JSON.stringify(data, null, 2),
  };
}
