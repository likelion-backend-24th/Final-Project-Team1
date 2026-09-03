import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { roundApi } from '../api/round'
import { useToast } from '../components/Toast'
import type { Expo, Round } from '../types'

function toLocal(d: Date) {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth()+1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}
function fmt(dt: string) {
  return new Date(dt).toLocaleString('ko-KR', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

export default function RoundManagePage() {
  const { expoId } = useParams<{ expoId: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const toast = useToast()
  const id = Number(expoId)

  // GET /expos/{id} 는 PUBLISHED 만 내려준다(HIDDEN 은 404).
  // 방금 등록한 HIDDEN 박람회를 관리하려면 이동할 때 넘겨받은 값을 먼저 쓰고,
  // 없으면(새로고침 등) 공개 조회로 보완한다.
  const passed = (location.state as { expo?: Expo } | null)?.expo ?? null
  const [expo, setExpo] = useState<Expo | null>(passed)
  const [rounds, setRounds] = useState<Round[]>([])
  const [publishing, setPublishing] = useState(false)
  const [showForm, setShowForm] = useState(false)

  const tmrw = new Date()
  tmrw.setDate(tmrw.getDate() + 1)
  tmrw.setHours(10, 0, 0, 0)
  const tmrwEnd = new Date(tmrw); tmrwEnd.setHours(18, 0, 0, 0)

  const [form, setForm] = useState({
    startsAt: toLocal(tmrw),
    endsAt: toLocal(tmrwEnd),
    capacity: 100,
    fee: 0,
  })
  const [addLoading, setAddLoading] = useState(false)
  const [addError, setAddError] = useState('')

  useEffect(() => {
    if (!passed) {
      expoApi.getExpo(id)
        .then(r => setExpo(r.data))
        .catch(() => toast('박람회 정보를 불러오지 못했습니다 (비공개 박람회는 주최자 센터에서 들어와주세요)', 'error'))
    }
    // 주최자용 회차 목록. 채널 소유자만 조회된다.
    roundApi.listByExpo(id)
      .then(r => setRounds(r.data ?? []))
      .catch(() => toast('회차 목록을 불러오지 못했습니다', 'error'))
  }, [id])

  async function handleAddRound(e: React.FormEvent) {
    e.preventDefault()
    setAddError('')
    if (form.capacity < 1) { setAddError('정원은 1명 이상이어야 합니다.'); return }
    setAddLoading(true)
    try {
      const r = await roundApi.createRound(id, {
        startsAt: new Date(form.startsAt).toISOString(),
        endsAt: new Date(form.endsAt).toISOString(),
        capacity: form.capacity,
        fee: form.fee,
      })
      setRounds(prev => [...prev, r.data])
      toast('회차가 등록되었습니다 ✓', 'success')
      setShowForm(false)
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 400) setAddError('입력값을 확인해주세요. (시작은 미래, 종료 > 시작, 정원 1 이상)')
      else if (e.status === 403) setAddError('이 박람회의 주최자만 회차를 등록할 수 있습니다.')
      else if (e.status === 503) setAddError('박람회 정보를 확인할 수 없어 등록하지 못했습니다. (expo-service 응답 없음)')
      else setAddError('회차 등록에 실패했습니다.')
    } finally {
      setAddLoading(false)
    }
  }

  async function handlePublish() {
    if (!confirm('박람회를 공개하시겠습니까? 방문자에게 즉시 노출됩니다.')) return
    setPublishing(true)
    try {
      // 응답은 { expoId, status } 뿐이라 기존 정보를 유지한 채 상태만 갈아끼운다.
      const r = await expoApi.publishExpo(id)
      setExpo(prev => (prev ? { ...prev, status: r.data.status } : prev))
      toast('박람회가 공개되었습니다! 🎉', 'success')
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 400) toast('회차를 먼저 등록해야 공개할 수 있습니다', 'error')
      else if (e.status === 403) toast('이 박람회의 주최자만 공개할 수 있습니다', 'error')
      else if (e.status === 409) toast('이미 종료된 박람회는 다시 공개할 수 없습니다', 'error')
      else if (e.status === 503) toast('회차 확인에 실패해 공개하지 못했습니다 (reservation-service 응답 없음)', 'error')
      else toast('공개 처리에 실패했습니다', 'error')
    } finally {
      setPublishing(false)
    }
  }

  const isPublished = expo?.status === 'PUBLISHED'
  const isClosed = expo?.status === 'CLOSED'

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      <div className="container page-wrap">
        <button className="btn btn-secondary btn-sm" onClick={() => navigate('/host/channel')} style={{ marginBottom: 28 }}>
          ← 채널 목록
        </button>

        {expo && (
          <>
            {/* Expo Header card */}
            <div className="card" style={{ padding: '24px 28px', marginBottom: 28 }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 16 }}>
                <div>
                  <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
                    <span className={`badge badge-${(expo.status ?? 'HIDDEN').toLowerCase()}`}>
                      {isPublished ? '● 공개중' : isClosed ? '● 종료' : '○ HIDDEN'}
                    </span>
                    <span className="badge badge-blue">{expo.category}</span>
                  </div>
                  <h1 style={{ fontSize: 22, fontWeight: 800, color: 'var(--text)', marginBottom: 6 }}>
                    {expo.title}
                  </h1>
                  {(expo.region || expo.venue) && (
                    <p style={{ fontSize: 13, color: 'var(--sub)' }}>
                      {[expo.venue, expo.region].filter(Boolean).join(' · ')}
                    </p>
                  )}
                </div>

                {!isClosed && (
                  <button
                    className={`btn ${isPublished ? 'btn-secondary' : 'btn-primary'}`}
                    onClick={handlePublish}
                    disabled={publishing || rounds.length === 0 || isPublished}
                    style={{ flexShrink: 0 }}
                  >
                    {publishing ? '처리 중...' : isPublished ? '✓ 공개됨' : '공개하기'}
                  </button>
                )}
              </div>

              {!isPublished && !isClosed && (
                <div className="alert alert-warning" style={{ marginTop: 16, marginBottom: 0 }}>
                  {rounds.length === 0
                    ? '⚠ 회차를 먼저 등록해야 공개할 수 있습니다.'
                    : '⚑ 회차가 등록되었습니다. 공개하기 버튼을 눌러 방문자에게 노출하세요.'}
                </div>
              )}
            </div>

            {/* Rounds section */}
            <div className="section-header">
              <span className="section-title">회차 목록</span>
              {!isClosed && (
                <button className="btn btn-outline btn-sm" onClick={() => setShowForm(f => !f)}>
                  {showForm ? '취소' : '+ 회차 추가'}
                </button>
              )}
            </div>

            {showForm && (
              <div className="card" style={{ padding: 24, marginBottom: 16 }}>
                <h3 style={{ fontSize: 15, fontWeight: 700, color: 'var(--text)', marginBottom: 20 }}>
                  새 회차 등록
                </h3>
                {addError && <div className="alert alert-danger"><span>⚠</span><span>{addError}</span></div>}
                <form onSubmit={handleAddRound}>
                  <div className="form-row">
                    <div className="form-group">
                      <label className="form-label">시작 일시 <span className="req">*</span></label>
                      <input
                        className="form-input"
                        type="datetime-local"
                        value={form.startsAt}
                        onChange={e => setForm(p => ({ ...p, startsAt: e.target.value }))}
                        required
                      />
                    </div>
                    <div className="form-group">
                      <label className="form-label">종료 일시 <span className="req">*</span></label>
                      <input
                        className="form-input"
                        type="datetime-local"
                        value={form.endsAt}
                        onChange={e => setForm(p => ({ ...p, endsAt: e.target.value }))}
                        required
                      />
                    </div>
                  </div>
                  <div className="form-row">
                    <div className="form-group">
                      <label className="form-label">정원 <span className="req">*</span></label>
                      <input
                        className={`form-input ${form.capacity < 1 ? 'error' : ''}`}
                        type="number"
                        min={1}
                        value={form.capacity}
                        onChange={e => setForm(p => ({ ...p, capacity: Number(e.target.value) }))}
                        required
                      />
                      {form.capacity < 1 && <p className="form-error">1명 이상이어야 합니다.</p>}
                    </div>
                    <div className="form-group">
                      <label className="form-label">참가비</label>
                      <input
                        className="form-input"
                        type="number"
                        min={0}
                        value={form.fee}
                        onChange={e => setForm(p => ({ ...p, fee: Number(e.target.value) }))}
                      />
                      <p className="form-hint">0원 = 무료</p>
                    </div>
                  </div>
                  <div style={{ display: 'flex', gap: 10 }}>
                    <button type="submit" className="btn btn-primary" disabled={addLoading}>
                      {addLoading ? '등록 중...' : '회차 등록'}
                    </button>
                    <button type="button" className="btn btn-secondary" onClick={() => setShowForm(false)}>
                      취소
                    </button>
                  </div>
                </form>
              </div>
            )}

            {rounds.length === 0 ? (
              <div className="empty-state">
                <div className="es-icon">📅</div>
                <p className="es-title">등록된 회차가 없습니다</p>
                <p className="es-desc">회차를 추가해야 박람회를 공개할 수 있습니다.</p>
                <button className="btn btn-primary" onClick={() => setShowForm(true)}>회차 추가하기</button>
              </div>
            ) : (
              <div>
                {rounds.map(r => (
                  <div key={r.roundId} className="round-card">
                    <div style={{ fontSize: 20, flexShrink: 0 }}>📅</div>
                    <div style={{ flex: 1 }}>
                      <div style={{ fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>
                        {fmt(r.startsAt)} – {fmt(r.endsAt)}
                      </div>
                      <div style={{ fontSize: 12, color: 'var(--sub)' }}>
                        정원 {r.capacity}명 · 잔여 {r.remaining}명
                        {r.fee !== undefined && ` · ${r.fee === 0 ? '무료' : `${r.fee.toLocaleString()}원`}`}
                      </div>
                    </div>
                    {/* 회차 삭제는 Sprint 2 (예약 존재 시 정책 미확정) */}
                  </div>
                ))}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
