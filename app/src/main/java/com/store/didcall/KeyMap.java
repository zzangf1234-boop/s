package com.store.didcall;

import android.content.SharedPreferences;
import android.view.KeyEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 키패드·키보드 키 설정 (호출기 앱과 브라우저 호출 페이지가 함께 사용).
 * 키 이름은 브라우저의 KeyboardEvent.code 와 같은 이름을 씁니다 (예: NumpadEnter, NumpadAdd, Backspace).
 * 저장 형식: "call=NumpadEnter,Enter;next=NumpadAdd;..."
 * 숫자 키(텐키·윗줄 숫자)는 동작에 지정하지 않는 한 항상 번호 입력입니다.
 */
final class KeyMap {
    static final String K_KEYS = "keys";

    static final String[] ACTIONS = {"call", "next", "recall", "blank", "range", "clear", "back"};
    static final String[] LABELS = {"호출", "다음 번호 입력", "재호출", "화면 비우기", "연속 호출 ~ (예: 1~4)", "입력 전체 삭제", "한 칸 지우기"};

    /** 텐키형 (백스페이스 없음): . = 한 칸 지우기 */
    static final String TENKEY = "call=NumpadEnter,Enter;next=NumpadAdd;recall=NumpadMultiply;blank=NumpadDivide;"
            + "range=NumpadSubtract,Minus;clear=Escape;back=NumpadDecimal,NumpadComma,Backspace";
    /** 백스페이스형: Backspace = 한 칸 지우기, . = 전체 삭제 */
    static final String BACKSPACE = "call=NumpadEnter,Enter;next=NumpadAdd;recall=NumpadMultiply;blank=NumpadDivide;"
            + "range=NumpadSubtract,Minus;clear=NumpadDecimal,NumpadComma,Escape;back=Backspace";

    private KeyMap() {}

    static String current(SharedPreferences p) {
        String s = p.getString(K_KEYS, null);
        if (s != null && !s.isEmpty()) {
            if (!s.contains("range=")) {
                // v1.2 이전 설정: '-' 키를 연속 호출(~)로 옮김
                Map<String, List<String>> m = parse(s);
                assign(m, "range", "NumpadSubtract");
                assign(m, "range", "Minus");
                s = serialize(m);
                p.edit().putString(K_KEYS, s).apply();
            }
            return s;
        }
        // 이전 버전의 '키패드 종류' 설정을 이어받음
        return "backspace".equals(p.getString(HostService.K_KEYPAD, "tenkey")) ? BACKSPACE : TENKEY;
    }

