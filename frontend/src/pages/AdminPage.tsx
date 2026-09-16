import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import { settlementApi, type AdminSettlementResponse } from '../api/settlement'
import { useAuth } from '../context/AuthContext'
import { useToast } from '../components/Toast'
import { usePageTitle } from '../hooks/usePageTitle'

export default function AdminPage() {
  usePageTitle('전체 관리자')
  const { isRole } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()

  useEffect(() => {
    if (!isRole('SUPER_ADMIN')) {
      toast('관리자 권한이 필요합니다', 'error')
      navigate('/')
    }
  }, [])

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      <div className="container page-wrap">
        <div className="page-header">
          <h1 className="page-title">관리자 콘솔</h1>
          <p className="page-sub">주최자 계정 발급</p>
        </div>

        <SettlementDashboard />

        {/* 주최자 신청(host-requests) 접수·승인은 Sprint 2 범위라 아직 API 가 없다. */}
        <CreateOrganizer />
      </div>
    </div>
  )
}

function SettlementDashboard() {
  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [data, setData] = useState<AdminSettlementResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  function fetchSettlement(y: number, m: number) {
    settlementApi.getSettlement(y, m)
      .then(res => {
        setData(res)
        setError('')
      })
      .catch(() => {
        setData(null)
        setError('정산 데이터를 불러오지 못했습니다.')
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    fetchSettlement(year, month)
  }, [])

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setLoading(true)
    fetchSettlement(year, month)
  }

  return (
    <div className="card" style={{ padding: 32, marginBottom: 24, maxWidth: 720 }}>
      <h2 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text)', marginBottom: 4 }}>
        플랫폼 정산 대시보드
      </h2>
      <p style={{ fontSize: 13, color: 'var(--sub)', marginBottom: 24 }}>
        결제 완료 기준으로 집계된 월별 매출·환불 현황입니다.
      </p>

      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: 12, alignItems: 'flex-end', marginBottom: 24 }}>
        <div className="form-group" style={{ marginBottom: 0 }}>
          <label className="form-label">연도</label>
          <input
            className="form-input"
            type="number"
            value={year}
            onChange={e => setYear(Number(e.target.value))}
            style={{ width: 120 }}
          />
        </div>
        <div className="form-group" style={{ marginBottom: 0 }}>
          <label className="form-label">월</label>
          <input
            className="form-input"
            type="number"
            min={1}
            max={12}
            value={month}
            onChange={e => setMonth(Number(e.target.value))}
            style={{ width: 80 }}
          />
        </div>
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? '조회 중...' : '조회'}
        </button>
      </form>

      {error && <div className="alert alert-danger"><span>⚠</span><span>{error}</span></div>}

      {data && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 16 }}>
          <SettlementStat label="매출" value={data.totalRevenue} />
          <SettlementStat label="환불" value={data.totalRefund} />
          <SettlementStat label="순매출" value={data.netRevenue} />
          <SettlementStat label={`플랫폼 수수료 (${Math.round(data.feeRate * 100)}%)`} value={data.platformFee} />
        </div>
      )}
    </div>
  )
}

function SettlementStat({ label, value }: { label: string; value: number }) {
  return (
    <div style={{ padding: 16, background: 'var(--bg)', borderRadius: 8 }}>
      <div style={{ fontSize: 12, color: 'var(--sub)', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 18, fontWeight: 700, color: 'var(--text)' }}>{value.toLocaleString()}원</div>
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
