# #79 티켓 발급 통지 E2E 검증
# 사전: docker compose up -d (mysql, nginx) + identity/expo/reservation/ticket 4개 기동
# 사용: .\e2e-ticket-issue.ps1 -AdminPassword '<SUPER_ADMIN 비밀번호>'

param(
    [Parameter(Mandatory = $true)][string] $AdminPassword,
    [string] $AdminEmail    = 'admin@team1.local',
    [string] $InternalToken,                    # 미지정 시 application-local.yml 에서 읽는다
    [string] $DbPassword,                       # 미지정 시 .env 의 DB_ROOT_PASSWORD
    [string] $MysqlContainer = 'fpt1-mysql',
    [string] $IdentityUrl      = 'http://localhost:8081',
    [string] $ExpoUrl          = 'http://localhost:8082',
    [string] $ReservationUrl   = 'http://localhost:8083',
    [string] $TicketUrl        = 'http://localhost:8084'
)

$ErrorActionPreference = 'Stop'
$stamp = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

function ReadYamlToken($path) {
    if (-not (Test-Path $path)) { return $null }
    $m = Select-String -Path $path -Pattern '^\s+token:\s*(\S+)\s*$'
    if ($m) { return $m.Matches[0].Groups[1].Value.Trim('"').Trim("'") }
    return $null
}

