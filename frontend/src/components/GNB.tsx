import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useToast } from './Toast'

export default function GNB() {
  const { user, logout, isRole } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  const handleLogout = () => {
    logout()
    toast('로그아웃 되었습니다')
    navigate('/')
  }

  return (
    <nav className="gnb">
      <Link to="/" className="gnb-logo">🎪 <span>Expo</span>Hub</Link>

      <div className="gnb-links">
        <Link to="/expos">박람회 탐색</Link>
        {isRole('ORGANIZER') && (
          <>
            <Link to="/host/channel">내 채널</Link>
          </>
        )}
        {isRole('SUPER_ADMIN') && (
          <Link to="/admin">관리자</Link>
        )}
      </div>

      <div className="gnb-auth">
        {user ? (
          <div className="gnb-user">
            <div className="gnb-avatar">{user.name[0]}</div>
            <span>{user.name}</span>
            <span className="gnb-role">{roleLabel(user.role)}</span>
            <button className="btn btn-ghost btn-sm" onClick={handleLogout}>로그아웃</button>
          </div>
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
  return '회원'
}
