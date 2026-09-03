import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { roundApi } from '../api/round'
import { useToast } from '../components/Toast'
import type { Expo, Round } from '../types'

function toLocal(dt: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${dt.getFullYear()}-${pad(dt.getMonth()+1)}-${pad(dt.getDate())}T${pad(dt.getHours())}:${pad(dt.getMinutes())}`
}

function fmt(dt: string) {
  return new Date(dt).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

export default function RoundManagePage() {
  const { expoId } = useParams<{ expoId: string }>()
  const navigate = useNavigate()
  const toast = useToast()
  const id = Number(expoId)

  const [expo, setExpo] = useState<Expo | null>(null)
  const [rounds, setRounds] = useState<Round[]>([])
  const [publishing, setPublishing] = useState(false)
  const [showForm, setShowForm] = useState(false)

  // tomorrow defaults
  const tomorrow = new Date()
  tomorrow.setDate(tomorrow.getDate() + 1)
  tomorrow.setHours(10, 0, 0, 0)
  const tomorrowEnd = new Date(tomorrow)
  tomorrowEnd.setHours(18, 0, 0, 0)

  const [form, setForm] = useState({
    startAt: toLocal(tomorrow),
    endAt: toLocal(tomorrowEnd),
    capacity: 100,
    price: 0,
  })
  const [addLoading, setAddLoading] = useState(false)
  const [addError, setAddError] = useState('')

  useEffect(() => {
    expoApi.getExpo(id).then(res => setExpo(res.data)).catch(() => navigate('/host/channel'))
    roundApi.listByExpo(id).then(res => setRounds(res.data ?? []))
  }, [id])

  async function handleAddRound(e: React.FormEvent) {
    e.preventDefault()
    setAddError('')
    if (form.capacity < 1) {
      setAddError('정원은 1명 이상이어야 합니다.')
      return
    }
    setAddLoading(true)
    try {
      const res = await roundApi.createRound(id, {
        startAt: new Date(form.startAt).toISOString(),
        endAt: new Date(form.endAt).toISOString(),
        capacity: form.capacity,
        price: form.price,
      })
      setRounds(prev => [...prev, res.data])
      toast('회차가 등록되었습니다', 'success')
      setShowForm(false)
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 400) setAddError('입력값을 확인해주세요. (정원 1 이상, 종료시각 > 시작시각)')
      else if (e.status === 403) setAddError('해당 박람회의 주최자만 등록할 수 있습니다.')
      else setAddError('회차 등록에 실패했습니다.')
    } finally {
      setAddLoading(false)
    }
  }

  async function handleDelete(roundId: number) {
    if (!confirm('이 회차를 삭제하시겠습니까?')) return
    try {
      await roundApi.deleteRound(id, roundId)
      setRounds(prev => prev.filter(r => r.id !== roundId))
      toast('회차가 삭제되었습니다', 'success')
    } catch {
      toast('삭제 실패 (예약이 존재하는 회차는 삭제할 수 없습니다)', 'error')
    }
  }

  async function handlePublish() {
    if (!confirm('박람회를 공개하시겠습니까? 공개 후 방문자에게 노출됩니다.')) return
    setPublishing(true)
    try {
      const res = await expoApi.publishExpo(id)
      setExpo(res.data)
      toast('박람회가 공개되었습니다!', 'success')
    } catch (err: unknown) {
      const e = err as { status?: number; body?: { data?: { code?: string } } }
      if (e.status === 409) {
        if (e.body?.data?.code === 'ALREADY_PUBLISHED') toast('이미 공개된 박람회입니다', 'info')
        else toast('회차를 먼저 등록해주세요', 'error')
      } else {
        toast('공개 처리에 실패했습니다', 'error')
      }
    } finally {
      setPublishing(false)
    }
  }

  return (
    <div className="container page-wrap">
      <button className="btn btn-secondary btn-sm" onClick={() => navigate(-1)} style={{ marginBottom: 24 }}>
        ← 뒤로
      </button>

      {expo && (
        <div style={{ marginBottom: 32 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 8 }}>
            <h1 className="page-title" style={{ marginBottom: 0 }}>{expo.title}</h1>
            <span className={`badge badge-${expo.status.toLowerCase()}`}>
              {expo.status === 'PUBLISHED' ? '공개중' : expo.status === 'HIDDEN' ? 'HIDDEN' : '종료'}
            </span>
          </div>
          <p className="page-sub">회차를 등록한 뒤 공개 버튼을 누르면 방문자에게 노출됩니다.</p>

          {expo.status !== 'PUBLISHED' && (
            <div className="alert alert-warning" style={{ maxWidth: 560 }}>
              현재 <strong>HIDDEN</strong> 상태입니다.
              {rounds.length === 0
                ? ' 회차를 먼저 등록해주세요.'
                : ' 준비가 완료되면 아래 공개 버튼을 눌러주세요.'}
            </div>
          )}

          {expo.status !== 'CLOSED' && (
            <div style={{ display: 'flex', gap: 12, marginTop: 16 }}>
              <button
                className="btn btn-primary"
                onClick={handlePublish}
                disabled={publishing || rounds.length === 0 || expo.status === 'PUBLISHED'}
              >
                {publishing ? '처리 중...' : expo.status === 'PUBLISHED' ? '✓ 공개됨' : '공개하기'}
              </button>
            </div>
          )}
        </div>
      )}

      {/* Rounds list */}
      <div className="section-header">
        <span className="section-title">회차 목록</span>
        <button className="btn btn-outline btn-sm" onClick={() => setShowForm(f => !f)}>
          {showForm ? '취소' : '+ 회차 추가'}
        </button>
      </div>

      {showForm && (
        <div className="card" style={{ padding: 24, marginBottom: 20, maxWidth: 560 }}>
          <h3 style={{ fontWeight: 700, marginBottom: 16, color: 'var(--navy)' }}>회차 등록</h3>
          {addError && <div className="alert alert-danger"><span>⚠</span> {addError}</div>}
          <form onSubmit={handleAddRound}>
            <div className="form-row">
              <div className="form-group">
                <label className="form-label">시작 일시<span className="req">*</span></label>
                <input
                  className="form-input"
                  type="datetime-local"
                  value={form.startAt}
                  onChange={e => setForm(p => ({ ...p, startAt: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group">
                <label className="form-label">종료 일시<span className="req">*</span></label>
                <input
                  className="form-input"
                  type="datetime-local"
                  value={form.endAt}
                  onChange={e => setForm(p => ({ ...p, endAt: e.target.value }))}
                  required
                />
              </div>
            </div>
            <div className="form-row">
              <div className="form-group">
                <label className="form-label">정원<span className="req">*</span></label>
                <input
                  className={`form-input ${form.capacity < 1 ? 'error' : ''}`}
                  type="number"
                  min={1}
                  value={form.capacity}
                  onChange={e => setForm(p => ({ ...p, capacity: Number(e.target.value) }))}
                  required
                />
                {form.capacity < 1 && <p className="form-error">정원은 1명 이상이어야 합니다.</p>}
              </div>
              <div className="form-group">
                <label className="form-label">참가비</label>
                <input
                  className="form-input"
                  type="number"
                  min={0}
                  value={form.price}
                  onChange={e => setForm(p => ({ ...p, price: Number(e.target.value) }))}
                />
                <p className="form-hint">0 입력 시 무료</p>
              </div>
            </div>
            <button type="submit" className="btn btn-primary" disabled={addLoading}>
              {addLoading ? '등록 중...' : '회차 등록'}
            </button>
          </form>
        </div>
      )}

      {rounds.length === 0 ? (
        <div className="empty-state">
          <div className="emoji">📅</div>
          <p>등록된 회차가 없습니다</p>
          <button className="btn btn-outline" onClick={() => setShowForm(true)}>회차 추가하기</button>
        </div>
      ) : (
        <div>
          {rounds.map(r => (
            <div key={r.id} className="round-card">
              <div style={{ flex: 1 }}>
                <div style={{ fontWeight: 700, color: 'var(--navy)', marginBottom: 4 }}>
                  {fmt(r.startAt)} ~ {fmt(r.endAt)}
                </div>
                <div style={{ fontSize: 13, color: 'var(--gray5)' }}>
                  정원 {r.capacity}명 · 잔여 {r.remaining}명
                </div>
              </div>
              <button
                className="btn btn-danger btn-sm"
                onClick={() => handleDelete(r.id)}
              >
                삭제
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
