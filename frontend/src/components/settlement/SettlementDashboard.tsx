import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  settlementApi,
  type AdminSettlementResponse,
  type SettlementBucket,
  type SettlementPeriodType,
} from '../../api/settlement'

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const PERIODS: { value: SettlementPeriodType; label: string }[] = [
  { value: 'DAY', label: '당일' },
  { value: 'WEEK', label: '주간' },
  { value: 'MONTH', label: '월간' },
  { value: 'YEAR', label: '연간' },
]

function parseDateStr(s: string): Date {
  const [y, m, d] = s.split('-').map(Number)
  return new Date(y, m - 1, d)
}

function toDateStr(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function todayStr(): string {
  return toDateStr(new Date())
}

/** MONTH·YEAR 는 일부러 day=1로 고정한다 - setMonth/setFullYear 를 날짜 그대로 쓰면
 * "31일 -> 다음 달로 넘어가는" JS Date 함정에 걸린다. */
function shiftAnchor(period: SettlementPeriodType, dateStr: string, dir: 1 | -1): string {
  const d = parseDateStr(dateStr)
  if (period === 'YEAR') {
    d.setFullYear(d.getFullYear() + dir, 0, 1)
    return toDateStr(d)
  }
  if (period === 'MONTH') {
    d.setMonth(d.getMonth() + dir, 1)
    return toDateStr(d)
  }
  d.setDate(d.getDate() + dir * (period === 'WEEK' ? 7 : 1))
  return toDateStr(d)
}

function formatPeriodLabel(period: SettlementPeriodType, from: string, to: string): string {
  const [fy, fm, fd] = from.split('-').map(Number)
  if (period === 'YEAR') return `${fy}년`
  if (period === 'MONTH') return `${fy}년 ${fm}월`
  if (period === 'DAY') return `${fy}년 ${fm}월 ${fd}일`
  const [, tm, td] = to.split('-').map(Number)
  return `${fy}년 ${fm}월 ${fd}일 ~ ${tm}월 ${td}일`
}

function won(n: number): string {
  return `${n.toLocaleString()}원`
}

/** 좁은 차트 막대 라벨용 - 큰 숫자를 "11만원"처럼 줄인다. 정확한 값은 won()과 hover/클릭 상세에 있다. */
function wonCompact(n: number): string {
  if (Math.abs(n) >= 10000) {
    const man = n / 10000
    const rounded = Math.round(man * 10) / 10
    return `${Number.isInteger(rounded) ? rounded : rounded.toFixed(1)}만원`
  }
  return won(n)
}

const PREV_PERIOD_LABEL: Record<SettlementPeriodType, string> = {
  DAY: '전일',
  WEEK: '전주',
  MONTH: '전월',
  YEAR: '전년',
}

/** 실제 오늘 기준 고정 범위 - anchor 가 바뀌어도 선택지 목록이 흔들리지 않게 한다. */
function yearOptions(): number[] {
  const center = new Date().getFullYear()
  const years: number[] = []
  for (let y = center - 10; y <= center + 2; y++) years.push(y)
  return years
}

export default function SettlementDashboard() {
  const [period, setPeriod] = useState<SettlementPeriodType>('MONTH')
  const [anchor, setAnchor] = useState(todayStr())
  const [data, setData] = useState<AdminSettlementResponse | null>(null)
  const [prevData, setPrevData] = useState<AdminSettlementResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedDay, setSelectedDay] = useState<string | null>(null)
  const [pickerOpen, setPickerOpen] = useState(false)
  // DAY 는 그 날 하루 buckets 뿐이라 달력을 못 채운다 - WEEK·MONTH·YEAR와 같은 모양의
  // "그 달 전체" 달력을 보여주려고 따로 받는다(클릭 동작은 미리보기로 동일하게 맞춘다).
  const [calendarMonth, setCalendarMonth] = useState<AdminSettlementResponse | null>(null)

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true)
    setError('')
    setSelectedDay(null)
    Promise.all([
      settlementApi.getSettlement(period, anchor, true),
      settlementApi.getSettlement(period, shiftAnchor(period, anchor, -1)),
      period === 'DAY' ? settlementApi.getSettlement('MONTH', anchor) : Promise.resolve(null),
    ])
      .then(([curr, prev, month]) => {
        setData(curr)
        setPrevData(prev)
        setCalendarMonth(month)
      })
      .catch(() => setError('정산 데이터를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [period, anchor])

  // DAY 는 data.buckets 에 그 날 하루뿐이라 달력을 못 채운다 - calendarMonth(그 달 전체)를
  // 달력용 데이터로 쓴다. 클릭 동작은 WEEK·MONTH·YEAR와 똑같이 "미리보기"다 -
  // 위 매출/환불/순매출/수수료 숫자는 그대로 두고 아래 집계 패널만 바뀐다.
  const calendarBuckets = period === 'DAY' && calendarMonth ? calendarMonth.buckets : data?.buckets ?? []
  const calendarFrom = period === 'DAY' && calendarMonth ? calendarMonth.from : data?.from ?? anchor
  const calendarPeriod: SettlementPeriodType = period === 'DAY' ? 'MONTH' : period

  const selectedBucket = selectedDay ? calendarBuckets.find(b => b.label === selectedDay) ?? null : null

  const statTiles = data && (
    <>
      <StatTile label="매출" value={data.totalRevenue} prev={prevData?.totalRevenue} bg="var(--blue-l)" fg="var(--blue)" />
      <StatTile label="환불" value={data.totalRefund} prev={prevData?.totalRefund} bg="var(--red-l)" fg="var(--red)" />
      <StatTile label="순매출" value={data.netRevenue} prev={prevData?.netRevenue} bg="var(--green-l)" fg="var(--green)" />
      <StatTile
        label={`수수료 수익 (${Math.round(data.feeRate * 100)}%)`}
        value={data.platformFee}
        prev={prevData?.platformFee}
        bg="var(--yellow-l)"
        fg="var(--yellow)"
      />
    </>
  )

  return (
    <div className="card" style={{ padding: 32, marginBottom: 24 }}>
      <h2 style={{ fontSize: 16, fontWeight: 700, color: 'var(--text)', marginBottom: 4 }}>
        전체 정산 대시보드
      </h2>
      <p style={{ fontSize: 13, color: 'var(--sub)', marginBottom: 20 }}>
        결제 완료 기준으로 집계된 매출·환불 현황입니다.
      </p>

      <div style={{ display: 'flex', gap: 6, marginBottom: 16 }}>
        {PERIODS.map(p => (
          <button
            key={p.value}
            type="button"
            className={`btn btn-sm ${period === p.value ? 'btn-primary' : 'btn-secondary'}`}
            onClick={() => { setPeriod(p.value); setPickerOpen(false) }}
          >
            {p.label}
          </button>
        ))}
      </div>

      {error && <div className="alert alert-danger"><span>⚠</span><span>{error}</span></div>}

      {loading ? (
        <p style={{ fontSize: 13, color: 'var(--sub)' }}>불러오는 중...</p>
      ) : data && (
        <>
          {/* 날짜 이동 줄과 달력의 너비를 맞추려고 하나의 inline-flex 컬럼으로 묶는다 -
              둘 중 더 넓은 쪽 폭에 나머지가 맞춰져서, "오늘" 버튼 오른쪽 끝이 달력 오른쪽 끝과 나란해진다. */}
          <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 28, marginBottom: 8, alignItems: 'start' }}>
            <div style={{ display: 'inline-flex', flexDirection: 'column', gap: 10 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                <button type="button" className="btn btn-outline btn-sm" onClick={() => setAnchor(a => shiftAnchor(period, a, -1))}>
                  이전
                </button>
                <button
                  type="button"
                  className="settlement-period-label"
                  onClick={() => setPickerOpen(o => !o)}
                  title="눌러서 연도·월을 바로 선택합니다"
                >
                  {formatPeriodLabel(data.period, data.from, data.to)} <span style={{ fontSize: 11 }}>▾</span>
                </button>
                <button type="button" className="btn btn-outline btn-sm" onClick={() => setAnchor(a => shiftAnchor(period, a, 1))}>
                  다음
                </button>
                <button type="button" className="settlement-today-btn" onClick={() => setAnchor(todayStr())}>
                  오늘
                </button>
              </div>
              {pickerOpen && (
                <DatePicker period={period} anchor={anchor} onChange={setAnchor} onDone={() => setPickerOpen(false)} />
              )}
              <div>
                <h3 className="settlement-section-title">날짜별 매출 (클릭하면 그 구간 집계를 봅니다)</h3>
                <PeriodHeatmap period={calendarPeriod} from={calendarFrom} buckets={calendarBuckets} selected={selectedDay} onSelect={setSelectedDay} />
              </div>
            </div>
            <div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                {statTiles}
              </div>
              <p style={{ fontSize: 11, color: 'var(--sub)', marginTop: 8 }}>증감률은 {PREV_PERIOD_LABEL[period]} 대비입니다.</p>
            </div>
          </div>

          {data.aiSummary && (
            <div
              style={{
                display: 'flex',
                gap: 10,
                alignItems: 'flex-start',
                padding: '12px 16px',
                marginBottom: 20,
                background: 'var(--primary-l)',
                borderRadius: 8,
                fontSize: 13,
                color: 'var(--text)',
              }}
            >
              <span style={{ flexShrink: 0 }}>✨</span>
              <span>{data.aiSummary}</span>
            </div>
          )}

          {data.buckets.length > 1 && (
            <section style={{ marginBottom: 28 }}>
              <h3 className="settlement-section-title">매출 추이 (막대를 클릭하면 값을 봅니다)</h3>
              <TrendChart buckets={data.buckets} selected={selectedDay} onSelect={setSelectedDay} />
            </section>
          )}

          {selectedBucket && (
            <section style={{ marginBottom: 28 }}>
              <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--text)', marginBottom: 8 }}>
                {selectedBucket.label} 집계
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 12 }}>
                <StatTile label="매출" value={selectedBucket.revenue} bg="var(--blue-l)" fg="var(--blue)" compact />
                <StatTile label="환불" value={selectedBucket.refund} bg="var(--red-l)" fg="var(--red)" compact />
                <StatTile label="순매출" value={selectedBucket.net} bg="var(--green-l)" fg="var(--green)" compact />
                <StatTile
                  label="수수료 수익"
                  value={Math.round(selectedBucket.net * data.feeRate)}
                  bg="var(--yellow-l)"
                  fg="var(--yellow)"
                  compact
                />
              </div>
            </section>
          )}

          <div style={{ display: 'grid', gridTemplateColumns: 'minmax(200px, 320px) 1fr', gap: 32, marginBottom: 28 }}>
            <section>
              <h3 className="settlement-section-title">매출 구성</h3>
              <RevenueDonut reservation={data.reservationRevenue} promotion={data.promotionRevenue} />
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 12 }}>
                <LegendRow color="var(--teal)" label="박람회 예약" value={data.reservationRevenue} sub={`환불 ${won(data.reservationRefund)} · 결제 ${data.reservationPaidCount}건`} />
                <LegendRow color="#7C3AED" label="VIP 배너" value={data.promotionRevenue} sub={`환불 ${won(data.promotionRefund)} · 결제 ${data.promotionPaidCount}건`} />
              </div>
            </section>

            <section>
              <h3 className="settlement-section-title">박람회별 매출 Top 5</h3>
              <RankingList
                items={data.topExpos.map(e => ({ key: String(e.expoId), label: e.title || `#${e.expoId}`, value: e.revenue }))}
                color="var(--teal)"
                linkTo={key => `/expos/${key}`}
              />
            </section>
          </div>

          <section>
            <h3 className="settlement-section-title">카테고리별 매출</h3>
            <RankingList
              items={data.topCategories.map(c => ({ key: c.category, label: c.category, value: c.revenue }))}
              color="var(--primary)"
            />
          </section>
        </>
      )}
    </div>
  )
}

