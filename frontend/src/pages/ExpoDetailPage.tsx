import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { roundApi } from '../api/round'
import { useAuth } from '../context/AuthContext'
import type { Expo, Round } from '../types'

function fmt(dt: string) {
  return new Date(dt).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

export default function ExpoDetailPage() {
  const { expoId } = useParams<{ expoId: string }>()
  const navigate = useNavigate()
  const { isRole } = useAuth()

  const [expo, setExpo] = useState<Expo | null>(null)
  const [rounds, setRounds] = useState<Round[]>([])
  const [expoLoading, setExpoLoading] = useState(true)
  const [roundsLoading, setRoundsLoading] = useState(true)
  const [roundsError, setRoundsError] = useState(false)

  useEffect(() => {
    if (!expoId) return
    const id = Number(expoId)

    expoApi.getExpo(id)
      .then(res => setExpo(res.data))
      .catch(() => navigate('/'))
      .finally(() => setExpoLoading(false))

    roundApi.listByExpo(id)
      .then(res => setRounds(res.data ?? []))
      .catch(() => setRoundsError(true))
      .finally(() => setRoundsLoading(false))
  }, [expoId])

  if (expoLoading) return (
    <div style={{ textAlign: 'center', padding: '80px 0', color: 'var(--gray5)' }}>불러오는 중...</div>
  )
  if (!expo) return null

  return (
    <div className="container page-wrap">
      <button className="btn btn-secondary btn-sm" onClick={() => navigate(-1)} style={{ marginBottom: 24 }}>
        ← 목록으로
      </button>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 360px', gap: 32, alignItems: 'start' }}>
        {/* Main info */}
        <div>
          <div style={{ background: 'linear-gradient(135deg,var(--navy),var(--navy2))', borderRadius: 'var(--r)', height: 240, display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 24 }}>
            <span style={{ fontSize: 72 }}>🎪</span>
          </div>

          <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
            <span className={`badge badge-${expo.status.toLowerCase()}`}>
              {expo.status === 'PUBLISHED' ? '공개중' : expo.status === 'HIDDEN' ? '비공개' : '종료'}
            </span>
            <span className="badge" style={{ background: 'var(--blue-l)', color: 'var(--blue)' }}>
              {expo.category}
            </span>
          </div>

          <h1 style={{ fontSize: 28, fontWeight: 800, color: 'var(--navy)', marginBottom: 8 }}>{expo.title}</h1>

          <div style={{ display: 'flex', gap: 20, color: 'var(--gray5)', fontSize: 14, marginBottom: 20 }}>
            {expo.region && <span>📍 {expo.region}</span>}
            {expo.venue && <span>🏛 {expo.venue}</span>}
          </div>

          {expo.description && (
            <p style={{ fontSize: 15, color: 'var(--gray6)', lineHeight: 1.8, marginBottom: 24 }}>
              {expo.description}
            </p>
          )}
        </div>

        {/* Rounds */}
        <div>
          <div className="card" style={{ padding: 24 }}>
            <h2 className="section-title" style={{ marginBottom: 16 }}>회차 정보</h2>

            {roundsLoading ? (
              <p style={{ color: 'var(--gray5)', fontSize: 13 }}>불러오는 중...</p>
            ) : roundsError ? (
              <div className="alert alert-warning">회차 정보 조회 실패 — 새로고침해주세요.</div>
            ) : !rounds.length ? (
              <p style={{ color: 'var(--gray5)', fontSize: 13 }}>등록된 회차가 없습니다.</p>
            ) : (
              rounds.map(r => {
                const isFull = r.remaining === 0
                return (
                  <div key={r.id} className="round-card" style={{ flexDirection: 'column', alignItems: 'flex-start' }}>
                    <div style={{ fontWeight: 700, color: 'var(--navy)', fontSize: 14 }}>
                      {fmt(r.startAt)} ~ {fmt(r.endAt)}
                    </div>
                    <div style={{ display: 'flex', gap: 16, fontSize: 13, color: 'var(--gray5)', marginTop: 6 }}>
                      <span>정원 {r.capacity}명</span>
                      <span className={`round-card-remain ${isFull ? 'full' : ''}`}>
                        {isFull ? '마감' : `잔여 ${r.remaining}명`}
                      </span>
                    </div>
                    {isRole('USER') && (
                      <button
                        className="btn btn-primary btn-sm"
                        disabled={isFull}
                        style={{ marginTop: 10, width: '100%' }}
                        onClick={() => alert('예약 기능은 Sprint 2에서 제공됩니다.')}
                      >
                        {isFull ? '마감' : '예약하기'}
                      </button>
                    )}
                  </div>
                )
              })
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
