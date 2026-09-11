import { useState } from 'react'
import { requestPayment } from '@portone/browser-sdk/v2'
import { reservationApi } from '../api/reservation'
import { useToast } from './Toast'
import type { Reservation, Round } from '../types'

interface Props {
  round: Round
  onClose: () => void
  onSuccess: (reservation: Reservation) => void
  /** 결제 취소로 잡아둔 자리를 돌려줬을 때. 목록의 잔여 인원을 되돌린다. */
  onReleased: (reservation: Reservation) => void
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

type Step = 'form' | 'paying' | 'confirming' | 'done'

const USER_CANCEL_MARKERS = ['PAY_PROCESS_CANCELED', 'USER_CANCEL', 'CANCELED_BY_USER']

/** PortOne 은 code 에 'PGProviderError' 만 준다. PG 원본 코드는 어느 필드에 들어올지 보장이 없다. */
function isUserCancel(response: { code?: string; message?: string; pgCode?: string; pgMessage?: string }) {
  const haystack = [response.code, response.message, response.pgCode, response.pgMessage]
    .filter((v): v is string => typeof v === 'string')
    .join(' ')
    .toUpperCase()
  return USER_CANCEL_MARKERS.some(marker => haystack.includes(marker))
}

export default function ReservationModal({ round, onClose, onSuccess, onReleased }: Props) {
  const toast = useToast()
  const [step, setStep] = useState<Step>('form')
  const [headcount, setHeadcount] = useState(1)
  const [contactName, setContactName] = useState('')
  const [contactPhone, setContactPhone] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<Reservation | null>(null)
  const [pending, setPending] = useState<Reservation | null>(null)
  // 결제창을 통과했는지. 통과했으면 재시도는 확정만 다시 부른다(재결제 금지).
  const [paid, setPaid] = useState(false)

  const isFree = round.fee === 0

  const pay = async (reservation: Reservation) => {
    if (!reservation.paymentId) {
      setStep('form')
      setError('결제 정보를 불러오지 못했습니다. 다시 시도해주세요.')
      return
    }

    setStep('paying')
    let response
    try {
      response = await requestPayment({
        storeId: import.meta.env.VITE_PORTONE_STORE_ID,
        channelKey: import.meta.env.VITE_PORTONE_CHANNEL_KEY,
        paymentId: reservation.paymentId,
        orderName: `회차 예약 (${headcount}명)`,
        totalAmount: reservation.amount,
        currency: 'CURRENCY_KRW',
        payMethod: 'CARD',
        customer: { fullName: contactName.trim(), phoneNumber: contactPhone.trim() },
      })
    } catch (e) {
      // SDK 입력값 오류(storeId·channelKey 누락 등)는 여기로 온다. 예약은 이미 만들어졌다.
      console.error('portone requestPayment failed', e)
      setStep('form')
      setError('결제창을 열지 못했습니다. 결제 설정을 확인해주세요.')
      return
    }

    if (!response || response.code) {
      // 판정이 어긋나면 자리가 잠긴 채 빠져나갈 수 없으므로 응답 원본을 남긴다.
      console.warn('[portone] payment not completed', response)
      setStep('form')

      // 사용자가 직접 닫은 경우에만 예약을 정리한다. 안 그러면 자리가 10분간 잠겨
      // 같은 회차에 다시 예약할 수 없다(DUPLICATE_RESERVATION).
      if (response && isUserCancel(response)) {
        if (await releasePending(reservation)) {
          setError('결제를 취소했습니다. 다시 신청할 수 있습니다.')
        }
        return
      }

      // 인증 실패 등은 곧바로 다시 시도할 가능성이 높아 자리를 붙들고 있는 편이 유리하다.
      // PortOne 의 message 는 내부 코드가 섞여있어 그대로 노출하지 않는다.
      setError('결제에 실패했습니다. 다시 시도하거나, 예약을 취소하고 다시 신청해주세요.')
      return
    }

    setPaid(true)
    await confirm(reservation)
  }

  const releasePending = async (reservation: Reservation) => {
    try {
      await reservationApi.cancel(reservation.reservationId)
      setPending(null)
      setPaid(false)
      onReleased(reservation)
      return true
    } catch (e) {
      // 자리가 잠긴 채 남는다. 만료 배치가 10분 뒤 회수하지만 그동안 재예약이 막히므로 알린다.
      console.error('[reservation] release failed', e)
      setError(errorMessage(e, '예약 정리에 실패했습니다. 내 예약에서 직접 취소해주세요.'))
      return false
    }
  }

  const handleReleaseClick = async () => {
    if (!pending) return
    setSubmitting(true)
    const released = await releasePending(pending)
    setSubmitting(false)
    if (released) setError('예약을 취소했습니다. 다시 신청할 수 있습니다.')
  }

  const confirm = async (reservation: Reservation) => {
    setStep('confirming')
    try {
      const confirmed = (await reservationApi.confirmPayment(reservation.reservationId)).data
      const finalReservation = { ...reservation, status: confirmed.status }
      setResult(finalReservation)
      setStep('done')
      onSuccess(finalReservation)
    } catch (e) {
      setStep('form')
      setError(errorMessage(e, '결제 확인에 실패했습니다. 다시 시도해주세요.'))
    }
  }

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

      setPending(created)
      await pay(created)
    } catch (e) {
      setStep('form')
      setError(errorMessage(e, '예약에 실패했습니다. 다시 시도해주세요.'))
    } finally {
      setSubmitting(false)
    }
  }

  const handleRetry = () => {
    if (pending) (paid ? confirm(pending) : pay(pending)).catch(() => setStep('form'))
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
                disabled={!!pending}
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
                disabled={!!pending}
              />
            </div>

            <div className="form-group">
              <label className="form-label">연락처 <span className="req">*</span></label>
              <input
                className="form-input"
                value={contactPhone}
                onChange={(e) => setContactPhone(e.target.value)}
                placeholder="010-1234-5678"
                disabled={!!pending}
              />
            </div>

            {!isFree && (
              <div className="alert alert-info">
                결제 금액: {(round.fee * headcount).toLocaleString()}원
              </div>
            )}

            <div className="modal-footer">
              <button className="btn btn-secondary" onClick={onClose} disabled={submitting}>닫기</button>
              {pending && (
                <button className="btn btn-outline" onClick={handleReleaseClick} disabled={submitting}>
                  예약 취소
                </button>
              )}
              <button className="btn btn-primary" onClick={pending ? handleRetry : handleSubmit} disabled={submitting}>
                {submitting ? '처리 중...' : pending ? (paid ? '결제 확인 재시도' : '다시 결제하기') : isFree ? '예약 확정' : '결제하고 예약하기'}
              </button>
            </div>
          </>
        )}

        {step === 'paying' && (
          <div style={{ textAlign: 'center', padding: '24px 0' }}>
            <p style={{ fontSize: 15, color: 'var(--text2)' }}>결제창을 여는 중입니다...</p>
          </div>
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
