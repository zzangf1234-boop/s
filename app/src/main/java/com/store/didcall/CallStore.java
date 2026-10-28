package com.store.didcall;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * 현재 호출 번호와 최근 호출 목록 (서버 스레드와 화면 스레드가 함께 사용).
 * 최근 호출은 마지막으로 호출된 시각으로부터 keepMinutes 가 지나면 목록에서 사라짐.
 * save()/load() 로 문자열에 저장해 앱·폰이 재시작돼도 복원.
 */
final class CallStore {
    private static final int MAX_HISTORY = 12;

    private static final class Entry {
        final String n;
        final long at;
        Entry(String n, long at) { this.n = n; this.at = at; }
    }

    private String current = "";
    private long currentAt;
    private long seq;               // 호출/재호출 때마다 1씩 증가 (화면이 새 호출을 감지)
    private int keepMinutes = 5;    // 0 이면 사라지지 않음
    private final LinkedList<Entry> history = new LinkedList<>();

    /** 조리중(키오스크로 들어온 대기 주문) — 들어온 순서대로 */
    static final class Waiting {
        final String n, items;
        final long at;
        Waiting(String n, String items, long at) { this.n = n; this.items = items == null ? "" : items; this.at = at; }
    }
    private static final int MAX_WAITING = 60;
    private final LinkedList<Waiting> waiting = new LinkedList<>();

    synchronized void call(String n) {
        long now = System.currentTimeMillis();
        removeWaiting(n); // 조리중이던 번호를 부르면 목록에서 빠짐
        if (!current.isEmpty() && !current.equals(n)) {
            remove(current);
            history.addFirst(new Entry(current, currentAt));
        }
        remove(n);
        current = n;
        currentAt = now;
        seq++;
        trim();
    }

    synchronized void recall() {
        if (current.isEmpty()) return;
        currentAt = System.currentTimeMillis();
        seq++;
    }

    synchronized void clear() {
        if (!current.isEmpty()) {
            remove(current);
            history.addFirst(new Entry(current, currentAt));
        }
        current = "";
        trim();
    }

    // ───────────────────────── 조리중 ─────────────────────────
    /** @return 새로 추가되면 true (이미 조리중이거나 방금 호출한 번호면 false) */
    synchronized boolean addWaiting(String n, String items) {
        if (!Net.validNumber(n)) return false;
        for (Waiting w : waiting) if (w.n.equals(n)) return false;
        if (n.equals(current) && System.currentTimeMillis() - currentAt < 30 * 60_000L) return false;
        waiting.addLast(new Waiting(n, items, System.currentTimeMillis()));
        while (waiting.size() > MAX_WAITING) waiting.removeFirst();
        return true;
    }

    synchronized boolean removeWaiting(String n) {
        Iterator<Waiting> it = waiting.iterator();
        while (it.hasNext()) {
            if (it.next().n.equals(n)) { it.remove(); return true; }
        }
        return false;
    }

    /** 가장 먼저 들어온 조리중 번호 (없으면 null) */
    synchronized String oldestWaiting() {
        return waiting.isEmpty() ? null : waiting.getFirst().n;
    }

    synchronized List<Waiting> waiting() {
        return new ArrayList<>(waiting);
    }

    synchronized void clearWaiting() {
        waiting.clear();
    }

    synchronized String current() {
        return current;
    }

    synchronized long seq() {
        return seq;
    }

    synchronized void setKeepMinutes(int m) {
        keepMinutes = Math.max(0, m);
    }

    /** 만료되지 않은 최근 호출 (최신순) */
    synchronized List<String> history() {
        prune();
        List<String> out = new ArrayList<>();
        for (Entry e : history) out.add(e.n);
        return out;
    }

    // ───────────────────────── 저장 / 복원 ─────────────────────────
    /** 형식: seq|current|currentAt|번호:시각,번호:시각,...|조리중(번호:시각:메뉴,...) */
    synchronized String save() {
        StringBuilder b = new StringBuilder();
        b.append(seq).append('|').append(current).append('|').append(currentAt).append('|');
        boolean first = true;
        for (Entry e : history) {
            if (!first) b.append(',');
            b.append(e.n).append(':').append(e.at);
            first = false;
        }
        b.append('|');
        first = true;
        for (Waiting w : waiting) {
            if (!first) b.append(',');
            b.append(w.n).append(':').append(w.at).append(':').append(enc(w.items));
            first = false;
        }
        return b.toString();
    }

    synchronized void load(String s) {
        if (s == null || s.isEmpty()) return;
        try {
            String[] p = s.split("\\|", -1);
            if (p.length < 4) return;
            seq = Long.parseLong(p[0]);
            current = Net.validNumber(p[1]) ? p[1] : "";
            currentAt = Long.parseLong(p[2]);
            history.clear();
            if (!p[3].isEmpty()) {
                for (String it : p[3].split(",")) {
                    int c = it.lastIndexOf(':');
                    if (c <= 0) continue;
                    String n = it.substring(0, c);
                    if (Net.validNumber(n)) history.add(new Entry(n, Long.parseLong(it.substring(c + 1))));
                }
            }
            waiting.clear();
            if (p.length >= 5 && !p[4].isEmpty()) {
                for (String it : p[4].split(",")) {
                    String[] w = it.split(":", 3);
                    if (w.length >= 2 && Net.validNumber(w[0])) {
                        waiting.add(new Waiting(w[0], w.length > 2 ? dec(w[2]) : "", Long.parseLong(w[1])));
                    }
                }
            }
            trim();
        } catch (Exception ignored) {
            // 저장값이 깨졌으면 빈 상태로 시작
        }
    }

    private static String enc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    private static String dec(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    private void prune() {
        if (keepMinutes <= 0) return;
        long limit = System.currentTimeMillis() - keepMinutes * 60_000L;
        Iterator<Entry> it = history.iterator();
        while (it.hasNext()) if (it.next().at < limit) it.remove();
    }

    private void remove(String n) {
        Iterator<Entry> it = history.iterator();
        while (it.hasNext()) if (it.next().n.equals(n)) it.remove();
    }

    private void trim() {
        prune();
        while (history.size() > MAX_HISTORY) history.removeLast();
    }
}
