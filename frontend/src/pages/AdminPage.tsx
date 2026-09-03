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

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      <div className="container page-wrap">
        <div className="page-header">
          <h1 className="page-title">관리자 콘솔</h1>
          <p className="page-sub">주최자 계정 발급 및 신청 관리</p>
        </div>

        {/* Tab bar */}
        <div style={{
          display: 'flex',
          gap: 0,
          borderBottom: '2px solid var(--border)',
          marginBottom: 32,
        }}>
          {[
            { key: 'create', label: '주최자 계정 발급' },
            { key: 'requests', label: '신청 목록' },
          ].map(t => (
            <button
              key={t.key}
              onClick={() => setTab(t.key as typeof tab)}
              style={{
                padding: '10px 20px',
                border: 'none',
                background: 'transparent',
                fontSize: 14,
                fontWeight: tab === t.key ? 700 : 500,
                color: tab === t.key ? 'var(--primary)' : 'var(--sub)',
                borderBottom: `2px solid ${tab === t.key ? 'var(--primary)' : 'transparent'}`,
                marginBottom: -2,
                cursor: 'pointer',
                transition: '.15s',
              }}
            >
              {t.label}
            </button>
          ))}
        </div>

        {tab === 'create' && <CreateOrganizer />}
        {tab === 'requests' && <HostRequestList />}
      </div>
    </div>
  )
}

function CreateOrganizer() {
  const toast = useToast()
  const [form, setForm] = useState({ name: '', email: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await authApi.createOrganizerAccount(form)
      toast('주최자 계정이 발급되었습니다 ✓', 'success')
      setForm({ name: '', email: '', password: '' })
    } catch (err: unknown) {
      const e = err as { status?: number }
      setError(e.status === 409 ? '이미 사용 중인 이메일입니다.' : '계정 발급에 실패했습니다.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 520 }}>
      <div className="card" style={{ padding: 32 }}>
        <h2 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text)', marginBottom: 4 }}>
          ORGANIZER 계정 발급
        </h2>
        <p style={{ fontSize: 13, color: 'var(--sub)', marginBottom: 24 }}>
          발급된 계정은 채널 생성 및 박람회 등록 권한을 갖습니다.
        </p>

        {error && <div className="alert alert-danger"><span>⚠</span><span>{error}</span></div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label">이름 <span className="req">*</span></label>
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
            <label className="form-label">이메일 <span className="req">*</span></label>
            <input
              className="form-input"
              type="email"
              placeholder="organizer@company.com"
              value={form.email}
              onChange={e => setForm(p => ({ ...p, email: e.target.value }))}
              required
            />
          </div>
          <div className="form-group" style={{ marginBottom: 24 }}>
            <label className="form-label">초기 비밀번호 <span className="req">*</span></label>
            <input
              className="form-input"
              type="password"
              placeholder="8자 이상"
              value={form.password}
              onChange={e => setForm(p => ({ ...p, password: e.target.value }))}
              required
            />
            <p className="form-hint">발급 후 주최자에게 별도 안내하세요.</p>
          </div>
          <button type="submit" className="btn btn-primary btn-block" disabled={loading}>
            {loading ? '발급 중...' : '계정 발급'}
          </button>
        </form>
      </div>
    </div>
  )
}

function HostRequestList() {
  const toast = useToast()
  const [rows, setRows] = useState<{
    id: number; userName: string; userEmail: string; orgName: string; status: string; createdAt: string
  }[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    authApi.listHostRequests()
      .then(res => setRows((res.data as never[]) ?? []))
      .catch(() => toast('목록을 불러오지 못했습니다', 'error'))
      .finally(() => setLoading(false))
  }, [])

  async function approve(id: number) {
    try {
      await authApi.approveHostRequest(id)
      toast('승인 완료 ✓', 'success')
      setRows(prev => prev.map(r => r.id === id ? { ...r, status: 'APPROVED' } : r))
    } catch {
      toast('승인 실패', 'error')
    }
  }

  if (loading) return <p style={{ color: 'var(--sub)', padding: '40px 0' }}>불러오는 중...</p>
  if (!rows.length) return (
    <div className="empty-state">
      <div className="es-icon">📋</div>
      <p className="es-title">신청 내역이 없습니다</p>
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
            {rows.map(r => (
              <tr key={r.id}>
                <td style={{ fontWeight: 600 }}>{r.userName}</td>
                <td>{r.userEmail}</td>
                <td>{r.orgName}</td>
                <td>{new Date(r.createdAt).toLocaleDateString('ko-KR')}</td>
                <td>
                  <span className={`badge ${r.status === 'APPROVED' ? 'badge-published' : r.status === 'PENDING' ? 'badge-hidden' : 'badge-closed'}`}>
                    {r.status === 'APPROVED' ? '승인' : r.status === 'PENDING' ? '대기중' : '거절'}
                  </span>
                </td>
                <td>
                  {r.status === 'PENDING' && (
                    <button className="btn btn-primary btn-sm" onClick={() => approve(r.id)}>승인</button>
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
