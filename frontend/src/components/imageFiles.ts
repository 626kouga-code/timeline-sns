// 選べる画像（docs/02 F-11）。サーバーでもファイルの中身から形式を確認するが、送る前に画面でも確かめる
export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024

/** 選べない画像ならその理由、選べるなら null */
export function imageFileError(file: File): string | null {
  if (!IMAGE_TYPES.includes(file.type)) return 'JPEG・PNG・WebP・GIF の画像を選んでください'
  if (file.size > MAX_IMAGE_BYTES) return '画像は 5MB 以内にしてください'
  return null
}
