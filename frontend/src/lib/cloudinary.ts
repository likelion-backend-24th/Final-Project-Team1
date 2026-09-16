/**
 * Cloudinary unsigned 업로드. 브라우저에서 직접 올리고 URL 만 받아온다.
 *
 * 우리 서버를 거치지 않으므로 백엔드는 한 줄도 바뀌지 않는다 - 받은 secure_url 이
 * 기존 thumbnailUrl 자리에 그대로 들어간다.
 *
 * cloud name 과 preset 이름은 번들에 노출된다(unsigned 방식의 전제다).
 * 그래서 Cloudinary 콘솔의 preset 쪽에 폴더 고정·허용 포맷·최대 크기를 반드시 걸어둔다.
 * API Secret 은 여기에도 .env 에도 절대 넣지 않는다.
 */

const CLOUD_NAME = import.meta.env.VITE_CLOUDINARY_CLOUD_NAME as string | undefined
const UPLOAD_PRESET = import.meta.env.VITE_CLOUDINARY_UPLOAD_PRESET as string | undefined

export const cloudinaryEnabled = Boolean(CLOUD_NAME && UPLOAD_PRESET)

// 무료 플랜의 이미지 업로드 상한이 10MB 다. preset 의 Max file size 와 같은 값으로 맞춘다.
const MAX_BYTES = 10 * 1024 * 1024
const ALLOWED = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

export class UploadError extends Error {}

export async function uploadImage(file: File): Promise<string> {
  if (!cloudinaryEnabled) {
    throw new UploadError('이미지 업로드가 설정되지 않았습니다. 주소를 직접 입력해주세요.')
  }
  // 서버(Cloudinary)도 막지만, 여기서 걸러야 사용자가 업로드를 기다린 뒤 실패하지 않는다.
  if (!ALLOWED.includes(file.type)) {
    throw new UploadError('JPG · PNG · WEBP · GIF 만 올릴 수 있습니다.')
  }
  if (file.size > MAX_BYTES) {
    throw new UploadError('이미지는 10MB 이하만 올릴 수 있습니다.')
  }

  const form = new FormData()
  form.append('file', file)
  form.append('upload_preset', UPLOAD_PRESET!)

  const res = await fetch(`https://api.cloudinary.com/v1_1/${CLOUD_NAME}/image/upload`, {
    method: 'POST',
    body: form,
  })
  if (!res.ok) {
    const body = await res.json().catch(() => null)
    throw new UploadError(body?.error?.message ?? '이미지 업로드에 실패했습니다.')
  }

  const data = await res.json()
  // http 로 받으면 배포(HTTPS) 후 mixed content 로 차단된다. 항상 secure_url 을 쓴다.
  if (!data.secure_url) {
    throw new UploadError('업로드 응답에 주소가 없습니다.')
  }
  return data.secure_url as string
}

/**
 * 전송 최적화. 원본을 그대로 내려보내지 않고 폭·포맷·품질을 Cloudinary 가 변환해 준다.
 *
 * 8MB 짜리 PNG 를 올려도 방문자는 화면 폭에 맞춘 WebP 수백 KB 를 받는다.
 * f_auto = 브라우저가 지원하는 최신 포맷, q_auto = 눈에 안 보이는 선까지 압축.
 *
 * Cloudinary 주소가 아니면(주최자가 외부 URL 을 붙여넣은 경우) 그대로 돌려준다.
 */
export function cdnImage(url: string | undefined, width: number): string | undefined {
  if (!url) return url
  const marker = '/image/upload/'
  const at = url.indexOf(marker)
  if (!url.includes('res.cloudinary.com') || at === -1) return url

  const head = url.slice(0, at + marker.length)
  const tail = url.slice(at + marker.length)
  // 이미 변환이 붙어 있으면 건드리지 않는다.
  if (/^[a-z]_[^/]+\//.test(tail)) return url
  return `${head}f_auto,q_auto,c_limit,w_${width}/${tail}`
}
