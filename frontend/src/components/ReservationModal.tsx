import { useState } from 'react'
import { reservationApi } from '../api/reservation'
import { useToast } from './Toast'
import type { Reservation, Round } from '../types'

interface Props {
  round: Round
  onClose: () => void
  onSuccess: (reservation: Reservation) => void
}

const ERROR_MESSAGES: Record<string, string> = {
  CAPACITY_EXCEEDED: '잔여 정원을 초과했습니다. 인원을 줄여 다시 시도해주세요.',
  DUPLICATE_RESERVATION: '이미 이 회차에 예약이 있습니다.',
  NOT_FOUND: '회차 정보를 찾을 수 없습니다.',
  DEPENDENCY_UNAVAILABLE: '결제 확인이 지연되고 있습니다. 잠시 후 다시 시도해주세요.',
  UNAUTHENTICATED: '로그인이 필요합니다.',
}

function errorMessage(e: unknown, fallback: string) {
  const code = (e as { body?: { data?: { code?: string } } } | undefined)?.body?.data?.code
  return (code && ERROR_MESSAGES[code]) || fallback
}

type Step = 'form' | 'confirming' | 'done'

export default function ReservationModal({ round, onClose, onSuccess }: Props) {
  const toast = useToast()
  const [step, setStep] = useState<Step>('form')
  const [headcount, setHeadcount] = useState(1)
  const [contactName, setContactName] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<Reservation | null>(null)

  const isFree = round.fee === 0

  const handleSubmit = async () => {
    setError(null)

    if (!contactName.trim()) return setError('이름을 입력해주세요.')
    if (!/^01[0-9][- ]?\d{3,4}[- ]?\d{4}$/.test(contactPhone.trim())) {
      return setError('휴대폰 번호 형식이 올바르지 않습니다. 예: 010-1234-5678')
    }
    if (headcount < 1 || headcount > round.remaining) {
      return setError(`예약 가능 인원은 1명 이상 ${round.remaining}명 이하입니다.`)
    }

    setSubmitting(true)
    try {
      const created = (await reservationApi.create(round.roundId, {
        headcount,
        contactName: contactName.trim(),
        contactPhone: contactPhone.trim(),
      })).data

      if (created.status === 'CONFIRMED') {
        setResult(created)
        setStep('done')
        onSuccess(created)
        return
      }

      // 유료 회차: 실제 결제창 연동 전까지는 승인 조회를 바로 이어서 부른다.
      setStep('confirming')
      const confirmed = (await reservationApi.confirmPayment(created.reservationId)).data
      const finalReservation = { ...created, status: confirmed.status }
      setResult(finalReservation)
      setStep('done')
      onSuccess(finalReservation)
    } catch (e) {
      setStep('form')
      setError(errorMessage(e, '예약에 실패했습니다. 다시 시도해주세요.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        {step === 'form' && (
          <>
            <h3 className="modal-title">예약 신청</h3>
            <p className="modal-desc">
              {isFree ? '무료 회차입니다. 신청 즉시 예약이 확정됩니다.' : '신청 직후 결제가 진행됩니다.'}
            </p>

            {error && <div className="alert alert-danger">{error}</div>}

            <div className="form-group">
              <label className="form-label">인원 <span className="req">*</span></label>
              <input
                className="form-input"
                type="number"
                min={1}
                max={round.remaining}
                value={headcount}
                onChange={(e) => setHeadcount(Number(e.target.value))}
              />
              <p className="form-hint">잔여 {round.remaining}명</p>
            </div>

            <div className="form-group">
              <label className="form-label">예약자 이름 <span className="req">*</span></label>
              <input
                className="form-input"
                value={contactName}
                onChange={(e) => setContactName(e.target.value)}
                placeholder="홍길동"
              />
            </div>

            <div className="form-group">
              <label className="form-label">연락처 <span className="req">*</span></label>
              <input
                className="form-input"
                value={contactPhone}
                onChange={(e) => setContactPhone(e.target.value)}
                placeholder="010-1234-5678"
              />
            </div>

            {!isFree && (
              <div className="alert alert-info">
                결제 금액: {(round.fee * headcount).toLocaleString()}원
              </div>
            )}

            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={onClose} disabled={submitting}>취소</button>
              <button className="btn btn-primary" onClick={handleSubmit} disabled={submitting}>
                {submitting ? '처리 중...' : isFree ? '예약 확정' : '결제하고 예약하기'}
              </button>
            </div>
          </>
        )}

        {step === 'confirming' && (
          <div style={{ textAlign: 'center', padding: '24px 0' }}>
            <p style={{ fontSize: 15, color: 'var(--text2)' }}>결제를 확인하고 있습니다...</p>
          </div>
        )}

        {step === 'done' && result && (
          <>
            {result.status === 'CONFIRMED' ? (
              <>
                <h3 className="modal-title">예약이 확정되었습니다</h3>
                <p className="modal-desc">
                  예약번호 <strong>{result.reservationNo}</strong> · {result.headcount}명
                  {result.amount > 0 && ` · ${result.amount.toLocaleString()}원`}
                  <br />
                  티켓은 내 예약 페이지에서 확인하실 수 있습니다.
                </p>
              </>
            ) : (
              <>
                <h3 className="modal-title">결제 확인이 지연되고 있습니다</h3>
                <p className="modal-desc">
                  예약번호 <strong>{result.reservationNo}</strong>은 결제 대기 상태입니다.
                  잠시 후 내 예약 페이지에서 다시 확인해주세요.
                </p>
              </>
            )}
            <div className="modal-footer">
              <button
                className="btn btn-primary"
                onClick={() => {
                  toast('예약이 처리되었습니다', 'success')
                  onClose()
                }}
              >
                확인
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  )
}
