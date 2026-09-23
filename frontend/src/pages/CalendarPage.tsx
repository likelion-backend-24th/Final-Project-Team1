import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { calendarApi, type CalendarRoundView, type CalendarSuggestMeta, type ConstraintSource } from '../api/calendar'
import { expoApi } from '../api/expo'
import type { Expo } from '../types'
import { cdnImage } from '../lib/cloudinary'
import { useAuth } from '../context/AuthContext'
import { usePageTitle } from '../hooks/usePageTitle'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const DAY_PAGE_SIZE = 3

const THUMB_COLORS: [string, string][] = [
  ['#1A1A2E', '#374151'],
  ['#0F3460', '#16213E'],
  ['#7C3AED', '#4C1D95'],
  ['#065F46', '#064E3B'],
]

function monthRange(year: number, month: number) {
  const from = new Date(year, month, 1, 0, 0, 0)
  const to = new Date(year, month + 1, 0, 23, 59, 59)
  return { from: from.toISOString(), to: to.toISOString() }
}

// new Date(isoUtc) 를 로컬(KST) 기준으로 읽어서 "그 날짜"를 만든다.
function dayKey(iso: string) {
  const d = new Date(iso)
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`
}

function constraintSourceLabel(source: ConstraintSource) {
  if (source === 'GEMINI') return '입력한 조건을 반영했습니다.'
  if (source === 'RULE') return '입력한 조건을 간단히 반영했습니다.'
  return '조건 없이 전체 후보 중에서 골랐습니다.'
}

function defaultDayKey(year: number, month: number, now: Date) {
  if (year === now.getFullYear() && month === now.getMonth()) {
    return `${year}-${month}-${now.getDate()}`
  }
  return `${year}-${month}-1`
}

function formatTime(iso: string) {
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

export default function CalendarPage() {
  usePageTitle('행사 캘린더')
  const { user } = useAuth()
  const navigate = useNavigate()

  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth())
  const [constraintInput, setConstraintInput] = useState('')
  const [selectedKey, setSelectedKey] = useState(() => defaultDayKey(year, month, now))
  const [dayPage, setDayPage] = useState(0)

  // 기본 화면 - 그 달의 전체 공개 일정. 로그인 없이도 보인다.
  const [events, setEvents] = useState<CalendarRoundView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // AI 추천 - 기본 화면 위에 얹는 선택 동작. 로그인해야 쓸 수 있다.
  const [recommended, setRecommended] = useState<Set<number>>(new Set())
  const [meta, setMeta] = useState<CalendarSuggestMeta | null>(null)
  const [suggesting, setSuggesting] = useState(false)
  const [suggestError, setSuggestError] = useState('')

  // 선택한 날짜의 행사를 전체 박람회 카드와 같은 모양으로 보여주기 위한 상세 캐시
  const [expoDetails, setExpoDetails] = useState<Record<number, Expo>>({})

  function selectDay(key: string) {
    setSelectedKey(key)
    setDayPage(0)
  }

  function loadEvents() {
    setLoading(true)
    setError('')
    setRecommended(new Set())
    setMeta(null)
    setSuggestError('')
    selectDay(defaultDayKey(year, month, now))
    const { from, to } = monthRange(year, month)
    calendarApi.listEvents(from, to)
      .then(res => setEvents(res.data ?? []))
      .catch(() => {
        setError('일정을 불러오지 못했습니다.')
        setEvents([])
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadEvents()
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [year, month])

  function handleSuggest(e: React.FormEvent) {
    e.preventDefault()
    if (!user) { navigate('/auth'); return }

    setSuggesting(true)
    setSuggestError('')
    const { from, to } = monthRange(year, month)
    calendarApi.suggest(from, to, constraintInput)
      .then(res => {
        setRecommended(new Set((res.data ?? []).map(ev => ev.roundId)))
        setMeta((res.meta as CalendarSuggestMeta) ?? null)
      })
      .catch((err: unknown) => {
        const e2 = err as { status?: number }
        if (e2.status === 429) setSuggestError('요청이 너무 많습니다. 잠시 후 다시 시도해주세요.')
        else if (e2.status === 400) setSuggestError('조회 조건을 확인해주세요.')
        else setSuggestError('추천을 받지 못했습니다.')
        setRecommended(new Set())
        setMeta(null)
      })
      .finally(() => setSuggesting(false))
  }

  function moveMonth(delta: number) {
    const d = new Date(year, month + delta, 1)
    setYear(d.getFullYear())
    setMonth(d.getMonth())
  }

  const eventsByDay = useMemo(() => {
    const map = new Map<string, CalendarRoundView[]>()
    for (const ev of events) {
      const key = dayKey(ev.startsAt)
      const list = map.get(key) ?? []
      list.push(ev)
      map.set(key, list)
    }
    return map
  }, [events])

  const cells = useMemo(() => {
    const firstWeekday = new Date(year, month, 1).getDay()
    const daysInMonth = new Date(year, month + 1, 0).getDate()
    const prevMonthDays = new Date(year, month, 0).getDate()
    const prevMonth = month === 0 ? 11 : month - 1
    const prevYear = month === 0 ? year - 1 : year
    const nextMonth = month === 11 ? 0 : month + 1
    const nextYear = month === 11 ? year + 1 : year

    const result: Array<{ date: number; year: number; month: number; key: string; inMonth: boolean }> = []
    for (let i = firstWeekday - 1; i >= 0; i--) {
      const d = prevMonthDays - i
      result.push({ date: d, year: prevYear, month: prevMonth, key: `${prevYear}-${prevMonth}-${d}`, inMonth: false })
    }
    for (let d = 1; d <= daysInMonth; d++) {
      result.push({ date: d, year, month, key: `${year}-${month}-${d}`, inMonth: true })
    }
    const remainder = result.length % 7
    if (remainder !== 0) {
      for (let d = 1; d <= 7 - remainder; d++) {
        result.push({ date: d, year: nextYear, month: nextMonth, key: `${nextYear}-${nextMonth}-${d}`, inMonth: false })
      }
    }
    return result
  }, [year, month])

  const isToday = (cell: { date: number; year: number; month: number }) =>
    now.getFullYear() === cell.year && now.getMonth() === cell.month && now.getDate() === cell.date

  const selectedEvents = eventsByDay.get(selectedKey) ?? []
  const [selYear, selMonth, selDate] = selectedKey.split('-').map(Number)
  const selectedLabel = `${selMonth + 1}월 ${selDate}일 (${WEEKDAYS[new Date(selYear, selMonth, selDate).getDay()]})`

  // 같은 날 여러 회차가 있어도 박람회 기준으로 한 장씩만 카드로 보여준다.
  const selectedExpos = useMemo(() => {
    const order: number[] = []
    const byExpo = new Map<number, CalendarRoundView[]>()
    for (const ev of selectedEvents) {
      if (!byExpo.has(ev.expoId)) order.push(ev.expoId)
      byExpo.set(ev.expoId, [...(byExpo.get(ev.expoId) ?? []), ev])
    }
    return order.map(expoId => {
      const rounds = byExpo.get(expoId)!
      const ended = rounds.every(r => new Date(r.endsAt) < now)
      const recommendedHere = rounds.some(r => recommended.has(r.roundId))
      return { expoId, rounds, ended, recommendedHere }
    })
  }, [selectedEvents, recommended])

  const dayTotalPages = Math.max(1, Math.ceil(selectedExpos.length / DAY_PAGE_SIZE))
  const pagedExpos = selectedExpos.slice(dayPage * DAY_PAGE_SIZE, dayPage * DAY_PAGE_SIZE + DAY_PAGE_SIZE)

  useEffect(() => {
    const missing = pagedExpos.map(e => e.expoId).filter(id => !expoDetails[id])
    if (missing.length === 0) return
    missing.forEach(id => {
      expoApi.getExpo(id)
        .then(res => {
          if (!res.data) return
          setExpoDetails(prev => ({ ...prev, [id]: res.data }))
        })
        .catch(() => {})
    })
  }, [pagedExpos, expoDetails])

  return (
    <div style={{ background: 'var(--bg)', minHeight: 'calc(100vh - 64px)' }}>
      <div className="container page-wrap">
        <div className="calendar-page-inner">
          <div className="page-header">
            <h1 className="page-title">행사 캘린더</h1>
            <p style={{ fontSize: 13, color: 'var(--sub)', marginTop: 4 }}>
              그 달에 열리는 박람회 일정을 한눈에 볼 수 있습니다. 조건을 입력하면 그중 시간이 겹치지 않는 일정을 추천해드려요.
            </p>
          </div>

          <div className="calendar-wrap">
            <div className="calendar-toolbar">
              <div className="calendar-nav">
                <button type="button" className="btn btn-outline" onClick={() => moveMonth(-1)}>이전</button>
                <span className="calendar-month-label">{year}년 {month + 1}월</span>
                <button type="button" className="btn btn-outline" onClick={() => moveMonth(1)}>다음</button>
              </div>

              <form className="calendar-constraint-form" onSubmit={handleSuggest}>
                <input
                  className="form-input"
                  placeholder="예: 오후 2시 이후만, IT 박람회만"
                  value={constraintInput}
                  onChange={e => setConstraintInput(e.target.value)}
                  maxLength={200}
                />
                <button type="submit" className="btn btn-primary btn-sm calendar-suggest-btn" disabled={suggesting}>추천받기</button>
              </form>
            </div>

            {error && <p className="calendar-constraint-note" style={{ color: 'var(--red)' }}>{error}</p>}
            {suggestError && <p className="calendar-constraint-note" style={{ color: 'var(--red)' }}>{suggestError}</p>}
            {!suggestError && meta && (
              <p className="calendar-constraint-note">
                {constraintSourceLabel(meta.constraintSource)} 후보 {meta.candidateCount}건 중 {recommended.size}건을 추천 표시했습니다.
                <button type="button" className="calendar-clear-btn" onClick={() => { setRecommended(new Set()); setMeta(null) }}>지우기</button>
              </p>
            )}

            <div className="calendar-grid">
              {WEEKDAYS.map(w => (
                <div key={w} className="calendar-weekday">{w}</div>
              ))}
              {cells.map(cell => {
                const dayEvents = eventsByDay.get(cell.key) ?? []
                const hasRecommended = dayEvents.some(ev => recommended.has(ev.roundId))
                return (
                  <div
                    key={cell.key}
                    className={`calendar-cell${cell.inMonth ? '' : ' out'}${isToday(cell) ? ' today' : ''}${selectedKey === cell.key ? ' selected' : ''}`}
                    onClick={() => cell.inMonth && selectDay(cell.key)}
                  >
                    <span className="calendar-date">{cell.date}</span>
                    {dayEvents.length > 0 && (
                      <span className={`calendar-count${hasRecommended ? ' recommended' : ''}`}>{dayEvents.length}건</span>
                    )}
                  </div>
                )
              })}
            </div>

            {loading && (
              <p style={{ textAlign: 'center', padding: '24px 0', color: 'var(--sub)' }}>불러오는 중...</p>
            )}

            {!loading && (
              <div className="calendar-day-panel">
                <h3 className="calendar-day-panel-title">{selectedLabel} · {selectedExpos.length}개 행사</h3>
                {selectedExpos.length === 0 ? (
                  <p className="calendar-day-empty">이 날에는 등록된 행사가 없습니다.</p>
                ) : (
                  <div className="expo-grid calendar-expo-grid">
                    {pagedExpos.map(({ expoId, rounds, ended, recommendedHere }) => {
                      const expo = expoDetails[expoId]
                      const colors = THUMB_COLORS[expoId % THUMB_COLORS.length]
                      const timeLabel = rounds.map(r => `${formatTime(r.startsAt)}~${formatTime(r.endsAt)}`).join(', ')
                      return (
                        <div
                          key={expoId}
                          className={`expo-card${recommendedHere ? ' recommended' : ''}${ended ? ' ended' : ''}`}
                          onClick={() => navigate(`/expos/${expoId}`)}
                          role="button"
                          tabIndex={0}
                        >
                          <div className="expo-card-thumb">
                            <div
                              className="expo-card-thumb-inner"
                              style={{ background: `linear-gradient(135deg, ${colors[0]}, ${colors[1]})` }}
                            >
                              {expo?.thumbnailUrl && (
                                <img
                                  src={cdnImage(expo.thumbnailUrl, 600)}
                                  alt=""
                                  loading="lazy"
                                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                />
                              )}
                            </div>
                            {ended && (
                              <div className="expo-card-thumb-badge">
                                <span className="badge badge-closed">종료</span>
                              </div>
                            )}
                          </div>
                          <div className="expo-card-body">
                            <p className="expo-card-cat">{expo?.category ?? ' '}</p>
                            <h3 className="expo-card-title">{expo?.title ?? rounds[0].expoTitle}</h3>
                            <div className="expo-card-meta">
                              {expo?.venue && (
                                <div className="expo-card-meta-row"><span>{expo.venue}</span></div>
                              )}
                              <div className="expo-card-meta-row"><span>{timeLabel}</span></div>
                            </div>
                          </div>
                        </div>
                      )
                    })}
                  </div>
                )}
                {dayTotalPages > 1 && (
                  <div className="calendar-day-pager">
                    <button
                      type="button"
                      className="btn btn-outline btn-sm"
                      disabled={dayPage === 0}
                      onClick={() => setDayPage(p => p - 1)}
                    >
                      이전
                    </button>
                    <span className="calendar-day-pager-label">{dayPage + 1} / {dayTotalPages}</span>
                    <button
                      type="button"
                      className="btn btn-outline btn-sm"
                      disabled={dayPage >= dayTotalPages - 1}
                      onClick={() => setDayPage(p => p + 1)}
                    >
                      다음
                    </button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