/**
 * 기간 라벨을 눌렀을 때 뜨는 빠른 이동 상자. 이전/다음만으로는 몇 년 전 달로
 * 가려면 수십 번 눌러야 해서, 연도(+월)를 바로 골라 anchor 를 옮긴다.
 */
function DatePicker({ period, anchor, onChange, onDone }: {
  period: SettlementPeriodType
  anchor: string
  onChange: (next: string) => void
  onDone: () => void
}) {
  const [y, m] = anchor.split('-').map(Number)
  const years = yearOptions()

  if (period === 'YEAR') {
    return (
      <div className="settlement-date-picker">
        <select
          className="settlement-date-picker-select"
          value={y}
          onChange={e => { onChange(`${e.target.value}-01-01`); onDone() }}
        >
          {years.map(yr => <option key={yr} value={yr}>{yr}년</option>)}
        </select>
      </div>
    )
  }

  if (period === 'MONTH') {
    return (
      <div className="settlement-date-picker">
        <select
          className="settlement-date-picker-select"
          value={y}
          onChange={e => onChange(`${e.target.value}-${String(m).padStart(2, '0')}-01`)}
        >
          {years.map(yr => <option key={yr} value={yr}>{yr}년</option>)}
        </select>
        <select
          className="settlement-date-picker-select"
          value={m}
          onChange={e => { onChange(`${y}-${String(e.target.value).padStart(2, '0')}-01`); onDone() }}
        >
          {Array.from({ length: 12 }, (_, i) => i + 1).map(mo => <option key={mo} value={mo}>{mo}월</option>)}
        </select>
      </div>
    )
  }

  // DAY · WEEK - 특정 날짜 하나를 고르면 되니 네이티브 날짜 입력이 제일 간단하다.
  return (
    <div className="settlement-date-picker">
      <input
        type="date"
        className="settlement-date-picker-select"
        value={anchor}
        onChange={e => { if (e.target.value) { onChange(e.target.value); onDone() } }}
      />
    </div>
  )
}

