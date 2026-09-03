import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { expoApi } from '../api/expo'
import type { Expo } from '../types'

const CATEGORIES = ['전체', 'IT·전자', '식품·음료', '패션·뷰티', '교육·취업', '문화·예술', '기타']
const CATEGORY_EMOJI: Record<string, string> = {
  'IT·전자': '💻', '식품·음료': '🍽️', '패션·뷰티': '👗', '교육·취업': '🎓', '문화·예술': '🎨', '기타': '📦'
}

export default function HomePage() {
  const navigate = useNavigate()
  const [expos, setExpos] = useState<Expo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [category, setCategory] = useState('전체')
  const [keyword, setKeyword] = useState('')
  const [search, setSearch] = useState('')

  useEffect(() => {
    setLoading(true)
    setError(false)
    expoApi.listPublished({
      category: category === '전체' ? undefined : category,
      keyword: search || undefined,
    })
      .then(res => setExpos(res.data ?? []))
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [category, search])

  function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    setSearch(keyword)
  }

  return (
    <>
      {/* Hero */}
      <div className="hero-banner">
        <div className="container">
          <h1>박람회의 모든 것, ExpoHub</h1>
          <p>관심 분야의 박람회를 찾아 예약하세요</p>
          <form className="hero-search" onSubmit={handleSearch}>
            <input
              type="text"
              placeholder="박람회 검색..."
              value={keyword}
              onChange={e => setKeyword(e.target.value)}
            />
            <button type="submit" className="btn btn-primary">검색</button>
          </form>
        </div>
      </div>

      <div className="container page-wrap">
        {/* Category filter */}
        <div className="filter-bar">
          {CATEGORIES.map(c => (
            <button
              key={c}
              className={`filter-chip ${category === c ? 'active' : ''}`}
              onClick={() => setCategory(c)}
            >
              {CATEGORY_EMOJI[c] ?? '🏷️'} {c}
            </button>
          ))}
        </div>

        {/* Results */}
        {loading ? (
          <p style={{ textAlign: 'center', color: 'var(--gray5)', padding: '60px 0' }}>불러오는 중...</p>
        ) : error ? (
          <div className="empty-state">
            <div className="emoji">⚠️</div>
            <p>불러올 수 없습니다.</p>
            <button className="btn btn-outline" onClick={() => setCategory(category)}>새로고침</button>
          </div>
        ) : !expos.length ? (
          <div className="empty-state">
            <div className="emoji">🔍</div>
            <p>조건에 맞는 박람회가 없습니다.</p>
          </div>
        ) : (
          <>
            <div className="section-header">
              <span className="section-title">
                {category === '전체' ? '전체 박람회' : category}
                <span style={{ fontSize: 14, fontWeight: 400, color: 'var(--gray5)', marginLeft: 8 }}>
                  {expos.length}개
                </span>
              </span>
            </div>
            <div className="expo-grid">
              {expos.map(expo => (
                <ExpoCard key={expo.id} expo={expo} onClick={() => navigate(`/expos/${expo.id}`)} />
              ))}
            </div>
          </>
        )}
      </div>
    </>
  )
}

function ExpoCard({ expo, onClick }: { expo: Expo; onClick: () => void }) {
  const emoji = CATEGORY_EMOJI[expo.category] ?? '🏷️'
  const colors = ['#1C2340', '#2E3A5C', '#3B5BDB', '#0CA678', '#7048E8']
  const color = colors[expo.id % colors.length]

  return (
    <div className="expo-card" onClick={onClick}>
      <div className="expo-card-img" style={{ background: `linear-gradient(135deg, ${color}, ${color}cc)` }}>
        <span style={{ fontSize: 48 }}>{emoji}</span>
      </div>
      <div className="expo-card-body">
        <p className="expo-card-channel">{expo.channelName ?? `채널 #${expo.channelId}`}</p>
        <h3 className="expo-card-title">{expo.title}</h3>
        <div className="expo-card-meta">
          {expo.region && <span>📍 {expo.region}</span>}
          {expo.venue && <span>🏛 {expo.venue}</span>}
        </div>
      </div>
      <div className="expo-card-footer">
        <span className="badge badge-published">공개중</span>
        <span style={{ fontSize: 12, color: 'var(--primary)', fontWeight: 700 }}>자세히 보기 →</span>
      </div>
    </div>
  )
}
