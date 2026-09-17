import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { expoApi } from '../api/expo'
import { notificationApi, onNotificationRefresh } from '../api/notification'
import type { NotificationItem } from '../api/notification'

const POLL_MS = 30_000
// 예약 확정 → 알림 생성은 서버에서 비동기라, 요청 직후 바로 읽으면 아직 없을 수 있다
const REFRESH_DELAY_MS = 1_500
const LIST_SIZE = 10

// 박람회 제목은 바뀔 일이 드물어 화면을 옮겨 다녀도 다시 부르지 않는다
const expoTitles = new Map<number, string>()

export default function NotificationBell() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const wrapperRef = useRef<HTMLDivElement>(null)

  const [open, setOpen] = useState(false)
  const [unread, setUnread] = useState(0)
  const [items, setItems] = useState<NotificationItem[]>([])
  const [loading, setLoading] = useState(false)
  const [failed, setFailed] = useState(false)
  const [, setTitlesVersion] = useState(0)

  const refreshCount = useCallback(() => {
    notificationApi.unreadCount()
      .then(res => setUnread(res.data?.unreadCount ?? 0))
      .catch(() => {})
  }, [])

  // 화면을 옮길 때마다, 그리고 주기적으로 뱃지 숫자만 가볍게 다시 읽는다
  useEffect(() => {
    refreshCount()
  }, [pathname, refreshCount])

  useEffect(() => {
    const timer = window.setInterval(refreshCount, POLL_MS)
    const onFocus = () => refreshCount()
    window.addEventListener('focus', onFocus)
    let delayed: number | undefined
    const off = onNotificationRefresh(() => {
      window.clearTimeout(delayed)
      delayed = window.setTimeout(refreshCount, REFRESH_DELAY_MS)
    })
    return () => {
      window.clearInterval(timer)
      window.clearTimeout(delayed)
      window.removeEventListener('focus', onFocus)
      off()
    }
  }, [refreshCount])

  // 바깥을 누르거나 Esc 로 닫는다
  useEffect(() => {
    if (!open) return
    const onClick = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onClick)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onClick)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const loadList = async () => {
    setLoading(true)
    setFailed(false)
    try {
      const list = (await notificationApi.list(LIST_SIZE)).data?.notifications ?? []
      setItems(list)
      setUnread(prev => Math.max(prev, list.filter(n => !n.isRead).length))
      loadTitles(list)
    } catch {
      setFailed(true)
    } finally {
      setLoading(false)
    }
  }

  const loadTitles = (list: NotificationItem[]) => {
    const missing = [...new Set(list.map(n => n.expoId))].filter(id => !expoTitles.has(id))
    if (missing.length === 0) return
    Promise.allSettled(missing.map(id => expoApi.getExpo(id))).then(results => {
      results.forEach((r, i) => {
        if (r.status === 'fulfilled' && r.value.data?.title) expoTitles.set(missing[i], r.value.data.title)
      })
      setTitlesVersion(v => v + 1)
    })
  }

  const handleToggle = () => {
    const next = !open
    setOpen(next)
    if (next) loadList()
  }

  const handleItemClick = (n: NotificationItem) => {
    setOpen(false)
    if (!n.isRead) {
      setItems(prev => prev.map(it => (it.id === n.id ? { ...it, isRead: true } : it)))
      setUnread(prev => Math.max(prev - 1, 0))
      notificationApi.markRead(n.id).catch(refreshCount)
    }
    navigate(n.type === 'RESERVATION_CONFIRMED' ? '/my/reservations' : `/expos/${n.expoId}`)
  }

  const handleReadAll = () => {
    setItems(prev => prev.map(it => ({ ...it, isRead: true })))
    setUnread(0)
    notificationApi.markAllRead().catch(refreshCount)
  }

  const hasUnreadInList = items.some(n => !n.isRead)

  return (
    <div className="notif" ref={wrapperRef}>
      <button
        type="button"
        className={`notif-bell ${open ? 'open' : ''}`}
        onClick={handleToggle}
        aria-label={unread > 0 ? `알림 ${unread}개 안 읽음` : '알림'}
        aria-expanded={open}
      >
        <BellIcon />
        {unread > 0 && <span className="notif-badge">{unread > 9 ? '9+' : unread}</span>}
      </button>

      {open && (
        <div className="notif-panel" role="dialog" aria-label="알림">
          <div className="notif-head">
            <span className="notif-head-title">알림</span>
            <button
              type="button"
              className="notif-read-all"
              onClick={handleReadAll}
              disabled={!hasUnreadInList && unread === 0}
            >
              모두 읽음
            </button>
          </div>

          <div className="notif-body">
            {loading && items.length === 0 ? (
              <p className="notif-empty">불러오는 중...</p>
            ) : failed ? (
              <p className="notif-empty">알림을 불러오지 못했습니다.<br />잠시 후 다시 열어주세요.</p>
            ) : items.length === 0 ? (
              <p className="notif-empty">새로운 알림이 없어요.<br />박람회를 예약하면 여기에서 알려드려요.</p>
            ) : (
              items.map(n => (
                <button
                  key={n.id}
                  type="button"
                  className={`notif-item ${n.isRead ? 'read' : ''}`}
                  onClick={() => handleItemClick(n)}
                >
                  <span className={`notif-kind ${n.type === 'RESERVATION_CONFIRMED' ? 'kind-reserve' : 'kind-reco'}`}>
                    {n.type === 'RESERVATION_CONFIRMED' ? '예약 확정' : '추천'}
                  </span>
                  <span className="notif-main">
                    {expoTitles.has(n.expoId) && <span className="notif-expo">{expoTitles.get(n.expoId)}</span>}
                    <span className="notif-msg">{n.message}</span>
                    <span className="notif-time">{timeAgo(n.createdAt)}</span>
                  </span>
                  {!n.isRead && <span className="notif-dot" aria-label="안 읽음" />}
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  )
}

function timeAgo(iso: string) {
  const diff = Date.now() - new Date(iso).getTime()
  const min = Math.floor(diff / 60_000)
  if (min < 1) return '방금 전'
  if (min < 60) return `${min}분 전`
  const hour = Math.floor(min / 60)
  if (hour < 24) return `${hour}시간 전`
  const day = Math.floor(hour / 24)
  if (day < 7) return `${day}일 전`
  return new Date(iso).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' })
}

function BellIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor"
      strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
      <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
    </svg>
  )
}
