@echo off
rem ============================================================
rem  DID number call - kiosk order splitter
rem  The kiosk program prints to virtual ports instead of the real printers:
rem     real COM1 : kiosk uses COM21, this program reads COM22
rem     real COM3 : kiosk uses COM23, this program reads COM24
rem  (virtual pairs COM21 + COM22 and COM23 + COM24 are made with com0com)
rem  This program
rem   1) passes everything to the real printers in both directions (printing works as before)
rem   2) sends a copy of every receipt / kitchen slip to the DID caller phone (port 9100)
rem  Settings below: change only the values after "=".
rem    ROUTES = list separated by spaces. Each item:
rem             readport, then the greater-than sign, then real printer port, then @speed
rem             or only a port with @speed = listen only (Y-cable tap: just copy to the phone, no forwarding)
rem    PHONE  = caller phone IP (empty = find automatically on the same Wi-Fi)
rem  Close this window = splitter stops (nothing prints). To undo: set kiosk printer ports back to COM1 / COM3.
rem ============================================================
set "ROUTES=COM22>COM1@9600 COM24>COM3@9600"
set "PHONE="
set "DID_SELF=%~f0"
title DID Order Splitter
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t=(Get-Content -LiteralPath '%~f0' -Raw -Encoding UTF8) -split '#DIDCALL_PS#'; iex $t[-1]"
pause
exit /b
#DIDCALL_PS#
$ErrorActionPreference = 'Stop'
$phoneFixed = ($env:PHONE + '').Trim()
# 경로: "COM22>COM1@9600" (가상 포트의 반대쪽 > 실제 프린터 @ 속도)
$routes = @()
foreach ($r in ($env:ROUTES -split '\s+')) {
  # "COM22>COM1@9600" = 가상 포트로 받아 실제 프린터로 넘기며 복사 / "COM5@9600" = 듣기만 (Y 케이블)
  if ($r -match '^(COM\d+)(>(COM\d+))?(@(\d+))?$') {
    $routes += [pscustomobject]@{ In = $matches[1]; Out = $(if ($matches[3]) { $matches[3] } else { '' }); Baud = $(if ($matches[5]) { [int]$matches[5] } else { 9600 });
      V = $null; P = $null; Buf = (New-Object System.IO.MemoryStream); Last = (Get-Date); Retry = (Get-Date); Ready = $false }
  } elseif ($r) { Write-Host (' 설정 형식이 틀렸습니다: {0}  (예: COM22>COM1@9600)' -f $r) -ForegroundColor Red }
}
$logDir = Join-Path ([Environment]::GetFolderPath('Desktop')) 'DID-주문분기-기록'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$enc = [System.Text.Encoding]::GetEncoding(949)

function Log([string]$msg, [string]$color = 'Gray') {
  $line = '{0}  {1}' -f (Get-Date -Format 'HH:mm:ss'), $msg
  Write-Host $line -ForegroundColor $color
  try { Add-Content -LiteralPath (Join-Path $logDir ((Get-Date -Format 'yyyy-MM-dd') + '.txt')) -Value $line -Encoding UTF8 } catch {}
}

# ── 윈도우 시작 시 자동 실행 (시작프로그램 폴더에 바로가기, 최소화로) ──
try {
  $startup = [Environment]::GetFolderPath('Startup')
  $lnk = Join-Path $startup 'DID 주문분기.lnk'
  $bat = (Get-ChildItem -LiteralPath $env:DID_SELF).FullName
  if (-not (Test-Path -LiteralPath $lnk)) {
    $ws = New-Object -ComObject WScript.Shell
    $s = $ws.CreateShortcut($lnk)
    $s.TargetPath = $bat
    $s.WorkingDirectory = Split-Path $bat
    $s.WindowStyle = 7
    $s.Save()
    Write-Host ' ✔ 윈도우를 켜면 자동으로 실행되도록 등록했습니다 (시작프로그램 폴더의 "DID 주문분기" 바로가기)' -ForegroundColor Green
    Write-Host '   자동 실행을 끄려면 Win+R → shell:startup → 그 바로가기를 지우세요.' -ForegroundColor DarkGray
  }
} catch {}

