@echo off
rem ============================================================
rem  DID number call - Windows display launcher
rem  - Opens the call screen full screen in Microsoft Edge (sound allowed)
rem  - Starts a small hidden helper so the screen menu can
rem    minimize / close the window
rem    (triple-click the top-left corner of the screen)
rem  - To start with Windows: Win+R -> shell:startup -> put this file there
rem ============================================================
set "URL=__URL__"
set "PROFILE=%LOCALAPPDATA%\DIDCallDisplay"
start "" /min powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command "$t=(Get-Content -LiteralPath '%~f0' -Raw) -split '#DIDCALL_PS#'; iex $t[-1]"
start "" msedge --app="%URL%" --start-fullscreen --autoplay-policy=no-user-gesture-required --no-first-run --no-default-browser-check --user-data-dir="%PROFILE%" --disable-features=BlockInsecurePrivateNetworkRequests,PrivateNetworkAccessSendPreflights,PrivateNetworkAccessRespectPreflightResults,LocalNetworkAccessChecks
exit /b
#DIDCALL_PS#
$ErrorActionPreference = 'SilentlyContinue'
Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class DidWin { [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c); }'
$listener = New-Object System.Net.Sockets.TcpListener ([System.Net.IPAddress]::Loopback), 8799
try { $listener.Start() } catch { exit }
function Get-DidWindows { Get-Process msedge | Where-Object { $_.MainWindowTitle -like '*DID Display*' } }
$seen = $false
$idle = 0
while ($true) {
  if (-not $listener.Pending()) {
    Start-Sleep -Milliseconds 200
    $idle++
    if ($idle -ge 25) {
      $idle = 0
      $w = Get-DidWindows
      if ($w) { $seen = $true } elseif ($seen) { break }
    }
    continue
  }
  $client = $listener.AcceptTcpClient()
  $stream = $client.GetStream()
  $reader = New-Object System.IO.StreamReader($stream)
  $line = $reader.ReadLine()
  while ($true) { $h = $reader.ReadLine(); if ($h -eq $null -or $h -eq '') { break } }
  $resp = "HTTP/1.1 200 OK`r`nAccess-Control-Allow-Origin: *`r`nAccess-Control-Allow-Private-Network: true`r`nAccess-Control-Allow-Methods: GET`r`nContent-Type: text/plain`r`nContent-Length: 2`r`nConnection: close`r`n`r`nok"
  $bytes = [System.Text.Encoding]::ASCII.GetBytes($resp)
  $stream.Write($bytes, 0, $bytes.Length)
  $client.Close()
  if ($line -like 'GET /min*') {
    foreach ($p in Get-DidWindows) { [DidWin]::ShowWindow($p.MainWindowHandle, 6) | Out-Null }
  } elseif ($line -like 'GET /close*') {
    foreach ($p in Get-DidWindows) { $p.CloseMainWindow() | Out-Null }
    break
  }
}
$listener.Stop()
