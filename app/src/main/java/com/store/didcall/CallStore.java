package com.store.didcall;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * 현재 호출 번호와 최근 호출 목록 (서버 스레드와 화면 스레드가 함께 사용).
 * 최근 호출은 마지막으로 호출된 시각으로부터 keepMinutes 가 지나면 목록에서 사라짐.
 */
final class CallStore {
    private static final int MAX_HISTORY = 6;

    private static final class Entry {
        final String n;
        final long at;
        Entry(String n, long at) { this.n = n; this.at = at; }
    }

    private String current = "";
    private long currentAt;
    private long seq;               // 호출/재호출 때마다 1씩 증가 (브라우저 화면이 새 호출을 감지)
    private int keepMinutes = 5;    // 0 이면 사라지지 않음
    private final LinkedList<Entry> history = new LinkedList<>();

    synchronized void call(String n) {
        long now = System.currentTimeMillis();
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
