import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import NotificationBell from './NotificationBell'
import { useToast } from './Toast'
import Avatar from './Avatar'

export default function GNB() {
  const { user, logout, isRole } = useAuth()
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const toast = useToast()
  const [menuOpen, setMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  const handleLogout = () => {
    setMenuOpen(false)
    logout()
    toast('로그아웃 되었습니다')
    navigate('/')
  }

  const isActive = (path: string) => pathname.startsWith(path) ? 'active' : ''

  useEffect(() => {
    if (!menuOpen) return

    function handleOutside(e: MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false)
      }
    }
    function handleEscape(e: KeyboardEvent) {
      if (e.key === 'Escape') setMenuOpen(false)
    }

    document.addEventListener('mousedown', handleOutside)
    document.addEventListener('keydown', handleEscape)
    return () => {
      document.removeEventListener('mousedown', handleOutside)
      document.removeEventListener('keydown', handleEscape)
    }
  }, [menuOpen])

  return (
    <nav className="gnb">
      <Link to="/" className="gnb-logo">
        <span style={{ color: 'var(--primary)', fontSize: 22 }}>◈</span>
        <span>Expo<span className="accent">Hub</span></span>
      </Link>

      <div className="gnb-links">
        <Link to="/expos" className={isActive('/expos')}>박람회 탐색</Link>
        {isRole('USER') && (
          <Link to="/my/reservations" className={isActive('/my/reservations')}>내 예약</Link>
        )}
        {isRole('ORGANIZER') && (
          <>
            <Link to="/host/channel" className={isActive('/host/channel')}>주최자 센터</Link>
            <Link to="/host/checkin" className={isActive('/host/checkin')}>현장 체크인</Link>
          </>
        )}
        {isRole('SUPER_ADMIN') && (
          <Link to="/admin" className={isActive('/admin')}>관리자</Link>
        )}
      </div>

      <div className="gnb-auth">
        {user ? (
          <>
            <NotificationBell />
            <div className="gnb-user-menu" ref={menuRef}>
              <button
                type="button"
                className="gnb-user-trigger"
                onClick={() => setMenuOpen(o => !o)}
                aria-haspopup="true"
                aria-expanded={menuOpen}
              >
                <Avatar userId={user.id} name={user.name} imageUrl={user.profileImageUrl} size={34} />
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span className="gnb-name">{user.name}</span>
                  <span className="gnb-role-badge" style={roleBadgeStyle(user.role)}>{roleLabel(user.role)}</span>
                </div>
                <span className={`gnb-caret ${menuOpen ? 'open' : ''}`}>▾</span>
              </button>

              {menuOpen && (
                <div className="gnb-dropdown" role="menu">
                  <Link
                    to="/my/profile"
                    className={`gnb-dropdown-item ${isActive('/my/profile')}`}
                    role="menuitem"
                    onClick={() => setMenuOpen(false)}
                  >
                    마이페이지
                  </Link>
                  <button type="button" className="gnb-dropdown-item danger" role="menuitem" onClick={handleLogout}>
                    로그아웃
                  </button>
                </div>
              )}
            </div>
          </>
        ) : (
          <>
            <Link to="/auth" className="btn btn-ghost btn-sm">로그인</Link>
            <Link to="/auth?tab=signup" className="btn btn-primary btn-sm">회원가입</Link>
          </>
        )}
      </div>
    </nav>
  )
}

function roleLabel(role: string) {
  if (role === 'SUPER_ADMIN') return '관리자'
  if (role === 'ORGANIZER') return '주최자'
  return '일반회원'
}

function roleBadgeStyle(role: string) {
  if (role === 'SUPER_ADMIN') return { background: 'var(--red-l)', color: 'var(--red)' }
  if (role === 'ORGANIZER') return { background: 'var(--blue-l)', color: 'var(--blue)' }
  return { background: 'var(--gray2)', color: 'var(--text2)' }
}
