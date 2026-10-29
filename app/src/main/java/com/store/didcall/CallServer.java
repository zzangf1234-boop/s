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
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 호출기(호스트) 폰에서 돌아가는 작은 HTTP 서버 + 자동 검색 응답기.
 * TV 화면 기기와 PC 브라우저는 /display 페이지로 접속해 화면을 표시합니다.
 *
 * 누구나 (화면 보기용)
 *  GET  /  /display  /display.bat  /ping  /status
 *  GET  /media                 이미지·동영상 목록     GET /m/{name}  파일 (구간 요청 지원)
 *  GET  /voice?n=123           안내 음성 WAV          GET /voice/closing  마감 안내 음성
 *  GET  /chime[?type=]         알림음                 GET /voices  음성 엔진·목소리 목록
 *  GET  /auth/request?name=    호출 권한 요청         GET /auth/check?id=  승인 여부 확인
 *
 * 승인된 기기만 (?t=토큰) — 호출기 폰 자신은 항상 허용
 *  GET  /call?n=  (/call?next=1 = 가장 먼저 들어온 조리중 번호)  /recall  /clear  /config?..  /closing?on=1|0
 *  GET  /waiting/add?n=  /waiting/remove?n=  /waiting/clear  /voice/preview?n=&voice=&rate=&pitch=
 *  POST /media (이미지·동영상)  POST /chime/upload  GET /media/delete?name=  /media/move?name=&d=-1|1  /media/clear
 *
 * 호출기 폰만
 *  GET  /auth/decide?id=&ok=1|0  /auth/devices  /auth/revoke?token=
 */
final class CallServer {

    interface Host {
        CallStore store();
        MediaStore media();
        Auth auth();
        /** 설정·상태 값을 status 에 넣기 */
        void putConfig(JSONObject o) throws Exception;
        void applyConfig(Map<String, String> q);
        /** 번호 안내 음성 (기계 음성 또는 내 목소리), 실패하면 null — 서버 스레드에서 호출, 최대 수 초 대기 */
        File voiceFor(String n);
        /** 영업 마감 안내 음성, 없으면 null */
        File closingVoice();
        /** 목소리 미리 듣기 (저장 전 설정값으로) */
        File previewVoice(String n, String voice, int rate, int pitch);
        /** 앱에 들어 있는 녹음 목소리(승아 등)로 만든 안내 음성, 못 만들면 null */
        File packVoice(String pack, String phrase, String n);
        /** 알림음 WAV (type 이 null 이면 현재 설정). 직접 올린 소리면 null */
        byte[] chimeWav(String type);
        /** 직접 올린 알림음 파일, 없으면 null */
        File customChime();
        /** 알림음 파일 저장. 오류 메시지, 성공이면 null */
        String saveCustomChime(InputStream in, long len) throws IOException;
        /** {engines:[...], voices:[...]} */
        JSONObject voices() throws Exception;
        /** 영업 마감 표시 수동 켜기/끄기 */
        void setClosed(boolean closed);
        /** 받은 주문서 원본 파일 (진단용), 없으면 null */
        File receiptFile(String id);
        /** 주문서 진단 글 (해석한 글자·그림 명령·16진수) */
        String receiptDiagnose(File f) throws IOException;
        /** event: call | recall | clear | config | media | closing | auth | waiting — 서버 스레드에서 호출됨 */
        void onChanged(String event);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final String JSON = "application/json; charset=utf-8";
    private static final String TEXT = "text/plain; charset=utf-8";

    private final Host host;
    private final ExecutorService pool = Executors.newFixedThreadPool(24);
    /** 호출 화면(TV·PC)별 마지막 접속 시각 — 호출기에 '화면 N대 연결됨' 표시용 */
    private final ConcurrentHashMap<String, Long> displays = new ConcurrentHashMap<>();
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
            s.setSoTimeout(20000);
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            String line = readLine(in);
            if (line == null) return;

