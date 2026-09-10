import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { expoApi } from '../api/expo'
import type { ExpoSort } from '../api/expo'
import { expoKey } from '../types'
import type { ActivePromotion, Expo } from '../types'

const CATS = ['전체', 'IT·전자', '식품·음료', '패션·뷰티', '교육·취업', '문화·예술', '기타']

const SORTS: { value: ExpoSort; label: string }[] = [
  { value: 'recommended', label: '추천순' },
  { value: 'newest', label: '새 행사순' },
  { value: 'deadline', label: '모집마감일순' },
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
  const [sort, setSort] = useState<ExpoSort>('recommended')
  const [promotions, setPromotions] = useState<ActivePromotion[]>([])

  useEffect(() => {
    let cancelled = false
    setLoading(true)

    const fetchExpos = sort === 'recommended'
      // 추천순: 전체 가져온 뒤 클라이언트에서 VIP 우선 정렬
      ? expoApi.listPublished({ category: category === '전체' ? undefined : category })
      : expoApi.listPublished({ category: category === '전체' ? undefined : category, sort })

    fetchExpos
      .then(res => {
        if (cancelled) return
        const list = res.data ?? []
        if (sort === 'recommended' && promotions.length > 0) {
          const vipIds = new Set(promotions.map(p => p.expoId))
          const vip = list.filter(e => vipIds.has(expoKey(e)))
          const rest = list.filter(e => !vipIds.has(expoKey(e)))
          setExpos([...vip, ...rest])
        } else {
          setExpos(list)
        }
        setError(false)
      })
      .catch(() => { if (!cancelled) setError(true) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [category, sort, promotions])

  useEffect(() => {
    expoApi.getActivePromotions()
      .then(res => setPromotions(res.data ?? []))
      .catch(() => setPromotions([]))
  }, [])

  const vipIds = new Set(promotions.map(p => p.expoId))

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
          {/* 키워드 검색은 Sprint 2 에서 열린다. 카테고리 필터로 대체. */}
        </div>
      </section>

      {/* ─── Content ─── */}
      <div className="container page-wrap">

        {/* Category filter */}
        <div className="cat-bar">
          {CATS.map(c => (
            <button
              key={c}
              className={`cat-chip ${category === c ? 'active' : ''}`}
              onClick={() => setCategory(c)}
            >
              {c}
            </button>
          ))}
        </div>

        {/* Sort */}
        <div className="sort-bar">
          {SORTS.map(s => (
            <button
              key={s.value}
              className={`sort-chip ${sort === s.value ? 'active' : ''}`}
              onClick={() => setSort(s.value)}
            >
              {s.label}
            </button>
          ))}
        </div>

        {loading ? (
          <SkeletonGrid />
        ) : error ? (
          <div className="empty-state">
            <p className="es-title">불러올 수 없습니다</p>
            <p className="es-desc">잠시 후 다시 시도해주세요.</p>
            <button className="btn btn-outline" onClick={() => setCategory(category)}>새로고침</button>
          </div>
        ) : expos.length === 0 ? (
          <div className="empty-state">
            <p className="es-title">검색 결과가 없습니다</p>
            <p className="es-desc">다른 키워드나 카테고리로 검색해보세요.</p>
          </div>
        ) : (
          <>
            <div className="section-header">
              <span className="section-title">
                {category === '전체' ? '전체 박람회' : category}
                <span className="section-count">{expos.length}개</span>
              </span>
            </div>
            <div className="expo-grid">
              {expos.map(expo => (
                <ExpoCard
                  key={expoKey(expo)}
                  expo={expo}
                  colors={THUMB_COLORS[expoKey(expo) % THUMB_COLORS.length]}
                  isVip={vipIds.has(expoKey(expo))}
                  onClick={() => navigate(`/expos/${expoKey(expo)}`)}
                />
              ))}
            </div>
          </>
        )}
      </div>
    </>
  )
}

function ExpoCard({ expo, colors, isVip, onClick }: {
  expo: Expo
  colors: string[]
  isVip?: boolean
  onClick: () => void
}) {
  return (
    <div className="expo-card" onClick={onClick} role="button" tabIndex={0}>
      <div className="expo-card-thumb">
        <div
          className="expo-card-thumb-inner"
          style={{ background: `linear-gradient(135deg, ${colors[0]}, ${colors[1]})` }}
        />
        <div className="expo-card-thumb-badge">
          {isVip && <span className="badge" style={{ background: '#7C3AED', color: '#fff', marginRight: 4 }}>⭐ VIP</span>}
          <span className="badge badge-published">● 공개중</span>
        </div>
      </div>

      <div className="expo-card-body">
        <p className="expo-card-cat">{expo.category}</p>
        <h3 className="expo-card-title">{expo.title}</h3>
        <div className="expo-card-meta">
          {expo.venue && (
            <div className="expo-card-meta-row">
              <span>{expo.venue}</span>
            </div>
          )}
          {expo.region && (
            <div className="expo-card-meta-row">
              <span>{expo.region}</span>
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
