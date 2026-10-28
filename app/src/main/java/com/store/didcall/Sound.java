package com.store.didcall;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 소리 도구 모음 (안드로이드 API 없이 순수 자바)
 *  - 알림음 합성 (여러 종류)
 *  - WAV 읽기/쓰기
 *  - 녹음 조각 다듬기(앞뒤 무음 자르기·소리 크기 맞추기)
 *  - '내 목소리' 모드: 번호를 한국어로 읽는 순서대로 녹음 조각 이어 붙이기
 */
final class Sound {
    static final int RATE = 22050;

    /** 알림음 종류: 설정값, 표시 이름 */
    static final String[][] CHIMES = {
            {"dingdong", "딩동 (기본)"},
            {"tri", "띵동댕 (3음)"},
            {"xylo", "실로폰"},
            {"bell", "차임벨"},
            {"ding", "띵~ (한 음)"},
            {"beep", "삐빅 (짧게)"},
    };

    private Sound() {}

    // ───────────────────────── 알림음 합성 ─────────────────────────
    static short[] chime(String type) {
        if (type == null) type = "dingdong";
        switch (type) {
            case "tri":  // 도-미-솔 (띵동댕)
                return notes(new double[]{523.25, 659.25, 783.99}, 0.42, 1.3, 3.0, new double[]{1, .25, .06});
            case "xylo": // 솔-미-도-솔 실로폰 (빠른 감쇠, 높은 배음)
                return notes(new double[]{783.99, 659.25, 523.25, 783.99}, 0.18, 0.9, 7.0, new double[]{1, 0, 0, .35});
            case "bell": // 종소리 (비정수 배음, 긴 여운) 두 번
                return bell();
            case "ding": // 한 음 길게
                return notes(new double[]{880.0}, 0.0, 2.2, 2.2, new double[]{1, .3, .1});
            case "beep": // 짧은 삐빅
                return beep();
            default:     // 딩동: 미-도
                return notes(new double[]{659.25, 523.25}, 0.6, 1.2, 3.2, new double[]{.7 / .7, .2 / .7, .05 / .7});
        }
    }

    /** 음들을 gap 간격으로 차례로 울리고 각 음은 decay 만큼 감쇠 */
    private static short[] notes(double[] freqs, double gap, double tail, double decay, double[] partials) {
        int total = (int) (RATE * (gap * (freqs.length - 1) + tail));
        double[] mix = new double[total];
        for (int k = 0; k < freqs.length; k++) {
            int start = (int) (RATE * gap * k);
            for (int i = start; i < total; i++) {
                double t = (double) (i - start) / RATE;
                double env = Math.exp(-decay * t) * Math.min(1.0, (i - start) / 120.0);
                double v = 0;
                for (int h = 0; h < partials.length; h++) {
                    if (partials[h] != 0) v += Math.sin(2 * Math.PI * freqs[k] * (h + 1) * t) * partials[h];
                }
                mix[i] += v * env;
            }
        }
        return normalize(mix, 0.75);
    }

    private static short[] bell() {
        double[] ratios = {1.0, 2.0, 2.4, 3.0, 4.2, 5.4};
        double[] amps = {1.0, .55, .35, .25, .18, .1};
        double base = 587.33; // 레
        double[] strikes = {0.0, 0.9};
        int total = (int) (RATE * 3.2);
        double[] mix = new double[total];
        for (int s = 0; s < strikes.length; s++) {
            int start = (int) (RATE * strikes[s]);
            double f0 = s == 0 ? base : base * 0.8909; // 두 번째는 시(아래)
            for (int i = start; i < total; i++) {
                double t = (double) (i - start) / RATE;
                double v = 0;
                for (int h = 0; h < ratios.length; h++) {
                    v += Math.sin(2 * Math.PI * f0 * ratios[h] * t) * amps[h] * Math.exp(-(1.2 + h * 0.6) * t);
                }
                mix[i] += v * Math.min(1.0, (i - start) / 60.0);
            }
        }
        return normalize(mix, 0.7);
    }

