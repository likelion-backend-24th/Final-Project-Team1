import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { recommendationApi } from '../api/recommendation'
import { cdnImage } from '../lib/cloudinary'
import { useAuth } from '../context/AuthContext'
import ReservationModal from '../components/ReservationModal'
import { usePageTitle } from '../hooks/usePageTitle'
import type { Expo, Reservation, Round } from '../types'
import { expoKey } from '../types'

function fmtDate(dt: string) {
  return new Date(dt).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}
function fmtTime(dt: string) {
  return new Date(dt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}
function fmtMonthDay(dt: string) {
  return new Date(dt).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })
}
/**
 * 회차 시간 범위. 날짜가 시작 기준으로만 찍혀 있어서, 다음 날 끝나는 회차의 종료일이 화면에서 사라졌다.
 * 끝나는 날이 다르면 종료 쪽에 날짜를 붙인다.
 */
function fmtRange(startsAt: string, endsAt: string) {
  const sameDay = new Date(startsAt).toDateString() === new Date(endsAt).toDateString()
  return sameDay
    ? `${fmtTime(startsAt)} – ${fmtTime(endsAt)}`
    : `${fmtTime(startsAt)} – ${fmtMonthDay(endsAt)} ${fmtTime(endsAt)}`
}

/** 환불 창. 서버의 reservation.cancellation.refund-window(1d) 와 같은 값이어야 한다. */
const REFUND_WINDOW_MS = 24 * 60 * 60 * 1000

const THUMB_COLORS: Record<string, [string, string]> = {
  'IT·전자': ['#1E3A5F', '#1D4ED8'],
  '식품·음료': ['#134E4A', '#0F766E'],
  '패션·뷰티': ['#3B0764', '#7C3AED'],
  '교육·취업': ['#14532D', '#15803D'],
  '문화·예술': ['#7C2D12', '#C2410C'],
  '기타': ['#1A1A2E', '#374151'],
}