function StatTile({ label, value, prev, bg, fg, compact }: {
  label: string
  value: number
  prev?: number
  bg: string
  fg: string
  compact?: boolean
}) {
  return (
    <div style={{ padding: compact ? 12 : 16, background: bg, borderRadius: 8 }}>
      <div style={{ fontSize: 12, color: fg, fontWeight: 600, marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: compact ? 15 : 18, fontWeight: 700, color: fg, display: 'flex', alignItems: 'baseline', gap: 6 }}>
        {value.toLocaleString()}원
        {prev !== undefined && <ChangeBadge curr={value} prev={prev} />}
      </div>
    </div>
  )
}

function ChangeBadge({ curr, prev }: { curr: number; prev: number }) {
  if (prev === 0) {
    if (curr === 0) return null
    return <span className="settlement-change-badge up">신규</span>
  }
  const pct = Math.round(((curr - prev) / Math.abs(prev)) * 100)
  if (pct === 0) return <span className="settlement-change-badge flat">–0%</span>
  const up = pct > 0
  return (
    <span className={`settlement-change-badge ${up ? 'up' : 'down'}`}>
      {up ? '▲' : '▼'}{Math.abs(pct)}%
    </span>
  )
}

function TrendChart({ buckets, selected, onSelect }: {
  buckets: SettlementBucket[]
  selected: string | null
  onSelect: (label: string) => void
}) {
  const [hovered, setHovered] = useState<string | null>(null)
  const width = 800
  const height = 176
  const topPad = 28
  const max = Math.max(...buckets.map(b => b.revenue), 1)
  const barSlot = width / buckets.length
  const barWidth = Math.max(barSlot * 0.62, 2)
  const maxIdx = buckets.reduce((best, b, i) => (b.revenue > buckets[best].revenue ? i : best), 0)

  const labelIdx = new Set([0, buckets.length - 1, Math.floor((buckets.length - 1) / 2)])
  // 막대가 많을수록(월간 최대 31개) 지금 보는 게 어디쯤인지 구분이 안 돼서, 몇 개마다 세로선을 그어 기준점을 준다.
  const gridEvery = buckets.length > 15 ? 5 : buckets.length > 7 ? 3 : 0

  const previewLabel = hovered ?? selected
  const previewBucket = buckets.find(b => b.label === previewLabel)

  return (
    <div>
      <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--text)', minHeight: 18, marginBottom: 4 }}>
        {previewBucket
          ? `${previewBucket.label} · 매출 ${won(previewBucket.revenue)} · 환불 ${won(previewBucket.refund)} · 순매출 ${won(previewBucket.net)}`
          : ' '}
      </div>
      <svg
        viewBox={`0 0 ${width} ${height}`}
        style={{ width: '100%', height: 'auto', display: 'block', cursor: 'pointer' }}
        preserveAspectRatio="xMidYMid meet"
        onMouseLeave={() => setHovered(null)}
      >
        {gridEvery > 0 && buckets.map((b, i) => (
          i % gridEvery === 0 && i !== 0 ? (
            <line key={`grid-${b.label}`} x1={i * barSlot} y1={0} x2={i * barSlot} y2={height - 16} stroke="var(--border)" strokeWidth={1} />
          ) : null
        ))}
        {buckets.map((b, i) => {
          const barHeight = (b.revenue / max) * (height - topPad)
          const x = i * barSlot + (barSlot - barWidth) / 2
          const y = height - barHeight
          const isSelected = selected === b.label
          const isHovered = hovered === b.label
          return (
            <g key={b.label} onClick={() => onSelect(b.label)} onMouseEnter={() => setHovered(b.label)}>
              {/* 클릭·hover 판정 영역은 막대 전체 높이 - 값이 작은 막대는 얇아서 막대만으론 누르기 어렵다 */}
              <rect x={i * barSlot} y={0} width={barSlot} height={height} fill="transparent" />
              <rect
                x={x} y={y} width={barWidth} height={Math.max(barHeight, 1)} rx={Math.min(2, barWidth / 2)}
                fill={isSelected ? 'var(--primary)' : 'var(--blue)'}
                opacity={isHovered && !isSelected ? 0.75 : 1}
              />
              {(i === maxIdx || isSelected || isHovered) && b.revenue > 0 && (
                <text
                  x={x + barWidth / 2}
                  y={Math.max(y - 6, 12)}
                  textAnchor="middle"
                  fontSize="11"
                  fill={isSelected ? 'var(--primary)' : 'var(--sub)'}
                >
                  {wonCompact(b.revenue)}
                </text>
              )}
            </g>
          )
        })}
      </svg>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 4 }}>
        {buckets.map((b, i) => (
          <span key={b.label} style={{ fontSize: 10, color: 'var(--sub)', visibility: labelIdx.has(i) ? 'visible' : 'hidden' }}>
            {b.label.slice(5)}
          </span>
        ))}
      </div>
    </div>
  )
}

