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
      toast('채널이 생성되었습니다', 'success')
      navigate(`/host/expos/new?channelId=${res.data.id}`)
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 409) setError('이미 사용 중인 채널명입니다.')
      else if (e.status === 403) setError('주최자 권한이 필요합니다.')
      else setError('채널 생성에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container page-wrap">
      <button className="btn btn-secondary btn-sm" onClick={() => navigate(-1)} style={{ marginBottom: 24 }}>
        ← 뒤로
      </button>
      <h1 className="page-title">채널 등록</h1>
      <p className="page-sub">채널은 주최자 공간입니다. 채널 안에서 박람회를 등록·관리할 수 있습니다.</p>

      <div className="card" style={{ maxWidth: 560, padding: 32 }}>
        {error && <div className="alert alert-danger"><span>⚠</span> {error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">채널명<span className="req">*</span></label>
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
          <div className="form-group">
            <label className="form-label">채널 설명</label>
            <textarea
              className="form-input"
              placeholder="채널에 대한 간단한 소개를 입력하세요"
              value={form.description}
              onChange={e => setForm(p => ({ ...p, description: e.target.value }))}
            />
          </div>
          <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
            {loading ? '생성 중...' : '채널 생성'}
          </button>
        </form>
      </div>
    </div>
  )
}
