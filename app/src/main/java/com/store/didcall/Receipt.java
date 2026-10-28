package com.store.didcall;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 키오스크·포스가 프린터로 보내는 주문서(ESC/POS 명령 + CP949 글자)를 해석.
 *  - 프린터 명령 바이트를 걸러내고 글자만 남김
 *  - "주문번호: 50" 의 번호, "재발행" 여부, 메뉴 이름 추출
 */
final class Receipt {
    final String text;      // 사람이 읽을 수 있는 글자
    final String number;    // 주문번호 (없으면 null)
    final boolean reprint;  // 재발행 주문서
    final List<String> items; // 메뉴 이름 (옵션 줄 제외)

    private Receipt(String text, String number, boolean reprint, List<String> items) {
        this.text = text; this.number = number; this.reprint = reprint; this.items = items;
    }

    private static final Charset CP949 = koreanCharset();

    /** 한국어 프린터 글자 코드 (CP949, 없으면 EUC-KR) */
    private static Charset koreanCharset() {
        for (String name : new String[]{"MS949", "x-windows-949", "windows-949", "EUC-KR"}) {
            try { return Charset.forName(name); } catch (Exception ignored) {}
        }
        return Charset.forName("UTF-8");
    }

    static Receipt parse(byte[] raw, String label) {
        String text = new String(strip(raw), CP949);
        String lb = label == null || label.trim().isEmpty() ? "주문번호" : label.trim();
        // "주 문 번 호 : 50" 처럼 띄어 써도 찾음
        StringBuilder re = new StringBuilder();
        for (char c : lb.toCharArray()) {
            if (Character.isWhitespace(c)) continue;
            if (re.length() > 0) re.append("\\s*");
            re.append(Pattern.quote(String.valueOf(c)));
        }
        re.append("\\s*[:：]*\\s*(\\d{1,6})");
        Matcher m = Pattern.compile(re.toString()).matcher(text);
        String number = null;
        if (m.find()) {
            number = m.group(1).replaceFirst("^0+(?=\\d)", ""); // 앞자리 0 제거 (007 → 7)
        }
        boolean reprint = Pattern.compile("재\\s*발\\s*행").matcher(text).find();
        return new Receipt(text, number, reprint, items(text));
    }

