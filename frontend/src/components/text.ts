export const MAX_BODY = 280

// 絵文字などを 1 文字として数える（サーバーの @MaxCodePoints と同じ数え方）
export const countChars = (s: string) => [...s].length
