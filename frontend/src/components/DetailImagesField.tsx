import { useRef, useState } from 'react'
import { cdnImage, cloudinaryEnabled, uploadImage, UploadError } from '../lib/cloudinary'

/**
 * 행사 소개용 상세 이미지 여러 장. 순서가 곧 화면에 쌓이는 순서다.
 * 값은 URL 배열이고, 서버는 통째로 교체한다 - 순서 바꾸기도 새 배열을 보내는 것으로 끝난다.
 */
export default function DetailImagesField({ value, onChange }: {
  value: string[]
  onChange: (urls: string[]) => void
}) {
  const fileRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')

  async function handleFiles(e: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(e.target.files ?? [])
    if (files.length === 0) return
    setError('')
    setUploading(true)
    const uploaded: string[] = []
    try {
      // 한 장이 실패해도 앞서 올라간 것은 살린다. 여러 장을 다시 고르게 하지 않는다.
      for (const file of files) {
        uploaded.push(await uploadImage(file))
      }
    } catch (err) {
      setError(err instanceof UploadError ? err.message : '이미지 업로드에 실패했습니다.')
    } finally {
      if (uploaded.length) onChange([...value, ...uploaded].slice(0, 20))
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''
    }
  }

  function move(index: number, delta: number) {
    const next = [...value]
    const target = index + delta
    if (target < 0 || target >= next.length) return
    ;[next[index], next[target]] = [next[target], next[index]]
    onChange(next)
  }

  return (
    <div className="form-group">
      <label className="form-label">행사 소개 이미지</label>

      {value.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginBottom: 10 }}>
          {value.map((url, i) => (
            <div
              key={`${url}-${i}`}
              style={{
                display: 'flex', alignItems: 'center', gap: 10, padding: 8,
                border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              }}
            >
              <img
                src={cdnImage(url, 160)}
                alt=""
                style={{ width: 64, height: 44, objectFit: 'cover', borderRadius: 4, background: 'var(--gray2)' }}
              />
              <span style={{
                flex: 1, fontSize: 12, color: 'var(--sub)',
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
              }}>
                {i + 1}. {url}
              </span>
              <button type="button" className="btn btn-secondary btn-sm"
                      disabled={i === 0} onClick={() => move(i, -1)}>↑</button>
              <button type="button" className="btn btn-secondary btn-sm"
                      disabled={i === value.length - 1} onClick={() => move(i, 1)}>↓</button>
              <button type="button" className="btn btn-secondary btn-sm"
                      onClick={() => onChange(value.filter((_, j) => j !== i))}>삭제</button>
            </div>
          ))}
        </div>
      )}

      {cloudinaryEnabled ? (
        <button
          type="button"
          className="btn btn-secondary btn-block"
          disabled={uploading || value.length >= 20}
          onClick={() => fileRef.current?.click()}
        >
          {uploading ? '올리는 중...' : value.length >= 20 ? '20장까지 올릴 수 있습니다' : '+ 이미지 추가'}
        </button>
      ) : (
        <p style={{ fontSize: 12, color: 'var(--sub)' }}>
          이미지 업로드가 설정되지 않았습니다(.env 의 Cloudinary 설정).
        </p>
      )}

      <input
        ref={fileRef}
        type="file"
        multiple
        accept="image/jpeg,image/png,image/webp,image/gif"
        onChange={handleFiles}
        style={{ display: 'none' }}
      />

      {error && <p style={{ fontSize: 12, color: 'var(--red)' }}>{error}</p>}
      <p style={{ fontSize: 12, color: 'var(--sub)' }}>
        상세 페이지에 이 순서대로 세로로 이어 붙습니다. 가로 폭이 넓은 이미지가 잘 맞습니다.
      </p>
    </div>
  )
}