# ── 호출기 폰 찾기 (같은 와이파이의 DID 호출기 앱에 물어봄) ──
$script:phone = $null
function Find-Phone {
  if ($phoneFixed) { return $phoneFixed }
  $u = New-Object System.Net.Sockets.UdpClient
  try {
    $u.EnableBroadcast = $true
    $u.Client.ReceiveTimeout = 800
    $msg = [System.Text.Encoding]::UTF8.GetBytes('DIDCALL_DISCOVER')
    $targets = @([System.Net.IPAddress]::Broadcast)
    foreach ($ni in [System.Net.NetworkInformation.NetworkInterface]::GetAllNetworkInterfaces()) {
      if ($ni.OperationalStatus -ne 'Up') { continue }
      foreach ($ua in $ni.GetIPProperties().UnicastAddresses) {
        if ($ua.Address.AddressFamily -ne 'InterNetwork' -or $ua.IPv4Mask -eq $null) { continue }
        $a = $ua.Address.GetAddressBytes(); $m = $ua.IPv4Mask.GetAddressBytes()
        $b = New-Object byte[] 4
        for ($i = 0; $i -lt 4; $i++) { $b[$i] = $a[$i] -bor (255 -bxor $m[$i]) }
        $targets += New-Object System.Net.IPAddress (,$b)
      }
    }
    for ($try = 0; $try -lt 3; $try++) {
      foreach ($t in $targets) { try { [void]$u.Send($msg, $msg.Length, (New-Object System.Net.IPEndPoint $t, 8766)) } catch {} }
      try {
        $ep = New-Object System.Net.IPEndPoint ([System.Net.IPAddress]::Any), 0
        $r = $u.Receive([ref]$ep)
        if ([System.Text.Encoding]::UTF8.GetString($r).StartsWith('DIDCALL_HOST')) { return $ep.Address.ToString() }
      } catch {}
    }
  } finally { $u.Close() }
  return $null
}

# ── 폰으로 보내기 (실패하면 쌓아 두었다가 다시 보냄) ──
$pending = New-Object System.Collections.ArrayList
$script:nextRetry = Get-Date
function Send-Job([byte[]]$bytes) {
  if (-not $script:phone) { $script:phone = Find-Phone }
  if (-not $script:phone) { return $false }
  $hostName = $script:phone; $port = 9100
  if ($hostName -match '^(.+):(\d+)$') { $hostName = $matches[1]; $port = [int]$matches[2] }
  $c = New-Object System.Net.Sockets.TcpClient
  try {
    $ar = $c.BeginConnect($hostName, $port, $null, $null)
    if (-not $ar.AsyncWaitHandle.WaitOne(2000)) { throw 'timeout' }
    $c.EndConnect($ar)
    $s = $c.GetStream()
    $s.WriteTimeout = 3000
    $s.Write($bytes, 0, $bytes.Length)
    $s.Flush()
    Start-Sleep -Milliseconds 100
    return $true
  } catch {
    if (-not $phoneFixed) { $script:phone = $null }  # 주소가 바뀌었을 수 있으니 다음에 다시 찾기
    return $false
  } finally { try { $c.Close() } catch {} }
}

function Order-Number([byte[]]$bytes) {
  $clean = New-Object byte[] $bytes.Length
  for ($i = 0; $i -lt $bytes.Length; $i++) { $c = $bytes[$i]; if ($c -lt 0x20 -and $c -ne 0x0A) { $clean[$i] = 0x20 } else { $clean[$i] = $c } }
  $m = [regex]::Match($enc.GetString($clean), '주\s*문\s*번\s*호\s*[:：]*\s*(\d{1,6})')
  if ($m.Success) { return $m.Groups[1].Value } else { return '' }
}

function Handle-Job([byte[]]$bytes, [string]$port) {
  $stamp = Get-Date -Format 'yyyyMMdd_HHmmss_fff'
  try { [System.IO.File]::WriteAllBytes((Join-Path $logDir "$stamp`_$port.bin"), $bytes) } catch {}
  $n = Order-Number $bytes
  $what = if ($n) { "[$port] 주문번호 $n" } else { "[$port] 인쇄물" }
  if (Send-Job $bytes) {
    Log ("{0} → 호출기({1}) 전달  [{2}바이트]" -f $what, $script:phone, $bytes.Length) 'Green'
  } else {
    [void]$pending.Add($bytes)
    while ($pending.Count -gt 50) { $pending.RemoveAt(0) }
    Log ("{0} 인쇄는 됐지만 호출기에 못 보냄 → 잠시 뒤 다시 보냄 (대기 {1}장)" -f $what, $pending.Count) 'Yellow'
    $script:nextRetry = (Get-Date).AddSeconds(10)
  }
}

function Retry-Pending {
  if ($pending.Count -eq 0 -or (Get-Date) -lt $script:nextRetry) { return }
  while ($pending.Count -gt 0) {
    if (Send-Job $pending[0]) { $pending.RemoveAt(0); Log ("밀린 주문서 1장 호출기로 보냄 (남은 {0}장)" -f $pending.Count) 'Green' }
    else { $script:nextRetry = (Get-Date).AddSeconds(10); return }
  }
}

function Open-Port([string]$name, [int]$baud) {
  $p = New-Object System.IO.Ports.SerialPort $name, $baud, ([System.IO.Ports.Parity]::None), 8, ([System.IO.Ports.StopBits]::One)
  $p.Handshake = [System.IO.Ports.Handshake]::None
  $p.DtrEnable = $true
  $p.RtsEnable = $true
  $p.ReadTimeout = 50
  $p.WriteTimeout = 5000
  $p.Open()
  return $p
}