/**
 * 기간별 날짜 상자 - 전부 같은 buckets 배열로 그린다(추가 API 호출 없음).
 * MONTH 만 진짜 달력(요일 정렬) 모양이고, 나머지는 요일 정렬이 의미가 없어서
 * (WEEK 는 월~일이라 일요일이 첫 칸에 와 순서가 헷갈리고, YEAR 는 달 단위라 요일 자체가 없다)
 * 칸 안에 라벨을 직접 적는 단순한 가로 배치로 통일한다.
 */
function PeriodHeatmap({ period, from, buckets, selected, onSelect }: {
  period: SettlementPeriodType
  from: string
  buckets: SettlementBucket[]
  selected: string | null
  onSelect: (label: string) => void
}) {
  const max = Math.max(...buckets.map(b => b.revenue), 1)

  function cellBg(revenue: number): string | undefined {
    if (revenue <= 0) return undefined
    const opacity = 0.12 + 0.78 * (revenue / max)
    return `rgba(232,56,13,${opacity.toFixed(2)})`
  }

  if (period === 'MONTH') {
    const [y, m] = from.split('-').map(Number)
    const firstWeekday = new Date(y, m - 1, 1).getDay()
    const daysInMonth = new Date(y, m, 0).getDate()
    const byLabel = new Map(buckets.map(b => [b.label, b]))

    const cells: Array<{ day: number; label: string } | null> = []
    for (let i = 0; i < firstWeekday; i++) cells.push(null)
    for (let d = 1; d <= daysInMonth; d++) {
      cells.push({ day: d, label: `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}` })
    }

    return (
      <div className="settlement-heatmap-grid">
        {WEEKDAYS.map(w => (
          <div key={w} className="settlement-heatmap-weekday">{w}</div>
        ))}
        {cells.map((cell, i) => {
          if (!cell) return <div key={`empty-${i}`} />
          const revenue = byLabel.get(cell.label)?.revenue ?? 0
          return (
            <button
              key={cell.label}
              type="button"
              className={`settlement-heatmap-cell${selected === cell.label ? ' selected' : ''}`}
              style={{ background: cellBg(revenue) }}
              onClick={() => onSelect(cell.label)}
              title={`${cell.day}일 매출 ${won(revenue)}`}
            >
              {cell.day}
            </button>
          )
        })}
      </div>
    )
  }

  if (period === 'YEAR') {
    return (
      <div className="settlement-heatmap-grid" style={{ gridTemplateColumns: 'repeat(6, 1fr)' }}>
        {buckets.map(b => {
          const monthNum = Number(b.label.slice(5))
          return (
            <button
              key={b.label}
              type="button"
              className={`settlement-heatmap-cell flat${selected === b.label ? ' selected' : ''}`}
              style={{ background: cellBg(b.revenue) }}
              onClick={() => onSelect(b.label)}
              title={`${monthNum}월 매출 ${won(b.revenue)}`}
            >
              {monthNum}월
            </button>
          )
        })}
      </div>
    )
  }

  // DAY · WEEK - 요일을 칸 안에 같이 적는다(공유 헤더에 맞춰 정렬하면 월~일 범위라 일요일이 맨 앞으로 와 헷갈린다).
  return (
    <div className="settlement-heatmap-grid" style={{ gridTemplateColumns: `repeat(${buckets.length}, 1fr)` }}>
      {buckets.map(b => {
        const d = parseDateStr(b.label)
        return (
          <button
            key={b.label}
            type="button"
            className={`settlement-heatmap-cell flat${selected === b.label ? ' selected' : ''}`}
            style={{ background: cellBg(b.revenue) }}
            onClick={() => onSelect(b.label)}
            title={`${b.label} 매출 ${won(b.revenue)}`}
          >
            <div style={{ fontSize: 9, opacity: 0.7 }}>{WEEKDAYS[d.getDay()]}</div>
            <div>{d.getDate()}</div>
          </button>
        )
      })}
    </div>
  )
}