    private static short[] beep() {
        int one = (int) (RATE * 0.11), gap = (int) (RATE * 0.07);
        double[] mix = new double[one * 2 + gap + RATE / 10];
        for (int b = 0; b < 2; b++) {
            int start = b * (one + gap);
            for (int i = 0; i < one; i++) {
                double t = (double) i / RATE;
                double env = Math.min(1.0, i / 80.0) * Math.min(1.0, (one - i) / 80.0);
                mix[start + i] = (Math.sin(2 * Math.PI * 1318.5 * t) + Math.sin(2 * Math.PI * 1318.5 * 3 * t) * 0.15) * env;
            }
        }
        return normalize(mix, 0.6);
    }

    private static short[] normalize(double[] mix, double peak) {
        double max = 1e-9;
        for (double v : mix) max = Math.max(max, Math.abs(v));
        short[] out = new short[mix.length];
        for (int i = 0; i < mix.length; i++) out[i] = (short) (mix[i] / max * peak * 32767);
        return out;
    }

    // ───────────────────────── WAV ─────────────────────────
    static byte[] toWav(short[] pcm, int rate) {
        int dataLen = pcm.length * 2;
        ByteArrayOutputStream b = new ByteArrayOutputStream(44 + dataLen);
        writeStr(b, "RIFF"); writeInt(b, 36 + dataLen); writeStr(b, "WAVE");
        writeStr(b, "fmt "); writeInt(b, 16); writeShort(b, 1); writeShort(b, 1);
        writeInt(b, rate); writeInt(b, rate * 2); writeShort(b, 2); writeShort(b, 16);
        writeStr(b, "data"); writeInt(b, dataLen);
        for (short v : pcm) writeShort(b, v);
        return b.toByteArray();
    }

