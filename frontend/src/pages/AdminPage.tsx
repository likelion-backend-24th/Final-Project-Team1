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
        <div style={{ maxWidth: 720, margin: '0 auto' }}>
          <SettlementDashboard />

          {/* 주최자 신청(host-requests) 접수·승인은 Sprint 2 범위라 아직 API 가 없다. */}
          <CreateOrganizer />
        </div>
      </div>
    </div>
  )
}

type StatKind = 'revenue' | 'refund' | 'net' | 'fee'

const STAT_STYLE: Record<StatKind, { bg: string; fg: string }> = {
  revenue: { bg: 'var(--blue-l)', fg: 'var(--blue)' },
  refund: { bg: 'var(--red-l)', fg: 'var(--red)' },
  net: { bg: 'var(--green-l)', fg: 'var(--green)' },
  fee: { bg: 'var(--yellow-l)', fg: 'var(--yellow)' },
}

function SettlementDashboard() {
  const now = new Date()
  const [mode, setMode] = useState<'month' | 'year'>('month')
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [data, setData] = useState<AdminSettlementResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  function fetchSettlement(y: number, m?: number) {
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
    fetchSettlement(year, mode === 'month' ? month : undefined)
  }

  return (
    <div className="card" style={{ padding: 32, marginBottom: 24 }}>
      <h2 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text)', marginBottom: 4 }}>
        전체 정산 대시보드
      </h2>
      <p style={{ fontSize: 13, color: 'var(--sub)', marginBottom: 24 }}>
        결제 완료 기준으로 집계된 {mode === 'month' ? '월별' : '연간'} 매출·환불 현황입니다.
      </p>

      <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
        <button
          type="button"
          className={`btn btn-sm ${mode === 'month' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => setMode('month')}
        >
          월간
        </button>
        <button
          type="button"
          className={`btn btn-sm ${mode === 'year' ? 'btn-primary' : 'btn-secondary'}`}
          onClick={() => setMode('year')}
        >
          연간
        </button>
      </div>

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
        {mode === 'month' && (
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
        )}
        <button type="submit" className="btn btn-primary" disabled={loading}>
          {loading ? '조회 중...' : '조회'}
        </button>
      </form>

      {error && <div className="alert alert-danger"><span>⚠</span><span>{error}</span></div>}

      {data && (
        <>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 16 }}>
            <SettlementStat kind="revenue" label="매출" value={data.totalRevenue} />
            <SettlementStat kind="refund" label="환불" value={data.totalRefund} />
            <SettlementStat kind="net" label="순매출" value={data.netRevenue} />
            <SettlementStat kind="fee" label={`수수료 수익 (${Math.round(data.feeRate * 100)}%)`} value={data.platformFee} />
          </div>
          <SettlementBarChart data={data} />
        </>
      )}
    </div>
  )
}

function SettlementStat({ kind, label, value }: { kind: StatKind; label: string; value: number }) {
  const { bg, fg } = STAT_STYLE[kind]
  return (
    <div style={{ padding: 16, background: bg, borderRadius: 8 }}>
      <div style={{ fontSize: 12, color: fg, fontWeight: 600, marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 18, fontWeight: 700, color: fg }}>{value.toLocaleString()}원</div>
    </div>
  )
}

function SettlementBarChart({ data }: { data: AdminSettlementResponse }) {
  const bars: { kind: StatKind; label: string; value: number }[] = [
    { kind: 'revenue', label: '매출', value: data.totalRevenue },
    { kind: 'refund', label: '환불', value: data.totalRefund },
    { kind: 'net', label: '순매출', value: data.netRevenue },
    { kind: 'fee', label: '수수료', value: data.platformFee },
  ]
  const max = Math.max(...bars.map(b => b.value), 1)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 10, marginTop: 24 }}>
      {bars.map(b => (
        <div key={b.kind} style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{ width: 90, fontSize: 12, color: 'var(--sub)', flexShrink: 0 }}>{b.label}</div>
          <div style={{ flex: 1, background: 'var(--gray2)', borderRadius: 6, overflow: 'hidden', height: 20 }}>
            <div
              style={{
                width: `${(b.value / max) * 100}%`,
                height: '100%',
                background: STAT_STYLE[b.kind].fg,
                opacity: 0.85,
                borderRadius: 6,
              }}
            />
          </div>
          <div style={{ width: 100, fontSize: 12, fontWeight: 600, color: 'var(--text)', textAlign: 'right', flexShrink: 0 }}>
            {b.value.toLocaleString()}원
          </div>
        </div>
      ))}
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