function RevenueDonut({ reservation, promotion }: { reservation: number; promotion: number }) {
  const total = reservation + promotion
  const r = 40
  const c = 2 * Math.PI * r

  if (total <= 0) {
    return (
      <svg viewBox="0 0 100 100" width={120} height={120}>
        <circle cx="50" cy="50" r={r} fill="none" stroke="var(--gray2)" strokeWidth="14" />
      </svg>
    )
  }

  const resLen = (reservation / total) * c
  return (
    <svg viewBox="0 0 100 100" width={120} height={120}>
      <circle cx="50" cy="50" r={r} fill="none" stroke="#7C3AED" strokeWidth="14">
        <title>{`VIP 배너 매출 ${won(promotion)} (${Math.round((promotion / total) * 100)}%)`}</title>
      </circle>
      <circle
        cx="50" cy="50" r={r} fill="none" stroke="var(--teal)" strokeWidth="14"
        strokeDasharray={`${resLen} ${c}`}
        transform="rotate(-90 50 50)"
      >
        <title>{`박람회 예약 매출 ${won(reservation)} (${Math.round((reservation / total) * 100)}%)`}</title>
      </circle>
    </svg>
  )
}

function LegendRow({ color, label, value, sub }: { color: string; label: string; value: number; sub: string }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8, fontSize: 12 }}>
      <span style={{ width: 10, height: 10, borderRadius: 3, background: color, marginTop: 3, flexShrink: 0 }} />
      <div>
        <div style={{ fontWeight: 600, color: 'var(--text)' }}>{label} {won(value)}</div>
        <div style={{ color: 'var(--sub)', fontSize: 11 }}>{sub}</div>
      </div>
    </div>
  )
}

