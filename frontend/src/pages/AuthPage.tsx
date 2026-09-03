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

  useEffect(() => { setError('') }, [tab])

  const [loginForm, setLoginForm] = useState({ email: '', password: '' })
  const [signupForm, setSignupForm] = useState({ name: '', email: '', password: '', confirm: '' })

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await authApi.login(loginForm)
      const d = res.data
      login({ id: d.userId, name: d.name, role: d.role, token: d.accessToken })
      toast('로그인되었습니다 👋', 'success')
      navigate('/')
    } catch (err: unknown) {
      const e = err as { status?: number }
      setError(e.status === 401 ? '이메일 또는 비밀번호가 올바르지 않습니다.' : '로그인에 실패했습니다. 잠시 후 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  async function handleSignup(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    if (signupForm.password !== signupForm.confirm) {
      setError('비밀번호가 일치하지 않습니다.')
      return
    }
    setLoading(true)
    try {
      await authApi.signup({ name: signupForm.name, email: signupForm.email, password: signupForm.password })
      toast('가입 완료! 로그인해주세요 🎉', 'success')
      setTab('login')
      setLoginForm({ email: signupForm.email, password: '' })
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 409) setError('이미 사용 중인 이메일 주소입니다.')
      else if (e.status === 400) setError('입력값을 확인해주세요. (비밀번호 8자 이상, 영문+숫자+특수문자)')
      else setError('회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  const pwMismatch = signupForm.confirm.length > 0 && signupForm.password !== signupForm.confirm

  return (
    <div className="auth-wrap">
      <div className="auth-card">
        {/* Logo */}
        <div className="auth-logo">
          <div className="logo-icon">◈</div>
          <h2>{tab === 'login' ? '로그인' : '회원가입'}</h2>
          <p>{tab === 'login' ? 'ExpoHub에 오신 것을 환영합니다' : '박람회의 모든 것, ExpoHub'}</p>
        </div>

        {/* Tabs */}
        <div className="auth-tabs">
          <div className={`auth-tab ${tab === 'login' ? 'active' : ''}`} onClick={() => setTab('login')}>로그인</div>
          <div className={`auth-tab ${tab === 'signup' ? 'active' : ''}`} onClick={() => setTab('signup')}>회원가입</div>
        </div>

        {error && (
          <div className="alert alert-danger">
            <span>⚠</span><span>{error}</span>
          </div>
        )}

        {tab === 'login' ? (
          <form onSubmit={handleLogin}>
            <div className="form-group">
              <label className="form-label">이메일 <span className="req">*</span></label>
              <input
                className="form-input"
                type="email"
                placeholder="example@email.com"
                value={loginForm.email}
                onChange={e => setLoginForm(p => ({ ...p, email: e.target.value }))}
                autoComplete="email"
                required
              />
            </div>
            <div className="form-group" style={{ marginBottom: 24 }}>
              <label className="form-label">비밀번호 <span className="req">*</span></label>
              <input
                className="form-input"
                type="password"
                placeholder="비밀번호를 입력하세요"
                value={loginForm.password}
                onChange={e => setLoginForm(p => ({ ...p, password: e.target.value }))}
                autoComplete="current-password"
                required
              />
            </div>
            <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={loading}>
              {loading ? '로그인 중...' : '로그인'}
            </button>

            <div className="auth-divider"><span>또는</span></div>

            <p style={{ textAlign: 'center', fontSize: 13, color: 'var(--sub)' }}>
              계정이 없으신가요?{' '}
              <span
                onClick={() => setTab('signup')}
                style={{ color: 'var(--primary)', fontWeight: 700, cursor: 'pointer' }}
              >
                회원가입
              </span>
            </p>
          </form>
        ) : (
          <form onSubmit={handleSignup}>
            <div className="form-group">
              <label className="form-label">이름 <span className="req">*</span></label>
              <input
                className="form-input"
                type="text"
                placeholder="홍길동"
                value={signupForm.name}
                onChange={e => setSignupForm(p => ({ ...p, name: e.target.value }))}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">이메일 <span className="req">*</span></label>
              <input
                className="form-input"
                type="email"
                placeholder="example@email.com"
                value={signupForm.email}
                onChange={e => setSignupForm(p => ({ ...p, email: e.target.value }))}
                autoComplete="email"
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">비밀번호 <span className="req">*</span></label>
              <input
                className="form-input"
                type="password"
                placeholder="8자 이상"
                value={signupForm.password}
                onChange={e => setSignupForm(p => ({ ...p, password: e.target.value }))}
                required
              />
              <p className="form-hint">영문, 숫자, 특수문자를 포함해 8자 이상</p>
            </div>
            <div className="form-group" style={{ marginBottom: 24 }}>
              <label className="form-label">비밀번호 확인 <span className="req">*</span></label>
              <input
                className={`form-input ${pwMismatch ? 'error' : ''}`}
                type="password"
                placeholder="비밀번호를 다시 입력하세요"
                value={signupForm.confirm}
                onChange={e => setSignupForm(p => ({ ...p, confirm: e.target.value }))}
                required
              />
              {pwMismatch && <p className="form-error">비밀번호가 일치하지 않습니다.</p>}
            </div>
            <button type="submit" className="btn btn-primary btn-block btn-lg" disabled={loading}>
              {loading ? '가입 중...' : '가입하기'}
            </button>

            <div className="auth-divider"><span>또는</span></div>

            <p style={{ textAlign: 'center', fontSize: 13, color: 'var(--sub)' }}>
              이미 계정이 있으신가요?{' '}
              <span
                onClick={() => setTab('login')}
                style={{ color: 'var(--primary)', fontWeight: 700, cursor: 'pointer' }}
              >
                로그인
              </span>
            </p>
          </form>
        )}

        <div className="auth-host-hint">
          <p>🎯 주최자 계정은 관리자가 직접 발급합니다.</p>
        </div>
      </div>
    </div>
  )
}
