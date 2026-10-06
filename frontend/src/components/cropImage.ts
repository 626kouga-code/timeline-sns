/** 切り抜く範囲（元画像のピクセル単位）。react-easy-crop の croppedAreaPixels と同じ形 */
export interface CropArea {
  x: number
  y: number
  width: number
  height: number
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('画像を読み込めませんでした'))
    image.src = src
  })
}

/**
 * 画像の指定範囲を切り抜き、size × size の PNG にする。
 * ブラウザは Exif の向きを反映して描画するので、スマホで撮った写真も正しい向きになる。
 * 透過を保つため PNG にする（サーバー側で JPEG / PNG に作り直される）。
 */
export async function cropToSquare(src: string, area: CropArea, size: number): Promise<Blob> {
  const image = await loadImage(src)
  const canvas = document.createElement('canvas')
  canvas.width = size
  canvas.height = size
  const context = canvas.getContext('2d')
  if (!context) throw new Error('画像を処理できませんでした')
  context.imageSmoothingQuality = 'high'
  context.drawImage(image, area.x, area.y, area.width, area.height, 0, 0, size, size)
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => (blob ? resolve(blob) : reject(new Error('画像を処理できませんでした'))), 'image/png')
  })
}
