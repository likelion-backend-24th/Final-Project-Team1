import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { recommendationApi, type RecommendationItem } from '../api/recommendation'
import { useAuth } from '../context/AuthContext'
import { usePageTitle } from '../hooks/usePageTitle'

export default function RecommendationsPage() {
  usePageTitle('맞춤 추천')
  const { user } = useAuth()
  const navigate = useNavigate()
  const [items, setItems] = useState<RecommendationItem[]>([])
  const [loading, setLoading] = useState(true)
  const [generatedAt, setGeneratedAt] = useState<string | null>(null)

  useEffect(() => {
    if (!user) { navigate('/auth'); return }
    recommendationApi.getRecommendations(20)
      .then(res => {
        setItems(res.data?.recommendations ?? [])
        setGeneratedAt(res.data?.generatedAt ?? null)
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [user, navigate])

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      <div className="container page-wrap">
        <div className="page-header">
          <h1 className="page-title">🎯 맞춤 추천</h1>
          <p style={{ fontSize: 13, color: 'var(--sub)', marginTop: 4 }}>
            내 예약·체크인 이력을 분석해 AI가 추천하는 박람회입니다.
          </p>
          {generatedAt && (
            <p style={{ fontSize: 12, color: 'var(--sub)', marginTop: 2 }}>
              기준: {new Date(generatedAt).toLocaleString('ko-KR')}
            </p>
          )}
        </div>

        {loading ? (
          <div style={{ textAlign: 'center', padding: '80px 0', color: 'var(--sub)' }}>불러오는 중...</div>
        ) : items.length === 0 ? (
          <div className="empty-state">
            <p className="es-title">아직 추천할 박람회가 없어요</p>
            <p className="es-desc">
              박람회를 예약하거나 체크인하면 AI가 취향을 분석해 추천해드립니다.<br />
              마이페이지에서 관심사를 등록하면 더 빠르게 추천받을 수 있어요.
            </p>
            <div style={{ display: 'flex', gap: 10, justifyContent: 'center', marginTop: 16 }}>
              <button className="btn btn-outline" onClick={() => navigate('/')}>박람회 둘러보기</button>
              <button className="btn btn-primary" onClick={() => navigate('/my/profile')}>관심사 등록</button>
            </div>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {items.map((item, idx) => (
              <RecommendationCard
                key={item.expoId}
                item={item}
                rank={idx + 1}
                onClick={() => navigate(`/expos/${item.expoId}`)}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}

function RecommendationCard({ item, rank, onClick }: {
  item: RecommendationItem
  rank: number
  onClick: () => void
}) {
  return (
    <div
      className="card"
      style={{ padding: '20px 24px', cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 20 }}
      onClick={onClick}
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && onClick()}
    >
      <div style={{
        minWidth: 40, height: 40, borderRadius: '50%',
        background: rank <= 3 ? 'var(--teal)' : 'var(--gray3)',
        color: rank <= 3 ? '#fff' : 'var(--sub)',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        fontWeight: 800, fontSize: 16,
      }}>
        {rank}
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <h3 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text)', marginBottom: 8, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {item.title}
        </h3>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {item.matchedTags.map(tag => (
            <span key={tag} className="badge badge-blue">#{tag}</span>
          ))}
        </div>
      </div>

      <div style={{ textAlign: 'right', minWidth: 60 }}>
        <div style={{ fontSize: 18, fontWeight: 800, color: 'var(--teal)' }}>
          {item.score.toFixed(1)}
        </div>
        <div style={{ fontSize: 11, color: 'var(--sub)' }}>점수</div>
      </div>
    </div>
  )
}
