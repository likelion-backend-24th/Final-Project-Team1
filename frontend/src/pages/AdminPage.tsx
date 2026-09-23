import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import SettlementDashboard from '../components/settlement/SettlementDashboard'
import { organizerAdminApi } from '../api/organizerAdmin'
import type { OrganizerApplicationResponse } from '../api/organizerRequest'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/Toast'
import { usePageTitle } from '../hooks/usePageTitle'

type AdminTab = 'settlement' | 'applications' | 'create'

export default function AdminPage() {
  usePageTitle('전체 관리자')
  const { isRole } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const [tab, setTab] = useState<AdminTab>('settlement')

  useEffect(() => {
    if (!isRole('SUPER_ADMIN')) {
      toast('관리자 권한이 필요합니다', 'error')
      navigate('/')
    }
  }, [])

  const TABS: { key: AdminTab; label: string }[] = [
    { key: 'settlement', label: '정산' },
    { key: 'applications', label: '계정 관리' },
    { key: 'create', label: '계정 발급' },
  ]

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      <div className="container page-wrap">
        <div style={{ maxWidth: tab === 'settlement' ? 980 : 720, margin: '0 auto' }}>
          <div style={{ display: 'flex', gap: 6, marginBottom: 24, borderBottom: '2px solid var(--border)' }}>
            {TABS.map(t => (
              <button
                key={t.key}
                onClick={() => setTab(t.key)}
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

          {tab === 'settlement' && <SettlementDashboard />}
          {tab === 'applications' && <OrganizerApplicationsReview />}
          {tab === 'create' && <CreateOrganizer />}
        </div>
      </div>
    </div>
  )
}

function OrganizerApplicationsReview() {
  const toast = useToast()
  const [applications, setApplications] = useState<OrganizerApplicationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [rejectingId, setRejectingId] = useState<number | null>(null)
  const [rejectReason, setRejectReason] = useState('')

  function load() {
    organizerAdminApi.listPending()
      .then(res => setApplications(res.data))
      .catch(() => toast('신청 목록을 불러오지 못했습니다', 'error'))
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    load()
  }, [])

  async function handleApprove(id: number) {
    setBusyId(id)
    try {
      await organizerAdminApi.approve(id)
      toast('승인되었습니다 ✓', 'success')
      load()
    } catch {
      toast('승인에 실패했습니다', 'error')
    } finally {
      setBusyId(null)
    }
  }

  async function handleReject(id: number) {
    setBusyId(id)
    try {
      await organizerAdminApi.reject(id, rejectReason)
      toast('거절되었습니다', 'success')
      setRejectingId(null)
      setRejectReason('')
      load()
    } catch {
      toast('거절에 실패했습니다', 'error')
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="card" style={{ padding: 32 }}>
      <h2 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text)', marginBottom: 4 }}>
        주최자 신청 관리
      </h2>
      <p style={{ fontSize: 13, color: 'var(--sub)', marginBottom: 24 }}>
        기존 회원이 셀프로 신청한 주최자 승격 요청입니다. 승인하면 즉시 ORGANIZER 권한이 부여됩니다.
      </p>

      {loading ? (
        <p style={{ color: 'var(--sub)', textAlign: 'center', padding: '40px 0' }}>불러오는 중...</p>
      ) : applications.length === 0 ? (
        <div className="empty-state">
          <p className="es-desc">대기 중인 신청이 없습니다.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {applications.map(a => (
            <div key={a.id} className="card" style={{ padding: 16 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12 }}>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13, color: 'var(--text)', marginBottom: 4 }}>
                    <strong>{a.userName ?? `User #${a.userId}`}</strong>
                    {a.userEmail && (
                      <span style={{ color: 'var(--sub)', fontWeight: 400 }}> · {a.userEmail}</span>
                    )}
                  </div>
                  <p style={{ fontSize: 13, color: 'var(--sub)' }}>{a.reason || '(신청 사유 없음)'}</p>
                </div>

                {rejectingId !== a.id && (
                  <div style={{ display: 'flex', gap: 8, flexShrink: 0 }}>
                    <button
                      className="btn btn-primary btn-sm"
                      disabled={busyId === a.id}
                      onClick={() => handleApprove(a.id)}
                    >
                      승인
                    </button>
                    <button
                      className="btn btn-secondary btn-sm"
                      disabled={busyId === a.id}
                      onClick={() => setRejectingId(a.id)}
                    >
                      거절
                    </button>
                  </div>
                )}
              </div>

              {rejectingId === a.id && (
                <div style={{ marginTop: 12 }}>
                  <input
                    className="form-input"
                    style={{ marginBottom: 8 }}
                    placeholder="거절 사유 (선택)"
                    value={rejectReason}
                    onChange={e => setRejectReason(e.target.value)}
                  />
                  <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                    <button
                      className="btn btn-secondary btn-sm"
                      onClick={() => { setRejectingId(null); setRejectReason('') }}
                    >
                      취소
                    </button>
                    <button
                      className="btn btn-danger btn-sm"
                      disabled={busyId === a.id}
                      onClick={() => handleReject(a.id)}
                    >
                      거절 확정
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>
      )}
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
    <div>
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
