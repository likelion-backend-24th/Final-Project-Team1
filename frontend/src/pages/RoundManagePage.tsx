import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { roundApi } from '../api/round'
import { useToast } from '../components/Toast'
import { usePageTitle } from '../hooks/usePageTitle'
import type { Expo, Round, RoundSummary } from '../types'

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

  // 주최자 조회(getMyExpoById)는 HIDDEN 도 내려준다. 넘겨받은 값이 있으면 그걸 먼저 쓴다.
  const passed = (location.state as { expo?: Expo } | null)?.expo ?? null
  const [expo, setExpo] = useState<Expo | null>(passed)
  usePageTitle(expo?.title ? `${expo.title} 회차 관리` : '회차 관리')
  const [rounds, setRounds] = useState<Round[]>([])
  const [summary, setSummary] = useState<Record<number, RoundSummary>>({})
  const [publishing, setPublishing] = useState(false)
  const [showForm, setShowForm] = useState(false)
  const [downloadingRoundId, setDownloadingRoundId] = useState<number | 'all' | null>(null)
  // 수정 중인 회차. 등록 폼과 같은 모양이라 form 을 공유하지 않고 별도로 둔다.
  const [editingId, setEditingId] = useState<number | null>(null)
  const [editForm, setEditForm] = useState({ startsAt: '', endsAt: '', capacity: 0, fee: 0 })
  const [editLoading, setEditLoading] = useState(false)
  const [editError, setEditError] = useState('')
  const [deletingId, setDeletingId] = useState<number | null>(null)

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
      expoApi.getMyExpoById(id)
        .then(r => setExpo(r.data))
        .catch(() => toast('박람회 정보를 불러오지 못했습니다', 'error'))
    }
    // 주최자용 회차 목록. 채널 소유자만 조회된다.
    roundApi.listByExpo(id)
      .then(r => setRounds(r.data ?? []))
      .catch(() => toast('회차 목록을 불러오지 못했습니다', 'error'))

    // 예약 현황(확정/취소/체크인 수)은 별도 API 라 실패해도 회차 관리 자체는 막지 않는다.
    expoApi.getReservationSummary(id)
      .then(r => {
        const byRound: Record<number, RoundSummary> = {}
        for (const s of r.data.rounds ?? []) byRound[s.roundId] = s
        setSummary(byRound)
      })
      .catch(() => {})
  }, [id])

  async function handleDownload(roundId?: number) {
    setDownloadingRoundId(roundId ?? 'all')
    try {
      await expoApi.downloadAttendeesExcel(id, roundId)
    } catch {
      toast('명단 다운로드에 실패했습니다', 'error')
    } finally {
      setDownloadingRoundId(null)
    }
  }

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

  function startEdit(r: Round) {
    setEditError('')
    setEditingId(r.roundId)
    // datetime-local 은 로컬 시각 문자열을 받는다. ISO(UTC) 를 그대로 넣으면 9시간 밀린다.
    setEditForm({
      startsAt: toLocal(new Date(r.startsAt)),
      endsAt: toLocal(new Date(r.endsAt)),
      capacity: r.capacity,
      fee: r.fee ?? 0,
    })
  }

  async function handleUpdateRound(e: React.FormEvent) {
    e.preventDefault()
    if (editingId === null) return
    setEditError('')
    if (editForm.capacity < 1) { setEditError('정원은 1명 이상이어야 합니다.'); return }
    setEditLoading(true)
    try {
      const r = await roundApi.updateRound(id, editingId, {
        startsAt: new Date(editForm.startsAt).toISOString(),
        endsAt: new Date(editForm.endsAt).toISOString(),
        capacity: editForm.capacity,
        fee: editForm.fee,
      })
      setRounds(prev => prev.map(x => (x.roundId === editingId ? r.data : x)))
      toast('회차가 수정되었습니다 ✓', 'success')
      setEditingId(null)
    } catch (err: unknown) {
      const e = err as { status?: number; body?: { data?: { code?: string } } }
      const code = e.body?.data?.code
      if (code === 'ROUND_HAS_RESERVATIONS') {
        setEditError('예약이 있는 회차는 수정할 수 없습니다. 예약이 모두 취소되면 다시 수정할 수 있습니다.')
      } else if (code === 'ROUND_ALREADY_STARTED') {
        setEditError('이미 시작한 회차는 수정할 수 없습니다.')
      } else if (e.status === 400) {
        setEditError('입력값을 확인해주세요. (시작은 미래, 종료 > 시작, 정원 1 이상)')
      } else if (e.status === 403) {
        setEditError('이 박람회의 주최자만 수정할 수 있습니다.')
      } else {
        setEditError('회차 수정에 실패했습니다.')
      }
    } finally {
      setEditLoading(false)
    }
  }

  async function handleDeleteRound(r: Round) {
    // 마지막 살아있는 회차를 지우면 회차가 없는 공개 박람회가 되므로 서버가 먼저 비공개로 바꾼다.
    // 목록과 상태를 이미 들고 있어 별도 조회 없이 미리 알릴 수 있다.
    const isLast = rounds.length === 1
    const warning = isLast && isPublished
      ? '현재 회차를 삭제하면 회차가 남지 않아 박람회가 비공개로 전환됩니다. 계속하시겠습니까?'
      : '이 회차를 삭제하시겠습니까?'
    if (!confirm(warning)) return

    setDeletingId(r.roundId)
    try {
      await roundApi.deleteRound(id, r.roundId)
      setRounds(prev => prev.filter(x => x.roundId !== r.roundId))
      if (isLast && isPublished) {
        setExpo(prev => (prev ? { ...prev, status: 'HIDDEN' } : prev))
        toast('회차가 삭제되어 박람회가 비공개로 전환되었습니다', 'success')
      } else {
        toast('회차가 삭제되었습니다', 'success')
      }
    } catch (err: unknown) {
      const e = err as { status?: number; body?: { data?: { code?: string } } }
      const code = e.body?.data?.code
      if (code === 'ROUND_HAS_RESERVATIONS') {
        toast('예약이 있는 회차는 삭제할 수 없습니다. 예약이 모두 취소되면 다시 삭제할 수 있습니다', 'error')
      } else if (code === 'ROUND_ALREADY_STARTED') {
        toast('이미 시작한 회차는 삭제할 수 없습니다', 'error')
      } else if (e.status === 503) {
        toast('박람회 비공개 전환에 실패해 삭제하지 않았습니다. 잠시 후 다시 시도해주세요', 'error')
      } else {
        toast('회차 삭제에 실패했습니다', 'error')
      }
    } finally {
      setDeletingId(null)
    }
  }

  async function handlePublish() {
    if (!confirm('박람회를 공개하시겠습니까? 방문자에게 즉시 노출됩니다.')) return
    setPublishing(true)
    try {
      // 응답은 { expoId, status } 뿐이라 기존 정보를 유지한 채 상태만 갈아끼운다.
      const r = await expoApi.publishExpo(id)
      setExpo(prev => (prev ? { ...prev, status: r.data.status } : prev))
      toast('박람회가 공개되었습니다', 'success')
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

              {isClosed && (
                <div className="alert alert-warning" style={{ marginTop: 16, marginBottom: 0 }}>
                  ⚑ 모든 회차가 종료되어 박람회가 자동으로 마감되었습니다. 박람회 탐색 목록에는 더 이상 노출되지 않습니다.
                  종료된 회차는 수정·삭제할 수 없습니다.
                </div>
              )}

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
              <div style={{ display: 'flex', gap: 8 }}>
                {rounds.length > 0 && (
                  <button
                    className="btn btn-outline btn-sm"
                    onClick={() => handleDownload()}
                    disabled={downloadingRoundId !== null}
                  >
                    {downloadingRoundId === 'all' ? '다운로드 중...' : '전체 명단 다운로드'}
                  </button>
                )}
                {!isClosed && (
                  <button className="btn btn-outline btn-sm" onClick={() => setShowForm(f => !f)}>
                    {showForm ? '취소' : '+ 회차 추가'}
                  </button>
                )}
              </div>
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
                <p className="es-title">등록된 회차가 없습니다</p>
                <p className="es-desc">회차를 추가해야 박람회를 공개할 수 있습니다.</p>
                <button className="btn btn-primary" onClick={() => setShowForm(true)}>회차 추가하기</button>
              </div>
            ) : (
              <div>
                {rounds.map(r => {
                  const s = summary[r.roundId]
                  // 예약이 한 건이라도 있거나 이미 시작했으면 서버가 409 로 거절한다. 버튼도 미리 막는다.
                  const hasReservation = r.remaining < r.capacity
                  // eslint-disable-next-line react-hooks/purity -- 시각 비교는 렌더 시점 스냅샷이면 충분하다.
                  const started = new Date(r.startsAt).getTime() <= Date.now()
                  const locked = hasReservation || started

                  if (editingId === r.roundId) {
                    return (
                      <form key={r.roundId} className="round-card" onSubmit={handleUpdateRound}
                            style={{ display: 'block', padding: 16 }}>
                        {editError && <div className="alert alert-danger" style={{ marginBottom: 12 }}>{editError}</div>}
                        <div className="form-row">
                          <div className="form-group">
                            <label className="form-label">시작</label>
                            <input className="form-input" type="datetime-local" value={editForm.startsAt}
                                   onChange={e => setEditForm(p => ({ ...p, startsAt: e.target.value }))} required />
                          </div>
                          <div className="form-group">
                            <label className="form-label">종료</label>
                            <input className="form-input" type="datetime-local" value={editForm.endsAt}
                                   onChange={e => setEditForm(p => ({ ...p, endsAt: e.target.value }))} required />
                          </div>
                        </div>
                        <div className="form-row">
                          <div className="form-group">
                            <label className="form-label">정원</label>
                            <input className="form-input" type="number" min={1} value={editForm.capacity}
                                   onChange={e => setEditForm(p => ({ ...p, capacity: Number(e.target.value) }))} required />
                          </div>
                          <div className="form-group">
                            <label className="form-label">참가비(원)</label>
                            <input className="form-input" type="number" min={0} value={editForm.fee}
                                   onChange={e => setEditForm(p => ({ ...p, fee: Number(e.target.value) }))} required />
                          </div>
                        </div>
                        <p className="form-hint" style={{ marginBottom: 12 }}>
                          참가비를 바꿔도 이미 만들어진 예약의 결제 금액은 변하지 않습니다. 금액은 신청 시점에 고정됩니다.
                        </p>
                        <div style={{ display: 'flex', gap: 8 }}>
                          <button className="btn btn-primary btn-sm" type="submit" disabled={editLoading}>
                            {editLoading ? '저장 중...' : '저장'}
                          </button>
                          <button className="btn btn-secondary btn-sm" type="button"
                                  onClick={() => setEditingId(null)} disabled={editLoading}>
                            취소
                          </button>
                        </div>
                      </form>
                    )
                  }

                  return (
                    <div key={r.roundId} className="round-card">
                      <div style={{ flex: 1 }}>
                        <div style={{ fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>
                          {r.sequence > 0 && (
                            <span style={{ color: 'var(--primary)', marginRight: 8 }}>{r.sequence}회차</span>
                          )}
                          {fmt(r.startsAt)} – {fmt(r.endsAt)}
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--sub)' }}>
                          정원 {r.capacity}명 · 잔여 {r.remaining}명
                          {r.fee !== undefined && ` · ${r.fee === 0 ? '무료' : `${r.fee.toLocaleString()}원`}`}
                        </div>
                        {s && (
                          <div style={{ fontSize: 12, color: 'var(--sub)', marginTop: 4 }}>
                            확정 {s.confirmed}명 · 취소 {s.cancelled}명
                            {s.checkedIn != null && ` · 체크인 ${s.checkedIn}명`}
                          </div>
                        )}
                      </div>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => startEdit(r)}
                        disabled={locked}
                        title={started ? '이미 시작한 회차는 수정할 수 없습니다'
                          : hasReservation ? '예약이 있는 회차는 수정할 수 없습니다' : undefined}
                      >
                        수정
                      </button>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleDownload(r.roundId)}
                        disabled={downloadingRoundId !== null}
                      >
                        {downloadingRoundId === r.roundId ? '다운로드 중...' : '명단 다운로드'}
                      </button>
                      <button
                        className="btn btn-secondary btn-sm"
                        onClick={() => handleDeleteRound(r)}
                        disabled={locked || deletingId !== null}
                        title={started ? '이미 시작한 회차는 삭제할 수 없습니다'
                          : hasReservation ? '예약이 있는 회차는 삭제할 수 없습니다' : undefined}
                      >
                        {deletingId === r.roundId ? '삭제 중...' : '삭제'}
                      </button>
                    </div>
                  )
                })}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
