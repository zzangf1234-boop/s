@echo off
rem ============================================================
rem  DID number call - network receipt receiver (TEST version)
rem  Acts like a network receipt printer (raw TCP, port 9100).
rem  Point the existing splitter's network output to THIS PC's IP : 9100.
rem  Shows the order number of every receipt and saves raw data on the Desktop.
rem  Close this window = receiver stops.
rem ============================================================
set "PORT=9100"
title DID Receipt Receiver (TEST) - port %PORT%
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t=(Get-Content -LiteralPath '%~f0' -Raw -Encoding UTF8) -split '#DIDCALL_PS#'; iex $t[-1]"
pause
exit /b
#DIDCALL_PS#
$ErrorActionPreference = 'Stop'
$port = [int]$env:PORT
$logDir = Join-Path ([Environment]::GetFolderPath('Desktop')) 'DID-영수증-기록'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$enc = [System.Text.Encoding]::GetEncoding(949)

# 영수증 한 장 분량: 원본 저장 + 글자로 바꿔 '주문번호' 찾기
function Handle-Receipt([byte[]]$bytes, [string]$from) {
  $stamp = Get-Date -Format 'yyyyMMdd_HHmmss_fff'
  [System.IO.File]::WriteAllBytes((Join-Path $logDir "$stamp.bin"), $bytes)
  $clean = New-Object byte[] $bytes.Length
  for ($i = 0; $i -lt $bytes.Length; $i++) {
    $c = $bytes[$i]
    if ($c -lt 0x20 -and $c -ne 0x0A) { $clean[$i] = 0x20 } else { $clean[$i] = $c }
  }
  $text = $enc.GetString($clean)
  [System.IO.File]::WriteAllText((Join-Path $logDir "$stamp.txt"), $text, [System.Text.Encoding]::UTF8)
  $m = [regex]::Match($text, '주\s*문\s*번\s*호\s*[:：]+\s*(\d{1,6})')
  $time = Get-Date -Format 'HH:mm:ss'
  if ($m.Success) {
    Write-Host ("{0}  주문번호 {1}  감지   (보낸 곳 {2}, {3}바이트)" -f $time, $m.Groups[1].Value, $from, $bytes.Length) -ForegroundColor Green
  } else {
    Write-Host ("{0}  영수증 {1}바이트 받음 - 주문번호는 못 찾음 (보낸 곳 {2}, 기록: {3}.txt)" -f $time, $bytes.Length, $from, $stamp) -ForegroundColor DarkYellow
  }
}

# 이 PC의 같은 공유기 주소 표시
$ips = @()
try {
  $ips = [System.Net.Dns]::GetHostAddresses([System.Net.Dns]::GetHostName()) |
    Where-Object { $_.AddressFamily -eq 'InterNetwork' -and $_.ToString() -notlike '127.*' -and $_.ToString() -notlike '169.254.*' } |
    ForEach-Object { $_.ToString() }
} catch {}

Write-Host ''
Write-Host ' DID 네트워크 영수증 수신 - 시험판' -ForegroundColor Yellow
Write-Host ' 기존 분기 프로그램의 네트워크 출력(프린터 주소)을 아래 주소로 맞춰 주세요:' -ForegroundColor Yellow
foreach ($ip in $ips) { Write-Host ("     {0} : {1}" -f $ip, $port) -ForegroundColor Cyan }
Write-Host (' 기록 폴더: {0}' -f $logDir)
Write-Host ' 처음 실행 시 "Windows 보안 경고(방화벽)"가 뜨면 [액세스 허용]을 눌러 주세요.'
Write-Host ''

try {
  $listener = New-Object System.Net.Sockets.TcpListener ([System.Net.IPAddress]::Any), $port
  $listener.Start()
} catch {
  Write-Host (' {0}번 포트를 열 수 없습니다: {1}' -f $port, $_.Exception.Message) -ForegroundColor Red
  Write-Host ' 이 PC에 이미 9100번을 쓰는 프로그램(네트워크 프린터 공유 등)이 있는지 확인하세요.' -ForegroundColor Red
  return
}
Write-Host (' 대기 중... (포트 {0})' -f $port) -ForegroundColor Cyan

while ($true) {
  $client = $listener.AcceptTcpClient()
  $from = $client.Client.RemoteEndPoint.ToString()
  $stream = $client.GetStream()
  $stream.ReadTimeout = 1500
  $buf = New-Object System.IO.MemoryStream
  $chunk = New-Object byte[] 8192
  try {
    while ($true) {
      $r = $stream.Read($chunk, 0, $chunk.Length)
      if ($r -le 0) { break }
      $buf.Write($chunk, 0, $r)
      # 프린터 상태 물음(DLE EOT n)에 '정상'으로 답해 줌 (분기 프로그램이 기다리지 않게)
      for ($i = 0; $i -lt $r - 2; $i++) {
        if ($chunk[$i] -eq 0x10 -and $chunk[$i + 1] -eq 0x04) {
          $n = $chunk[$i + 2]
          if ($n -eq 1) { $stream.WriteByte(0x16) } else { $stream.WriteByte(0x12) }
        }
      }
    }
  } catch {
    # 1.5초 동안 더 오는 게 없으면 한 장 끝으로 봄
  }
  try { $client.Close() } catch {}
  if ($buf.Length -gt 0) {
    try { Handle-Receipt $buf.ToArray() $from } catch { Write-Host (' 처리 오류: {0}' -f $_.Exception.Message) -ForegroundColor Red }
  } else {
    Write-Host ("{0}  연결만 확인됨 (보낸 곳 {1}, 내용 없음)" -f (Get-Date -Format 'HH:mm:ss'), $from) -ForegroundColor DarkGray
  }
}
