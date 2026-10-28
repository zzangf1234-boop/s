package com.store.didcall;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
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
 *  GET /call?n=123   번호 호출
 *  GET /recall       현재 번호 다시 호출
 *  GET /clear        화면 비우기
 *  GET /status       현재 상태(JSON)
 *  GET /config?tts=..&sub=..&repeat=2   안내 문구/반복 횟수 변경
 *  GET /             휴대폰 브라우저용 호출 페이지
 */
final class CallServer {

    interface Host {
        CallStore store();
        String ttsTemplate();
        String subtitle();
        int repeat();
        void saveConfig(String tts, String subtitle, int repeat);
        /** event: "call" | "recall" | "clear" | "config" — 서버 스레드에서 호출됨 */
        void onChanged(String event);
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

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
            s.setSoTimeout(5000);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), UTF8));
            String line = in.readLine();
            if (line == null) return;
            // 헤더는 읽고 버림
            String h;
            while ((h = in.readLine()) != null && !h.isEmpty()) { /* skip */ }

            String[] parts = line.split(" ");
            if (parts.length < 2) return;
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

            OutputStream out = s.getOutputStream();
            switch (path) {
                case "/":
                case "/index.html":
                    send(out, 200, "text/html; charset=utf-8", WebPage.HTML);
                    break;
                case "/ping":
                    send(out, 200, "text/plain; charset=utf-8", "DIDCALL OK");
                    break;
                case "/status":
                    send(out, 200, "application/json; charset=utf-8", status(true, null));
                    break;
                case "/call": {
                    String n = q.get("n");
                    if (n != null) n = n.trim();
                    if (!Net.validNumber(n)) {
                        send(out, 400, "application/json; charset=utf-8", status(false, "번호는 숫자/영문 1~6자"));
                        break;
                    }
                    host.store().call(n);
                    host.onChanged("call");
                    send(out, 200, "application/json; charset=utf-8", status(true, null));
                    break;
                }
                case "/recall":
                    if (!host.store().current().isEmpty()) host.onChanged("recall");
                    send(out, 200, "application/json; charset=utf-8", status(true, null));
                    break;
                case "/clear":
                    host.store().clear();
                    host.onChanged("clear");
                    send(out, 200, "application/json; charset=utf-8", status(true, null));
                    break;
                case "/config": {
                    String tts = q.containsKey("tts") ? q.get("tts") : host.ttsTemplate();
                    String sub = q.containsKey("sub") ? q.get("sub") : host.subtitle();
                    int rep = host.repeat();
                    try { if (q.containsKey("repeat")) rep = Integer.parseInt(q.get("repeat")); } catch (Exception ignored) {}
                    rep = Math.max(0, Math.min(5, rep));
                    host.saveConfig(tts, sub, rep);
                    host.onChanged("config");
                    send(out, 200, "application/json; charset=utf-8", status(true, null));
                    break;
                }
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
            o.put("tts", host.ttsTemplate());
            o.put("sub", host.subtitle());
            o.put("repeat", host.repeat());
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false}";
        }
    }

    private static void send(OutputStream out, int code, String type, String body) throws Exception {
        byte[] b = body.getBytes(UTF8);
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

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
