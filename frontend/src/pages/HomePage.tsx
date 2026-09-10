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

  // 박람회 목록 — 추천순은 VIP 박람회만 표시
  useEffect(() => {
    let cancelled = false
    setLoading(true)

    if (sort === 'recommended') {
      // 추천순: 활성 VIP 프로모션 박람회만
      promotionApi.getActive()
        .then(async res => {
          if (cancelled) return
          const active = res.data ?? []
          if (active.length === 0) { setExpos([]); setError(false); return }
          // expoId 목록으로 전체 박람회 가져와서 VIP 것만 필터
          const all = await expoApi.listPublished({ size: 100 })
          const vipIds = new Set(active.map(p => p.expoId))
          setExpos((all.data ?? []).filter(e => vipIds.has(e.expoId ?? e.id)))
          setError(false)
        })
        .catch(() => { if (!cancelled) setError(true) })
        .finally(() => { if (!cancelled) setLoading(false) })
    } else {
      expoApi.listPublished({
        category: category === '전체' ? undefined : category,
        sort,
      })
        .then(res => { if (!cancelled) { setExpos(res.data ?? []); setError(false) } })
        .catch(() => { if (!cancelled) setError(true) })
        .finally(() => { if (!cancelled) setLoading(false) })
    }
    return () => { cancelled = true }
  }, [category, sort])

  // VIP 배너 (5초 rotation, 추천순 탭에서만 표시)
  useEffect(() => {
    promotionApi.getActive()
      .then(res => setBanners(res.data ?? []))
      .catch(() => {})

    bannerTimer.current = setInterval(() => setBannerIdx(i => i + 1), 5000)
    return () => { if (bannerTimer.current) clearInterval(bannerTimer.current) }
  }, [])

  const catIcon = (label: string) => CATS.find(c => c.label === label)?.icon ?? '🏷️'

  const banner = banners.length > 0 ? banners[bannerIdx % banners.length] : null
  const showBanner = sort === 'recommended' && banners.length > 0

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
        </div>
      </section>

      {/* ─── Content ─── */}
      <div className="container page-wrap">

        {/* 카테고리 필터 */}
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

        {/* 정렬 탭 */}
        <div style={{ display: 'flex', borderBottom: '2px solid var(--border)', marginBottom: 24 }}>
          {SORT_TABS.map(t => (
            <button
              key={t.key}
              onClick={() => setSort(t.key)}
              style={{
                padding: '10px 20px',
                fontSize: 14, fontWeight: sort === t.key ? 700 : 400,
                color: sort === t.key ? 'var(--primary)' : 'var(--sub)',
                background: 'none', border: 'none', cursor: 'pointer',
                borderBottom: sort === t.key ? '2px solid var(--primary)' : '2px solid transparent',
                marginBottom: -2,
              }}
            >
              {t.label}
            </button>
          ))}
        </div>

        {/* 추천순: VIP 배너 캐러셀 */}
        {showBanner && (
          <div
            style={{
              background: 'linear-gradient(135deg, #3B0764 0%, #6D28D9 45%, #4338CA 100%)',
              borderRadius: 16, padding: '32px 36px', marginBottom: 28,
              cursor: 'pointer', position: 'relative', overflow: 'hidden',
            }}
            onClick={() => navigate(`/expos/${banner!.expoId}`)}
          >
            <div style={{ position: 'absolute', top: -40, right: -40, width: 200, height: 200, borderRadius: '50%', background: 'rgba(255,255,255,0.06)', pointerEvents: 'none' }} />
            <div style={{ position: 'absolute', bottom: -60, right: 100, width: 160, height: 160, borderRadius: '50%', background: 'rgba(255,255,255,0.04)', pointerEvents: 'none' }} />

            <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
              <span style={{ background: 'linear-gradient(90deg, #FCD34D, #F59E0B)', color: '#1a1a1a', fontSize: 11, fontWeight: 900, padding: '3px 10px', borderRadius: 4, letterSpacing: '.08em' }}>⭐ VIP SPONSOR</span>
              <span style={{ color: 'rgba(255,255,255,0.6)', fontSize: 12 }}>
                {CAT_ICON[banner!.category] ?? '🎪'} {banner!.category}{banner!.region && ` · ${banner!.region}`}
              </span>
            </div>

            <h2 style={{ color: '#fff', fontSize: 24, fontWeight: 800, lineHeight: 1.3, marginBottom: 20, maxWidth: 600 }}>
              {banner!.title}
            </h2>

            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
              <button
                style={{ background: '#fff', color: '#6D28D9', border: 'none', borderRadius: 8, padding: '9px 22px', fontSize: 14, fontWeight: 700, cursor: 'pointer' }}
                onClick={e => { e.stopPropagation(); navigate(`/expos/${banner!.expoId}`) }}
              >
                자세히 보기 →
              </button>
              {banners.length > 1 && (
                <div style={{ display: 'flex', gap: 6 }}>
                  {banners.map((_, i) => (
                    <button key={i} onClick={e => { e.stopPropagation(); setBannerIdx(i) }}
                      style={{ width: i === bannerIdx % banners.length ? 20 : 8, height: 8, borderRadius: 4, border: 'none', background: i === bannerIdx % banners.length ? '#fff' : 'rgba(255,255,255,0.4)', cursor: 'pointer', padding: 0, transition: 'width .3s' }}
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        )}

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
            <div className="es-icon">{sort === 'recommended' ? '⭐' : '🔍'}</div>
            <p className="es-title">{sort === 'recommended' ? 'VIP 추천 박람회가 없습니다' : '검색 결과가 없습니다'}</p>
            <p className="es-desc">{sort === 'recommended' ? '주최자 센터에서 VIP 배너를 신청해보세요.' : '다른 카테고리를 선택해보세요.'}</p>
          </div>
        ) : (
          <>
            <div className="section-header" style={{ marginBottom: 16 }}>
              <span className="section-title">
                {sort === 'recommended' ? '추천 박람회' : category === '전체' ? '전체 박람회' : category}
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
