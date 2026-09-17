import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { recommendationApi, type RecommendationItem } from '../api/recommendation'
import { cdnImage } from '../lib/cloudinary'
import type { ExpoSort } from '../api/expo'
import { expoKey } from '../types'
import type { ActivePromotion, Expo } from '../types'
import { usePageTitle } from '../hooks/usePageTitle'
import { useAuth } from '../context/AuthContext'

const CATS = ['전체', 'IT·전자', '식품·음료', '패션·뷰티', '교육·취업', '문화·예술', '기타']

const SORTS: { value: ExpoSort; label: string }[] = [
  { value: 'recommended', label: 'AI 추천순' },
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
  const { user } = useAuth()
  const [expos, setExpos] = useState<Expo[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [category, setCategory] = useState('전체')
  const [sort, setSort] = useState<ExpoSort>('recommended')
  const [promotions, setPromotions] = useState<ActivePromotion[]>([])
  const [recommendations, setRecommendations] = useState<RecommendationItem[]>([])
  const [tagMap, setTagMap] = useState<Record<string, string[]>>({})
  usePageTitle(category === '전체' ? '박람회 탐색' : `${category} 박람회`)

  useEffect(() => {
    let cancelled = false
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true)
    expoApi.listPublished({
      category: category === '전체' ? undefined : category,
      sort: sort === 'recommended' ? undefined : sort,
    })
      .then(res => {
        if (cancelled) return
        const list = res.data ?? []
        setExpos(list)
        setError(false)
        const ids = list.map(e => expoKey(e)).filter(Boolean)
        if (ids.length > 0) {
          recommendationApi.getBulkTags(ids)
            .then(r => { if (!cancelled) setTagMap(r.data ?? {}) })
            .catch(() => {})
        }
      })
      .catch(() => { if (!cancelled) setError(true) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [category, sort])

  useEffect(() => {
    expoApi.getActivePromotions()
      .then(res => setPromotions(res.data ?? []))
      .catch(() => setPromotions([]))
  }, [])

  useEffect(() => {
    if (!user) return
    recommendationApi.getRecommendations(20)
      .then(res => setRecommendations(res.data?.recommendations ?? []))
      .catch(() => setRecommendations([]))
  }, [user])

  const vipIds = new Set(promotions.map(p => p.expoId))

  // AI 추천순: VIP 먼저, 그 다음 AI 추천 점수순, 나머지
  const displayExpos = (() => {
    if (sort !== 'recommended') return expos
    const recOrder = new Map(recommendations.map((r, i) => [r.expoId, i]))
    return [...expos].sort((a, b) => {
      const aVip = vipIds.has(expoKey(a)) ? 0 : 1
      const bVip = vipIds.has(expoKey(b)) ? 0 : 1
      if (aVip !== bVip) return aVip - bVip
      const aRec = recOrder.get(expoKey(a)) ?? 9999
      const bRec = recOrder.get(expoKey(b)) ?? 9999
      return aRec - bRec
    })
  })()

  // AI 배너용: 추천 결과에서 expos 목록의 thumbnailUrl 보충
  const expoMap = new Map(expos.map(e => [expoKey(e), e]))

  return (
    <>
      {/* ─── Hero Carousel (VIP 슬라이드 포함) ─── */}
      <HeroCarousel promotions={promotions.slice(0, 10)} onNavigate={id => navigate(`/expos/${id}`)} />

      {/* ─── AI 추천 배너: AI 추천 있을 때만 표시 ─── */}
      {recommendations.length > 0 && (
        <AiRecommendBanner
          recommendations={recommendations.slice(0, 6)}
          expoMap={expoMap}
          onNavigate={id => navigate(`/expos/${id}`)}
        />
      )}

      {/* ─── Content ─── */}
      <div className="container page-wrap">
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
                <span className="section-count">{displayExpos.length}개</span>
              </span>
            </div>
            <div className="expo-grid">
              {displayExpos.map(expo => (
                <ExpoCard
                  key={expoKey(expo)}
                  expo={expo}
                  colors={THUMB_COLORS[expoKey(expo) % THUMB_COLORS.length]}
                  isVip={vipIds.has(expoKey(expo))}
                  tags={tagMap[String(expoKey(expo))] ?? []}
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

// ─── Hero: 기본 hero 텍스트 + VIP 슬라이드 스와이프 캐러셀 ───
function HeroCarousel({ promotions, onNavigate }: {
  promotions: ActivePromotion[]
  onNavigate: (id: number) => void
}) {
  // 0 = 기본 hero 슬라이드, 1..n = VIP 슬라이드
  const total = 1 + promotions.length
  const [idx, setIdx] = useState(0)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const swipeRef = useRef<{ startX: number; swiped: boolean }>({ startX: 0, swiped: false })

  function resetTimer() {
    if (timerRef.current) clearInterval(timerRef.current)
    if (total <= 1) return
    timerRef.current = setInterval(() => setIdx(i => (i + 1) % total), 5000)
  }

  useEffect(() => {
    resetTimer()
    return () => { if (timerRef.current) clearInterval(timerRef.current) }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [total])

  function go(i: number) {
    setIdx(i)
    resetTimer()
  }

  function handlePointerDown(e: React.PointerEvent) {
    swipeRef.current = { startX: e.clientX, swiped: false }
  }

  function handlePointerUp(e: React.PointerEvent) {
    const diff = e.clientX - swipeRef.current.startX
    if (Math.abs(diff) > 50) {
      swipeRef.current.swiped = true
      go(diff < 0 ? (idx + 1) % total : (idx - 1 + total) % total)
    }
  }

  const vip = idx > 0 ? promotions[idx - 1] : null

  return (
    <section
      className="hero"
      style={{
        position: 'relative',
        cursor: vip ? 'pointer' : 'default',
        overflow: 'hidden',
        aspectRatio: '16 / 5',
        padding: 0,
        display: 'flex',
        alignItems: 'center',
      }}
      onPointerDown={handlePointerDown}
      onPointerUp={handlePointerUp}
      onClick={() => { if (swipeRef.current.swiped) { swipeRef.current.swiped = false; return }; if (vip) onNavigate(vip.expoId) }}
      role={vip ? 'button' : undefined}
      tabIndex={vip ? 0 : undefined}
      onKeyDown={e => vip && e.key === 'Enter' && onNavigate(vip.expoId)}
    >
      {/* VIP 슬라이드 배경 이미지 */}
      {vip && vip.thumbnailUrl && (
        <>
          <img
            src={cdnImage(vip.thumbnailUrl, 400)}
            alt=""
            aria-hidden
            style={{
              position: 'absolute', inset: 0, width: '100%', height: '100%',
              objectFit: 'cover', filter: 'blur(24px)', transform: 'scale(1.1)', opacity: 0.4,
            }}
          />
          <img
            src={cdnImage(vip.thumbnailUrl, 1600)}
            alt=""
            style={{
              position: 'absolute', inset: 0, width: '100%', height: '100%',
              objectFit: 'cover', opacity: 0.35,
            }}
          />
        </>
      )}

      {/* 어두운 오버레이 */}
      {vip && (
        <div style={{
          position: 'absolute', inset: 0,
          background: 'linear-gradient(135deg, rgba(0,0,0,0.65) 0%, rgba(0,0,0,0.3) 100%)',
        }} />
      )}

      {/* 이전 화살표 */}
      {total > 1 && (
        <button
          onClick={e => { e.stopPropagation(); go((idx - 1 + total) % total) }}
          aria-label="이전 슬라이드"
          style={{
            position: 'absolute', left: 16, top: '50%', transform: 'translateY(-50%)',
            width: 40, height: 40, borderRadius: '50%',
            background: 'rgba(255,255,255,0.18)', border: '1px solid rgba(255,255,255,0.3)',
            color: '#fff', fontSize: 22, cursor: 'pointer', zIndex: 3,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >‹</button>
      )}

      {/* 다음 화살표 */}
      {total > 1 && (
        <button
          onClick={e => { e.stopPropagation(); go((idx + 1) % total) }}
          aria-label="다음 슬라이드"
          style={{
            position: 'absolute', right: 16, top: '50%', transform: 'translateY(-50%)',
            width: 40, height: 40, borderRadius: '50%',
            background: 'rgba(255,255,255,0.18)', border: '1px solid rgba(255,255,255,0.3)',
            color: '#fff', fontSize: 22, cursor: 'pointer', zIndex: 3,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >›</button>
      )}

      <div className="container" style={{ position: 'relative', zIndex: 1, width: '100%' }}>
        {vip ? (
          <div>
            <h2 style={{ fontSize: 26, fontWeight: 800, color: '#fff', lineHeight: 1.3, marginBottom: 8 }}>
              {vip.title}
            </h2>
            <p style={{ fontSize: 13, color: 'rgba(255,255,255,0.75)', marginBottom: 16 }}>
              {[vip.category, vip.region].filter(Boolean).join(' · ')}
            </p>
            <button
              onClick={e => { e.stopPropagation(); onNavigate(vip.expoId) }}
              style={{
                fontSize: 12, fontWeight: 600, color: '#fff',
                background: 'transparent',
                border: '1.5px solid rgba(255,255,255,0.6)',
                padding: '6px 16px', borderRadius: 20, cursor: 'pointer',
              }}
            >
              자세히 보기
            </button>
          </div>
        ) : (
          <>
            <p className="hero-eyebrow">ExpoHub — 박람회 플랫폼</p>
            <h1>
              원하는 박람회를<br />
              <em>지금 바로</em> 찾아보세요
            </h1>
            <p style={{ marginBottom: 0 }}>IT·식품·패션·문화까지, 다양한 분야의 박람회가 모여있습니다</p>
          </>
        )}
      </div>

      {/* 닷 네비게이션 */}
      {total > 1 && (
        <div style={{
          position: 'absolute', bottom: 16, left: '50%', transform: 'translateX(-50%)',
          display: 'flex', gap: 8, zIndex: 2,
        }}>
          {Array.from({ length: total }).map((_, i) => (
            <button
              key={i}
              onClick={e => { e.stopPropagation(); go(i) }}
              aria-label={`슬라이드 ${i + 1}`}
              style={{
                width: i === idx ? 20 : 8, height: 8, borderRadius: 4, border: 'none', cursor: 'pointer', padding: 0,
                background: i === idx ? '#fff' : 'rgba(255,255,255,0.45)',
                transition: 'all 0.3s',
              }}
            />
          ))}
        </div>
      )}
    </section>
  )
}

// ─── AI 추천 배너: hero와 동일한 전체 폭 + 비율 유지 캐러셀 ───
function AiRecommendBanner({ recommendations, expoMap, onNavigate }: {
  recommendations: RecommendationItem[]
  expoMap: Map<number, Expo>
  onNavigate: (id: number) => void
}) {
  const slides = recommendations.slice(0, 6).map(r => ({
    id: r.expoId,
    title: r.title,
    thumbnailUrl: expoMap.get(r.expoId)?.thumbnailUrl,
    tags: r.matchedTags,
  }))

  const [idx, setIdx] = useState(0)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const swipeRef = useRef<{ startX: number; swiped: boolean }>({ startX: 0, swiped: false })

  function resetTimer() {
    if (timerRef.current) clearInterval(timerRef.current)
    if (slides.length <= 1) return
    timerRef.current = setInterval(() => setIdx(i => (i + 1) % slides.length), 4000)
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setIdx(0)
    resetTimer()
    return () => { if (timerRef.current) clearInterval(timerRef.current) }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slides.length])

  function go(i: number) { setIdx(i); resetTimer() }

  function handlePointerDown(e: React.PointerEvent) {
    swipeRef.current = { startX: e.clientX, swiped: false }
  }

  function handlePointerUp(e: React.PointerEvent) {
    const diff = e.clientX - swipeRef.current.startX
    if (Math.abs(diff) > 50) {
      swipeRef.current.swiped = true
      go(diff < 0 ? (idx + 1) % slides.length : (idx - 1 + slides.length) % slides.length)
    }
  }

  const slide = slides[idx]
  if (!slide) return null
  const colors = THUMB_COLORS[slide.id % THUMB_COLORS.length]

  return (
    <section
      className="hero"
      style={{
        position: 'relative',
        cursor: 'pointer',
        overflow: 'hidden',
        aspectRatio: '16 / 5',
        padding: 0,
        display: 'flex',
        alignItems: 'center',
        background: slide.thumbnailUrl ? undefined : `linear-gradient(135deg, ${colors[0]}, ${colors[1]})`,
      }}
      onPointerDown={handlePointerDown}
      onPointerUp={handlePointerUp}
      onClick={() => { if (swipeRef.current.swiped) { swipeRef.current.swiped = false; return }; onNavigate(slide.id) } }
      role="button"
      tabIndex={0}
      onKeyDown={e => e.key === 'Enter' && onNavigate(slide.id)}
    >
      {slide.thumbnailUrl && (
        <>
          <img
            src={cdnImage(slide.thumbnailUrl, 400)}
            alt="" aria-hidden
            style={{
              position: 'absolute', inset: 0, width: '100%', height: '100%',
              objectFit: 'cover', filter: 'blur(24px)', transform: 'scale(1.1)', opacity: 0.4,
            }}
          />
          <img
            src={cdnImage(slide.thumbnailUrl, 1600)}
            alt=""
            style={{
              position: 'absolute', inset: 0, width: '100%', height: '100%',
              objectFit: 'cover', opacity: 0.35,
            }}
          />
        </>
      )}

      <div style={{
        position: 'absolute', inset: 0,
        background: 'linear-gradient(135deg, rgba(0,0,0,0.65) 0%, rgba(0,0,0,0.3) 100%)',
      }} />

      {/* 이전 화살표 */}
      {slides.length > 1 && (
        <button
          onClick={e => { e.stopPropagation(); go((idx - 1 + slides.length) % slides.length) }}
          aria-label="이전 추천"
          style={{
            position: 'absolute', left: 16, top: '50%', transform: 'translateY(-50%)',
            width: 40, height: 40, borderRadius: '50%',
            background: 'rgba(255,255,255,0.18)', border: '1px solid rgba(255,255,255,0.3)',
            color: '#fff', fontSize: 22, cursor: 'pointer', zIndex: 3,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >‹</button>
      )}

      {/* 다음 화살표 */}
      {slides.length > 1 && (
        <button
          onClick={e => { e.stopPropagation(); go((idx + 1) % slides.length) }}
          aria-label="다음 추천"
          style={{
            position: 'absolute', right: 16, top: '50%', transform: 'translateY(-50%)',
            width: 40, height: 40, borderRadius: '50%',
            background: 'rgba(255,255,255,0.18)', border: '1px solid rgba(255,255,255,0.3)',
            color: '#fff', fontSize: 22, cursor: 'pointer', zIndex: 3,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >›</button>
      )}

      <div className="container" style={{ position: 'relative', zIndex: 1, width: '100%' }}>
        <span style={{
          fontSize: 11, fontWeight: 700, color: '#38BDF8',
          letterSpacing: '0.05em', display: 'block', marginBottom: 8,
        }}>
          ✨ AI 추천
        </span>
        <h2 style={{ fontSize: 26, fontWeight: 800, color: '#fff', lineHeight: 1.3, marginBottom: 8 }}>
          {slide.title}
        </h2>
        {slide.tags.length > 0 && (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 16 }}>
            {slide.tags.slice(0, 4).map(tag => (
              <span key={tag} style={{
                fontSize: 11, color: 'rgba(255,255,255,0.85)',
                background: 'rgba(255,255,255,0.15)', padding: '2px 8px', borderRadius: 10,
              }}>#{tag}</span>
            ))}
          </div>
        )}
        <button
          onClick={e => { e.stopPropagation(); onNavigate(slide.id) }}
          style={{
            fontSize: 12, fontWeight: 600, color: '#fff',
            background: 'transparent',
            border: '1.5px solid rgba(255,255,255,0.6)',
            padding: '6px 16px', borderRadius: 20, cursor: 'pointer',
          }}
        >
          자세히 보기
        </button>
      </div>

      {/* 닷 네비게이션 */}
      {slides.length > 1 && (
        <div style={{
          position: 'absolute', bottom: 16, left: '50%', transform: 'translateX(-50%)',
          display: 'flex', gap: 8, zIndex: 2,
        }}>
          {slides.map((_, i) => (
            <button
              key={i}
              onClick={e => { e.stopPropagation(); go(i) }}
              aria-label={`추천 ${i + 1}`}
              style={{
                width: i === idx ? 20 : 8, height: 8, borderRadius: 4, border: 'none', cursor: 'pointer', padding: 0,
                background: i === idx ? '#fff' : 'rgba(255,255,255,0.45)',
                transition: 'all 0.3s',
              }}
            />
          ))}
        </div>
      )}
    </section>
  )
}

function ExpoCard({ expo, colors, isVip, tags = [], onClick }: {
  expo: Expo
  colors: string[]
  isVip?: boolean
  tags?: string[]
  onClick: () => void
}) {
  const [broken, setBroken] = useState(false)

  return (
    <div className="expo-card" onClick={onClick} role="button" tabIndex={0}>
      <div className="expo-card-thumb">
        <div
          className="expo-card-thumb-inner"
          style={{ background: `linear-gradient(135deg, ${colors[0]}, ${colors[1]})` }}
        >
          {expo.thumbnailUrl && !broken && (
            <img
              src={cdnImage(expo.thumbnailUrl, 600)}
              alt=""
              loading="lazy"
              onError={() => setBroken(true)}
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          )}
        </div>
        <div className="expo-card-thumb-badge">
          {isVip && <span className="badge" style={{ background: '#7C3AED', color: '#fff', marginRight: 4 }}>⭐ VIP</span>}
          <span className="badge badge-published">● 공개중</span>
        </div>
        {expo.paid != null && (
          <div className="expo-card-thumb-fee">
            <span className={`badge ${expo.paid ? 'badge-paid' : 'badge-free'}`}>
              {expo.paid ? '유료' : '무료'}
            </span>
          </div>
        )}
      </div>

      <div className="expo-card-body">
        <p className="expo-card-cat">{expo.category}</p>
        <h3 className="expo-card-title">{expo.title}</h3>
        {tags.length > 0 && (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 4, marginTop: 6, marginBottom: 4 }}>
            {tags.slice(0, 3).map(tag => (
              <span key={tag} style={{ fontSize: 11, color: 'var(--teal)', fontWeight: 600 }}>#{tag}</span>
            ))}
          </div>
        )}
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
