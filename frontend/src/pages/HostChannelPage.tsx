import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/Toast'
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
  const [selectedChannel, setSelectedChannel] = useState<Channel | null>(null)
  const [expos, setExpos] = useState<Expo[]>([])
  const [loadingChannels, setLoadingChannels] = useState(true)
  const [loadingExpos, setLoadingExpos] = useState(false)

  useEffect(() => {
    expoApi.listMyChannels()
      .then(res => {
        const list = res.data ?? []
        setChannels(list)
        if (list.length > 0) selectChannel(list[0])
      })
      .catch(() => toast('채널 목록을 불러오지 못했습니다', 'error'))
      .finally(() => setLoadingChannels(false))
  }, [])

  function selectChannel(ch: Channel) {
    setSelectedChannel(ch)
    setLoadingExpos(true)
    expoApi.listMyExpos(ch.id)
      .then(res => setExpos(res.data ?? []))
      .catch(() => toast('박람회 목록을 불러오지 못했습니다', 'error'))
      .finally(() => setLoadingExpos(false))
  }

  return (
    <div className="container page-wrap">
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 32 }}>
        <div>
          <h1 className="page-title">내 채널</h1>
          <p className="page-sub">채널을 선택하면 해당 채널의 박람회 목록을 확인할 수 있습니다.</p>
        </div>
        <button className="btn btn-primary" onClick={() => navigate('/host/channel/new')}>
          + 채널 생성
        </button>
      </div>

      {loadingChannels ? (
        <p style={{ color: 'var(--gray5)' }}>불러오는 중...</p>
      ) : channels.length === 0 ? (
        <div className="empty-state">
          <div className="emoji">📢</div>
          <p>아직 채널이 없습니다.<br />채널을 먼저 생성해주세요.</p>
          <button className="btn btn-primary" onClick={() => navigate('/host/channel/new')}>채널 만들기</button>
        </div>
      ) : (
        <div className="host-layout">
          {/* Channel list */}
          <div className="host-sidebar">
            <p style={{ fontSize: 11, fontWeight: 700, color: 'var(--gray5)', textTransform: 'uppercase', letterSpacing: '.04em', marginBottom: 12 }}>
              내 채널
            </p>
            {channels.map(ch => (
              <div
                key={ch.id}
                className={`host-nav-item ${selectedChannel?.id === ch.id ? 'active' : ''}`}
                onClick={() => selectChannel(ch)}
              >
                <span>📢</span>
                <span>{ch.name}</span>
              </div>
            ))}
          </div>

          {/* Expos in selected channel */}
          <div>
            {selectedChannel && (
              <>
                <div className="section-header">
                  <span className="section-title">{selectedChannel.name}</span>
                  <button
                    className="btn btn-primary btn-sm"
                    onClick={() => navigate(`/host/expos/new?channelId=${selectedChannel.id}`)}
                  >
                    + 박람회 등록
                  </button>
                </div>

                {loadingExpos ? (
                  <p style={{ color: 'var(--gray5)' }}>불러오는 중...</p>
                ) : expos.length === 0 ? (
                  <div className="empty-state">
                    <div className="emoji">🎪</div>
                    <p>등록된 박람회가 없습니다</p>
                    <button
                      className="btn btn-outline"
                      onClick={() => navigate(`/host/expos/new?channelId=${selectedChannel.id}`)}
                    >
                      박람회 등록하기
                    </button>
                  </div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    {expos.map(expo => (
                      <div
                        key={expo.id}
                        className="card"
                        style={{ padding: '16px 20px', display: 'flex', alignItems: 'center', gap: 16, cursor: 'pointer' }}
                        onClick={() => navigate(`/host/expos/${expo.id}/rounds`)}
                      >
                        <div style={{ flex: 1 }}>
                          <div style={{ display: 'flex', gap: 8, marginBottom: 6 }}>
                            <span className={`badge badge-${expo.status.toLowerCase()}`}>
                              {expo.status === 'PUBLISHED' ? '공개중' : expo.status === 'HIDDEN' ? 'HIDDEN' : '종료'}
                            </span>
                            <span className="badge" style={{ background: 'var(--blue-l)', color: 'var(--blue)' }}>
                              {expo.category}
                            </span>
                          </div>
                          <h3 style={{ fontWeight: 700, color: 'var(--navy)' }}>{expo.title}</h3>
                          {expo.region && (
                            <span style={{ fontSize: 12, color: 'var(--gray5)' }}>📍 {expo.region}</span>
                          )}
                        </div>
                        <button
                          className="btn btn-secondary btn-sm"
                          onClick={e => { e.stopPropagation(); navigate(`/host/expos/${expo.id}/rounds`) }}
                        >
                          회차 관리
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
