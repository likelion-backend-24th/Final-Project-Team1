import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { useToast } from '../components/Toast'

const CATEGORIES = ['IT·전자', '식품·음료', '패션·뷰티', '교육·취업', '문화·예술', '기타']

export default function ExpoManagePage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const channelId = Number(params.get('channelId'))
  const toast = useToast()

  const [form, setForm] = useState({
    title: '',
    description: '',
    category: CATEGORIES[0],
    region: '',
    venue: '',
  })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (!channelId) { setError('채널을 먼저 생성해주세요.'); return }
    setLoading(true)
    try {
      const res = await expoApi.createExpo(channelId, form)
      toast('박람회가 등록되었습니다 (HIDDEN)', 'success')
      navigate(`/host/expos/${res.data.id}/rounds`)
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 403) setError('해당 채널의 소유자만 등록할 수 있습니다.')
      else setError('박람회 등록에 실패했습니다.')
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

        <div style={{ maxWidth: 660 }}>
          <div className="page-header">
            <h1 className="page-title">박람회 등록</h1>
            <p className="page-sub">등록 후 회차를 추가하면 공개 버튼이 활성화됩니다.</p>
          </div>

          {!channelId && (
            <div className="alert alert-warning" style={{ marginBottom: 20 }}>
              ⚠ 채널을 먼저 생성해야 박람회를 등록할 수 있습니다.{' '}
              <span
                style={{ color: 'var(--primary)', fontWeight: 700, cursor: 'pointer' }}
                onClick={() => navigate('/host/channel/new')}
              >
                채널 생성하기
              </span>
            </div>
          )}

          <div className="card" style={{ padding: 32 }}>
            {error && <div className="alert alert-danger"><span>⚠</span><span>{error}</span></div>}
            <form onSubmit={handleSubmit}>
              <div className="form-group">
                <label className="form-label">박람회 제목 <span className="req">*</span></label>
                <input
                  className="form-input"
                  type="text"
                  placeholder="예: 2026 테크 잡페어"
                  value={form.title}
                  onChange={e => setForm(p => ({ ...p, title: e.target.value }))}
                  maxLength={200}
                  required
                />
              </div>

              <div className="form-group">
                <label className="form-label">카테고리 <span className="req">*</span></label>
                <select
                  className="form-input"
                  value={form.category}
                  onChange={e => setForm(p => ({ ...p, category: e.target.value }))}
                >
                  {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                </select>
              </div>

              <div className="form-row">
                <div className="form-group">
                  <label className="form-label">지역</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="서울"
                    value={form.region}
                    onChange={e => setForm(p => ({ ...p, region: e.target.value }))}
                    maxLength={50}
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">장소(venue)</label>
                  <input
                    className="form-input"
                    type="text"
                    placeholder="코엑스"
                    value={form.venue}
                    onChange={e => setForm(p => ({ ...p, venue: e.target.value }))}
                    maxLength={200}
                  />
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: 28 }}>
                <label className="form-label">박람회 소개</label>
                <textarea
                  className="form-input"
                  placeholder="방문자에게 보여질 박람회 소개를 입력하세요"
                  value={form.description}
                  onChange={e => setForm(p => ({ ...p, description: e.target.value }))}
                />
              </div>

              <button
                type="submit"
                className="btn btn-primary btn-block btn-lg"
                disabled={loading || !channelId}
              >
                {loading ? '등록 중...' : '박람회 등록'}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  )
}
