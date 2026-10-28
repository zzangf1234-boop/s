package com.store.didcall;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/** 현재 호출 번호와 최근 호출 목록 (서버 스레드와 화면 스레드가 함께 사용) */
final class CallStore {
    private static final int MAX_HISTORY = 6;

    private String current = "";
    private final LinkedList<String> history = new LinkedList<>();

    synchronized void call(String n) {
        if (!current.isEmpty() && !current.equals(n)) {
            history.remove(current);
            history.addFirst(current);
        }
        history.remove(n);
        current = n;
        trim();
    }

    synchronized void clear() {
        if (!current.isEmpty()) {
            history.remove(current);
            history.addFirst(current);
        }
        current = "";
        trim();
    }

    synchronized String current() {
        return current;
    }

    synchronized List<String> history() {
        return new ArrayList<>(history);
    }

    private void trim() {
        while (history.size() > MAX_HISTORY) history.removeLast();
    }
}