function RankingList({ items, color, linkTo }: {
  items: { key: string; label: string; value: number }[]
  color: string
  linkTo?: (key: string) => string
}) {
  const navigate = useNavigate()
  if (items.length === 0) {
    return <p style={{ fontSize: 12, color: 'var(--sub)' }}>데이터가 없습니다.</p>
  }
  const max = Math.max(...items.map(i => i.value), 1)
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
      {items.map(item => (
        <div key={item.key} style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div
            className={linkTo ? 'settlement-rank-label linkable' : 'settlement-rank-label'}
            onClick={linkTo ? () => navigate(linkTo(item.key)) : undefined}
            title={linkTo ? `${item.label} - 클릭하면 상세 페이지로 이동합니다` : item.label}
          >
            {item.label}
          </div>
          <div style={{ flex: 1, background: 'var(--gray2)', borderRadius: 6, height: 16, overflow: 'hidden' }}>
            <div style={{ width: `${(item.value / max) * 100}%`, height: '100%', background: color, borderRadius: 6 }} />
          </div>
          <div style={{ width: 100, fontSize: 12, fontWeight: 600, color: 'var(--text)', textAlign: 'right', flexShrink: 0 }}>
            {won(item.value)}
          </div>
        </div>
      ))}
    </div>
  )
}
