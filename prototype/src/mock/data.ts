import type { Comment, Db, Follow, Like, Notification, Post, PostImage, Report, User } from './types'

// 外部の画像サービスに依存しないよう、グラデーションの SVG をプレースホルダ画像にする。
export function placeholderImage(seed: number, label: string, width = 1200, height = 800): string {
  const hue = (seed * 67) % 360
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
<defs><linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
<stop offset="0" stop-color="hsl(${hue},70%,62%)"/><stop offset="1" stop-color="hsl(${(hue + 50) % 360},70%,42%)"/>
</linearGradient></defs>
<rect width="100%" height="100%" fill="url(#g)"/>
<text x="50%" y="50%" fill="rgba(255,255,255,0.9)" font-family="sans-serif" font-size="${Math.round(height / 9)}" text-anchor="middle" dominant-baseline="middle">${label}</text>
</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

export const DEMO_PASSWORD = 'password123'

const MINUTE = 60 * 1000
const HOUR = 60 * MINUTE
const now = Date.now()

function user(
  id: string,
  handle: string,
  displayName: string,
  bio: string,
  avatarColor: string,
  extra: Partial<User> = {},
): User {
  return {
    id,
    email: `${handle}@example.com`,
    password: DEMO_PASSWORD,
    handle,
    displayName,
    bio,
    avatarUrl: null,
    avatarColor,
    role: 'USER',
    status: 'ACTIVE',
    createdAt: now - 90 * 24 * HOUR,
    ...extra,
  }
}

const users: User[] = [
  user('u1', 'taro', '山田 太郎', 'Web エンジニア。React と Spring Boot が好き。週末はカメラを持って散歩しています。', '#0ea5e9'),
  user('u2', 'hanako', '佐藤 花子', 'カフェ巡りと読書。おすすめの本があれば教えてください📚', '#ec4899'),
  user('u3', 'dev_ken', 'ケン@個人開発', '個人開発でアプリを作っています。技術ネタ多め。', '#22c55e'),
  user('u4', 'neko_suki', 'ねこすき', '猫の写真を毎日あげます🐈', '#f59e0b'),
  user('u5', 'yuki_photo', 'Yuki', '写真と旅行。フィルムカメラ勢。', '#8b5cf6'),
  user('u6', 'spam_bot99', '今すぐ稼げる情報', '【限定】1日5分で月100万円！プロフのリンクから', '#64748b'),
  user('u7', 'admin', '運営', 'timeline-sns の運営アカウントです。', '#0f172a', { role: 'ADMIN' }),
]

const texts: [string, string][] = [
  ['u2', '新しくできたカフェに行ってきました。ラテアートがかわいかった☕'],
  ['u3', 'Spring Boot 4 に上げたら起動がちょっと速くなった気がする'],
  ['u4', '今日のうちの猫。日なたで溶けてます'],
  ['u5', '朝の港。霧が出ていて幻想的でした'],
  ['u1', 'タイムライン型の SNS を個人開発中。まずは要件定義から。'],
  ['u6', '【今だけ】スマホ1台で月収100万円！詳しくはプロフのリンクから！！'],
  ['u2', '積読が20冊を超えた。今月こそ減らしたい'],
  ['u3', 'カーソル方式のページング、UUIDv7 だと id でそのまま並べられて便利'],
  ['u4', '猫が キーボードの上で寝てしまって仕事が進まない'],
  ['u5', 'フィルムの現像が上がってきた。やっぱり色がいい'],
  ['u1', '週末は写真散歩。紅葉にはまだ早かった'],
  ['u2', 'おすすめのミステリー小説ありますか？'],
  ['u3', 'Tailwind v4、設定ファイルなしで始められるのが気楽'],
  ['u4', 'ごはんの時間だけ異様に正確'],
  ['u5', '夕焼けがきれいだったので寄り道'],
  ['u1', 'React 19 の use フック、慣れると手放せない'],
  ['u3', 'Flyway のマイグレーション、ファイル名の番号を間違えがち'],
  ['u2', '雨の日は家で読書に限る'],
  ['u4', '毛布を占領されました'],
  ['u5', '次の旅行先を考え中。どこかおすすめあれば！'],
  ['u6', '無料プレゼント企画実施中！フォロー＆いいねで参加！'],
  ['u3', 'テストが通った瞬間が一番うれしい'],
  ['u1', '個人開発のモチベーション維持、みんなどうしてるんだろう'],
  ['u2', '駅前のパン屋さん、クロワッサンが絶品'],
  ['u4', '猫草を買ってきたら全然食べない'],
  ['u5', '星空を撮りに山まで。寒かったけど最高'],
  ['u3', 'GitHub Actions のキャッシュ設定を見直したらビルドが半分の時間に'],
  ['u2', '久しぶりに映画館で映画を観た'],
  ['u1', 'ダークモードは Could に入れた。まずは MVP'],
  ['u4', '寝顔コレクションが増えていく'],
]

// 画像付きにする投稿の index と枚数
const imageCounts: Record<number, number> = { 0: 1, 2: 2, 3: 1, 9: 4, 14: 1, 18: 3, 25: 1, 29: 2 }

const posts: Post[] = texts.map(([userId, body], i) => {
  const count = imageCounts[i] ?? 0
  const images: PostImage[] = Array.from({ length: count }, (_, j) => ({
    id: `p${i + 1}-img${j + 1}`,
    url: placeholderImage(i * 5 + j, `画像 ${j + 1}`),
    width: 1200,
    height: 800,
  }))
  return {
    id: `p${i + 1}`,
    userId,
    body,
    images,
    // index が小さいほど新しい
    createdAt: now - (i * 97 + 5) * MINUTE,
  }
})

const comments: Comment[] = [
  { id: 'c1', postId: 'p5', userId: 'u3', parentId: null, body: '応援してます！技術スタックは何にしたんですか？', deletedAt: null, createdAt: now - 7 * HOUR },
  { id: 'c2', postId: 'p5', userId: 'u1', parentId: 'c1', body: 'Spring Boot と React です！', deletedAt: null, createdAt: now - 6.5 * HOUR },
  { id: 'c3', postId: 'p5', userId: 'u3', parentId: 'c2', body: 'いいですね、自分も同じ構成です', deletedAt: null, createdAt: now - 6 * HOUR },
  { id: 'c4', postId: 'p5', userId: 'u2', parentId: 'c3', body: 'お二人ともすごい…', deletedAt: null, createdAt: now - 5 * HOUR },
  { id: 'c5', postId: 'p5', userId: 'u5', parentId: null, body: 'リリースしたら使ってみたいです', deletedAt: null, createdAt: now - 4 * HOUR },
  { id: 'c6', postId: 'p5', userId: 'u4', parentId: null, body: '（削除されたコメントの例）', deletedAt: now - 3 * HOUR, createdAt: now - 4.5 * HOUR },
  { id: 'c7', postId: 'p5', userId: 'u2', parentId: 'c6', body: '↑消えちゃった', deletedAt: null, createdAt: now - 3.5 * HOUR },
  { id: 'c8', postId: 'p1', userId: 'u1', parentId: null, body: 'どこのカフェですか？行ってみたい', deletedAt: null, createdAt: now - 1 * HOUR },
  { id: 'c9', postId: 'p1', userId: 'u2', parentId: 'c8', body: '駅の東口です！', deletedAt: null, createdAt: now - 50 * MINUTE },
  { id: 'c10', postId: 'p12', userId: 'u1', parentId: null, body: '「十角館の殺人」おすすめです', deletedAt: null, createdAt: now - 20 * HOUR },
]

const likes: Like[] = [
  ['u1', 'p1'], ['u1', 'p3'], ['u2', 'p5'], ['u3', 'p5'], ['u4', 'p5'], ['u5', 'p5'],
  ['u2', 'p11'], ['u3', 'p16'], ['u1', 'p4'], ['u2', 'p4'], ['u3', 'p4'], ['u4', 'p10'],
  ['u5', 'p1'], ['u3', 'p1'], ['u2', 'p23'], ['u5', 'p23'],
].map(([userId, postId], i) => ({ userId, postId, createdAt: now - i * 13 * MINUTE }))

const follows: Follow[] = [
  ['u1', 'u2'], ['u1', 'u3'], ['u1', 'u5'],
  ['u2', 'u1'], ['u2', 'u4'],
  ['u3', 'u1'], ['u3', 'u2'],
  ['u4', 'u1'], ['u4', 'u2'],
  ['u5', 'u1'], ['u5', 'u4'],
].map(([followerId, followeeId], i) => ({ followerId, followeeId, createdAt: now - i * HOUR }))

const notifications: Notification[] = [
  { id: 'n1', recipientId: 'u1', actorId: 'u2', type: 'REPLY', postId: 'p1', commentId: 'c9', readAt: null, createdAt: now - 50 * MINUTE },
  { id: 'n2', recipientId: 'u1', actorId: 'u5', type: 'COMMENT', postId: 'p5', commentId: 'c5', readAt: null, createdAt: now - 4 * HOUR },
  { id: 'n3', recipientId: 'u1', actorId: 'u3', type: 'REPLY', postId: 'p5', commentId: 'c3', readAt: null, createdAt: now - 6 * HOUR },
  { id: 'n4', recipientId: 'u1', actorId: 'u5', type: 'FOLLOW', postId: null, commentId: null, readAt: now - 8 * HOUR, createdAt: now - 10 * HOUR },
  { id: 'n5', recipientId: 'u1', actorId: 'u2', type: 'LIKE', postId: 'p5', commentId: null, readAt: now - 8 * HOUR, createdAt: now - 12 * HOUR },
  { id: 'n6', recipientId: 'u1', actorId: 'u2', type: 'LIKE', postId: 'p11', commentId: null, readAt: now - 8 * HOUR, createdAt: now - 20 * HOUR },
]

const reports: Report[] = [
  { id: 'r1', reporterId: 'u2', targetType: 'POST', targetId: 'p6', reason: 'SPAM', detail: '同じような宣伝を何度も投稿しています', status: 'OPEN', createdAt: now - 2 * HOUR },
  { id: 'r2', reporterId: 'u3', targetType: 'USER', targetId: 'u6', reason: 'SPAM', detail: '', status: 'OPEN', createdAt: now - 3 * HOUR },
  { id: 'r3', reporterId: 'u4', targetType: 'COMMENT', targetId: 'c10', reason: 'OTHER', detail: 'ネタバレかもしれません', status: 'REJECTED', createdAt: now - 30 * HOUR },
]

export function createInitialDb(): Db {
  return structuredClone({ users, posts, comments, likes, follows, blocks: [], notifications, reports })
}