    static Map<String, List<String>> parse(String s) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (String a : ACTIONS) m.put(a, new ArrayList<>());
        if (s == null) return m;
        for (String part : s.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            List<String> list = m.get(part.substring(0, eq).trim());
            if (list == null) continue;
            for (String c : part.substring(eq + 1).split(",")) {
                c = c.trim();
                if (!c.isEmpty() && !list.contains(c)) list.add(c);
            }
        }
        return m;
    }

    static String serialize(Map<String, List<String>> m) {
        StringBuilder b = new StringBuilder();
        for (String a : ACTIONS) {
            if (b.length() > 0) b.append(';');
            b.append(a).append('=');
            List<String> l = m.get(a);
            if (l != null) b.append(join(l, ","));
        }
        return b.toString();
    }

    /** 브라우저에서 받은 설정값 검사 (허용 문자·알려진 동작만) */
    static String sanitize(String s) {
        if (s == null || s.length() > 2000 || !s.matches("[A-Za-z0-9_:,;=]*")) return null;
        return serialize(parse(s));
    }

    static String actionFor(Map<String, List<String>> m, String code) {
        for (Map.Entry<String, List<String>> e : m.entrySet()) if (e.getValue().contains(code)) return e.getKey();
        return null;
    }

    /** 키를 다른 동작에서 빼고 지정한 동작에 추가 (한 키 = 한 동작) */
    static void assign(Map<String, List<String>> m, String action, String code) {
        for (List<String> l : m.values()) l.remove(code);
        List<String> l = m.get(action);
        if (l != null) l.add(code);
    }

    static String digitOf(String code) {
        if (code.matches("(Numpad|Digit)[0-9]")) return code.substring(code.length() - 1);
        return null;
    }

    /** 단독으로는 동작에 쓰지 않는 키 */
    static boolean isModifier(String code) {
        return code.startsWith("Shift") || code.startsWith("Control") || code.startsWith("Alt")
                || code.startsWith("Meta") || code.equals("NumLock") || code.equals("CapsLock")
                || code.equals("ScrollLock") || code.equals("Android:KEYCODE_BACK")
                || code.equals("Android:KEYCODE_HOME") || code.startsWith("Android:KEYCODE_VOLUME");
    }

    static String label(String code) {
        if (code.startsWith("Numpad")) {
            String r = code.substring(6);
            switch (r) {
                case "Enter": return "텐키 Enter";
                case "Add": return "텐키 +";
                case "Subtract": return "텐키 -";
                case "Multiply": return "텐키 *";
                case "Divide": return "텐키 /";
                case "Decimal": return "텐키 .";
                case "Comma": return "텐키 ,";
                case "Equal": return "텐키 =";
                default: return "텐키 " + r;
            }
        }
        if (code.startsWith("Key") && code.length() == 4) return code.substring(3);
        if (code.startsWith("Digit")) return code.substring(5);
        switch (code) {
            case "Backspace": return "← 백스페이스";
            case "Escape": return "Esc";
            case "Enter": return "Enter";
            case "Delete": return "Del";
            case "Space": return "스페이스";
            case "Minus": return "-";
            case "Equal": return "=";
            case "Period": return ".";
            case "Comma": return ",";
            case "Slash": return "/";
            case "ArrowUp": return "↑";
            case "ArrowDown": return "↓";
            case "ArrowLeft": return "←(화살표)";
            case "ArrowRight": return "→";
            default: return code.startsWith("Android:KEYCODE_") ? code.substring(16) : code;
        }
    }

    static String describe(Map<String, List<String>> m, String action) {
        List<String> l = m.get(action);
        if (l == null || l.isEmpty()) return "(없음)";
        List<String> names = new ArrayList<>();
        for (String c : l) names.add(label(c));
        return join(names, ", ");
    }

    private static String join(List<String> l, String sep) {
        StringBuilder b = new StringBuilder();
        for (String x : l) { if (b.length() > 0) b.append(sep); b.append(x); }
        return b.toString();
    }

    // ───────────────────────── 안드로이드 키 → 키 이름 ─────────────────────────
    /** 물리 키 위치(리눅스 스캔 코드) 기준. NumLock 상태와 상관없이 같은 이름 */
    private static final Map<Integer, String> SCAN = new LinkedHashMap<>();
    static {
        String[] digits = {"Digit1", "Digit2", "Digit3", "Digit4", "Digit5", "Digit6", "Digit7", "Digit8", "Digit9", "Digit0"};
        for (int i = 0; i < 10; i++) SCAN.put(2 + i, digits[i]);
        String row1 = "QWERTYUIOP", row2 = "ASDFGHJKL", row3 = "ZXCVBNM";
        for (int i = 0; i < row1.length(); i++) SCAN.put(16 + i, "Key" + row1.charAt(i));
        for (int i = 0; i < row2.length(); i++) SCAN.put(30 + i, "Key" + row2.charAt(i));
        for (int i = 0; i < row3.length(); i++) SCAN.put(44 + i, "Key" + row3.charAt(i));
        for (int i = 0; i < 10; i++) SCAN.put(59 + i, "F" + (i + 1));
        Object[][] t = {
                {1, "Escape"}, {12, "Minus"}, {13, "Equal"}, {14, "Backspace"}, {15, "Tab"},
                {26, "BracketLeft"}, {27, "BracketRight"}, {28, "Enter"}, {29, "ControlLeft"},
                {39, "Semicolon"}, {40, "Quote"}, {41, "Backquote"}, {42, "ShiftLeft"}, {43, "Backslash"},
                {51, "Comma"}, {52, "Period"}, {53, "Slash"}, {54, "ShiftRight"}, {55, "NumpadMultiply"},
                {56, "AltLeft"}, {57, "Space"}, {58, "CapsLock"}, {69, "NumLock"}, {70, "ScrollLock"},
                {71, "Numpad7"}, {72, "Numpad8"}, {73, "Numpad9"}, {74, "NumpadSubtract"},
                {75, "Numpad4"}, {76, "Numpad5"}, {77, "Numpad6"}, {78, "NumpadAdd"},
                {79, "Numpad1"}, {80, "Numpad2"}, {81, "Numpad3"}, {82, "Numpad0"}, {83, "NumpadDecimal"},
                {87, "F11"}, {88, "F12"}, {96, "NumpadEnter"}, {97, "ControlRight"}, {98, "NumpadDivide"},
                {100, "AltRight"}, {102, "Home"}, {103, "ArrowUp"}, {104, "PageUp"}, {105, "ArrowLeft"},
                {106, "ArrowRight"}, {107, "End"}, {108, "ArrowDown"}, {109, "PageDown"}, {110, "Insert"},
                {111, "Delete"}, {117, "NumpadEqual"}, {121, "NumpadComma"}, {140, "LaunchApp2"},
        };
        for (Object[] r : t) SCAN.put((Integer) r[0], (String) r[1]);
    }

    static String codeOf(KeyEvent e) {
        String s = SCAN.get(e.getScanCode());
        if (s != null) return s;
        int k = e.getKeyCode();
        if (k >= KeyEvent.KEYCODE_NUMPAD_0 && k <= KeyEvent.KEYCODE_NUMPAD_9) return "Numpad" + (k - KeyEvent.KEYCODE_NUMPAD_0);
        if (k >= KeyEvent.KEYCODE_0 && k <= KeyEvent.KEYCODE_9) return "Digit" + (k - KeyEvent.KEYCODE_0);
        if (k >= KeyEvent.KEYCODE_A && k <= KeyEvent.KEYCODE_Z) return "Key" + (char) ('A' + k - KeyEvent.KEYCODE_A);
        switch (k) {
            case KeyEvent.KEYCODE_ENTER: return "Enter";
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return "NumpadEnter";
            case KeyEvent.KEYCODE_DEL: return "Backspace";
            case KeyEvent.KEYCODE_FORWARD_DEL: return "Delete";
            case KeyEvent.KEYCODE_ESCAPE: return "Escape";
            case KeyEvent.KEYCODE_NUMPAD_ADD: return "NumpadAdd";
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: return "NumpadSubtract";
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY: return "NumpadMultiply";
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE: return "NumpadDivide";
            case KeyEvent.KEYCODE_NUMPAD_DOT: return "NumpadDecimal";
            case KeyEvent.KEYCODE_NUMPAD_COMMA: return "NumpadComma";
            case KeyEvent.KEYCODE_MINUS: return "Minus";
            case KeyEvent.KEYCODE_EQUALS: return "Equal";
            case KeyEvent.KEYCODE_PERIOD: return "Period";
            case KeyEvent.KEYCODE_COMMA: return "Comma";
            case KeyEvent.KEYCODE_SLASH: return "Slash";
            case KeyEvent.KEYCODE_SPACE: return "Space";
            case KeyEvent.KEYCODE_SHIFT_LEFT: return "ShiftLeft";
            case KeyEvent.KEYCODE_SHIFT_RIGHT: return "ShiftRight";
            case KeyEvent.KEYCODE_NUM_LOCK: return "NumLock";
            default: return "Android:" + KeyEvent.keyCodeToString(k);
        }
    }
}
