package com.store.didcall;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 다른 기기의 호출·설정 권한 (호출기에서 승인 방식).
 *  - 새 기기가 요청 → 호출기 폰에 [허용]/[거부] 표시 → 허용하면 기기별 토큰 발급
 *  - 토큰이 있는 기기만 호출·설정 가능. 화면 보기는 토큰 없이 가능
 *  - 호출기 폰 자신(127.0.0.1)은 항상 허용
 */
final class Auth {

    interface Listener {
        /** 새 승인 요청이 들어옴 (서버 스레드) */
        void onRequest(Request r);
        /** 허용된 기기 목록이 바뀜 → 저장 */
        void onSave(String data);
    }

    static final class Device {
        final String token;
        String name, ip;
        final long added;
        long last;
        Device(String token, String name, String ip, long added, long last) {
            this.token = token; this.name = name; this.ip = ip; this.added = added; this.last = last;
        }
    }

    static final class Request {
        final String id, name, ip;
        final long at;
        String token;      // 허용되면 발급
        boolean denied;
        Request(String id, String name, String ip, long at) { this.id = id; this.name = name; this.ip = ip; this.at = at; }
    }

    private static final long REQUEST_TTL = 3 * 60_000L;
    private static final int MAX_PENDING = 5;
    private static final int MAX_DEVICES = 50;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Device> devices = new LinkedHashMap<>();
    private final Map<String, Request> requests = new LinkedHashMap<>();
    private final Listener listener;

    Auth(Listener listener, String saved) {
        this.listener = listener;
        load(saved);
    }

    /** 이름은 화면 표시용이라 길이·문자를 제한 */
    static String cleanName(String s) {
        if (s == null) return "이름 없는 기기";
        s = s.replaceAll("[\\p{Cntrl}<>\"'&]", "").trim();
        if (s.isEmpty()) return "이름 없는 기기";
        return s.length() > 40 ? s.substring(0, 40) : s;
    }

    synchronized Request request(String name, String ip) {
        expire();
        // 같은 기기(IP)의 대기 중 요청은 재사용
        for (Request r : requests.values()) {
            if (r.ip.equals(ip) && r.token == null && !r.denied) return r;
        }
        while (pendingCount() >= MAX_PENDING) {
            Iterator<String> it = requests.keySet().iterator();
            it.next();
            it.remove();
        }
        Request r = new Request(hex(6), cleanName(name), ip, System.currentTimeMillis());
        requests.put(r.id, r);
        listener.onRequest(r);
        return r;
    }

    /** @return "pending" / "denied" / "expired" / 토큰 */
    synchronized String check(String id) {
        expire();
        Request r = id == null ? null : requests.get(id);
        if (r == null) return "expired";
        if (r.denied) return "denied";
        if (r.token != null) {
            requests.remove(id); // 토큰은 한 번만 전달
            return r.token;
        }
        return "pending";
    }

    synchronized boolean decide(String id, boolean allow) {
        expire();
        Request r = id == null ? null : requests.get(id);
        if (r == null || r.token != null || r.denied) return false;
        if (!allow) {
            r.denied = true;
            return true;
        }
        while (devices.size() >= MAX_DEVICES) {
            Iterator<String> it = devices.keySet().iterator();
            it.next();
            it.remove();
        }
        String token = hex(16);
        long now = System.currentTimeMillis();
        devices.put(token, new Device(token, r.name, r.ip, now, now));
        r.token = token;
        listener.onSave(save());
        return true;
    }

    synchronized boolean valid(String token, String ip) {
        if (token == null || token.isEmpty()) return false;
        Device d = devices.get(token);
        if (d == null) return false;
        long now = System.currentTimeMillis();
        boolean changed = !ip.equals(d.ip) || now - d.last > 10 * 60_000L;
        d.ip = ip;
        d.last = now;
        if (changed) listener.onSave(save()); // 너무 자주 저장하지 않음
        return true;
    }

    synchronized boolean revoke(String token) {
        boolean ok = devices.remove(token) != null;
        if (ok) listener.onSave(save());
        return ok;
    }

    synchronized List<Device> devices() {
        return new ArrayList<>(devices.values());
    }

    /** 아직 결정하지 않은 요청 */
    synchronized List<Request> pending() {
        expire();
        List<Request> out = new ArrayList<>();
        for (Request r : requests.values()) if (r.token == null && !r.denied) out.add(r);
        return out;
    }

    private int pendingCount() {
        int n = 0;
        for (Request r : requests.values()) if (r.token == null && !r.denied) n++;
        return n;
    }

    private void expire() {
        long limit = System.currentTimeMillis() - REQUEST_TTL;
        Iterator<Request> it = requests.values().iterator();
        while (it.hasNext()) if (it.next().at < limit) it.remove();
    }

    private String hex(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x & 0xFF));
        return s.toString();
    }

    // 형식: 줄마다 토큰\t이름\tIP\t추가시각\t마지막접속
    synchronized String save() {
        StringBuilder b = new StringBuilder();
        for (Device d : devices.values()) {
            b.append(d.token).append('\t').append(d.name.replace('\t', ' ').replace('\n', ' '))
                    .append('\t').append(d.ip).append('\t').append(d.added).append('\t').append(d.last).append('\n');
        }
        return b.toString();
    }

    private void load(String s) {
        if (s == null) return;
        for (String line : s.split("\n")) {
            String[] p = line.split("\t");
            if (p.length < 5 || !p[0].matches("[0-9a-f]{32}")) continue;
            try {
                devices.put(p[0], new Device(p[0], p[1], p[2], Long.parseLong(p[3]), Long.parseLong(p[4])));
            } catch (NumberFormatException ignored) {
            }
        }
    }
}
