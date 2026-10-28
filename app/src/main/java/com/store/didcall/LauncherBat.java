package com.store.didcall;

/** 윈도우 실행 파일 (/display.bat), __URL__ 은 접속 주소로 바뀜 — tools/display-launcher.bat 에서 자동 생성 (직접 수정하지 마세요) */
final class LauncherBat {
    private LauncherBat() {}

    static final String HTML =
        "@echo off\r\n" +
        "rem ============================================================\r\n" +
        "rem  DID number call - Windows display launcher\r\n" +
        "rem  - Opens the call screen full screen in Microsoft Edge (sound allowed)\r\n" +
        "rem  - Starts a small hidden helper so the screen menu can\r\n" +
        "rem    minimize / close the window\r\n" +
        "rem    (triple-click the top-left corner of the screen)\r\n" +
        "rem  - To start with Windows: Win+R -> shell:startup -> put this file there\r\n" +
        "rem ============================================================\r\n" +
        "set \"URL=__URL__\"\r\n" +
        "set \"PROFILE=%LOCALAPPDATA%\\DIDCallDisplay\"\r\n" +
        "start \"\" /min powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command \"$t=(Get-Content -LiteralPath '%~f0' -Raw) -split '#DIDCALL_PS#'; iex $t[-1]\"\r\n" +
        "start \"\" msedge --app=\"%URL%\" --start-fullscreen --autoplay-policy=no-user-gesture-required --no-first-run --no-default-browser-check --user-data-dir=\"%PROFILE%\" --disable-features=BlockInsecurePrivateNetworkRequests,PrivateNetworkAccessSendPreflights,PrivateNetworkAccessRespectPreflightResults,LocalNetworkAccessChecks\r\n" +
        "exit /b\r\n" +
        "#DIDCALL_PS#\r\n" +
        "$ErrorActionPreference = 'SilentlyContinue'\r\n" +
        "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class DidWin { [DllImport(\"user32.dll\")] public static extern bool ShowWindow(IntPtr h, int c); }'\r\n" +
        "$listener = New-Object System.Net.Sockets.TcpListener ([System.Net.IPAddress]::Loopback), 8799\r\n" +
        "try { $listener.Start() } catch { exit }\r\n" +
        "function Get-DidWindows { Get-Process msedge | Where-Object { $_.MainWindowTitle -like '*DID Display*' } }\r\n" +
        "$seen = $false\r\n" +
        "$idle = 0\r\n" +
        "while ($true) {\r\n" +
        "  if (-not $listener.Pending()) {\r\n" +
        "    Start-Sleep -Milliseconds 200\r\n" +
        "    $idle++\r\n" +
        "    if ($idle -ge 25) {\r\n" +
        "      $idle = 0\r\n" +
        "      $w = Get-DidWindows\r\n" +
        "      if ($w) { $seen = $true } elseif ($seen) { break }\r\n" +
        "    }\r\n" +
        "    continue\r\n" +
        "  }\r\n" +
        "  $client = $listener.AcceptTcpClient()\r\n" +
        "  $stream = $client.GetStream()\r\n" +
        "  $reader = New-Object System.IO.StreamReader($stream)\r\n" +
        "  $line = $reader.ReadLine()\r\n" +
        "  while ($true) { $h = $reader.ReadLine(); if ($h -eq $null -or $h -eq '') { break } }\r\n" +
        "  $resp = \"HTTP/1.1 200 OK`r`nAccess-Control-Allow-Origin: *`r`nAccess-Control-Allow-Private-Network: true`r`nAccess-Control-Allow-Methods: GET`r`nContent-Type: text/plain`r`nContent-Length: 2`r`nConnection: close`r`n`r`nok\"\r\n" +
        "  $bytes = [System.Text.Encoding]::ASCII.GetBytes($resp)\r\n" +
        "  $stream.Write($bytes, 0, $bytes.Length)\r\n" +
        "  $client.Close()\r\n" +
        "  if ($line -like 'GET /min*') {\r\n" +
        "    foreach ($p in Get-DidWindows) { [DidWin]::ShowWindow($p.MainWindowHandle, 6) | Out-Null }\r\n" +
        "  } elseif ($line -like 'GET /close*') {\r\n" +
        "    foreach ($p in Get-DidWindows) { $p.CloseMainWindow() | Out-Null }\r\n" +
        "    break\r\n" +
        "  }\r\n" +
        "}\r\n" +
        "$listener.Stop()\r\n";
}
