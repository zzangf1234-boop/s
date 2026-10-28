package com.store.didcall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TV 쪽에서 돌아가는 아주 작은 HTTP 서버 + 자동 검색 응답기.
 *
 *  GET  /call?n=123          번호 호출
 *  GET  /recall              현재 번호 다시 호출
 *  GET  /clear               화면 비우기
 *  GET  /status              현재 상태(JSON)
 *  GET  /config?tts=..&sub=..&repeat=2&slide=8&fit=contain&popup=10
 *  POST /image               이미지 추가 (본문 = 이미지 파일)
 *  GET  /images              이미지 목록
 *  GET  /img/{name}          이미지 보기
 *  GET  /image/delete?name=  이미지 1장 삭제
 *  GET  /image/clear         이미지 모두 삭제
 *  GET  /                    휴대폰 브라우저용 호출 페이지
 *  GET  /display             PC 브라우저용 호출 화면 (TV 화면과 동일)
 *  GET  /display.bat         윈도우 전체화면 실행 파일
 *  GET  /voice?n=123         안내 음성 WAV (안드로이드와 같은 목소리)
 *  GET  /chime.wav           딩동 효과음 WAV
 */
final class CallServer {

    interface Host {
        CallStore store();
        ImageStore images();
        void putConfig(JSONObject o) throws Exception;
        void applyConfig(Map<String, String> q);
        /** 안내 음성 WAV 파일 (안드로이드 음성 엔진으로 생성), 실패하면 null */
        File voiceFor(String n);
        /** 딩동 효과음 WAV */
        byte[] chimeWav();
        /** event: call | recall | clear | config | images — 서버 스레드에서 호출됨 */
        void onChanged(String event);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int MAX_UPLOAD = 20 * 1024 * 1024;
    private static final String JSON = "application/json; charset=utf-8";

    private final Host host;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private volatile boolean running;
    private ServerSocket server;
    private DatagramSocket udp;

    CallServer(Host host) {
        this.host = host;
    }

    void start() {
        running = true;
        new Thread(this::httpLoop, "didcall-http").start();
        new Thread(this::discoveryLoop, "didcall-udp").start();
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
        try { if (udp != null) udp.close(); } catch (Exception ignored) {}
        pool.shutdownNow();
    }

    // ───────────────────────── 자동 검색 (UDP 브로드캐스트) ─────────────────────────
    private void discoveryLoop() {
        while (running) {
            try {
                udp = new DatagramSocket(null);
                udp.setReuseAddress(true);
                udp.setBroadcast(true);
                udp.bind(new InetSocketAddress(Net.DISCOVERY_PORT));
                byte[] buf = new byte[256];
                while (running) {
                    DatagramPacket p = new DatagramPacket(buf, buf.length);
                    udp.receive(p);
                    String msg = new String(p.getData(), 0, p.getLength(), UTF8).trim();
                    if (msg.startsWith(Net.DISCOVER_MSG)) {
                        String reply = Net.DISCOVER_REPLY + "|" + Net.HTTP_PORT + "|" + android.os.Build.MODEL;
                        byte[] out = reply.getBytes(UTF8);
                        udp.send(new DatagramPacket(out, out.length, p.getAddress(), p.getPort()));
                    }
                }
            } catch (Exception e) {
                if (!running) return;
                sleep(2000); // 포트 사용 중 등 오류 시 잠시 후 재시도
            } finally {
                try { if (udp != null) udp.close(); } catch (Exception ignored) {}
            }
        }
    }

