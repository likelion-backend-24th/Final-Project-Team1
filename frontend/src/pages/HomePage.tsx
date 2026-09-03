import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { expoApi } from '../api/expo'
import type { Expo } from '../types'

const CATS = [
  { label: '전체', icon: '🏷️' },
  { label: 'IT·전자', icon: '💻' },
  { label: '식품·음료', icon: '🍽️' },
  { label: '패션·뷰티', icon: '👗' },
  { label: '교육·취업', icon: '🎓' },
  { label: '문화·예술', icon: '🎨' },
  { label: '기타', icon: '📦' },
]

const THUMB_COLORS = [
  ['#1A1A2E', '#16213E'],
  ['#134E4A', '#0F766E'],
  ['#1E3A5F', '#1D4ED8'],
  ['#3B0764', '#6D28D9'],
  ['#7C2D12', '#C2410C'],
  ['#14532D', '#15803D'],
]

export default function HomePage() {
  const navigate = useNavigate()
  const [expos, setExpos] = useState<Expo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [category, setCategory] = useState('전체')
  const [input, setInput] = useState('')
  const [keyword, setKeyword] = useState('')

  useEffect(() => {
    setLoading(true)
    setError(false)
    expoApi.listPublished({
      category: category === '전체' ? undefined : category,
      keyword: keyword || undefined,
    })
      .then(res => setExpos(res.data ?? []))
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }, [category, keyword])

  function handleSearch(e: React.FormEvent) {
    e.preventDefault()
    setKeyword(input)
  }

  const catIcon = (label: string) => CATS.find(c => c.label === label)?.icon ?? '🏷️'

  return (
    <>
      {/* ─── Hero ─── */}
      <section className="hero">
        <div className="container">
          <p className="hero-eyebrow">ExpoHub — 박람회 플랫폼</p>
          <h1>
            원하는 박람회를<br />
            <em>지금 바로</em> 찾아보세요
          </h1>
          <p>IT·식품·패션·문화까지, 다양한 분야의 박람회가 모여있습니다</p>
          <form className="search-box" onSubmit={handleSearch}>
            <input
              type="text"
              placeholder="박람회명, 장소, 주최자 검색..."
              value={input}
              onChange={e => setInput(e.target.value)}
            />
            <button type="submit">🔍 검색</button>
          </form>
        </div>
      </section>

      {/* ─── Content ─── */}
      <div className="container page-wrap">

        {/* Category filter */}
        <div className="cat-bar">
          {CATS.map(c => (
            <button
              key={c.label}
              className={`cat-chip ${category === c.label ? 'active' : ''}`}
              onClick={() => { setCategory(c.label); setInput(''); setKeyword('') }}
            >
              <span className="cat-icon">{c.icon}</span>
              {c.label}
            </button>
          ))}
        </div>

        {loading ? (
          <SkeletonGrid />
        ) : error ? (
          <div className="empty-state">
            <div className="es-icon">⚠️</div>
            <p className="es-title">불러올 수 없습니다</p>
            <p className="es-desc">잠시 후 다시 시도해주세요.</p>
            <button className="btn btn-outline" onClick={() => setCategory(category)}>새로고침</button>
          </div>
        ) : expos.length === 0 ? (
          <div className="empty-state">
            <div className="es-icon">🔍</div>
            <p className="es-title">검색 결과가 없습니다</p>
            <p className="es-desc">다른 키워드나 카테고리로 검색해보세요.</p>
          </div>
        ) : (
          <>
            <div className="section-header">
              <span className="section-title">
                {keyword ? `"${keyword}" 검색 결과` : category === '전체' ? '전체 박람회' : category}
                <span className="section-count">{expos.length}개</span>
              </span>
            </div>
            <div className="expo-grid">
              {expos.map(expo => (
                <ExpoCard
                  key={expo.id}
                  expo={expo}
                  colors={THUMB_COLORS[expo.id % THUMB_COLORS.length]}
                  catIcon={catIcon(expo.category)}
                  onClick={() => navigate(`/expos/${expo.id}`)}
                />
              ))}
            </div>
          </>
        )}
      </div>
    </>
  )
}

function ExpoCard({ expo, colors, catIcon, onClick }: {
  expo: Expo
  colors: string[]
  catIcon: string
  onClick: () => void
}) {
  return (
    <div className="expo-card" onClick={onClick} role="button" tabIndex={0}>
      <div className="expo-card-thumb">
        <div
          className="expo-card-thumb-inner"
          style={{ background: `linear-gradient(135deg, ${colors[0]}, ${colors[1]})` }}
        >
          <span>{catIcon}</span>
        </div>
        <div className="expo-card-thumb-badge">
          <span className="badge badge-published">● 공개중</span>
        </div>
      </div>

      <div className="expo-card-body">
        <p className="expo-card-cat">{expo.category}</p>
        <h3 className="expo-card-title">{expo.title}</h3>
        <div className="expo-card-meta">
          {expo.venue && (
            <div className="expo-card-meta-row">
              <span className="expo-card-meta-icon">🏛</span>
              <span>{expo.venue}</span>
            </div>
          )}
          {expo.region && (
            <div className="expo-card-meta-row">
              <span className="expo-card-meta-icon">📍</span>
              <span>{expo.region}</span>
            </div>
          )}
          {expo.channelName && (
            <div className="expo-card-meta-row">
              <span className="expo-card-meta-icon">📢</span>
              <span>{expo.channelName}</span>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function SkeletonGrid() {
  return (
    <div className="expo-grid">
      {Array.from({ length: 8 }).map((_, i) => (
        <div key={i} className="expo-card" style={{ pointerEvents: 'none' }}>
          <div className="skeleton" style={{ paddingTop: '56.25%', borderRadius: 0 }} />
          <div style={{ padding: '14px 16px 16px' }}>
            <div className="skeleton" style={{ height: 12, width: '40%', marginBottom: 10 }} />
            <div className="skeleton" style={{ height: 16, marginBottom: 6 }} />
            <div className="skeleton" style={{ height: 14, width: '70%' }} />
          </div>
        </div>
      ))}
    </div>
  )
}
