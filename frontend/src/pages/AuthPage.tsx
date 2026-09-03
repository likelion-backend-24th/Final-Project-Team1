import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '../api/auth'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/Toast'

export default function AuthPage() {
  const [params] = useSearchParams()
  const [tab, setTab] = useState<'login' | 'signup'>(
    params.get('tab') === 'signup' ? 'signup' : 'login'
  )
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { login } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  useEffect(() => {
    setError('')
  }, [tab])

  // Login form state
  const [loginData, setLoginData] = useState({ email: '', password: '' })

  // Signup form state
  const [signupData, setSignupData] = useState({ name: '', email: '', password: '', confirm: '' })

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await authApi.login(loginData)
      const d = res.data
      login({ id: d.userId, name: d.name, role: d.role, token: d.accessToken })
      toast('로그인되었습니다', 'success')
      navigate('/')
    } catch (err: unknown) {
      const e = err as { status?: number }
      setError(e.status === 401 ? '이메일 또는 비밀번호가 올바르지 않습니다.' : '로그인에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  async function handleSignup(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (signupData.password !== signupData.confirm) {
      setError('비밀번호가 일치하지 않습니다.')
      return
    }
    setLoading(true)
    try {
      await authApi.signup({ name: signupData.name, email: signupData.email, password: signupData.password })
      toast('가입 완료! 로그인해주세요', 'success')
      setTab('login')
      setLoginData({ email: signupData.email, password: '' })
    } catch (err: unknown) {
      const e = err as { status?: number; body?: { data?: { code?: string } } }
      if (e.status === 409) setError('이미 사용 중인 이메일입니다.')
      else if (e.status === 400) setError('입력값을 확인해주세요. (비밀번호: 8자 이상, 영문+숫자+특수문자)')
      else setError('회원가입에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="auth-box">
      <div style={{ textAlign: 'center', marginBottom: 20 }}>
        <span style={{ fontSize: 40 }}>🎪</span>
      </div>

      <div className="auth-tabs">
        <div className={`auth-tab ${tab === 'login' ? 'active' : ''}`} onClick={() => setTab('login')}>
          로그인
        </div>
        <div className={`auth-tab ${tab === 'signup' ? 'active' : ''}`} onClick={() => setTab('signup')}>
          회원가입
        </div>
      </div>

      {error && (
        <div className="alert alert-danger">
          <span>⚠</span> {error}
        </div>
      )}

      {tab === 'login' ? (
        <form onSubmit={handleLogin}>
          <div className="form-group">
            <label className="form-label">이메일<span className="req">*</span></label>
            <input
              className="form-input"
              type="email"
              placeholder="example@email.com"
              value={loginData.email}
              onChange={e => setLoginData(p => ({ ...p, email: e.target.value }))}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">비밀번호<span className="req">*</span></label>
            <input
              className="form-input"
              type="password"
              placeholder="비밀번호를 입력하세요"
              value={loginData.password}
              onChange={e => setLoginData(p => ({ ...p, password: e.target.value }))}
              required
            />
          </div>
          <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={loading} style={{ marginBottom: 16 }}>
            {loading ? '로그인 중...' : '로그인'}
          </button>
          <p style={{ textAlign: 'center', fontSize: 13, color: 'var(--gray5)' }}>
            계정이 없으신가요?{' '}
            <span style={{ color: 'var(--primary)', fontWeight: 700, cursor: 'pointer' }} onClick={() => setTab('signup')}>
              회원가입
            </span>
          </p>
        </form>
      ) : (
        <form onSubmit={handleSignup}>
          <div className="form-group">
            <label className="form-label">이름<span className="req">*</span></label>
            <input
              className="form-input"
              type="text"
              placeholder="홍길동"
              value={signupData.name}
              onChange={e => setSignupData(p => ({ ...p, name: e.target.value }))}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">이메일<span className="req">*</span></label>
            <input
              className="form-input"
              type="email"
              placeholder="example@email.com"
              value={signupData.email}
              onChange={e => setSignupData(p => ({ ...p, email: e.target.value }))}
              required
            />
          </div>
          <div className="form-group">
            <label className="form-label">비밀번호<span className="req">*</span></label>
            <input
              className="form-input"
              type="password"
              placeholder="8자 이상 입력"
              value={signupData.password}
              onChange={e => setSignupData(p => ({ ...p, password: e.target.value }))}
              required
            />
            <p className="form-hint">8자 이상, 영문·숫자·특수문자 조합</p>
          </div>
          <div className="form-group">
            <label className="form-label">비밀번호 확인<span className="req">*</span></label>
            <input
              className={`form-input ${signupData.confirm && signupData.confirm !== signupData.password ? 'error' : ''}`}
              type="password"
              placeholder="비밀번호를 다시 입력하세요"
              value={signupData.confirm}
              onChange={e => setSignupData(p => ({ ...p, confirm: e.target.value }))}
              required
            />
            {signupData.confirm && signupData.confirm !== signupData.password && (
              <p className="form-error">비밀번호가 일치하지 않습니다.</p>
            )}
          </div>
          <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={loading}>
            {loading ? '가입 중...' : '가입하기'}
          </button>
          <p style={{ textAlign: 'center', fontSize: 13, color: 'var(--gray5)', marginTop: 16 }}>
            이미 계정이 있으신가요?{' '}
            <span style={{ color: 'var(--primary)', fontWeight: 700, cursor: 'pointer' }} onClick={() => setTab('login')}>
              로그인
            </span>
          </p>
        </form>
      )}

      <hr className="divider" style={{ margin: '24px 0' }} />
      <div style={{ background: 'var(--primary-l)', borderRadius: 8, padding: '14px 16px', textAlign: 'center' }}>
        <p style={{ fontSize: 13, color: 'var(--primary-d)', fontWeight: 600, marginBottom: 6 }}>
          🎯 주최자이신가요?
        </p>
        <span style={{ fontSize: 12, color: 'var(--gray5)' }}>주최자 계정은 관리자가 직접 발급합니다.</span>
      </div>
    </div>
  )
}
