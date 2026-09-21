import { useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { authApi, decodeJwt } from '../api/auth'
import { recommendationApi } from '../api/recommendation'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/Toast'
import { usePageTitle } from '../hooks/usePageTitle'

const CATS = ['IT·전자', '식품·음료', '패션·뷰티', '교육·취업', '문화·예술', '기타']

export default function AuthPage() {
  const [params] = useSearchParams()
  const [tab, setTab] = useState<'login' | 'signup'>(
    params.get('tab') === 'signup' ? 'signup' : 'login'
  )
  // 회원가입 완료 후 관심사 선택 단계
  const [signupStep, setSignupStep] = useState<'form' | 'interests'>('form')
  const [pendingCredentials, setPendingCredentials] = useState<{ email: string; password: string } | null>(null)
  const [selectedCats, setSelectedCats] = useState<string[]>([])

  usePageTitle(tab === 'signup' ? '회원가입' : '로그인')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { login } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  function changeTab(next: 'login' | 'signup') {
    setTab(next)
    setError('')
    setSignupStep('form')
    setPendingCredentials(null)
    setSelectedCats([])
  }

  const [loginForm, setLoginForm] = useState({ email: '', password: '' })
  const [signupForm, setSignupForm] = useState({ name: '', email: '', password: '', confirm: '' })

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const res = await authApi.login(loginForm)
      const token = res.data.accessToken
      const claims = decodeJwt(token)
      login({
        id: Number(claims.sub),
        name: loginForm.email.split('@')[0],
        role: claims.role,
        token,
      })
      toast('로그인되었습니다', 'success')
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
      // 관심사 선택 단계로 전환
      setPendingCredentials({ email: signupForm.email, password: signupForm.password })
      setSignupStep('interests')
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 409) setError('이미 사용 중인 이메일 주소입니다.')
      else if (e.status === 400) setError('입력값을 확인해주세요. (비밀번호 8~64자, 영문과 숫자 각각 1자 이상)')
      else setError('회원가입에 실패했습니다. 잠시 후 다시 시도해주세요.')
    } finally {
      setLoading(false)
    }
  }

  async function handleInterestsDone() {
    if (!pendingCredentials) return
    setLoading(true)
    try {
      // 자동 로그인
      const res = await authApi.login(pendingCredentials)
      const token = res.data.accessToken
      const claims = decodeJwt(token)

      // login()은 setState라 localStorage 업데이트가 다음 렌더에 일어남
      // saveInterests가 토큰을 읽을 수 있도록 먼저 직접 세팅
      localStorage.setItem('token', token)

      // 관심사 저장 (선택했을 경우)
      if (selectedCats.length > 0) {
        await recommendationApi.saveInterests({ categories: selectedCats, keywords: [] })
      }

      login({
        id: Number(claims.sub),
        name: pendingCredentials.email.split('@')[0],
        role: claims.role,
        token,
      })
      toast('환영합니다! ExpoHub를 시작해보세요 🎉', 'success')
      navigate('/')
    } catch {
      toast('가입은 완료됐습니다. 직접 로그인해주세요.', 'success')
      changeTab('login')
      setLoginForm({ email: pendingCredentials.email, password: '' })
    } finally {
      setLoading(false)
    }
  }

  function toggleCat(cat: string) {
    setSelectedCats(prev =>
      prev.includes(cat) ? prev.filter(c => c !== cat) : [...prev, cat]
    )
  }

  const pwMismatch = signupForm.confirm.length > 0 && signupForm.password !== signupForm.confirm

  // 회원가입 완료 후 관심사 선택 화면
  if (tab === 'signup' && signupStep === 'interests') {
    return (
      <div className="auth-wrap">
        <div className="auth-card">
          <div className="auth-logo">
            <div className="logo-icon">◈</div>
            <h2>관심사 설정</h2>
            <p>어떤 분야의 박람회를 좋아하시나요?</p>
          </div>

          <div style={{ marginBottom: 8 }}>
            <p style={{ fontSize: 13, color: 'var(--sub)', marginBottom: 16 }}>
              선택한 관심사를 기반으로 AI가 박람회를 추천해드립니다.<br />나중에 마이페이지에서 변경할 수 있습니다.
            </p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
              {CATS.map(cat => (
                <button
                  key={cat}
                  type="button"
                  onClick={() => toggleCat(cat)}
                  style={{
                    padding: '8px 18px',
                    borderRadius: 20,
                    border: selectedCats.includes(cat) ? '2px solid var(--primary)' : '1.5px solid var(--border)',
                    background: selectedCats.includes(cat) ? 'var(--primary)' : 'transparent',
                    color: selectedCats.includes(cat) ? '#fff' : 'var(--text)',
                    fontWeight: 600,
                    fontSize: 14,
                    cursor: 'pointer',
                    transition: 'all 0.15s',
                  }}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>

          <button
            type="button"
            className="btn btn-primary btn-block btn-lg"
            style={{ marginTop: 24 }}
            onClick={handleInterestsDone}
            disabled={loading}
          >
            {loading ? '처리 중...' : selectedCats.length > 0 ? '시작하기' : '나중에 설정할게요'}
          </button>
        </div>
      </div>
    )
  }

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
          <div className={`auth-tab ${tab === 'login' ? 'active' : ''}`} onClick={() => changeTab('login')}>로그인</div>
          <div className={`auth-tab ${tab === 'signup' ? 'active' : ''}`} onClick={() => changeTab('signup')}>회원가입</div>
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
                onClick={() => changeTab('signup')}
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
              <p className="form-hint">영문과 숫자를 포함해 8~64자</p>
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
                onClick={() => changeTab('login')}
                style={{ color: 'var(--primary)', fontWeight: 700, cursor: 'pointer' }}
              >
                로그인
              </span>
            </p>
          </form>
        )}

        <div className="auth-host-hint">
          <p>주최자 계정은 가입 후 마이페이지에서 신청하거나, 관리자가 직접 발급할 수 있습니다.</p>
        </div>
      </div>
    </div>
  )
}
