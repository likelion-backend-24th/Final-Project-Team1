import { useEffect, useState } from 'react'
import { reservationApi } from '../api/reservation'
import { useToast } from '../components/Toast'
import type { MyReservation, MyReservationDetail } from '../types'

function fmtDateTime(dt?: string) {
  if (!dt) return '-'
  return new Date(dt).toLocaleString('ko-KR', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

const STATUS_LABEL: Record<MyReservation['status'], string> = {
  PENDING: '결제 대기',
  CONFIRMED: '예약 확정',
  CANCELLED: '취소됨',
  EXPIRED: '기한 만료',
}
const STATUS_BADGE: Record<MyReservation['status'], string> = {
  PENDING: 'badge-hidden',
  CONFIRMED: 'badge-published',
  CANCELLED: 'badge-closed',
  EXPIRED: 'badge-closed',
}

const REFUND_LABEL: Record<string, string> = {
  REFUNDED: '환불 완료',
  REFUND_PENDING: '환불 처리 중',
  REFUND_UNRESOLVED: '환불 지연 — 문의 필요',
  NOT_REFUNDABLE: '환불 기한 경과(환불 불가)',
}

const CANCEL_ERROR_MESSAGES: Record<string, string> = {
  CANCELLATION_DEADLINE_PASSED: '회차가 이미 시작되어 취소할 수 없습니다.',
  NOT_FOUND: '예약 정보를 찾을 수 없습니다.',
  INVALID_STATE_TRANSITION: '이미 종료된 예약은 취소할 수 없습니다.',
}

function cancelErrorMessage(e: unknown, fallback: string) {
  const code = (e as { body?: { data?: { code?: string } } } | undefined)?.body?.data?.code
  return (code && CANCEL_ERROR_MESSAGES[code]) || fallback
}

export default function MyReservationsPage() {
  const toast = useToast()
  const [reservations, setReservations] = useState<MyReservation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)
  const [detailId, setDetailId] = useState<number | null>(null)

  const fetchReservations = () => {
    reservationApi.listMine()
      .then(res => { setReservations(res.data ?? []); setError(false) })
      .catch(() => setError(true))
      .finally(() => setLoading(false))
  }

  useEffect(fetchReservations, [])

  const reload = () => {
    setLoading(true)
    fetchReservations()
  }

  const handleCancelled = (reservationId: number, status: string, refundState: string) => {
    setReservations(prev => prev.map(r =>
      r.reservationId === reservationId ? { ...r, status: status as MyReservation['status'], refundState: refundState as MyReservation['refundState'] } : r
    ))
    toast('예약이 취소되었습니다', 'success')
  }

  return (
    <div className="container page-wrap">
      <div className="page-header">
        <h1 className="page-title">내 예약</h1>
        <p className="page-sub">신청한 예약을 확인하고, 취소·환불을 처리할 수 있습니다.</p>
      </div>

      {loading ? (
        <p style={{ color: 'var(--sub)', textAlign: 'center', padding: '60px 0' }}>불러오는 중...</p>
      ) : error ? (
        <div className="empty-state">
          <p className="es-title">불러올 수 없습니다</p>
          <p className="es-desc">잠시 후 다시 시도해주세요.</p>
          <button className="btn btn-outline" onClick={reload}>새로고침</button>
        </div>
      ) : reservations.length === 0 ? (
        <div className="empty-state">
          <p className="es-title">예약 내역이 없습니다</p>
          <p className="es-desc">박람회를 둘러보고 회차를 예약해보세요.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          {reservations.map(r => (
            <div
              key={r.reservationId}
              className="card"
              style={{ padding: '18px 22px', display: 'flex', alignItems: 'center', gap: 16, cursor: 'pointer' }}
              onClick={() => setDetailId(r.reservationId)}
            >
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', gap: 6, marginBottom: 6 }}>
                  <span className={`badge ${STATUS_BADGE[r.status]}`}>{STATUS_LABEL[r.status]}</span>
                  {REFUND_LABEL[r.refundState] && (
                    <span className="badge badge-blue">{REFUND_LABEL[r.refundState]}</span>
                  )}
                </div>
                <div style={{ fontWeight: 700, color: 'var(--text)', marginBottom: 2 }}>
                  {fmtDateTime(r.startsAt)} – {fmtDateTime(r.endsAt)}
                </div>
                <div style={{ fontSize: 12, color: 'var(--sub)' }}>
                  예약번호 {r.reservationNo} · {r.headcount}명 {r.amount > 0 && `· ${r.amount.toLocaleString()}원`}
                </div>
              </div>
              <button className="btn btn-secondary btn-sm" onClick={(e) => { e.stopPropagation(); setDetailId(r.reservationId) }}>
                상세보기
              </button>
            </div>
          ))}
        </div>
      )}

      {detailId != null && (
        <ReservationDetailModal
          reservationId={detailId}
          onClose={() => setDetailId(null)}
          onCancelled={handleCancelled}
        />
      )}
    </div>
  )
}

function ReservationDetailModal({ reservationId, onClose, onCancelled }: {
  reservationId: number
  onClose: () => void
  onCancelled: (reservationId: number, status: string, refundState: string) => void
}) {
  const toast = useToast()
  const [detail, setDetail] = useState<MyReservationDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [cancelling, setCancelling] = useState(false)
  const [confirmingCancel, setConfirmingCancel] = useState(false)

  useEffect(() => {
    reservationApi.getMine(reservationId)
      .then(res => setDetail(res.data))
      .catch(() => toast('상세 정보를 불러오지 못했습니다', 'error'))
      .finally(() => setLoading(false))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reservationId])

  const handleCancel = async () => {
    setCancelling(true)
    try {
      const result = (await reservationApi.cancel(reservationId)).data
      setDetail(prev => prev && { ...prev, status: result.status as MyReservationDetail['status'], refundState: result.refundState })
      onCancelled(reservationId, result.status, result.refundState)
      setConfirmingCancel(false)
    } catch (e) {
      toast(cancelErrorMessage(e, '취소에 실패했습니다. 잠시 후 다시 시도해주세요.'), 'error')
    } finally {
      setCancelling(false)
    }
  }

  const cancellable = detail?.status === 'PENDING' || detail?.status === 'CONFIRMED'

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h3 className="modal-title">예약 상세</h3>

        {loading ? (
          <p style={{ color: 'var(--sub)', padding: '20px 0' }}>불러오는 중...</p>
        ) : !detail ? (
          <p style={{ color: 'var(--sub)', padding: '20px 0' }}>정보를 불러올 수 없습니다.</p>
        ) : (
          <>
            <div style={{ display: 'flex', gap: 6, marginBottom: 14 }}>
              <span className={`badge ${STATUS_BADGE[detail.status]}`}>{STATUS_LABEL[detail.status]}</span>
              {REFUND_LABEL[detail.refundState] && (
                <span className="badge badge-blue">{REFUND_LABEL[detail.refundState]}</span>
              )}
            </div>

            <div className="form-group">
              <label className="form-label">예약번호</label>
              <p>{detail.reservationNo}</p>
            </div>
            <div className="form-group">
              <label className="form-label">일시</label>
              <p>{fmtDateTime(detail.startsAt)} – {fmtDateTime(detail.endsAt)}</p>
            </div>
            <div className="form-group">
              <label className="form-label">예약자</label>
              <p>{detail.contactName} · {detail.contactPhone}</p>
            </div>
            <div className="form-group">
              <label className="form-label">인원 / 결제 금액</label>
              <p>{detail.headcount}명{detail.amount > 0 && ` · ${detail.amount.toLocaleString()}원`}</p>
            </div>

            {detail.status === 'CONFIRMED' && (
              <div className="form-group">
                <label className="form-label">입장용 체크인 코드</label>
                {detail.ticketAvailable && detail.ticket ? (
                  <p style={{ fontFamily: 'monospace', fontSize: 13, wordBreak: 'break-all', background: 'var(--gray1)', padding: '10px 12px', borderRadius: 'var(--r-sm)' }}>
                    {detail.ticket.checkinToken}
                  </p>
                ) : (
                  <p style={{ fontSize: 13, color: 'var(--sub)' }}>티켓이 아직 발급되지 않았습니다. 잠시 후 다시 확인해주세요.</p>
                )}
              </div>
            )}

            {confirmingCancel ? (
              <div className="alert alert-warning" style={{ marginBottom: 4 }}>
                정말 예약을 취소하시겠습니까? 이 작업은 되돌릴 수 없습니다.
              </div>
            ) : null}

            <div className="modal-footer">
              {confirmingCancel ? (
                <>
                  <button className="btn btn-secondary" onClick={() => setConfirmingCancel(false)} disabled={cancelling}>
                    아니오
                  </button>
                  <button className="btn btn-danger" onClick={handleCancel} disabled={cancelling}>
                    {cancelling ? '처리 중...' : '취소 확정'}
                  </button>
                </>
              ) : (
                <>
                  <button className="btn btn-secondary" onClick={onClose}>닫기</button>
                  {cancellable && (
                    <button className="btn btn-danger" onClick={() => setConfirmingCancel(true)}>
                      예약 취소
                    </button>
                  )}
                </>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
