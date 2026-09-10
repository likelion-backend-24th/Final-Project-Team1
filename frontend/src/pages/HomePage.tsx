import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { promotionApi } from '../api/promotion'
import type { ActivePromotion } from '../api/promotion'
import { expoKey } from '../types'
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

const SORT_TABS = [
  { key: 'recommended', label: '추천순' },
  { key: 'newest', label: '새행사순' },
  { key: 'deadline', label: '모집마감일순' },
] as const

const THUMB_COLORS = [
  ['#1A1A2E', '#16213E'],
  ['#134E4A', '#0F766E'],
  ['#1E3A5F', '#1D4ED8'],
  ['#3B0764', '#6D28D9'],
  ['#7C2D12', '#C2410C'],
  ['#14532D', '#15803D'],
]

const CAT_ICON: Record<string, string> = {
  'IT·전자': '💻', '식품·음료': '🍽️', '패션·뷰티': '👗',
  '교육·취업': '🎓', '문화·예술': '🎨', '기타': '📦',
}

export default function HomePage() {
  const navigate = useNavigate()
  const [expos, setExpos] = useState<Expo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [category, setCategory] = useState('전체')
  const [sort, setSort] = useState<'recommended' | 'newest' | 'deadline'>('recommended')
  const [banners, setBanners] = useState<ActivePromotion[]>([])
  const [bannerIdx, setBannerIdx] = useState(0)
  const bannerTimer = useRef<ReturnType<typeof setInterval> | null>(null)

  // 박람회 목록
  useEffect(() => {
    let cancelled = false
    setLoading(true)
    expoApi.listPublished({
      category: category === '전체' ? undefined : category,
      sort,
    })
      .then(res => { if (!cancelled) { setExpos(res.data ?? []); setError(false) } })
      .catch(() => { if (!cancelled) setError(true) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [category, sort])

  // VIP 배너 (30초 rotation)
  useEffect(() => {
    promotionApi.getActive()
      .then(res => setBanners(res.data ?? []))
      .catch(() => {/* 배너 실패는 무시 */})

    bannerTimer.current = setInterval(() => {
      setBannerIdx(i => i + 1)
    }, 5000) // UI 체감을 위해 5초로 설정 (실제 백엔드 rotation은 30초)
    return () => { if (bannerTimer.current) clearInterval(bannerTimer.current) }
  }, [])

  const catIcon = (label: string) => CATS.find(c => c.label === label)?.icon ?? '🏷️'

  const banner = banners.length > 0 ? banners[bannerIdx % banners.length] : null

  return (
    <>
      {/* ─── VIP 배너 ─── */}
      {banner && (
        <div
          style={{
            background: 'linear-gradient(135deg, #7C3AED, #4F46E5)',
            color: '#fff',
            padding: '12px 0',
            cursor: 'pointer',
          }}
          onClick={() => navigate(`/expos/${banner.expoId}`)}
        >
          <div className="container" style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            <span style={{
              background: 'rgba(255,255,255,0.2)',
              fontSize: 11,
              fontWeight: 800,
              padding: '2px 8px',
              borderRadius: 4,
              letterSpacing: '.04em',
            }}>VIP</span>
            <span style={{ fontSize: 14, fontWeight: 600, flex: 1 }}>
              {CAT_ICON[banner.expoCategory] ?? '🎪'} {banner.expoTitle}
            </span>
            <span style={{ fontSize: 11, opacity: .7 }}>
              {bannerIdx % banners.length + 1} / {banners.length}
            </span>
          </div>
        </div>
      )}

      {/* ─── Hero ─── */}
      <section className="hero">
        <div className="container">
          <p className="hero-eyebrow">ExpoHub — 박람회 플랫폼</p>
          <h1>
            원하는 박람회를<br />
            <em>지금 바로</em> 찾아보세요
          </h1>
          <p>IT·식품·패션·문화까지, 다양한 분야의 박람회가 모여있습니다</p>
        </div>
      </section>

      {/* ─── Content ─── */}
      <div className="container page-wrap">

        {/* Sort tabs */}
        <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
          {SORT_TABS.map(t => (
            <button
              key={t.key}
              className={`btn btn-sm ${sort === t.key ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setSort(t.key)}
            >
              {t.label}
            </button>
          ))}
        </div>

        {/* Category filter */}
        <div className="cat-bar">
          {CATS.map(c => (
            <button
              key={c.label}
              className={`cat-chip ${category === c.label ? 'active' : ''}`}
              onClick={() => setCategory(c.label)}
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
            <button className="btn btn-outline" onClick={() => setSort(sort)}>새로고침</button>
          </div>
        ) : expos.length === 0 ? (
          <div className="empty-state">
            <div className="es-icon">🔍</div>
            <p className="es-title">검색 결과가 없습니다</p>
            <p className="es-desc">다른 카테고리를 선택해보세요.</p>
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
                  catIcon={catIcon(expo.category)}
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