function Post($url, $body, $token) {
    $headers = @{ 'Content-Type' = 'application/json; charset=utf-8' }
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    $json = ($body | ConvertTo-Json -Depth 5 -Compress)
    try {
        Invoke-RestMethod -Method Post -Uri $url -Headers $headers `
            -Body ([Text.Encoding]::UTF8.GetBytes($json))
    } catch {
        # 상태 코드만 보면 원인을 못 찾는다. 서버가 돌려준 본문을 그대로 보여준다.
        Write-Host "`n  POST $url" -ForegroundColor Yellow
        Write-Host "  보낸 것: $json" -ForegroundColor DarkGray
        $resp = $_.Exception.Response
        if ($resp) {
            $reader = New-Object IO.StreamReader($resp.GetResponseStream(), [Text.Encoding]::UTF8)
            Write-Host "  받은 것: $($reader.ReadToEnd())" -ForegroundColor Yellow
        }
        throw
    }
}
function Get_($url, $token) {
    $headers = @{}
    if ($token) { $headers['Authorization'] = "Bearer $token" }
    Invoke-RestMethod -Method Get -Uri $url -Headers $headers
}
function TicketIdFor($reservationId) {
    $q = "select id from tickets where reservation_id = $reservationId;"
    $rows = docker exec -e "MYSQL_PWD=$DbPassword" -i $MysqlContainer `
        mysql -uroot -N -B ticket -e $q 2>&1
    if ($LASTEXITCODE -ne 0) { Fail "DB 조회 실패: $rows" }

    $ids = @($rows | Where-Object { $_ -match '^\d+$' })
    if ($ids.Count -eq 0) {
        Fail "예약 $reservationId 의 티켓이 없다 — 통지가 안 나갔거나 실패했다. reservation-service 로그의 TICKET_ISSUE_FAILED 를 봐라"
    }
    if ($ids.Count -gt 1) { Fail "티켓이 $($ids.Count) 건이다 — 예약당 1건이어야 한다" }
    return [int]$ids[0]
}
function Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }
function Pass($msg)     { Write-Host "  PASS  $msg" -ForegroundColor Green }
function Fail($msg)     { Write-Host "  FAIL  $msg" -ForegroundColor Red; exit 1 }

# ── 준비: 관리자 → 주최자 → 채널 → 박람회 → 공개 → 회차(무료) ────────────────
Step 1 '관리자 로그인'
$admin = Post "$IdentityUrl/api/v1/auth/login" @{ email = $AdminEmail; password = $AdminPassword }
$adminToken = $admin.data.accessToken
Pass 'SUPER_ADMIN 토큰 확보'

Step 2 '주최자 생성 + 로그인'
$orgEmail = "organizer+$stamp@example.com"
Post "$IdentityUrl/api/v1/admin/organizers" `
    @{ email = $orgEmail; password = 'Passw0rd123'; name = "주최자$stamp" } $adminToken | Out-Null
$org = Post "$IdentityUrl/api/v1/auth/login" @{ email = $orgEmail; password = 'Passw0rd123' }
$orgToken = $org.data.accessToken
Pass $orgEmail

Step 3 '채널 → 박람회 (아직 HIDDEN)'
$channel = Post "$ExpoUrl/api/v1/channels" @{ name = "채널$stamp"; description = 'e2e' } $orgToken
$channelId = $channel.data.channelId
if (-not $channelId) { $channelId = $channel.data.id }

$expoRes = Post "$ExpoUrl/api/v1/channels/$channelId/expos" `
    @{ title = "E2E 박람회 $stamp"; description = 'e2e'; venue = 'COEX'; region = '서울'; category = 'IT·전자' } $orgToken
$expoId = $expoRes.data.expoId
if (-not $expoId) { $expoId = $expoRes.data.id }
Pass "channelId=$channelId expoId=$expoId"

# 공개는 회차가 하나라도 있어야 한다(ExpoPublicationService: HIDDEN + 회차 없음 → 400).
# 그래서 회차를 먼저 만들고 공개한다.
Step 4 '무료 회차 생성 (fee=0 → 예약 즉시 CONFIRMED) → 박람회 공개'
$starts = [DateTime]::UtcNow.AddDays(1).ToString('yyyy-MM-ddTHH:mm:ssZ')
$ends   = [DateTime]::UtcNow.AddDays(1).AddHours(4).ToString('yyyy-MM-ddTHH:mm:ssZ')
$roundRes = Post "$ReservationUrl/api/v1/expos/$expoId/rounds" `
    @{ startsAt = $starts; endsAt = $ends; capacity = 50; fee = 0 } $orgToken
$roundId = $roundRes.data.roundId
if (-not $roundId) { $roundId = $roundRes.data.id }

Post "$ExpoUrl/api/v1/expos/$expoId/publication" @{} $orgToken | Out-Null
Pass "roundId=$roundId (무료), expoId=$expoId PUBLISHED"

Step 5 '회원 가입 + 로그인'
$userEmail = "member+$stamp@example.com"
$signup = Post "$IdentityUrl/api/v1/auth/signup" `
    @{ email = $userEmail; password = 'Passw0rd123'; name = "회원$stamp" }
$userId = $signup.data.userId
$member = Post "$IdentityUrl/api/v1/auth/login" @{ email = $userEmail; password = 'Passw0rd123' }
$memberToken = $member.data.accessToken
Pass "$userEmail (userId=$userId)"

# ── 검증 ────────────────────────────────────────────────────────────────────
Step 6 '예약 생성 → 무료라서 즉시 CONFIRMED 여야 한다'
$res = Post "$ReservationUrl/api/v1/rounds/$roundId/reservations" `
    @{ headcount = 3; contactName = '홍길동'; contactPhone = '010-1234-5678' } $memberToken
$reservationId = $res.data.reservationId
if (-not $reservationId) { $reservationId = $res.data.id }
$status = $res.data.status
if ($status -ne 'CONFIRMED') { Fail "status=$status (CONFIRMED 예상)" }
Pass "reservationId=$reservationId status=CONFIRMED"

Step 7 '통지가 실제로 나갔는지 — DB 에 티켓 행이 있어야 한다'
Start-Sleep -Seconds 2   # 통지는 커밋 이후에 나간다
if (-not $DbPassword) {
    $envFile = Join-Path $PSScriptRoot '.env'
    $line = Select-String -Path $envFile -Pattern '^DB_ROOT_PASSWORD=(.*)$'
    if (-not $line) { Fail '.env 에서 DB_ROOT_PASSWORD 를 못 읽었다. -DbPassword 로 넘겨줘' }
    $DbPassword = $line.Matches[0].Groups[1].Value.Trim().Trim('"').Trim("'")
}

# 이 확인이 핵심이다. POST 로 먼저 찔러보면 스크립트가 만든 티켓인지
# 통지가 만든 티켓인지 구별할 수 없다.
$dbTicketId = TicketIdFor $reservationId
Pass "통지 성공. tickets 에 1건 (ticketId=$dbTicketId)"

Step 8 '멱등 — 다시 호출해도 같은 티켓을 돌려줘야 한다'
if (-not $InternalToken) {
    $InternalToken = ReadYamlToken (Join-Path $PSScriptRoot 'backend\reservation-service\src\main\resources\application-local.yml')
    if (-not $InternalToken) { Fail 'internal token 을 못 읽었다. -InternalToken 으로 넘겨줘' }
}
$again = Post "$TicketUrl/internal/v1/tickets" `
    @{ reservationId = $reservationId; expoId = $expoId; roundId = $roundId
       userId = $userId; headcount = 3 } $InternalToken

if (-not $again.ticketId) { Fail '응답에 ticketId 가 없다 — 봉투가 다시 붙었거나 필드명이 다르다' }
if ($again.ticketId -ne $dbTicketId) {
    Fail "ticketId 가 다르다: DB=$dbTicketId 재호출=$($again.ticketId) (reservation_id UNIQUE 가 안 걸렸다)"
}
Pass "동일 ticketId=$($again.ticketId) — 재발급하지 않고 기존 티켓 반환"

# ── 유료 경로: applyOutcome SUCCESS ─────────────────────────────────────────
Step 9 '유료 회차 생성 (fee=10000)'
$paidRound = Post "$ReservationUrl/api/v1/expos/$expoId/rounds" `
    @{ startsAt = $starts; endsAt = $ends; capacity = 50; fee = 10000 } $orgToken
$paidRoundId = $paidRound.data.roundId
if (-not $paidRoundId) { $paidRoundId = $paidRound.data.id }
Pass "roundId=$paidRoundId (10000원)"

Step 10 '유료 예약 → PENDING 이어야 한다 (아직 확정 아님)'
$paidRes = Post "$ReservationUrl/api/v1/rounds/$paidRoundId/reservations" `
    @{ headcount = 2; contactName = '김철수'; contactPhone = '010-9876-5432' } $memberToken
$paidReservationId = $paidRes.data.reservationId
if (-not $paidReservationId) { $paidReservationId = $paidRes.data.id }
if ($paidRes.data.status -ne 'PENDING') { Fail "status=$($paidRes.data.status) (PENDING 예상)" }
Pass "reservationId=$paidReservationId status=PENDING paymentId=$($paidRes.data.paymentId)"

Step 11 '결제 확정 → CONFIRMED (MockPgClient 가 PAID 를 돌려준다)'
$confirm = Post "$ReservationUrl/api/v1/reservations/$paidReservationId/payment" @{} $memberToken
if ($confirm.data.status -ne 'CONFIRMED') { Fail "status=$($confirm.data.status) (CONFIRMED 예상)" }
Pass "status=CONFIRMED"

Step 12 '유료 경로도 통지가 나갔는지 — DB 확인'
Start-Sleep -Seconds 2
$paidTicketId = TicketIdFor $paidReservationId
Pass "통지 성공. tickets 에 1건 (ticketId=$paidTicketId)"

Write-Host "`n전부 통과." -ForegroundColor Green
Write-Host @"

--- 검증된 것 ---
  무료 경로: 예약 즉시 CONFIRMED → 통지 → 티켓 1건 (예약 $reservationId / 티켓 $dbTicketId)
  유료 경로: PENDING → 결제 확정 → 통지 → 티켓 1건 (예약 $paidReservationId / 티켓 $paidTicketId)
  멱등: 재호출해도 같은 ticketId
  티켓 조회를 HTTP 이전에 DB 로 했으므로, 그 행은 통지가 만든 것이다.

--- 아직 검증 안 된 것 ---
  fail-open: ticket-service 를 내린 채로 결제 확정하면 예약은 CONFIRMED 로 남고
             reservation-service 로그에 TICKET_ISSUE_FAILED 만 찍혀야 한다.
  #77 만료 배치: PENDING 예약이 expires_at + 2분 뒤 EXPIRED 로 바뀌고 정원이 돌아오는지.
"@ -ForegroundColor DarkGray