export default function ExpoDetailPage() {
  const { expoId } = useParams<{ expoId: string }>()
  const navigate = useNavigate()
  const { isRole } = useAuth()

  const [expo, setExpo] = useState<Expo | null>(null)
  const [rounds, setRounds] = useState<Round[]>([])
  const [expoLoading, setExpoLoading] = useState(true)
  const [roundsError, setRoundsError] = useState(false)
  const [reservingRound, setReservingRound] = useState<Round | null>(null)
  const [coverBroken, setCoverBroken] = useState(false)
  const [descOpen, setDescOpen] = useState(false)
  const [tags, setTags] = useState<string[]>([])
  const [similarExpos, setSimilarExpos] = useState<Expo[]>([])
  usePageTitle(expo?.title ?? '박람회 상세')

  // 회차는 별도 API 로 가져오지 않는다.
  // GET /expos/{id} 응답에 expo-service 가 reservation-service 의 내부 API 를
  // 호출해 병합한 rounds 가 이미 들어 있다.
  // 그 호출이 실패하면 박람회 정보는 200 으로 내려오고 roundsAvailable=false 가 된다(부분 실패 허용).
  const fetchExpo = useCallback(() =>
    expoApi.getExpo(Number(expoId))
      .then(res => {
        setExpo(res.data)
        setRounds(res.data.rounds ?? [])
        setRoundsError(res.data.roundsAvailable === false)
      })
      .catch(() => navigate('/'))
      .finally(() => setExpoLoading(false)), [expoId, navigate])

  const shiftRemaining = (reservation: Reservation, delta: number) =>
    setRounds(prev => prev.map(r =>
      r.roundId === reservation.roundId ? { ...r, remaining: r.remaining + delta } : r
    ))

  // 방금 예약한 만큼 잔여 정원을 즉시 반영한다 — 다음 회차 목록 재조회를 기다리지 않는다.
  const handleReservationSuccess = (reservation: Reservation) =>
    shiftRemaining(reservation, -reservation.headcount)

  // 결제를 취소해 자리를 돌려준 경우. 서버가 실제로 몇 자리를 되돌렸는지는 응답으로 알 수 없어
  // 계산하지 않고 다시 조회한다.
  const handleReservationReleased = () => { fetchExpo() }

  useEffect(() => {
    if (!expoId) return
    const id = Number(expoId)
    recommendationApi.getTags(id)
      .then(res => setTags(res.data?.tags ?? []))
      .catch(() => {})
    recommendationApi.getSimilar(id)
      .then(async res => {
        const ids = (res.data?.expoIds ?? []).slice(0, 4)
        const results = await Promise.allSettled(ids.map(eid => expoApi.getExpo(eid)))
        setSimilarExpos(results.filter(r => r.status === 'fulfilled').map(r => (r as PromiseFulfilledResult<{ data: Expo }>).value.data))
      })
      .catch(() => {})

    fetchExpo()

    // 다른 페이지(내 예약 등)에서 취소·예약해 잔여 정원이 바뀐 뒤 이 페이지로 돌아왔을 때
    // 탭이 다시 보이는 시점에 조용히 재조회한다(로딩 스피너 없이) — 별도 상태 공유가 없어서
    // 이 방법으로 최신화한다. 최초 로딩(expoLoading)은 건드리지 않는다.
    const onVisible = () => { if (document.visibilityState === 'visible') fetchExpo() }
    document.addEventListener('visibilitychange', onVisible)
    window.addEventListener('pageshow', onVisible)
    return () => {
      document.removeEventListener('visibilitychange', onVisible)
      window.removeEventListener('pageshow', onVisible)
    }
  }, [expoId, fetchExpo])

  if (expoLoading) return (
    <div style={{ textAlign: 'center', padding: '120px 0', color: 'var(--sub)' }}>불러오는 중...</div>
  )
  if (!expo) return null

  const status = expo.status ?? 'PUBLISHED'
  const colors = THUMB_COLORS[expo.category] ?? ['#1A1A2E', '#374151']

  // 예약 가능한 회차만으로 대표 가격을 정한다. 마감·종료된 회차의 가격을 보여주면 오해를 준다.
  // eslint-disable-next-line react-hooks/purity -- 시각 비교는 렌더 시점 스냅샷이면 충분하다(아래 회차 목록과 같은 기준).
  const now = Date.now()
  const openRounds = rounds.filter(r => r.remaining > 0 && new Date(r.startsAt).getTime() > now)
  const lowestFee = openRounds.length ? Math.min(...openRounds.map(r => r.fee)) : 0
  const isLongDesc = (expo.description?.length ?? 0) > 300
  const hasDetailImages = (expo.detailImageUrls?.length ?? 0) > 0

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      {/* 커버.
          주최자가 어떤 비율의 이미지를 넣을지 알 수 없으므로 잘라내지 않는다(contain).
          비면 허전하니 같은 이미지를 흐리게 깔아 여백을 메운다.
          이미지가 없거나 불러오지 못하면 카테고리 그라데이션으로 떨어진다. */}
      <div className="container" style={{ paddingTop: 24 }}>
        <div style={{
          position: 'relative',
          width: '100%',
          aspectRatio: '1000 / 343',
          borderRadius: 'var(--r)',
          overflow: 'hidden',
          background: `linear-gradient(135deg, ${colors[0]}, ${colors[1]})`,
        }}>
          {expo.thumbnailUrl && !coverBroken && (
            <>
              <img
                src={cdnImage(expo.thumbnailUrl, 400)}
                alt=""
                aria-hidden
                style={{
                  position: 'absolute', inset: 0, width: '100%', height: '100%',
                  objectFit: 'cover', filter: 'blur(28px)', transform: 'scale(1.12)', opacity: .5,
                }}
              />
              <img
                src={cdnImage(expo.thumbnailUrl, 1600)}
                alt={expo.title}
                onError={() => setCoverBroken(true)}
                style={{
                  position: 'absolute', inset: 0, width: '100%', height: '100%',
                  objectFit: 'contain',
                }}
              />
            </>
          )}
        </div>
      </div>

      <div className="container" style={{ paddingTop: 32, paddingBottom: 80 }}>
        {/* Back */}
        <button
          className="btn btn-secondary btn-sm"
          onClick={() => navigate(-1)}
          style={{ marginBottom: 20 }}
        >
          ← 목록으로
        </button>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 340px', gap: 32, alignItems: 'start' }}>
          {/* ─── Left: Info ─── */}
          <div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 14 }}>
              <span className={`badge badge-${status.toLowerCase()}`}>
                {status === 'PUBLISHED' ? '● 공개중' : status === 'HIDDEN' ? '비공개' : '종료'}
              </span>
              <span className="badge badge-blue">{expo.category}</span>
            </div>

            <h1 style={{ fontSize: 30, fontWeight: 800, color: 'var(--text)', lineHeight: 1.3, marginBottom: 12 }}>
              {expo.title}
            </h1>

            {tags.length > 0 && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 16 }}>
                {tags.map(tag => (
                  <span key={tag} className="badge badge-blue" style={{ background: 'var(--teal-l, #e0f7f5)', color: 'var(--teal)', fontWeight: 600 }}>
                    #{tag}
                  </span>
                ))}
              </div>
            )}

            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 20, marginBottom: 28 }}>
              {expo.region && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 14, color: 'var(--sub)' }}>
                  <span>{expo.region}</span>
                </div>
              )}
              {expo.venue && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 14, color: 'var(--sub)' }}>
                  <span>{expo.venue}</span>
                </div>
              )}
            </div>

            {(expo.description || hasDetailImages) && (
              <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
                <div style={{ padding: expo.description ? '24px 28px' : '24px 28px 16px' }}>
                <h3 style={{ fontSize: 14, fontWeight: 700, color: 'var(--sub)', textTransform: 'uppercase', letterSpacing: '.04em', marginBottom: expo.description ? 12 : 0 }}>
                  행사 소개
                </h3>
                {expo.description && (<>
                {/* pre-wrap 이 없으면 주최자가 나눠 쓴 문단이 한 덩어리로 붙는다. */}
                <div style={{ position: 'relative' }}>
                  <p style={{
                    fontSize: 15, color: 'var(--text2)', lineHeight: 1.8,
                    whiteSpace: 'pre-wrap', wordBreak: 'break-word',
                    maxHeight: isLongDesc && !descOpen ? 320 : 'none',
                    overflow: 'hidden',
                  }}>
                    {expo.description}
                  </p>
                  {/* 페이드는 실제로 잘렸을 때만. 짧은 글에 덮이면 멀쩡한 문장이 흐려 보인다. */}
                  {isLongDesc && !descOpen && (
                    <div style={{
                      position: 'absolute', left: 0, right: 0, bottom: 0, height: 80,
                      background: 'linear-gradient(to bottom, transparent, var(--surface))',
                      pointerEvents: 'none',
                    }} />
                  )}
                </div>
                {isLongDesc && (
                  <button
                    className="btn btn-secondary btn-sm btn-block"
                    style={{ marginTop: 12 }}
                    onClick={() => setDescOpen(v => !v)}
                  >
                    {descOpen ? '접기' : '더보기'}
                  </button>
                )}
                </>)}
                </div>

                {/* 상세 이미지는 카드 폭을 꽉 채운다. 주최자가 정한 순서대로 이어 붙는다. */}
                {expo.detailImageUrls?.map((url, i) => (
                  <img
                    key={`${url}-${i}`}
                    src={cdnImage(url, 1200)}
                    alt=""
                    loading="lazy"
                    onError={e => { (e.currentTarget as HTMLImageElement).style.display = 'none' }}
                    style={{ display: 'block', width: '100%', height: 'auto' }}
                  />
                ))}
              </div>
            )}
            {similarExpos.length > 0 && (
              <div style={{ marginTop: 32 }}>
                <h3 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text)', marginBottom: 16 }}>
                  이런 박람회도 있어요
                </h3>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 12 }}>
                  {similarExpos.map(e => {
                    const eid = expoKey(e)
                    const c = THUMB_COLORS[e.category] ?? ['#1A1A2E', '#374151']
                    return (
                      <div
                        key={eid}
                        className="card"
                        style={{ padding: 0, overflow: 'hidden', cursor: 'pointer' }}
                        onClick={() => navigate(`/expos/${eid}`)}
                        role="button"
                        tabIndex={0}
                        onKeyDown={ev => ev.key === 'Enter' && navigate(`/expos/${eid}`)}
                      >
                        <div style={{
                          height: 80,
                          background: `linear-gradient(135deg, ${c[0]}, ${c[1]})`,
                          display: 'flex', alignItems: 'center', justifyContent: 'center',
                        }}>
                          {e.thumbnailUrl && (
                            <img src={e.thumbnailUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                          )}
                        </div>
                        <div style={{ padding: '12px 14px' }}>
                          <span className="badge badge-blue" style={{ fontSize: 10, marginBottom: 6 }}>{e.category}</span>
                          <p style={{ fontSize: 13, fontWeight: 700, color: 'var(--text)', lineHeight: 1.4, overflow: 'hidden', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>
                            {e.title}
                          </p>
                        </div>
                      </div>
                    )
                  })}
                </div>
              </div>
            )}
          </div>

          {/* ─── Right: Rounds — 스크롤을 따라다닌다(event-us 의 신청 패널과 같은 역할) ─── */}
          <div style={{ position: 'sticky', top: 84 }}>
            <div className="card" style={{ padding: 24, maxHeight: 'calc(100vh - 110px)', overflowY: 'auto' }}>
              {openRounds.length > 0 && (
                <div style={{ marginBottom: 16 }}>
                  <div style={{ fontSize: 24, fontWeight: 800, color: 'var(--text)' }}>
                    {lowestFee === 0 ? '무료' : `${lowestFee.toLocaleString()}원`}
                    {openRounds.length > 1 && (
                      <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--sub)' }}>부터</span>
                    )}
                  </div>
                  <div style={{ fontSize: 12, color: 'var(--sub)', marginTop: 2 }}>
                    예약 가능한 회차 {openRounds.length}개
                  </div>
                </div>
              )}
              <h2 style={{ fontSize: 16, fontWeight: 800, color: 'var(--text)', marginBottom: 4 }}>회차 정보</h2>
              <p style={{ fontSize: 12, color: 'var(--sub)', marginBottom: 20 }}>
                예약은 회차가 시작하기 전까지 받습니다. 환불은 시작 24시간 전까지 취소한 경우에만 됩니다.
              </p>

              {roundsError ? (
                <div className="alert alert-warning" style={{ fontSize: 12 }}>
                  ⚠ 회차 정보 조회에 실패했습니다.
                </div>
              ) : rounds.length === 0 ? (
                <p style={{ fontSize: 13, color: 'var(--sub)', textAlign: 'center', padding: '20px 0' }}>
                  등록된 회차가 없습니다.
                </p>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {rounds.map(r => {
                    // eslint-disable-next-line react-hooks/purity -- 시각 비교는 렌더 시점 스냅샷이면 충분하다(폴링·실시간 갱신 불필요).
                    const nowMs = Date.now()
                    const startMs = new Date(r.startsAt).getTime()
                    const isEnded = new Date(r.endsAt).getTime() <= nowMs
                    // 예약은 회차 시작 전까지만. 진행 중인 회차는 "마감" 이지 "종료" 가 아니다.
                    const isStarted = startMs <= nowMs
                    const isFull = r.remaining === 0
                    const isClosed = isStarted || isFull
                    // 환불은 시작 24시간 전까지 취소한 경우에만 된다(서버 refund-window 와 같은 값).
                    const noRefund = r.fee > 0 && startMs - nowMs < REFUND_WINDOW_MS
                    const pct = Math.round((r.remaining / r.capacity) * 100)
                    return (
                      <div
                        key={r.roundId}
                        style={{
                          padding: '16px',
                          border: `1.5px solid ${isClosed ? 'var(--border)' : 'var(--border)'}`,
                          borderRadius: 'var(--r-sm)',
                          background: isClosed ? 'var(--gray1)' : 'var(--surface)',
                          opacity: isClosed ? .6 : 1,
                        }}
                      >
                        <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginBottom: 6 }}>
                          {r.sequence > 0 && (
                            <span style={{ fontSize: 12, fontWeight: 800, color: 'var(--primary)' }}>
                              {r.sequence}회차
                            </span>
                          )}
                          <span style={{ fontSize: 13, fontWeight: 700, color: 'var(--text)' }}>
                            {fmtDate(r.startsAt)}
                          </span>
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--sub)', marginBottom: 10 }}>
                          {fmtRange(r.startsAt, r.endsAt)}
                        </div>

                        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--text)', marginBottom: 10 }}>
                          {r.fee ? `${r.fee.toLocaleString()}원` : '무료'}
                        </div>

                        {/* Capacity bar */}
                        <div style={{ marginBottom: 10 }}>
                          <div style={{ height: 4, background: 'var(--gray3)', borderRadius: 4, overflow: 'hidden' }}>
                            <div style={{
                              height: '100%',
                              width: `${pct}%`,
                              background: pct > 30 ? 'var(--teal)' : pct > 10 ? 'var(--yellow)' : 'var(--red)',
                              borderRadius: 4,
                              transition: 'width .3s',
                            }} />
                          </div>
                          <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
                            <span style={{ fontSize: 11, color: 'var(--sub)' }}>정원 {r.capacity}명</span>
                            <span style={{ fontSize: 11, fontWeight: 700, color: isClosed ? 'var(--sub)' : 'var(--teal)' }}>
                              {isEnded ? '종료' : isStarted ? '예약 마감' : isFull ? '정원 마감' : `잔여 ${r.remaining}명`}
                            </span>
                          </div>
                        </div>

                        {!isClosed && noRefund && (
                          <p style={{ fontSize: 11, color: 'var(--yellow)', marginBottom: 8 }}>
                            ⚠ 시작이 24시간 안으로 남아 환불되지 않는 회차입니다
                          </p>
                        )}

                        {isRole('USER') && (
                          <button
                            className={`btn ${isClosed ? 'btn-secondary' : 'btn-primary'} btn-sm btn-block`}
                            disabled={isClosed}
                            onClick={() => setReservingRound(r)}
                          >
                            {isEnded ? '종료된 회차' : isStarted ? '예약이 마감된 회차' : isFull ? '정원이 찬 회차' : '예약하기'}
                          </button>
                        )}
                        {!isRole('USER') && !isRole('ORGANIZER') && !isRole('SUPER_ADMIN') && (
                          <button
                            className="btn btn-outline btn-sm btn-block"
                            onClick={() => navigate('/auth')}
                          >
                            로그인 후 예약
                          </button>
                        )}
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {reservingRound && (
        <ReservationModal
          round={reservingRound}
          onClose={() => setReservingRound(null)}
          onSuccess={handleReservationSuccess}
          onReleased={handleReservationReleased}
        />
      )}
    </div>
  )
}
