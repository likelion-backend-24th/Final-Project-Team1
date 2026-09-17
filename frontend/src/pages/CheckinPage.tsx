import { useState } from 'react'
import { ticketApi, checkinMethodOf, type CheckinMethod } from '../api/ticket'
import { useToast } from '../components/Toast'
import { usePageTitle } from '../hooks/usePageTitle'
import type { CheckinTicketView } from '../types'

function fmtDateTime(dt?: string) {
  if (!dt) return '-'
  return new Date(dt).toLocaleString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const TICKET_ERROR_MESSAGES: Record<string, string> = {
  NOT_FOUND: '해당 QR 코드 또는 예약번호의 티켓을 찾을 수 없습니다.',
  CONFLICT: '이미 체크인되었거나 취소된 티켓입니다.',
  FORBIDDEN: '해당 박람회의 주최자만 체크인할 수 있습니다.',
  INVALID_REQUEST: '입력값을 확인해주세요.',
  CHECKIN_CLOSED: '회차가 종료되어 체크인할 수 없습니다.',
}

function errorMessage(e: unknown, fallback: string) {
  const body = (e as { body?: { data?: { code?: string }; message?: string } } | undefined)?.body
  const code = body?.data?.code
  if (code === 'CHECKIN_NOT_OPEN') {
    // 서버가 메시지에 ISO 시각을 담아 보낸다. 못 읽으면 시각 없이 안내한다.
    const opensAt = body?.message?.match(/\d{4}-\d{2}-\d{2}T[\d:.]+Z/)?.[0]
    return opensAt ? `아직 체크인할 수 없습니다. ${fmtDateTime(opensAt)}부터 가능합니다.` : '아직 체크인할 수 없습니다.'
  }
  return (code && TICKET_ERROR_MESSAGES[code]) || fallback
}

export default function CheckinPage() {
  usePageTitle('현장 체크인')
  const toast = useToast()
  const [code, setCode] = useState('')
  const [ticket, setTicket] = useState<CheckinTicketView | null>(null)
  // 확정할 때 이력에 남길 값. 조회에 쓴 수단을 그대로 들고 간다.
  const [method, setMethod] = useState<CheckinMethod | undefined>(undefined)
  const [error, setError] = useState<string | null>(null)
  const [verifying, setVerifying] = useState(false)
  const [checkingIn, setCheckingIn] = useState(false)
  const [cancelling, setCancelling] = useState(false)

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!code.trim()) return
    setError(null)
    setTicket(null)
    setVerifying(true)
    try {
      const res = await ticketApi.verify(code.trim())
      setTicket(res.data)
      setMethod(checkinMethodOf(code))
    } catch (e) {
      setError(errorMessage(e, '조회에 실패했습니다. 입력값을 다시 확인해주세요.'))
    } finally {
      setVerifying(false)
    }
  }

  const handleCheckin = async () => {
    if (!ticket) return
    setCheckingIn(true)
    try {
      const res = await ticketApi.checkin(ticket.ticketId, method)
      setTicket(prev => prev && { ...prev, status: res.data.status, usedAt: res.data.checkedInAt })
      toast('체크인이 완료되었습니다', 'success')
    } catch (e) {
      setError(errorMessage(e, '체크인에 실패했습니다. 잠시 후 다시 시도해주세요.'))
    } finally {
      setCheckingIn(false)
    }
  }

  const handleCancelCheckin = async () => {
    if (!ticket) return
    setError(null)
    setCancelling(true)
    try {
      const res = await ticketApi.cancelCheckin(ticket.ticketId)
      setTicket(prev => prev && { ...prev, status: res.data.status, usedAt: res.data.checkedInAt })
      toast('체크인을 되돌렸습니다', 'success')
    } catch (e) {
      setError(errorMessage(e, '체크인 취소에 실패했습니다. 잠시 후 다시 시도해주세요.'))
    } finally {
      setCancelling(false)
    }
  }

  return (
    <div className="container page-wrap" style={{ maxWidth: 560 }}>
      <div className="page-header">
        <h1 className="page-title">현장 체크인</h1>
        <p className="page-sub">QR 을 스캔하거나, QR 을 쓸 수 없으면 예약번호로 입장을 확인하세요.</p>
      </div>

      <form onSubmit={handleVerify} className="card" style={{ padding: 24, marginBottom: 20 }}>
        <div className="form-group" style={{ marginBottom: 12 }}>
          <label className="form-label">QR 코드 또는 예약번호</label>
          <input
            className="form-input"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="QR 스캔 값 또는 예약번호(R-XXXX-XXXX)를 입력하세요"
            autoFocus
          />
        </div>
        <button className="btn btn-primary btn-block" type="submit" disabled={verifying}>
          {verifying ? '조회 중...' : '조회'}
        </button>
      </form>

      {error && <div className="alert alert-danger" style={{ marginBottom: 20 }}><span>⚠</span><span>{error}</span></div>}

      {ticket && (
        <div className="card" style={{ padding: 24 }}>
          <div style={{ display: 'flex', gap: 6, marginBottom: 14 }}>
            <span className={`badge ${ticket.status === 'USED' ? 'badge-closed' : 'badge-published'}`}>
              {ticket.status === 'USED' ? '체크인 완료' : '입장 가능'}
            </span>
          </div>

          <div className="form-group">
            <label className="form-label">박람회</label>
            <p>{ticket.expoTitle || `박람회 #${ticket.expoId ?? '-'}`}</p>
          </div>
          <div className="form-group">
            <label className="form-label">예약번호</label>
            <p>{ticket.reservationNo ?? '-'}</p>
          </div>
          <div className="form-group">
            <label className="form-label">회차</label>
            {/* 번호는 표시용이라 서버가 못 채울 수 있다. 그때만 roundId 로 물러난다. */}
            <p>{ticket.roundSequence ? `${ticket.roundSequence}회차` : `round #${ticket.roundId}`}</p>
          </div>
          <div className="form-group">
            <label className="form-label">인원</label>
            <p>{ticket.headcount}명</p>
          </div>
          <div className="form-group">
            <label className="form-label">발급 시각</label>
            <p>{fmtDateTime(ticket.issuedAt)}</p>
          </div>
          {ticket.usedAt && (
            <div className="form-group">
              <label className="form-label">체크인 시각</label>
              <p>{fmtDateTime(ticket.usedAt)}</p>
            </div>
          )}

          {ticket.status !== 'USED' && (
            <button className="btn btn-primary btn-block" onClick={handleCheckin} disabled={checkingIn}>
              {checkingIn ? '처리 중...' : '체크인 확정'}
            </button>
          )}

          {ticket.status === 'USED' && (
            <button className="btn btn-secondary btn-block" onClick={handleCancelCheckin} disabled={cancelling}>
              {cancelling ? '처리 중...' : '체크인 취소'}
            </button>
          )}
        </div>
      )}
    </div>
  )
}
