import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { useToast } from '../components/Toast'

export default function ChannelCreatePage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [form, setForm] = useState({ name: '', description: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await expoApi.createChannel(form)
      toast('채널이 생성되었습니다 ✓', 'success')
      navigate(`/host/expos/new?channelId=${res.data.id}`)
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 409) setError('이미 사용 중인 채널명입니다.')
      else if (e.status === 403) setError('주최자(ORGANIZER) 권한이 필요합니다.')
      else setError('채널 생성에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      <div className="container page-wrap">
        <button className="btn btn-secondary btn-sm" onClick={() => navigate(-1)} style={{ marginBottom: 28 }}>
          ← 뒤로
        </button>

        <div style={{ maxWidth: 600 }}>
          <div className="page-header">
            <h1 className="page-title">채널 등록</h1>
            <p className="page-sub">채널은 주최자 전용 공간입니다. 채널 안에서 박람회를 등록·관리합니다.</p>
          </div>

          <div className="card" style={{ padding: 32 }}>
            {error && <div className="alert alert-danger"><span>⚠</span><span>{error}</span></div>}
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label className="form-label">채널명 <span className="req">*</span></label>
                <input
                  className="form-input"
                  type="text"
                  placeholder="예: 테크컨퍼런스코리아"
                  value={form.name}
                  onChange={e => setForm(p => ({ ...p, name: e.target.value }))}
                  maxLength={100}
                  required
                />
              </div>
              <div className="form-group" style={{ marginBottom: 28 }}>
                <label className="form-label">채널 소개</label>
                <textarea
                  className="form-input"
                  placeholder="채널에 대해 간략히 소개해주세요"
                  value={form.description}
                  onChange={e => setForm(p => ({ ...p, description: e.target.value }))}
                />
              </div>
              <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={loading}>
                {loading ? '생성 중...' : '채널 생성하기'}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  )
}
