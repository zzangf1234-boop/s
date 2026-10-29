package com.store.didcall;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 앱에 들어 있는 녹음 목소리 (예: 승아). assets/voices/{id}/ 의 조각 WAV 를 이어 붙여 안내 음성을 만듦.
 * 조각 이름: h1~h9 = (일)백~구백, t1~t9 = 십~구십, 끝에 b 가 붙으면 "…번" (u1b~u9b = 일번~구번)
 *  예) 232 → h2 t3 u2b ("이백삼십이번") / 230 → h2 t3b / 100 → h1b / 7 → u7b
 * 문장: p_food(음식이 나왔습니다) p_drink p_pickup p_again, 마감: s_closing
 */
final class VoicePack {

    /** {아이디, 이름} */
    static final String[][] PACKS = {{"seunga", "승아"}};
    /** {조각 이름, 화면에 보일 문장} */
    static final String[][] PHRASES = {
            {"p_food", "손님, 주문하신 음식이 나왔습니다."},
            {"p_drink", "손님, 주문하신 음료가 나왔습니다."},
            {"p_pickup", "손님, 픽업대에서 받아 가 주세요."},
            {"p_again", "손님, 주문하신 메뉴가 준비되어 있습니다."},
    };
    static final String DEF_PHRASE = "p_food";

    interface Assets { InputStream open(String path) throws Exception; }

    private VoicePack() {}

    static boolean known(String id) {
        for (String[] p : PACKS) if (p[0].equals(id)) return true;
        return false;
    }

    static String name(String id) {
        for (String[] p : PACKS) if (p[0].equals(id)) return p[1];
        return id;
    }

    static boolean knownPhrase(String k) {
        for (String[] p : PHRASES) if (p[0].equals(k)) return true;
        return false;
    }

    /** "pack:seunga" → "seunga" (녹음 목소리 모드가 아니면 null) */
    static String fromMode(String mode) {
        if (mode == null || !mode.startsWith("pack:")) return null;
        String id = mode.substring(5);
        return known(id) ? id : null;
    }

    /** 1~999 → 조각 이름들, 그 밖이면 null */
    static List<String> numberKeys(String n) {
        if (n == null || !n.matches("[0-9]{1,3}")) return null;
        int v = Integer.parseInt(n);
        if (v < 1 || v > 999) return null;
        List<String> out = new ArrayList<>();
        int[] d = {v / 100, v / 10 % 10, v % 10};
        String[] k = {"h", "t", "u"};
        for (int i = 0; i < 3; i++) if (d[i] != 0) out.add(k[i] + d[i]);
        out.set(out.size() - 1, out.get(out.size() - 1) + "b");
        return out;
    }

    static short[] clip(Assets a, String pack, String key) {
        try (InputStream in = a.open("voices/" + pack + "/" + key + ".wav")) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) b.write(buf, 0, r);
            byte[] all = b.toByteArray();
            return Sound.parseWav(all, all.length);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 번호(또는 연속 호출 "1~4") + 문장. 지원하지 않는 번호(1000 이상·영문)면 null.
     * 번호 조각은 바로 이어 붙이고, 번호와 번호 사이 0.25초, 번호 뒤 0.12초 쉬고 문장.
     */
    static short[] build(Assets a, String pack, String number, String phrase) {
        List<short[]> seq = new ArrayList<>();
        List<Integer> gaps = new ArrayList<>();
        List<String> members = Net.members(number);
        if (members.isEmpty()) return null;
        for (int m = 0; m < members.size(); m++) {
            List<String> keys = numberKeys(members.get(m));
            if (keys == null) return null;
            for (int i = 0; i < keys.size(); i++) {
                short[] c = clip(a, pack, keys.get(i));
                if (c == null) return null;
                seq.add(c);
                boolean lastKey = i == keys.size() - 1;
                gaps.add(!lastKey ? 0 : m < members.size() - 1 ? Sound.RATE / 4 : Sound.RATE * 12 / 100);
            }
        }
        short[] p = clip(a, pack, knownPhrase(phrase) ? phrase : DEF_PHRASE);
        if (p == null) return null;
        seq.add(p);
        gaps.add(0);
        return join(seq, gaps);
    }

    static short[] join(List<short[]> seq, List<Integer> gaps) {
        int total = 0;
        for (int i = 0; i < seq.size(); i++) total += seq.get(i).length + gaps.get(i);
        short[] out = new short[total];
        int pos = 0;
        for (int i = 0; i < seq.size(); i++) {
            System.arraycopy(seq.get(i), 0, out, pos, seq.get(i).length);
            pos += seq.get(i).length + gaps.get(i);
        }
        return out;
    }
}