            long contentLength = 0;
            String hostHeader = null, range = null;
            String h;
            while ((h = readLine(in)) != null && !h.isEmpty()) {
                int c = h.indexOf(':');
                if (c <= 0) continue;
                String k = h.substring(0, c).trim(), v = h.substring(c + 1).trim();
                if (k.equalsIgnoreCase("host")) hostHeader = v;
                else if (k.equalsIgnoreCase("range")) range = v;
                else if (k.equalsIgnoreCase("content-length")) {
                    try { contentLength = Long.parseLong(v); } catch (Exception ignored) {}
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

            String ip = s.getInetAddress().getHostAddress();
            boolean local = s.getInetAddress().isLoopbackAddress();
            boolean authed = local || host.auth().valid(q.get("t"), ip);

            // ── 파일 올리기 ──
            if ("POST".equals(method)) {
                if (!authed) { deny(out); return; }
                if ("/media".equals(path) || "/image".equals(path)) {
                    String err = contentLength <= 0 ? "파일 내용이 없습니다" : host.media().addFromStream(in, contentLength);
                    if (err == null) host.onChanged("media");
                    send(out, err == null ? 200 : 400, JSON, status(err == null, err, true, local));
                } else if ("/chime/upload".equals(path)) {
                    String err = contentLength <= 0 ? "파일 내용이 없습니다" : host.saveCustomChime(in, contentLength);
                    if (err == null) host.onChanged("config");
                    send(out, err == null ? 200 : 400, JSON, status(err == null, err, true, local));
                } else {
                    send(out, 404, TEXT, "Not Found");
                }
                return;
            }

            // ── 이미지·동영상 파일 (구간 요청 지원) ──
            if (path.startsWith("/m/") || path.startsWith("/img/")) {
                File f = host.media().get(path.substring(path.indexOf('/', 1) + 1));
                if (f == null) send(out, 404, TEXT, "Not Found");
                else sendFile(out, f, MediaStore.contentType(f.getName()), range, true);
                return;
            }

            switch (path) {
                // ── 누구나 ──
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
                case "/ping":
                    send(out, 200, TEXT, "DIDCALL OK");
                    break;
                case "/status":
                    if (q.containsKey("d")) displays.put(ip, System.currentTimeMillis());
                    send(out, 200, JSON, status(true, null, authed, local));
                    break;
                case "/media":
                case "/images": {
                    JSONArray arr = new JSONArray();
                    JSONArray names = new JSONArray();
                    for (File f : host.media().list()) {
                        JSONObject o = new JSONObject();
                        o.put("name", f.getName());
                        o.put("video", MediaStore.isVideo(f.getName()));
                        o.put("size", f.length());
                        arr.put(o);
                        names.put(f.getName());
                    }
                    JSONObject o = new JSONObject();
                    o.put("ok", true);
                    o.put("items", arr);
                    o.put("images", names); // 이전 버전 화면 호환
                    o.put("ver", host.media().version());
                    send(out, 200, JSON, o.toString());
                    break;
                }
                case "/voice": {
                    String n = q.get("n");
                    File f = Net.validCall(n) ? host.voiceFor(n) : null;
                    if (f == null) send(out, 404, TEXT, "Not Found");
                    else sendFile(out, f, "audio/wav", null, false);
                    break;
                }
                case "/voice/closing": {
                    File f = host.closingVoice();
                    if (f == null) send(out, 404, TEXT, "Not Found");
                    else sendFile(out, f, "audio/wav", null, false);
                    break;
                }
                case "/chime":
                case "/chime.wav": {
                    String type = q.get("type");
                    byte[] wav = host.chimeWav(type);
                    if (wav != null) {
                        send(out, 200, "audio/wav", wav);
                    } else {
                        File f = host.customChime();
                        if (f == null) send(out, 200, "audio/wav", host.chimeWav("dingdong"));
                        else sendFile(out, f, audioType(f.getName()), null, false);
                    }
                    break;
                }
                case "/voices":
                    send(out, 200, JSON, host.voices().toString());
                    break;
                case "/auth/request": {
                    Auth.Request r = host.auth().request(q.get("name"), ip);
                    JSONObject o = new JSONObject();
                    o.put("ok", true);
                    o.put("id", r.id);
                    send(out, 200, JSON, o.toString());
                    host.onChanged("auth");
                    break;
                }
                case "/auth/check": {
                    String st = host.auth().check(q.get("id"));
                    JSONObject o = new JSONObject();
                    o.put("ok", true);
                    if (st.length() == 32) {
                        o.put("state", "ok");
                        o.put("token", st);
                    } else {
                        o.put("state", st);
                    }
                    send(out, 200, JSON, o.toString());
                    break;
                }

                // ── 승인된 기기만 ──
                case "/call": {
                    if (!authed) { deny(out); break; }
                    String n = q.get("n");
                    if (n != null) n = n.trim();
                    if ((n == null || n.isEmpty()) && "1".equals(q.get("next"))) {
                        n = host.store().oldestWaiting();
                        if (n == null) {
                            send(out, 400, JSON, status(false, "조리중인 번호가 없습니다", authed, local));
                            break;
                        }
                    }
                    if (n != null && n.matches("[0-9]{1,6}\\s*[-~]\\s*[0-9]{1,6}")) n = n.replaceAll("\\s*[-~]\\s*", "~");
                    if (n != null && n.contains("~") && !Net.validCall(n)) {
                        send(out, 400, JSON, status(false, "연속 호출은 작은 번호~큰 번호, 최대 " + Net.MAX_GROUP + "개까지 (예: 1~4)", authed, local));
                        break;
                    }
                    if (!Net.validCall(n)) {
                        send(out, 400, JSON, status(false, "번호는 숫자/영문 1~6자", authed, local));
                        break;
                    }
                    host.store().call(n);
                    host.onChanged("call");
                    send(out, 200, JSON, status(true, null, authed, local));
                    break;
                }
                case "/recall":
                    if (!authed) { deny(out); break; }
                    if (!host.store().current().isEmpty()) {
                        host.store().recall();
                        host.onChanged("recall");
                    }
                    send(out, 200, JSON, status(true, null, authed, local));
                    break;
                case "/clear":
                    if (!authed) { deny(out); break; }
                    host.store().clear();
                    host.onChanged("clear");
                    send(out, 200, JSON, status(true, null, authed, local));
                    break;
                case "/config":
                    if (!authed) { deny(out); break; }
                    q.remove("t");
                    host.applyConfig(q);
                    host.onChanged("config");
                    send(out, 200, JSON, status(true, null, authed, local));
                    break;
                case "/waiting/add": {
                    if (!authed) { deny(out); break; }
                    String n = q.get("n") == null ? "" : q.get("n").trim();
                    boolean ok = host.store().addWaiting(n, q.get("items"));
                    if (ok) host.onChanged("waiting");
                    send(out, 200, JSON, status(ok, ok ? null : "이미 조리중이거나 올바르지 않은 번호입니다", authed, local));
                    break;
                }
                case "/waiting/remove": {
                    if (!authed) { deny(out); break; }
                    boolean ok = host.store().removeWaiting(q.get("n") == null ? "" : q.get("n").trim());
                    if (ok) host.onChanged("waiting");
                    send(out, 200, JSON, status(true, null, authed, local));
                    break;
                }
                case "/waiting/clear":
                    if (!authed) { deny(out); break; }
                    host.store().clearWaiting();
                    host.onChanged("waiting");
                    send(out, 200, JSON, status(true, null, authed, local));
                    break;
                case "/kiosk/raw":
                case "/kiosk/text": {
                    if (!authed) { deny(out); break; }
                    File f = host.receiptFile(q.get("id"));
                    if (f == null) { send(out, 404, TEXT, "주문서 기록이 없습니다 (앱을 다시 켜면 지워질 수 있음)"); break; }
                    if ("/kiosk/raw".equals(path)) {
                        byte[] b = readFile(f);
                        String head = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\n"
                                + "Content-Disposition: attachment; filename=\"receipt-" + f.getName() + "\"\r\n"
                                + "Content-Length: " + b.length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
                        out.write(head.getBytes(UTF8));
                        out.write(b);
                        out.flush();
                    } else {
                        String d = host.receiptDiagnose(f).replace("&", "&amp;").replace("<", "&lt;");
                        String tok = q.get("t") == null ? "" : "&t=" + java.net.URLEncoder.encode(q.get("t"), "UTF-8");
                        String html = "<!doctype html><meta charset=utf-8><meta name=viewport content='width=device-width'><title>주문서 " + f.getName()
                                + "</title><body style='background:#111;color:#eee;font-family:sans-serif;padding:12px'>"
                                + "<a style='display:block;background:#ff8a00;color:#fff;padding:12px;border-radius:10px;text-align:center;text-decoration:none;font-weight:700' href='/kiosk/raw?id="
                                + f.getName().replace(".bin", "") + tok + "'>⬇ 원본 파일 받기 (.bin)</a>"
                                + "<pre style='white-space:pre-wrap;word-break:break-all;font-size:13px'>" + d + "</pre>";
                        send(out, 200, "text/html; charset=utf-8", html.getBytes(UTF8));
                    }
                    break;
                }
                case "/closing":
                    if (!authed) { deny(out); break; }
                    host.setClosed("1".equals(q.get("on")));
                    send(out, 200, JSON, status(true, null, authed, local));
                    break;
                case "/voice/preview": {
                    if (!authed) { deny(out); break; }
                    int rate = parseInt(q.get("rate"), 100), pitch = parseInt(q.get("pitch"), 100);
                    String n = Net.validNumber(q.get("n")) ? q.get("n") : "123";
                    String pack = VoicePack.fromMode(q.get("mode"));
                    File f = pack != null ? host.packVoice(pack, q.get("phrase"), n) : host.previewVoice(n, q.get("voice"), rate, pitch);
                    if (f == null) send(out, 404, TEXT, "Not Found");
                    else sendFile(out, f, "audio/wav", null, false);
                    break;
                }
                case "/media/delete":
                case "/image/delete": {
                    if (!authed) { deny(out); break; }
                    boolean ok = host.media().delete(q.get("name"));
                    if (ok) host.onChanged("media");
                    send(out, 200, JSON, status(ok, ok ? null : "파일을 찾을 수 없습니다", authed, local));
                    break;
                }
                case "/media/move": {
                    if (!authed) { deny(out); break; }
                    boolean ok = host.media().move(q.get("name"), parseInt(q.get("d"), 1));
                    if (ok) host.onChanged("media");
                    send(out, 200, JSON, status(true, null, authed, local));
                    break;
                }
                case "/media/clear":
                case "/image/clear":
                    if (!authed) { deny(out); break; }
                    host.media().clear();
                    host.onChanged("media");
                    send(out, 200, JSON, status(true, null, authed, local));
                    break;

                // ── 호출기 폰만 ──
                case "/auth/decide": {
                    if (!local) { deny(out); break; }
                    boolean ok = host.auth().decide(q.get("id"), "1".equals(q.get("ok")));
                    host.onChanged("auth");
                    send(out, 200, JSON, status(ok, null, true, true));
                    break;
                }
                case "/auth/devices": {
                    if (!local) { deny(out); break; }
                    JSONArray arr = new JSONArray();
                    for (Auth.Device d : host.auth().devices()) {
                        JSONObject o = new JSONObject();
                        o.put("token", d.token);
                        o.put("name", d.name);
                        o.put("ip", d.ip);
                        o.put("last", d.last);
                        arr.put(o);
                    }
                    JSONObject o = new JSONObject();
                    o.put("ok", true);
                    o.put("devices", arr);
                    send(out, 200, JSON, o.toString());
                    break;
                }
                case "/auth/revoke": {
                    if (!local) { deny(out); break; }
                    boolean ok = host.auth().revoke(q.get("token"));
                    send(out, 200, JSON, status(ok, null, true, true));
                    break;
                }
                default:
                    send(out, 404, TEXT, "Not Found");
            }
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (Exception ignored) {}
        }
    }

