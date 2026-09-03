import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/Toast'

export default function AdminPage() {
  const { isRole } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  useEffect(() => {
    if (!isRole('SUPER_ADMIN')) {
      toast('관리자 권한이 필요합니다', 'error')
      navigate('/')
    }
  }, [])

  const [tab, setTab] = useState<'create' | 'requests'>('create')

  // Create organizer
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await authApi.createOrganizerAccount(form)
      toast('주최자 계정이 발급되었습니다', 'success')
      setForm({ name: '', email: '', password: '' })
    } catch (err: unknown) {
      const e = err as { status?: number }
      if (e.status === 409) setError('이미 사용 중인 이메일입니다.')
      else setError('계정 발급에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container page-wrap">
      <h1 className="page-title">관리자 콘솔</h1>
      <p className="page-sub">주최자 계정 발급 및 신청 관리</p>

      <div className="auth-tabs" style={{ maxWidth: 480, marginBottom: 32 }}>
        <div className={`auth-tab ${tab === 'create' ? 'active' : ''}`} onClick={() => setTab('create')}>
          주최자 계정 발급
        </div>
        <div className={`auth-tab ${tab === 'requests' ? 'active' : ''}`} onClick={() => setTab('requests')}>
          신청 목록
        </div>
      </div>

      {tab === 'create' && (
        <div className="card" style={{ maxWidth: 480, padding: 32 }}>
          <h2 style={{ fontSize: 16, fontWeight: 700, marginBottom: 20, color: 'var(--navy)' }}>
            주최자(ORGANIZER) 계정 발급
          </h2>

          {error && (
            <div className="alert alert-danger"><span>⚠</span> {error}</div>
          )}

          <form onSubmit={handleCreate}>
            <div className="form-group">
              <label className="form-label">이름<span className="req">*</span></label>
              <input
                className="form-input"
                type="text"
                placeholder="박주최"
                value={form.name}
                onChange={e => setForm(p => ({ ...p, name: e.target.value }))}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">이메일<span className="req">*</span></label>
              <input
                className="form-input"
                type="email"
                placeholder="organizer@company.com"
                value={form.email}
                onChange={e => setForm(p => ({ ...p, email: e.target.value }))}
                required
              />
            </div>
            <div className="form-group">
              <label className="form-label">초기 비밀번호<span className="req">*</span></label>
              <input
                className="form-input"
                type="password"
                placeholder="8자 이상"
                value={form.password}
                onChange={e => setForm(p => ({ ...p, password: e.target.value }))}
                required
              />
              <p className="form-hint">발급 후 주최자에게 별도 전달하세요.</p>
            </div>
            <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
              {loading ? '발급 중...' : '계정 발급'}
            </button>
          </form>
        </div>
      )}

      {tab === 'requests' && <HostRequestList />}
    </div>
  )
}

function HostRequestList() {
  const toast = useToast()
  const [requests, setRequests] = useState<{
    id: number; userName: string; userEmail: string; orgName: string; status: string; createdAt: string
  }[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    authApi.listHostRequests()
      .then(res => setRequests((res.data as never[]) ?? []))
      .catch(() => toast('목록을 불러오지 못했습니다', 'error'))
      .finally(() => setLoading(false))
  }, [])

  async function approve(id: number) {
    try {
      await authApi.approveHostRequest(id)
      toast('승인 완료', 'success')
      setRequests(prev => prev.map(r => r.id === id ? { ...r, status: 'APPROVED' } : r))
    } catch {
      toast('승인 실패', 'error')
    }
  }

  if (loading) return <p style={{ color: 'var(--gray5)' }}>불러오는 중...</p>
  if (!requests.length) return (
    <div className="empty-state">
      <div className="emoji">📋</div>
      <p>신청 내역이 없습니다</p>
    </div>
  )

  return (
    <div className="card">
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>신청자</th>
              <th>이메일</th>
              <th>단체명</th>
              <th>신청일</th>
              <th>상태</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {requests.map(r => (
              <tr key={r.id}>
                <td>{r.userName}</td>
                <td>{r.userEmail}</td>
                <td>{r.orgName}</td>
                <td>{new Date(r.createdAt).toLocaleDateString()}</td>
                <td>
                  <span className={`badge ${r.status === 'APPROVED' ? 'badge-published' : r.status === 'PENDING' ? 'badge-hidden' : 'badge-closed'}`}>
                    {r.status === 'APPROVED' ? '승인' : r.status === 'PENDING' ? '대기' : '거절'}
                  </span>
                </td>
                <td>
                  {r.status === 'PENDING' && (
                    <button className="btn btn-primary btn-sm" onClick={() => approve(r.id)}>
                      승인
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
