import { useRef, useState } from 'react'
import { cdnImage, cloudinaryEnabled, uploadImage, UploadError } from '../lib/cloudinary'

/**
 * 대표 이미지 입력. 파일을 올리거나 주소를 직접 붙여넣을 수 있다.
 * 값은 언제나 URL 문자열이라 서버 계약(thumbnailUrl)이 바뀌지 않는다.
 */
export default function ImageUploadField({ value, onChange }: {
  value: string
  onChange: (url: string) => void
}) {
  const fileRef = useRef<HTMLInputElement>(null)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')
  const [broken, setBroken] = useState(false)

  async function handleFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    setError('')
    setUploading(true)
    try {
      const url = await uploadImage(file)
      setBroken(false)
      onChange(url)
    } catch (err) {
      setError(err instanceof UploadError ? err.message : '이미지 업로드에 실패했습니다.')
    } finally {
      setUploading(false)
      if (fileRef.current) fileRef.current.value = ''   // 같은 파일 재선택을 허용한다
    }
  }

  return (
    <div className="form-group">
      <label className="form-label">대표 이미지</label>

      {value && !broken && (
        <div style={{
          position: 'relative', width: '100%', paddingTop: '34.3%',
          borderRadius: 'var(--r-sm)', overflow: 'hidden',
          background: 'var(--gray2)', marginBottom: 10,
        }}>
          <img
            src={cdnImage(value, 900)}
            alt="대표 이미지 미리보기"
            onError={() => setBroken(true)}
            onLoad={() => setBroken(false)}
            style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover' }}
          />
        </div>
      )}

      {value && broken && (
        <div className="alert alert-warning" style={{ marginBottom: 10, fontSize: 12 }}>
          <span>⚠</span>
          <span>이 주소에서 이미지를 불러오지 못했습니다. 직접 링크(.jpg · .png)인지 확인해주세요.</span>
        </div>
      )}

      <div style={{ display: 'flex', gap: 8, marginBottom: 8 }}>
        <input
          className="form-input"
          type="url"
          placeholder="https://example.com/poster.jpg"
          value={value}
          onChange={e => { setBroken(false); onChange(e.target.value) }}
          maxLength={500}
          style={{ flex: 1 }}
        />
        {cloudinaryEnabled && (
          <button
            type="button"
            className="btn btn-secondary"
            disabled={uploading}
            onClick={() => fileRef.current?.click()}
            style={{ whiteSpace: 'nowrap' }}
          >
            {uploading ? '올리는 중...' : '파일 올리기'}
          </button>
        )}
        {value && (
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => { setBroken(false); onChange('') }}
          >
            지우기
          </button>
        )}
      </div>

      <input
        ref={fileRef}
        type="file"
        accept="image/jpeg,image/png,image/webp,image/gif"
        onChange={handleFile}
        style={{ display: 'none' }}
      />

      {error && <p style={{ fontSize: 12, color: 'var(--red)' }}>{error}</p>}
      <p style={{ fontSize: 12, color: 'var(--sub)' }}>
        {cloudinaryEnabled
          ? '파일을 올리거나 이미지 주소를 붙여넣으세요. 가로로 긴 이미지(약 3:1)가 가장 잘 맞습니다. JPG · PNG · WEBP, 10MB 이하.'
          : '이미지 주소를 붙여넣어주세요. 가로로 긴 이미지(약 3:1)가 가장 잘 맞습니다.'}
      </p>
      {/* http 이미지는 배포(HTTPS)에서 mixed content 로 차단된다. 화면에는 안 보이고 콘솔에만 남아 원인을 찾기 어렵다. */}
      {value.startsWith('http://') && (
        <p style={{ fontSize: 12, color: 'var(--yellow)' }}>
          ⚠ http:// 주소는 배포 환경(HTTPS)에서 차단되어 이미지가 보이지 않습니다. https:// 주소를 사용하세요.
        </p>
      )}
    </div>
  )
}
