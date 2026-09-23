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

export default function SettlementDashboard() {
  const [period, setPeriod] = useState<SettlementPeriodType>('MONTH')
  const [anchor, setAnchor] = useState(todayStr())
  const [data, setData] = useState<AdminSettlementResponse | null>(null)
  const [prevData, setPrevData] = useState<AdminSettlementResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selectedDay, setSelectedDay] = useState<string | null>(null)

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setLoading(true)
    setError('')
    setSelectedDay(null)
    Promise.all([
      settlementApi.getSettlement(period, anchor),
      settlementApi.getSettlement(period, shiftAnchor(period, anchor, -1)),
    ])
      .then(([curr, prev]) => {
        setData(curr)
        setPrevData(prev)
      })
      .catch(() => setError('정산 데이터를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [period, anchor])

  const selectedBucket = selectedDay ? data?.buckets.find(b => b.label === selectedDay) ?? null : null

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
            onClick={() => setPeriod(p.value)}
          >
            {p.label}
          </button>
        ))}
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 24 }}>
        <button type="button" className="btn btn-outline btn-sm" onClick={() => setAnchor(a => shiftAnchor(period, a, -1))}>
          이전
        </button>
        <span style={{ fontSize: 15, fontWeight: 700, color: 'var(--text)', minWidth: 160, textAlign: 'center' }}>
          {data ? formatPeriodLabel(data.period, data.from, data.to) : ' '}
        </span>
        <button type="button" className="btn btn-outline btn-sm" onClick={() => setAnchor(a => shiftAnchor(period, a, 1))}>
          다음
        </button>
        <button type="button" className="settlement-today-btn" onClick={() => setAnchor(todayStr())}>
          오늘
        </button>
      </div>

      {error && <div className="alert alert-danger"><span>⚠</span><span>{error}</span></div>}

      {loading ? (
        <p style={{ fontSize: 13, color: 'var(--sub)' }}>불러오는 중...</p>
      ) : data && (
        <>
          {period === 'MONTH' ? (
            <div style={{ display: 'grid', gridTemplateColumns: 'auto 1fr', gap: 28, marginBottom: 28, alignItems: 'start' }}>
              <section>
                <h3 className="settlement-section-title">날짜별 매출 (클릭하면 그 날 집계를 봅니다)</h3>
                <MonthHeatmap from={data.from} buckets={data.buckets} selected={selectedDay} onSelect={setSelectedDay} />
              </section>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {statTiles}
                <p style={{ fontSize: 11, color: 'var(--sub)' }}>증감률은 {PREV_PERIOD_LABEL[period]} 대비입니다.</p>
              </div>
            </div>
          ) : (
            <>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))', gap: 16, marginBottom: 6 }}>
                {statTiles}
              </div>
              <p style={{ fontSize: 11, color: 'var(--sub)', marginBottom: 22 }}>
                증감률은 {PREV_PERIOD_LABEL[period]} 대비입니다.
              </p>
            </>
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

function MonthHeatmap({ from, buckets, selected, onSelect }: {
  from: string
  buckets: SettlementBucket[]
  selected: string | null
  onSelect: (label: string) => void
}) {
  const [y, m] = from.split('-').map(Number)
  const firstWeekday = new Date(y, m - 1, 1).getDay()
  const daysInMonth = new Date(y, m, 0).getDate()
  const byLabel = new Map(buckets.map(b => [b.label, b]))
  const max = Math.max(...buckets.map(b => b.revenue), 1)

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
        const opacity = revenue > 0 ? 0.12 + 0.78 * (revenue / max) : 0
        return (
          <button
            key={cell.label}
            type="button"
            className={`settlement-heatmap-cell${selected === cell.label ? ' selected' : ''}`}
            style={{ background: revenue > 0 ? `rgba(232,56,13,${opacity.toFixed(2)})` : undefined }}
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
