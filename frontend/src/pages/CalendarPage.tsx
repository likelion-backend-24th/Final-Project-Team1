import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { calendarApi, type CalendarRoundView, type CalendarSuggestMeta, type ConstraintSource } from '../api/calendar'
import { useAuth } from '../context/AuthContext'
import { usePageTitle } from '../hooks/usePageTitle'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']

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

export default function CalendarPage() {
  usePageTitle('행사 캘린더')
  const { user } = useAuth()
  const navigate = useNavigate()

  const now = new Date()
  const [year, setYear] = useState(now.getFullYear())
  const [month, setMonth] = useState(now.getMonth())
  const [constraintInput, setConstraintInput] = useState('')

  // 기본 화면 - 그 달의 전체 공개 일정. 로그인 없이도 보인다.
  const [events, setEvents] = useState<CalendarRoundView[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  // AI 추천 - 기본 화면 위에 얹는 선택 동작. 로그인해야 쓸 수 있다.
  const [recommended, setRecommended] = useState<Set<number>>(new Set())
  const [meta, setMeta] = useState<CalendarSuggestMeta | null>(null)
  const [suggesting, setSuggesting] = useState(false)
  const [suggestError, setSuggestError] = useState('')

  function loadEvents() {
    setLoading(true)
    setError('')
    setRecommended(new Set())
    setMeta(null)
    setSuggestError('')
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
    // eslint-disable-next-line react-hooks/set-state-in-effect, react-hooks/exhaustive-deps
    loadEvents()
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
                  placeholder="예: 오후 2시 이후만"
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
              {cells.map(cell => (
                <div
                  key={cell.key}
                  className={`calendar-cell${cell.inMonth ? '' : ' out'}${isToday(cell) ? ' today' : ''}`}
                >
                  <span className="calendar-date">{cell.date}</span>
                  {(eventsByDay.get(cell.key) ?? []).map(ev => (
                    <div
                      key={ev.roundId}
                      className={`calendar-event${recommended.has(ev.roundId) ? ' recommended' : ''}`}
                      title={`${ev.expoTitle} · ${ev.sequence}회차`}
                      onClick={() => navigate(`/expos/${ev.expoId}`)}
                    >
                      {ev.expoTitle}
                    </div>
                  ))}
                </div>
              ))}
            </div>

            {loading && (
              <p style={{ textAlign: 'center', padding: '24px 0', color: 'var(--sub)' }}>불러오는 중...</p>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
