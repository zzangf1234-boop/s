@echo off
rem ============================================================
rem  DID number call - kiosk receipt bridge (TEST version)
rem  Kiosk program prints to COM10 (virtual, com0com pair COM10<->COM11).
rem  This bridge reads COM11, forwards everything to the real printer
rem  (COM2) in both directions, and shows the order number it finds.
rem  Close this window = bridge stops. To undo: set kiosk printer port back to COM2.
rem ============================================================
set "VPORT=COM11"
set "PRINTER=COM2"
set "BAUD=9600"
title DID Kiosk Bridge (TEST) - %VPORT% to %PRINTER%
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t=(Get-Content -LiteralPath '%~f0' -Raw -Encoding UTF8) -split '#DIDCALL_PS#'; iex $t[-1]"
pause
exit /b
#DIDCALL_PS#
$ErrorActionPreference = 'Stop'
$vport = $env:VPORT; $printer = $env:PRINTER; $baud = [int]$env:BAUD
$logDir = Join-Path ([Environment]::GetFolderPath('Desktop')) 'DID-영수증-기록'
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
$enc = [System.Text.Encoding]::GetEncoding(949)
$count = 0; $found = 0

function Open-Port([string]$name) {
  $p = New-Object System.IO.Ports.SerialPort $name, $baud, ([System.IO.Ports.Parity]::None), 8, ([System.IO.Ports.StopBits]::One)
  $p.Handshake = [System.IO.Ports.Handshake]::None
  $p.DtrEnable = $true
  $p.RtsEnable = $true
  $p.ReadTimeout = 50
  $p.WriteTimeout = 5000
  $p.Open()
  return $p
}

# 영수증 한 장 분량을 받으면: 원본 저장 + 글자로 바꿔 '주문번호' 찾기
function Handle-Receipt([byte[]]$bytes) {
  $script:count++
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
    $script:found++
    Write-Host ("{0}  주문번호 {1}  감지  (영수증 {2}바이트)" -f $time, $m.Groups[1].Value, $bytes.Length) -ForegroundColor Green
  } else {
    Write-Host ("{0}  인쇄 {1}바이트 전달함 - 주문번호는 못 찾음 (기록: {2}.txt)" -f $time, $bytes.Length, $stamp) -ForegroundColor DarkYellow
  }
}

Write-Host ''
Write-Host ' DID 키오스크 영수증 분기 - 시험판' -ForegroundColor Yellow
Write-Host (' 키오스크 -> {0}(가상)  ==>  {1}(실제 프린터)   속도 {2}' -f $vport, $printer, $baud)
Write-Host (' 기록 폴더: {0}' -f $logDir)
Write-Host ' 이 창을 닫으면 분기가 멈춥니다. 원래대로: 키오스크 프린터 포트를 COM2 로 되돌리기'
Write-Host ''

$v = $null; $pr = $null
while ($true) {
  try {
    if ($v -eq $null -or -not $v.IsOpen) { $v = Open-Port $vport }
    if ($pr -eq $null -or -not $pr.IsOpen) { $pr = Open-Port $printer }
    Write-Host (' 연결됨. 키오스크에서 주문(또는 영수증 재발행)을 해 보세요.') -ForegroundColor Cyan
    $buf = New-Object System.IO.MemoryStream
    $last = Get-Date
    while ($true) {
      $did = $false
      $n = $v.BytesToRead
      if ($n -gt 0) {
        $b = New-Object byte[] $n
        $r = $v.Read($b, 0, $n)
        $pr.Write($b, 0, $r)
        $buf.Write($b, 0, $r)
        $last = Get-Date
        $did = $true
      }
      $n = $pr.BytesToRead
      if ($n -gt 0) {
        $b = New-Object byte[] $n
        $r = $pr.Read($b, 0, $n)
        $v.Write($b, 0, $r)
        $did = $true
      }
      if ($buf.Length -gt 0 -and ((Get-Date) - $last).TotalMilliseconds -gt 700) {
        Handle-Receipt $buf.ToArray()
        $buf.SetLength(0)
      }
      if (-not $did) { Start-Sleep -Milliseconds 5 }
    }
  } catch {
    Write-Host (' 포트 오류: {0}' -f $_.Exception.Message) -ForegroundColor Red
    if ($_.Exception.Message -match $printer) {
      Write-Host (' -> 키오스크 프로그램이 아직 {0} 를 쓰고 있으면, 프린터 포트를 COM10 으로 바꾼 뒤 기다리세요.' -f $printer) -ForegroundColor Red
    }
    try { if ($v) { $v.Close() } } catch {}
    try { if ($pr) { $pr.Close() } } catch {}
    $v = $null; $pr = $null
    Write-Host ' 3초 뒤 다시 연결합니다...'
    Start-Sleep -Seconds 3
  }
}
