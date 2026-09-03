import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { useAuth } from '../context/AuthContext'
import type { Expo, Round } from '../types'

function fmtDate(dt: string) {
  return new Date(dt).toLocaleDateString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric' })
}
function fmtTime(dt: string) {
  return new Date(dt).toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })
}

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

  useEffect(() => {
    if (!expoId) return
    // 회차는 별도 API 로 가져오지 않는다.
    // GET /expos/{id} 응답에 expo-service 가 reservation-service 의 내부 API 를
    // 호출해 병합한 rounds 가 이미 들어 있다.
    // 그 호출이 실패하면 박람회 정보는 200 으로 내려오고 roundsAvailable=false 가 된다(부분 실패 허용).
    expoApi.getExpo(Number(expoId))
      .then(res => {
        setExpo(res.data)
        setRounds(res.data.rounds ?? [])
        setRoundsError(res.data.roundsAvailable === false)
      })
      .catch(() => navigate('/'))
      .finally(() => setExpoLoading(false))
  }, [expoId, navigate])

  if (expoLoading) return (
    <div style={{ textAlign: 'center', padding: '120px 0', color: 'var(--sub)' }}>불러오는 중...</div>
  )
  if (!expo) return null

  const status = expo.status ?? 'PUBLISHED'
  const colors = THUMB_COLORS[expo.category] ?? ['#1A1A2E', '#374151']
  const catIcon = { 'IT·전자': '💻', '식품·음료': '🍽️', '패션·뷰티': '👗', '교육·취업': '🎓', '문화·예술': '🎨', '기타': '📦' }[expo.category] ?? '🎪'

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      {/* Thumb banner */}
      <div style={{
        background: `linear-gradient(135deg, ${colors[0]}, ${colors[1]})`,
        height: 280,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: 96,
      }}>
        {catIcon}
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

            <h1 style={{ fontSize: 30, fontWeight: 800, color: 'var(--text)', lineHeight: 1.3, marginBottom: 16 }}>
              {expo.title}
            </h1>

            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 20, marginBottom: 28 }}>
              {expo.region && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 14, color: 'var(--sub)' }}>
                  <span>📍</span><span>{expo.region}</span>
                </div>
              )}
              {expo.venue && (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 14, color: 'var(--sub)' }}>
                  <span>🏛</span><span>{expo.venue}</span>
                </div>
              )}
            </div>

            {expo.description && (
              <div className="card" style={{ padding: '24px 28px' }}>
                <h3 style={{ fontSize: 14, fontWeight: 700, color: 'var(--sub)', textTransform: 'uppercase', letterSpacing: '.04em', marginBottom: 12 }}>
                  박람회 소개
                </h3>
                <p style={{ fontSize: 15, color: 'var(--text2)', lineHeight: 1.8 }}>{expo.description}</p>
              </div>
            )}
          </div>

          {/* ─── Right: Rounds ─── */}
          <div>
            <div className="card" style={{ padding: 24 }}>
              <h2 style={{ fontSize: 16, fontWeight: 800, color: 'var(--text)', marginBottom: 4 }}>회차 정보</h2>
              <p style={{ fontSize: 12, color: 'var(--sub)', marginBottom: 20 }}>
                잔여 정원이 있는 회차에 예약할 수 있습니다.
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
                    const isFull = r.remaining === 0
                    const pct = Math.round((r.remaining / r.capacity) * 100)
                    return (
                      <div
                        key={r.roundId}
                        style={{
                          padding: '16px',
                          border: `1.5px solid ${isFull ? 'var(--border)' : 'var(--border)'}`,
                          borderRadius: 'var(--r-sm)',
                          background: isFull ? 'var(--gray1)' : 'var(--surface)',
                          opacity: isFull ? .6 : 1,
                        }}
                      >
                        <div style={{ fontSize: 13, fontWeight: 700, color: 'var(--text)', marginBottom: 6 }}>
                          {fmtDate(r.startsAt)}
                        </div>
                        <div style={{ fontSize: 12, color: 'var(--sub)', marginBottom: 10 }}>
                          {fmtTime(r.startsAt)} – {fmtTime(r.endsAt)}
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
                            <span style={{ fontSize: 11, fontWeight: 700, color: isFull ? 'var(--sub)' : 'var(--teal)' }}>
                              {isFull ? '마감' : `잔여 ${r.remaining}명`}
                            </span>
                          </div>
                        </div>

                        {isRole('USER') && (
                          <button
                            className={`btn ${isFull ? 'btn-secondary' : 'btn-primary'} btn-sm btn-block`}
                            disabled={isFull}
                            onClick={() => alert('예약 기능은 Sprint 2에서 제공됩니다.')}
                          >
                            {isFull ? '마감된 회차' : '예약하기'}
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
    </div>
  )
}
