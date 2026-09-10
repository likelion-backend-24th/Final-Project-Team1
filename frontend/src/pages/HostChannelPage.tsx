import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { promotionApi } from '../api/promotion'
import type { ApplyPromotionResponse } from '../api/promotion'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/Toast'
import { expoKey } from '../types'
import type { Channel, Expo } from '../types'

export default function HostChannelPage() {
  const { isRole } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  const [channel, setChannel] = useState<Channel | null>(null)
  const [expos, setExpos] = useState<Expo[]>([])
  const [loadingCh, setLoadingCh] = useState(true)
  const [loadingEx, setLoadingEx] = useState(false)
  // promotionId per expoId — track active promotions on this page
  const [activePromos, setActivePromos] = useState<Record<number, number>>({})
  const [promoLoading, setPromoLoading] = useState<Record<number, boolean>>({})

  function loadExpos(channelId: number) {
    setLoadingEx(true)
    expoApi.listPublished({ size: 100 })
      .then(res => setExpos((res.data ?? []).filter(e => e.channelId === channelId)))
      .catch(() => toast('박람회 목록을 불러오지 못했습니다', 'error'))
      .finally(() => setLoadingEx(false))
  }

  function loadActivePromos() {
    promotionApi.getActive()
      .then(res => {
        const map: Record<number, number> = {}
        for (const p of (res.data ?? [])) map[p.expoId] = p.promotionId
        setActivePromos(map)
      })
      .catch(() => {/* 무시 */})
  }

  useEffect(() => {
    if (!isRole('ORGANIZER', 'SUPER_ADMIN')) {
      toast('주최자 권한이 필요합니다', 'error')
      navigate('/')
    }
  }, [])

  useEffect(() => {
    expoApi.getMyChannel()
      .then(res => {
        if (res.data) {
          setChannel(res.data)
          loadExpos(res.data.id)
        }
      })
      .catch((err: { status?: number }) => {
        if (err.status !== 404) toast('채널 정보를 불러오지 못했습니다', 'error')
      })
      .finally(() => setLoadingCh(false))

    loadActivePromos()
  }, [])

  async function handleApply(expoId: number) {
    setPromoLoading(p => ({ ...p, [expoId]: true }))
    try {
      const res = await promotionApi.apply(expoId)
      const data = res.data as ApplyPromotionResponse
      toast(`VIP 배너 신청 완료 (결제ID: ${data.paymentId})`, 'success')
      loadActivePromos()
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 409) toast('이미 진행 중인 배너 신청이 있습니다', 'error')
      else toast('배너 신청에 실패했습니다', 'error')
    } finally {
      setPromoLoading(p => ({ ...p, [expoId]: false }))
    }
  }

  async function handleRefund(expoId: number, promotionId: number) {
    setPromoLoading(p => ({ ...p, [expoId]: true }))
    try {
      await promotionApi.refund(promotionId)
      toast('VIP 배너가 환불되었습니다', 'success')
      setActivePromos(p => { const next = { ...p }; delete next[expoId]; return next })
    } catch {
      toast('환불에 실패했습니다', 'error')
    } finally {
      setPromoLoading(p => ({ ...p, [expoId]: false }))
    }
  }

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      <div className="container page-wrap">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 32 }}>
          <div className="page-header" style={{ marginBottom: 0 }}>
            <h1 className="page-title">주최자 센터</h1>
            <p className="page-sub">내 채널과 소속 박람회를 관리하세요.</p>
          </div>
          {!channel && !loadingCh && (
            <button className="btn btn-primary" onClick={() => navigate('/host/channel/new')}>
              + 채널 생성
            </button>
          )}
        </div>

        {loadingCh ? (
          <p style={{ color: 'var(--sub)', textAlign: 'center', padding: '60px 0' }}>불러오는 중...</p>
        ) : !channel ? (
          <div className="empty-state">
            <div className="es-icon">📢</div>
            <p className="es-title">채널이 없습니다</p>
            <p className="es-desc">채널을 먼저 만들어야 박람회를 등록할 수 있습니다.</p>
            <button className="btn btn-primary" onClick={() => navigate('/host/channel/new')}>
              채널 만들기
            </button>
          </div>
        ) : (
          <div>
            <div className="section-header">
              <div>
                <span className="section-title">{channel.name}</span>
                {channel.description && (
                  <p style={{ fontSize: 13, color: 'var(--sub)', marginTop: 4 }}>{channel.description}</p>
                )}
              </div>
              <button
                className="btn btn-primary btn-sm"
                onClick={() => navigate(`/host/expos/new?channelId=${channel.id}`)}
              >
                + 박람회 등록
              </button>
            </div>

            <div className="alert alert-warning" style={{ marginBottom: 16 }}>
              ⚑ 공개(PUBLISHED)된 박람회만 표시됩니다.
            </div>

            {loadingEx ? (
              <p style={{ color: 'var(--sub)', padding: '40px 0' }}>불러오는 중...</p>
            ) : expos.length === 0 ? (
              <div className="empty-state">
                <div className="es-icon">🎪</div>
                <p className="es-title">등록된 박람회가 없습니다</p>
                <p className="es-desc">첫 번째 박람회를 등록해보세요.</p>
                <button
                  className="btn btn-primary"
                  onClick={() => navigate(`/host/expos/new?channelId=${channel.id}`)}
                >
                  박람회 등록하기
                </button>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {expos.map(expo => {
                  const eid = expoKey(expo)
                  const promoId = activePromos[eid]
                  const busy = promoLoading[eid] ?? false
                  return (
                    <ExpoRow
                      key={eid}
                      expo={expo}
                      hasActivePromo={promoId != null}
                      promoLoading={busy}
                      onManage={() => navigate(`/host/expos/${eid}/rounds`, { state: { expo } })}
                      onApplyPromo={() => handleApply(eid)}
                      onRefundPromo={() => handleRefund(eid, promoId)}
                    />
                  )
                })}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function ExpoRow({
  expo, hasActivePromo, promoLoading, onManage, onApplyPromo, onRefundPromo,
}: {
  expo: Expo
  hasActivePromo: boolean
  promoLoading: boolean
  onManage: () => void
  onApplyPromo: () => void
  onRefundPromo: () => void
}) {
  return (
    <div className="card" style={{ padding: '18px 22px', display: 'flex', alignItems: 'center', gap: 16 }}>
      <div
        style={{
          width: 48, height: 48,
          borderRadius: 'var(--r-sm)',
          background: 'linear-gradient(135deg, var(--navy), #2E3A5C)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 22, flexShrink: 0, cursor: 'pointer',
        }}
        onClick={onManage}
      >
        🎪
      </div>

      <div style={{ flex: 1, minWidth: 0, cursor: 'pointer' }} onClick={onManage}>
        <div style={{ display: 'flex', gap: 6, marginBottom: 6 }}>
          <span className={`badge badge-${(expo.status ?? 'PUBLISHED').toLowerCase()}`}>
            {(expo.status ?? 'PUBLISHED') === 'PUBLISHED' ? '● 공개중' : expo.status === 'HIDDEN' ? '○ HIDDEN' : '● 종료'}
          </span>
          <span className="badge badge-blue">{expo.category}</span>
          {hasActivePromo && (
            <span className="badge" style={{ background: '#7C3AED', color: '#fff' }}>⭐ VIP</span>
          )}
        </div>
        <h3 style={{
          fontWeight: 700, color: 'var(--text)', fontSize: 15,
          overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>
          {expo.title}
        </h3>
        {(expo.region || expo.venue) && (
          <p style={{ fontSize: 12, color: 'var(--sub)', marginTop: 2 }}>
            {[expo.venue, expo.region].filter(Boolean).join(' · ')}
          </p>
        )}
      </div>

      <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
        {hasActivePromo ? (
          <button
            className="btn btn-sm btn-secondary"
            disabled={promoLoading}
            onClick={onRefundPromo}
          >
            {promoLoading ? '처리중...' : 'VIP 환불'}
          </button>
        ) : (
          <button
            className="btn btn-sm"
            style={{ background: '#7C3AED', color: '#fff', border: 'none' }}
            disabled={promoLoading}
            onClick={onApplyPromo}
          >
            {promoLoading ? '처리중...' : '⭐ VIP 배너 신청'}
          </button>
        )}
        <button
          className="btn btn-secondary btn-sm"
          onClick={onManage}
        >
          회차 관리 →
        </button>
      </div>
    </div>
  )
}