    /** 메뉴 줄: 구분선 사이의 본문 중 옵션(-)·주문번호·시간·PosNo 줄을 뺀 것 */
    private static List<String> items(String text) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n")) {
            String l = line.trim();
            if (l.isEmpty() || l.startsWith("-") || l.startsWith("=") || l.startsWith("[") || l.startsWith("(")) continue;
            if (l.matches("[-=_*\\s]{5,}")) continue;
            if (l.matches(".*(주\\s*문|번\\s*호|시\\s*간|PosNo|POS|합\\s*계|금\\s*액|사업자|대표자|TEL|전화|영수|판매|카\\s*드|부가세|공급가|Wifi|재발행).*")) continue;
            if (l.matches("[0-9\\s:./,()-]+")) continue; // 숫자·날짜·괄호만 있는 줄 (수량, 줄바꿈된 1) 등)
            if (l.matches("(포장|매장|신규|추가|취소|배달|먹고\\s*가기|테이크\\s*아웃)(\\s.*)?")) continue; // 포장·신규 표시 줄
            l = l.replaceAll("\\s{2,}.*$", "");          // 오른쪽 수량·금액 칸 제거
            l = l.replaceAll("[.,]$", "").trim();
            if (l.length() >= 2 && out.size() < 6) out.add(l);
        }
        return out;
    }

    /** 메뉴 요약: "프레쉬 바질버거 세트 외 2" */
    String summary() {
        if (items.isEmpty()) return "";
        String first = items.get(0);
        if (first.length() > 18) first = first.substring(0, 18) + "…";
        return items.size() == 1 ? first : first + " 외 " + (items.size() - 1);
    }

    /**
     * ESC/POS 명령 바이트 걸러내기. 글자(0x20 이상)와 줄바꿈만 남기고,
     * 명령 뒤에 붙는 숫자 인자·그림 데이터는 건너뜀.
     */
    static byte[] strip(byte[] b) {
        return strip(b, null);
    }

    /** notes 가 있으면 그림·바코드 명령을 찾을 때마다 기록 (진단용) */
    static byte[] strip(byte[] b, StringBuilder notes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
        int i = 0, n = b.length;
        while (i < n) {
            int c = b[i] & 0xFF;
            if (c == 0x1B && i + 1 < n) {             // ESC
                int cmd = b[i + 1] & 0xFF;
                i += 2;
                switch (cmd) {
                    case '@': case '2': case '<': case 'i': case 'm': break;
                    case 'p': i += 3; break;          // 금전함
                    case '*': {                        // 비트 이미지
                        if (i + 2 < n) {
                            int mode = b[i] & 0xFF, len = (b[i + 1] & 0xFF) + (b[i + 2] & 0xFF) * 256;
                            i += 3 + len * (mode >= 32 ? 3 : 1);
                            note(notes, "ESC * 비트 그림 (가로 " + len + "점)");
                        }
                        break;
                    }
                    case 'D': while (i < n && b[i] != 0) i++; i++; break; // 탭 위치 (NUL 까지)
                    case '$': case '\\': i += 2; break;
                    default: i += 1;                   // 대부분 인자 1개
                }
                out.write(' ');
                continue;
            }
            if (c == 0x1D && i + 1 < n) {             // GS
                int cmd = b[i + 1] & 0xFF;
                i += 2;
                switch (cmd) {
                    case 'V': {                        // 용지 자르기
                        int m = i < n ? b[i] & 0xFF : 0;
                        i += (m == 65 || m == 66 || m == 97 || m == 98 || m == 103 || m == 104) ? 2 : 1;
                        break;
                    }
                    case 'L': case 'W': case 'P': case '$': case '\\': i += 2; break;
                    case 'k': {                        // 바코드
                        int m = i < n ? b[i] & 0xFF : 0;
                        i++;
                        note(notes, "바코드");
                        if (m <= 6) { while (i < n && b[i] != 0) i++; i++; }
                        else if (i < n) { i += 1 + (b[i] & 0xFF); }
                        break;
                    }
                    case 'v': {                        // 래스터 이미지 GS v 0 m xL xH yL yH d...
                        if (i + 5 < n) {
                            int xw = (b[i + 2] & 0xFF) + (b[i + 3] & 0xFF) * 256;
                            int yh = (b[i + 4] & 0xFF) + (b[i + 5] & 0xFF) * 256;
                            i += 6 + xw * yh;
                            note(notes, "GS v 0 래스터 그림 " + (xw * 8) + "×" + yh + "점");
                        } else i = n;
                        break;
                    }
                    case '(': case '8': {              // GS ( fn pL pH ... (QR 등)
                        if (i + 2 < n) {
                            int len = (b[i + 1] & 0xFF) + (b[i + 2] & 0xFF) * 256;
                            note(notes, "GS " + (char) cmd + " " + (char) (b[i] & 0xFF) + " 블록 " + len + "바이트 (그림·QR 등)");
                            i += 3 + len;
                        } else i = n;
                        break;
                    }
                    default: i += 1;
                }
                out.write(' ');
                continue;
            }
            if (c == 0x1C && i + 1 < n) {             // FS (한글 모드 등)
                int cmd = b[i + 1] & 0xFF;
                i += 2;
                switch (cmd) {
                    case '&': case '.': break;
                    case 'S': i += 2; break;
                    case 'p': note(notes, "FS p 저장 그림 출력"); i += 2; break;
                    case '(': if (i + 2 < n) i += 3 + (b[i + 1] & 0xFF) + (b[i + 2] & 0xFF) * 256; else i = n; break;
                    default: i += 1;
                }
                continue;
            }
            if (c == 0x10 && i + 1 < n) {             // DLE (상태 묻기 등)
                int cmd = b[i + 1] & 0xFF;
                i += 2;
                if (cmd == 0x04 || cmd == 0x05) i += 1;
                else if (cmd == 0x14) i += 3;
                continue;
            }
            if (c == 0x0A) out.write('\n');
            else if (c >= 0x20) out.write(c);
            else out.write(' ');
            i++;
        }
        return out.toByteArray();
    }

    private static void note(StringBuilder notes, String s) {
        if (notes != null && notes.length() < 2000) notes.append("· ").append(s).append('\n');
    }

    /** 진단: 해석한 글자 + 그림 명령 목록 + 앞부분 16진수 */
    static String diagnose(byte[] raw, String label) {
        StringBuilder notes = new StringBuilder();
        String text = new String(strip(raw, notes), CP949);
        Receipt r = parse(raw, label);
        StringBuilder o = new StringBuilder();
        o.append("크기 ").append(raw.length).append("바이트 · 주문번호 ").append(r.number == null ? "못 찾음" : r.number)
                .append(r.reprint ? " · 재발행" : "").append("\n\n[찾은 그림·명령]\n")
                .append(notes.length() == 0 ? "없음\n" : notes.toString())
                .append("\n[읽은 글자]\n").append(text.trim()).append("\n\n[원본 앞부분 16진수]\n");
        int n = Math.min(raw.length, 1536);
        for (int i = 0; i < n; i++) {
            o.append(String.format(java.util.Locale.US, "%02X", raw[i] & 0xFF)).append(i % 16 == 15 ? '\n' : ' ');
        }
        if (raw.length > n) o.append("\n… (나머지 ").append(raw.length - n).append("바이트 생략)");
        return o.toString();
    }
}
