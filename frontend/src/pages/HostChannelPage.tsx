import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/Toast'
import { expoKey } from '../types'
import type { Channel, Expo } from '../types'

export default function HostChannelPage() {
  const { isRole } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  useEffect(() => {
    if (!isRole('ORGANIZER', 'SUPER_ADMIN')) {
      toast('주최자 권한이 필요합니다', 'error')
      navigate('/')
    }
  }, [])

  const [channels, setChannels] = useState<Channel[]>([])
  const [selected, setSelected] = useState<Channel | null>(null)
  const [expos, setExpos] = useState<Expo[]>([])
  const [loadingCh, setLoadingCh] = useState(true)
  const [loadingEx, setLoadingEx] = useState(false)

  useEffect(() => {
    // GET /channels/my 는 Spring Data Page 를 그대로 준다. data 가 배열이 아니라 { content: [...] }.
    expoApi.listMyChannels()
      .then(res => {
        const list = res.data?.content ?? []
        setChannels(list)
        if (list.length > 0) pickChannel(list[0])
      })
      .catch(() => toast('채널 목록을 불러오지 못했습니다', 'error'))
      .finally(() => setLoadingCh(false))
  }, [])

  /*
   * 주최자용 "내 채널의 박람회 목록" API 는 Sprint 1 에 없다.
   * 공개 목록(GET /expos)을 받아 channelId 로 걸러서 대신 보여준다.
   * 따라서 아직 공개하지 않은 HIDDEN 박람회는 여기 나오지 않는다.
   */
  function pickChannel(ch: Channel) {
    setSelected(ch)
    setLoadingEx(true)
    expoApi.listPublished({ size: 100 })
      .then(res => setExpos((res.data ?? []).filter(e => e.channelId === ch.id)))
      .catch(() => toast('박람회 목록을 불러오지 못했습니다', 'error'))
      .finally(() => setLoadingEx(false))
  }

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      <div className="container page-wrap">
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 32 }}>
          <div className="page-header" style={{ marginBottom: 0 }}>
            <h1 className="page-title">주최자 센터</h1>
            <p className="page-sub">채널을 선택해 소속 박람회를 관리하세요.</p>
          </div>
          <button className="btn btn-primary" onClick={() => navigate('/host/channel/new')}>
            + 채널 생성
          </button>
        </div>

        {loadingCh ? (
          <p style={{ color: 'var(--sub)', textAlign: 'center', padding: '60px 0' }}>불러오는 중...</p>
        ) : channels.length === 0 ? (
          <div className="empty-state">
            <div className="es-icon">📢</div>
            <p className="es-title">채널이 없습니다</p>
            <p className="es-desc">채널을 먼저 만들어야 박람회를 등록할 수 있습니다.</p>
            <button className="btn btn-primary" onClick={() => navigate('/host/channel/new')}>
              채널 만들기
            </button>
          </div>
        ) : (
          <div className="host-layout">
            {/* Sidebar */}
            <div className="host-sidebar">
              <p className="host-sidebar-label">내 채널</p>
              {channels.map(ch => (
                <div
                  key={ch.id}
                  className={`host-nav-item ${selected?.id === ch.id ? 'active' : ''}`}
                  onClick={() => pickChannel(ch)}
                >
                  <span className="nav-icon">📢</span>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {ch.name}
                  </span>
                </div>
              ))}
            </div>

            {/* Main */}
            <div>
              {selected && (
                <>
                  <div className="section-header">
                    <div>
                      <span className="section-title">{selected.name}</span>
                      {selected.description && (
                        <p style={{ fontSize: 13, color: 'var(--sub)', marginTop: 4 }}>{selected.description}</p>
                      )}
                    </div>
                    <button
                      className="btn btn-primary btn-sm"
                      onClick={() => navigate(`/host/expos/new?channelId=${selected.id}`)}
                    >
                      + 박람회 등록
                    </button>
                  </div>

                  <div className="alert alert-warning" style={{ marginBottom: 16 }}>
                    ⚑ 공개(PUBLISHED)된 박람회만 표시됩니다. 비공개 박람회 목록 조회는 Sprint 2 범위입니다.
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
                        onClick={() => navigate(`/host/expos/new?channelId=${selected.id}`)}
                      >
                        박람회 등록하기
                      </button>
                    </div>
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                      {expos.map(expo => (
                        <ExpoRow
                          key={expoKey(expo)}
                          expo={expo}
                          onManage={() =>
                            navigate(`/host/expos/${expoKey(expo)}/rounds`, { state: { expo } })
                          }
                        />
                      ))}
                    </div>
                  )}
                </>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function ExpoRow({ expo, onManage }: { expo: Expo; onManage: () => void }) {
  return (
    <div
      className="card"
      style={{ padding: '18px 22px', display: 'flex', alignItems: 'center', gap: 16, cursor: 'pointer' }}
      onClick={onManage}
    >
      <div
        style={{
          width: 48, height: 48,
          borderRadius: 'var(--r-sm)',
          background: 'linear-gradient(135deg, var(--navy), #2E3A5C)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 22, flexShrink: 0,
        }}
      >
        🎪
      </div>

      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: 'flex', gap: 6, marginBottom: 6 }}>
          <span className={`badge badge-${(expo.status ?? 'PUBLISHED').toLowerCase()}`}>
            {(expo.status ?? 'PUBLISHED') === 'PUBLISHED' ? '● 공개중' : expo.status === 'HIDDEN' ? '○ HIDDEN' : '● 종료'}
          </span>
          <span className="badge badge-blue">{expo.category}</span>
        </div>
        <h3 style={{
          fontWeight: 700,
          color: 'var(--text)',
          fontSize: 15,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
          whiteSpace: 'nowrap',
        }}>
          {expo.title}
        </h3>
        {(expo.region || expo.venue) && (
          <p style={{ fontSize: 12, color: 'var(--sub)', marginTop: 2 }}>
            {[expo.venue, expo.region].filter(Boolean).join(' · ')}
          </p>
        )}
      </div>

      <button
        className="btn btn-secondary btn-sm"
        style={{ flexShrink: 0 }}
        onClick={e => { e.stopPropagation(); onManage() }}
      >
        회차 관리 →
      </button>
    </div>
  )
}