    private void deny(OutputStream out) throws IOException {
        send(out, 403, JSON, "{\"ok\":false,\"auth\":true,\"error\":\"호출기에서 이 기기를 허용해야 합니다\"}");
    }

    private String status(boolean ok, String error, boolean authed, boolean local) {
        try {
            JSONObject o = new JSONObject();
            o.put("ok", ok);
            if (error != null) o.put("error", error);
            o.put("current", host.store().current());
            JSONArray grp = new JSONArray();
            for (String m : host.store().currentMembers()) grp.put(m);
            o.put("group", grp);
            JSONArray hist = new JSONArray();
            for (String n : host.store().history()) hist.put(n);
            o.put("history", hist);
            JSONArray wait = new JSONArray();
            for (CallStore.Waiting w : host.store().waiting()) {
                JSONObject x = new JSONObject();
                x.put("n", w.n);
                x.put("items", w.items);
                x.put("at", w.at);
                wait.put(x);
            }
            o.put("waiting", wait);
            o.put("images", host.media().count());
            o.put("mediaVer", host.media().version());
            o.put("seq", host.store().seq());
            o.put("displays", displayCount());
            o.put("authed", authed);
            o.put("time", System.currentTimeMillis());
            if (local) {
                // 승인 대기 목록은 호출기 폰에만 알려줌
                JSONArray pend = new JSONArray();
                for (Auth.Request r : host.auth().pending()) {
                    JSONObject p = new JSONObject();
                    p.put("id", r.id);
                    p.put("name", r.name);
                    p.put("ip", r.ip);
                    pend.put(p);
                }
                o.put("pending", pend);
            }
            host.putConfig(o);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    int displayCount() {
        long limit = System.currentTimeMillis() - 6000;
        int n = 0;
        for (Iterator<Long> it = displays.values().iterator(); it.hasNext(); ) {
            if (it.next() < limit) it.remove(); else n++;
        }
        return n;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    static String audioType(String name) {
        if (name.endsWith(".mp3")) return "audio/mpeg";
        if (name.endsWith(".ogg")) return "audio/ogg";
        if (name.endsWith(".m4a")) return "audio/mp4";
        return "audio/wav";
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

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 206: return "Partial Content";
            case 400: return "Bad Request";
            case 403: return "Forbidden";
            case 416: return "Range Not Satisfiable";
            default: return "Not Found";
        }
    }

    private static void send(OutputStream out, int code, String type, String body) throws IOException {
        send(out, code, type, body.getBytes(UTF8));
    }

    private static void send(OutputStream out, int code, String type, byte[] b) throws IOException {
        String head = "HTTP/1.1 " + code + " " + reason(code) + "\r\n"
                + "Content-Type: " + type + "\r\n"
                + "Content-Length: " + b.length + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Cache-Control: no-store\r\n"
                + "Connection: close\r\n\r\n";
        out.write(head.getBytes(UTF8));
        out.write(b);
        out.flush();
    }

    /** 파일을 조금씩 보내기 (큰 동영상도 메모리에 올리지 않음), Range 요청이면 해당 구간만 */
    static void sendFile(OutputStream out, File f, String type, String range, boolean cacheable) throws IOException {
        long len = f.length();
        long start = 0, end = len - 1;
        boolean partial = false;
        if (range != null && range.startsWith("bytes=") && len > 0) {
            String r = range.substring(6);
            int comma = r.indexOf(',');
            if (comma >= 0) r = r.substring(0, comma);
            int dash = r.indexOf('-');
            try {
                String a = r.substring(0, dash).trim(), b = r.substring(dash + 1).trim();
                if (a.isEmpty()) {
                    start = Math.max(0, len - Long.parseLong(b));
                } else {
                    start = Long.parseLong(a);
                    if (!b.isEmpty()) end = Math.min(len - 1, Long.parseLong(b));
                }
                partial = true;
            } catch (Exception e) {
                start = 0;
                end = len - 1;
            }
            if (partial && (start >= len || start > end)) {
                String head = "HTTP/1.1 416 " + reason(416) + "\r\nContent-Range: bytes */" + len
                        + "\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                out.write(head.getBytes(UTF8));
                out.flush();
                return;
            }
        }
        long count = Math.max(0, end - start + 1);
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(partial ? 206 : 200).append(' ').append(reason(partial ? 206 : 200)).append("\r\n")
                .append("Content-Type: ").append(type).append("\r\n")
                .append("Content-Length: ").append(count).append("\r\n")
                .append("Accept-Ranges: bytes\r\n")
                .append("Access-Control-Allow-Origin: *\r\n")
                .append(cacheable ? "Cache-Control: max-age=86400\r\n" : "Cache-Control: no-store\r\n")
                .append("Connection: close\r\n");
        if (partial) head.append("Content-Range: bytes ").append(start).append('-').append(end).append('/').append(len).append("\r\n");
        head.append("\r\n");
        out.write(head.toString().getBytes(UTF8));
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            raf.seek(start);
            byte[] buf = new byte[64 * 1024];
            long left = count;
            while (left > 0) {
                int r = raf.read(buf, 0, (int) Math.min(buf.length, left));
                if (r < 0) break;
                out.write(buf, 0, r);
                left -= r;
            }
        }
        out.flush();
    }

    /** 윈도우에서 더블클릭하면 엣지 전체화면으로 호출 화면을 여는 실행 파일 (호출기 주소가 들어감, 최소화/종료 도우미 포함) */
    private static void sendBat(OutputStream out, String hostHeader) throws IOException {
        // 브라우저가 접속한 주소를 그대로 사용 (가장 확실하게 연결되는 주소)
        String host = hostHeader != null && hostHeader.matches("[0-9A-Za-z.\\-]+(:[0-9]{1,5})?")
                ? hostHeader : Net.localIp() + ":" + Net.HTTP_PORT;
        String url = "http://" + host + "/display";
        String bat = LauncherBat.HTML.replace("__URL__", url);
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

    @SuppressWarnings("unused")
    private static byte[] readFile(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int off = 0, r;
            while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
            return buf;
        }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