Write-Host ''
Write-Host ' DID 키오스크 주문 분기' -ForegroundColor Yellow
foreach ($rt in $routes) {
  if ($rt.Out) { Write-Host ('   키오스크 → (가상) → {0}  ==>  {1} 실제 프린터 ({2})  +  호출기 폰' -f $rt.In, $rt.Out, $rt.Baud) }
  else { Write-Host ('   {0} 듣기만 ({1})  ==>  호출기 폰' -f $rt.In, $rt.Baud) }
}
Write-Host (' 이 PC의 COM 포트: {0}' -f (([System.IO.Ports.SerialPort]::GetPortNames() | Sort-Object -Unique) -join ', '))
Write-Host (' 기록 폴더: {0}' -f $logDir)
Write-Host ' 이 창을 닫으면 영수증·주방주문서가 인쇄되지 않습니다. (최소화해 두세요)' -ForegroundColor Red
Write-Host ''
if ($routes.Count -eq 0) { Write-Host ' 설정(ROUTES)이 비어 있습니다.' -ForegroundColor Red; return }
$script:phone = Find-Phone
if ($script:phone) { Log ("호출기 폰 찾음: {0}" -f $script:phone) 'Cyan' }
else { Log '호출기 폰을 못 찾았습니다. 호출기 앱이 켜져 있고 같은 와이파이인지 확인하세요. (주문이 오면 다시 찾습니다)' 'Yellow' }

function Close-Route($rt) {
  try { if ($rt.V) { $rt.V.Close() } } catch {}
  try { if ($rt.P) { $rt.P.Close() } } catch {}
  $rt.V = $null; $rt.P = $null; $rt.Ready = $false
}

# 포트 여러 개를 번갈아 살핌. 한쪽에 문제가 생겨도 다른 쪽은 계속 동작
while ($true) {
  $did = $false
  foreach ($rt in $routes) {
    if (-not $rt.Ready) {
      if ((Get-Date) -lt $rt.Retry) { continue }
      try {
        if ($rt.V -eq $null) { $rt.V = Open-Port $rt.In $rt.Baud }
        if ($rt.Out -and $rt.P -eq $null) { $rt.P = Open-Port $rt.Out $rt.Baud }
        $rt.Ready = $true
        Log ('{0} → {1} 준비 완료' -f $rt.In, $(if ($rt.Out) { $rt.Out } else { '호출기 (듣기만)' })) 'Cyan'
      } catch {
        $msg = $_.Exception.Message
        Log ('{0} → {1} 포트 오류: {2}' -f $rt.In, $rt.Out, $msg) 'Red'
        if ($rt.V -eq $null) { Log (' → {0} 가 없거나 열 수 없습니다. 가상 포트 쌍이 만들어졌는지 확인하세요.' -f $rt.In) 'Red' }
        else { Log (' → 키오스크 프로그램이 아직 {0} 를 쓰고 있으면, 그 프린터 포트를 가상 포트로 바꾸고 키오스크 프로그램을 다시 켜세요.' -f $rt.Out) 'Red' }
        Close-Route $rt
        $rt.Retry = (Get-Date).AddSeconds(5)
        continue
      }
    }
    try {
      $n = $rt.V.BytesToRead
      if ($n -gt 0) {
        $b = New-Object byte[] $n
        $r = $rt.V.Read($b, 0, $n)
        if ($rt.P) { $rt.P.Write($b, 0, $r) }
        $rt.Buf.Write($b, 0, $r)
        $rt.Last = Get-Date
        $did = $true
      }
      $n = $(if ($rt.P) { $rt.P.BytesToRead } else { 0 })
      if ($n -gt 0) {
        $b = New-Object byte[] $n
        $r = $rt.P.Read($b, 0, $n)
        $rt.V.Write($b, 0, $r)
        $did = $true
      }
    } catch {
      Log ('{0} → {1} 연결 끊김: {2}  (5초 뒤 다시 연결)' -f $rt.In, $rt.Out, $_.Exception.Message) 'Red'
      Close-Route $rt
      $rt.Retry = (Get-Date).AddSeconds(5)
    }
    if ($rt.Buf.Length -gt 0 -and ((Get-Date) - $rt.Last).TotalMilliseconds -gt 700) {
      $job = $rt.Buf.ToArray(); $rt.Buf.SetLength(0)
      try { Handle-Job $job $(if ($rt.Out) { $rt.Out } else { $rt.In }) } catch { Log ('처리 오류: ' + $_.Exception.Message) 'Red' }
    }
  }
  if (-not $did) { Retry-Pending; Start-Sleep -Milliseconds 5 }
}