    static void writeWav(File f, short[] pcm, int rate) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(toWav(pcm, rate));
        }
    }

    /** 16비트 모노 PCM WAV 읽기 (이 앱이 만든 녹음 파일용). 형식이 다르면 null */
    static short[] readWav(File f) {
        if (f == null || !f.isFile() || f.length() < 44) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] all = new byte[(int) f.length()];
            int off = 0, r;
            while (off < all.length && (r = in.read(all, off, all.length - off)) > 0) off += r;
            if (!(all[0] == 'R' && all[1] == 'I' && all[8] == 'W' && all[9] == 'A')) return null;
            int pos = 12, channels = 1, bits = 16;
            while (pos + 8 <= off) {
                String id = new String(all, pos, 4, "US-ASCII");
                int size = (all[pos + 4] & 0xFF) | (all[pos + 5] & 0xFF) << 8 | (all[pos + 6] & 0xFF) << 16 | (all[pos + 7] & 0xFF) << 24;
                if (id.equals("fmt ")) {
                    channels = (all[pos + 10] & 0xFF) | (all[pos + 11] & 0xFF) << 8;
                    bits = (all[pos + 22] & 0xFF) | (all[pos + 23] & 0xFF) << 8;
                } else if (id.equals("data")) {
                    if (channels != 1 || bits != 16) return null;
                    int n = Math.min(size, off - pos - 8) / 2;
                    short[] pcm = new short[n];
                    for (int i = 0; i < n; i++) {
                        int p = pos + 8 + i * 2;
                        pcm[i] = (short) ((all[p] & 0xFF) | (all[p + 1] << 8));
                    }
                    return pcm;
                }
                pos += 8 + size + (size & 1);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private static void writeStr(ByteArrayOutputStream b, String s) { for (char c : s.toCharArray()) b.write(c); }
    private static void writeInt(ByteArrayOutputStream b, int v) { b.write(v); b.write(v >> 8); b.write(v >> 16); b.write(v >> 24); }
    private static void writeShort(ByteArrayOutputStream b, int v) { b.write(v); b.write(v >> 8); }

    // ───────────────────────── 녹음 다듬기 ─────────────────────────
    /** 앞뒤 무음 자르기(여유 60ms 남김) + 최대 소리를 일정하게 맞춤. 소리가 거의 없으면 null */
    static short[] cleanRecording(short[] pcm, int rate) {
        if (pcm == null || pcm.length == 0) return null;
        int max = 0;
        for (short v : pcm) max = Math.max(max, Math.abs((int) v));
        if (max < 800) return null; // 너무 작음 (말소리 없음)
        int thr = Math.max(500, max / 12);
        int win = rate / 100; // 10ms 단위로 판단
        int start = -1, end = -1;
        for (int i = 0; i + win <= pcm.length; i += win) {
            int peak = 0;
            for (int k = i; k < i + win; k++) peak = Math.max(peak, Math.abs((int) pcm[k]));
            if (peak >= thr) {
                if (start < 0) start = i;
                end = i + win;
            }
        }
        if (start < 0) return null;
        int pad = rate * 6 / 100;
        start = Math.max(0, start - pad);
        end = Math.min(pcm.length, end + pad);
        short[] out = new short[end - start];
        double gain = 0.89 * 32767 / max;
        for (int i = 0; i < out.length; i++) {
            double v = pcm[start + i] * gain;
            // 앞뒤 5ms 부드럽게
            int fade = rate / 200;
            if (i < fade) v *= (double) i / fade;
            if (out.length - i < fade) v *= (double) (out.length - i) / fade;
            out[i] = (short) Math.max(-32767, Math.min(32767, v));
        }
        return out;
    }

    // ───────────────────────── 내 목소리: 번호 읽기 ─────────────────────────
    /** 녹음 조각 키와 화면에 보일 이름 (녹음할 때 읽을 말) */
    static final String[][] CLIPS = {
            {"d1", "일"}, {"d2", "이"}, {"d3", "삼"}, {"d4", "사"}, {"d5", "오"},
            {"d6", "육"}, {"d7", "칠"}, {"d8", "팔"}, {"d9", "구"},
            {"ten", "십"}, {"hundred", "백"}, {"thousand", "천"},
    };

    /**
     * 번호를 한국어로 읽는 조각 순서 (예: 123 → 백, 이, 십, 삼 / 1005 → 천, 오).
     * 1~9999 의 숫자만 지원, 아니면 null
     */
    static List<String> readNumber(String n) {
        if (n == null || !n.matches("[0-9]{1,4}")) return null;
        int v = Integer.parseInt(n);
        if (v <= 0) return null;
        List<String> out = new ArrayList<>();
        int[] units = {1000, 100, 10, 1};
        String[] names = {"thousand", "hundred", "ten", null};
        for (int u = 0; u < 4; u++) {
            int d = v / units[u] % 10;
            if (d == 0) continue;
            if (names[u] == null) {
                out.add("d" + d);
            } else {
                if (d != 1) out.add("d" + d); // 일천·일백·일십 → 천·백·십
                out.add(names[u]);
            }
        }
        return out;
    }

    /**
     * 녹음 조각을 이어 붙여 안내 음성 만들기.
     * @param prefix 번호 앞 문장 조각 (없으면 null), suffix 번호 뒤 문장 조각
     * @return PCM, 필요한 조각이 하나라도 없으면 null
     */
    static short[] stitch(File clipDir, String number, boolean usePrefix) {
        List<String> parts = readNumber(number);
        if (parts == null) return null;
        List<short[]> seq = new ArrayList<>();
        List<Integer> gaps = new ArrayList<>();
        if (usePrefix) {
            short[] pre = readWav(new File(clipDir, "prefix.wav"));
            if (pre == null) return null;
            seq.add(pre);
            gaps.add(RATE * 15 / 100);
        }
        for (int i = 0; i < parts.size(); i++) {
            short[] c = readWav(new File(clipDir, parts.get(i) + ".wav"));
            if (c == null) return null;
            seq.add(c);
            gaps.add(i == parts.size() - 1 ? RATE * 8 / 100 : RATE * 2 / 100);
        }
        short[] suf = readWav(new File(clipDir, "suffix.wav"));
        if (suf == null) return null;
        seq.add(suf);
        gaps.add(0);
        int total = 0;
        for (int i = 0; i < seq.size(); i++) total += seq.get(i).length + gaps.get(i);
        short[] out = new short[total];
        int pos = 0;
        for (int i = 0; i < seq.size(); i++) {
            short[] c = seq.get(i);
            System.arraycopy(c, 0, out, pos, c.length);
            pos += c.length + gaps.get(i);
        }
        return out;
    }
}