    // ───────────────────────── HTTP ─────────────────────────
    private void httpLoop() {
        while (running) {
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(Net.HTTP_PORT));
                while (running) {
                    final Socket s = server.accept();
                    pool.execute(() -> handle(s));
                }
            } catch (Exception e) {
                if (!running) return;
                sleep(2000);
            } finally {
                try { if (server != null) server.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void handle(Socket s) {
        try {
            s.setSoTimeout(15000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            String line = readLine(in);
            if (line == null) return;

            int contentLength = 0;
            String hostHeader = null;
            String h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                int c = h.indexOf(':');
                if (c > 0 && h.substring(0, c).trim().equalsIgnoreCase("host")) hostHeader = h.substring(c + 1).trim();
                if (c > 0 && h.substring(0, c).trim().equalsIgnoreCase("content-length")) {
                    try { contentLength = Integer.parseInt(h.substring(c + 1).trim()); } catch (Exception ignored) {}
                }
            }

            String[] parts = line.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String target = parts[1];
            String path = target;
            Map<String, String> q = new HashMap<>();
            int qi = target.indexOf('?');
            if (qi >= 0) {
                path = target.substring(0, qi);
                for (String kv : target.substring(qi + 1).split("&")) {
                    int eq = kv.indexOf('=');
                    if (eq > 0) {
                        q.put(URLDecoder.decode(kv.substring(0, eq), "UTF-8"),
                              URLDecoder.decode(kv.substring(eq + 1), "UTF-8"));
                    }
                }
            }

            if ("POST".equals(method)) {
                if (!"/image".equals(path)) { send(out, 404, "text/plain; charset=utf-8", "Not Found"); return; }
                if (contentLength <= 0 || contentLength > MAX_UPLOAD) {
                    send(out, 400, JSON, status(false, "이미지는 20MB 이하만 올릴 수 있습니다"));
                    return;
                }
                byte[] body = readFully(in, contentLength);
                String err = host.images().add(body);
                if (err == null) host.onChanged("images");
                send(out, err == null ? 200 : 400, JSON, status(err == null, err));
                return;
            }

            if (path.startsWith("/img/")) {
                File f = host.images().get(path.substring(5));
                if (f == null) { send(out, 404, "text/plain; charset=utf-8", "Not Found"); return; }
                String n = f.getName();
                String type = n.endsWith(".png") ? "image/png" : n.endsWith(".webp") ? "image/webp" : "image/jpeg";
                send(out, 200, type, readFile(f));
                return;
            }

            switch (path) {
                case "/":
                case "/index.html":
                    send(out, 200, "text/html; charset=utf-8", WebPage.HTML);
                    break;
                case "/display":
                    send(out, 200, "text/html; charset=utf-8", DisplayPage.HTML);
                    break;
                case "/display.bat":
                    sendBat(out, hostHeader);
                    break;
                case "/voice": {
                    String n = q.get("n");
                    File f = Net.validNumber(n) ? host.voiceFor(n) : null;
                    if (f == null) send(out, 404, "text/plain; charset=utf-8", "Not Found");
                    else send(out, 200, "audio/wav", readFile(f));
                    break;
                }
                case "/chime.wav":
                    send(out, 200, "audio/wav", host.chimeWav());
                    break;
                case "/ping":
                    send(out, 200, "text/plain; charset=utf-8", "DIDCALL OK");
                    break;
                case "/status":
                    send(out, 200, JSON, status(true, null));
                    break;
                case "/call": {
                    String n = q.get("n");
                    if (n != null) n = n.trim();
                    if (!Net.validNumber(n)) {
                        send(out, 400, JSON, status(false, "번호는 숫자/영문 1~6자"));
                        break;
                    }
                    host.store().call(n);
                    host.onChanged("call");
                    send(out, 200, JSON, status(true, null));
                    break;
                }
                case "/recall":
                    if (!host.store().current().isEmpty()) {
                        host.store().recall();
                        host.onChanged("recall");
                    }
                    send(out, 200, JSON, status(true, null));
                    break;
                case "/clear":
                    host.store().clear();
                    host.onChanged("clear");
                    send(out, 200, JSON, status(true, null));
                    break;
                case "/config":
                    host.applyConfig(q);
                    host.onChanged("config");
                    send(out, 200, JSON, status(true, null));
                    break;
                case "/images": {
                    JSONArray arr = new JSONArray();
                    for (File f : host.images().list()) arr.put(f.getName());
                    JSONObject o = new JSONObject();
                    o.put("ok", true);
                    o.put("images", arr);
                    send(out, 200, JSON, o.toString());
                    break;
                }
                case "/image/delete": {
                    boolean ok = host.images().delete(q.get("name"));
                    if (ok) host.onChanged("images");
                    send(out, 200, JSON, status(ok, ok ? null : "이미지를 찾을 수 없습니다"));
                    break;
                }
                case "/image/clear":
                    host.images().clear();
                    host.onChanged("images");
                    send(out, 200, JSON, status(true, null));
                    break;
                default:
                    send(out, 404, "text/plain; charset=utf-8", "Not Found");
            }
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private String status(boolean ok, String error) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", ok);
            if (error != null) o.put("error", error);
            o.put("current", host.store().current());
            JSONArray hist = new JSONArray();
            for (String n : host.store().history()) hist.put(n);
            o.put("history", hist);
            o.put("images", host.images().count());
            o.put("seq", host.store().seq());
            host.putConfig(o);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    /** 헤더 한 줄 읽기 (본문이 바이너리일 수 있어 Reader 대신 바이트 단위로) */
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') b.write(c);
            if (b.size() > 8192) throw new IOException("header too long");
        }
        if (c == -1 && b.size() == 0) return null;
        return new String(b.toByteArray(), UTF8);
    }

    private static byte[] readFully(InputStream in, int len) throws IOException {
        byte[] buf = new byte[len];
        int off = 0;
        while (off < len) {
            int r = in.read(buf, off, len - off);
            if (r < 0) throw new IOException("unexpected end");
            off += r;
        }
        return buf;
    }

    private static byte[] readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            return readFully(in, (int) f.length());
        }
    }

    private static void send(OutputStream out, int code, String type, String body) throws IOException {
        send(out, code, type, body.getBytes(UTF8));
    }

    private static void send(OutputStream out, int code, String type, byte[] b) throws IOException {
        String reason = code == 200 ? "OK" : code == 400 ? "Bad Request" : "Not Found";
        String head = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + b.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(UTF8));
        out.write(b);
        out.flush();
    }

    /** 윈도우에서 더블클릭하면 엣지 전체화면으로 호출 화면을 여는 실행 파일 (이 기기의 주소가 들어감) */
    private static void sendBat(OutputStream out, String hostHeader) throws IOException {
        // 브라우저가 접속한 주소를 그대로 사용 (가장 확실하게 연결되는 주소)
        String host = hostHeader != null && hostHeader.matches("[0-9A-Za-z.\\-]+(:[0-9]{1,5})?")
                ? hostHeader : Net.localIp() + ":" + Net.HTTP_PORT;
        String url = "http://" + host + "/display";
        String bat = "@echo off\r\n"
                + "rem DID number call - browser display\r\n"
                + "rem Put a shortcut of this file in shell:startup to open it when Windows starts.\r\n"
                + "set URL=" + url + "\r\n"
                + "set PROFILE=%LOCALAPPDATA%\\DIDCallDisplay\r\n"
                + "start \"\" msedge --kiosk \"%URL%\" --edge-kiosk-type=fullscreen"
                + " --autoplay-policy=no-user-gesture-required --no-first-run"
                + " --user-data-dir=\"%PROFILE%\"\r\n";
        byte[] b = bat.getBytes(UTF8);
        String head = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: application/octet-stream\r\n"
                + "Content-Disposition: attachment; filename=\"DID-Display.bat\"\r\n"
                + "Content-Length: " + b.length + "\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(UTF8));
        out.write(b);
        out.flush();
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
