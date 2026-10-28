package com.store.didcall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * '내 목소리' 녹음 화면: 숫자·단위·문장 조각을 녹음하면
 * 호출기 서버가 번호에 맞게 이어 붙여 TV·PC 화면에서 재생합니다.
 */
final class VoiceRecorder {

    interface Host {
        void msg(String s);
        /** 호출기 서버로 GET 요청 (백그라운드) 후 완료되면 onDone 실행(화면 스레드) */
        void call(String pathAndQuery, Runnable onDone);
        /** 미리 듣기: 주소의 소리를 재생 */
        void play(String pathAndQuery);
    }

    private static final int RATE = Sound.RATE;
    private static final int MAX_SECONDS = 20;

    private final Activity a;
    private final Host host;
    private final File dir;
    private final Handler ui = new Handler(Looper.getMainLooper());

    VoiceRecorder(Activity a, Host host) {
        this.a = a;
        this.host = host;
        this.dir = HostService.recDir(a);
    }

    private static final class Clip {
        final String key, title, say;
        Clip(String key, String title, String say) { this.key = key; this.title = title; this.say = say; }
    }

    private List<Clip> clips(String template, String closeMsg) {
        List<Clip> out = new ArrayList<>();
        int at = template.indexOf("{n}");
        String before = at > 0 ? template.substring(0, at).trim() : "";
        String after = at >= 0 ? template.substring(at + 3).trim() : template.trim();
        if (!before.isEmpty()) out.add(new Clip("prefix", "번호 앞 문장", before));
        for (String[] c : Sound.CLIPS) out.add(new Clip(c[0], "숫자 '" + c[1] + "'", c[1]));
        out.add(new Clip("suffix", "번호 뒤 문장", after));
        out.add(new Clip("closing", "영업 마감 멘트 (선택)", closeMsg));
        return out;
    }

    private boolean has(String key) {
        return new File(dir, key + ".wav").isFile();
    }

    /** 녹음 목록 화면 */
    void showList(final String template, final String closeMsg, final boolean recMode) {
        final List<Clip> list = clips(template, closeMsg);
        int done = 0, need = 0;
        for (Clip c : list) {
            if (c.key.equals("closing") || c.key.equals("prefix")) continue;
            need++;
            if (has(c.key)) done++;
        }
        List<String> items = new ArrayList<>();
        items.add("음성 방식: " + (recMode ? "🎙 내 목소리 (누르면 기계 음성으로)" : "🤖 기계 음성 (누르면 내 목소리로)"));
        items.add("▶ 51번 호출 미리 듣기 (현재 방식)");
        for (Clip c : list) items.add((has(c.key) ? "✔ " : "○ ") + c.title + (c.key.length() == 2 ? "" : "  —  " + shorten(c.say)));
        items.add("🗑 녹음 모두 지우기");
        final int base = 2;
        final boolean ready = done == need;
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("내 목소리 녹음 (" + done + "/" + need + ")" + (ready ? " — 준비 완료" : ""))
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        if (!recMode && !ready) {
                            host.msg("먼저 숫자와 '번호 뒤 문장'을 모두 녹음해 주세요 (" + done + "/" + need + ")");
                            showList(template, closeMsg, false);
                            return;
                        }
                        host.call("/config?voiceMode=" + (recMode ? "tts" : "rec"), () -> {
                            host.msg(recMode ? "기계 음성으로 바꿨습니다" : "내 목소리로 바꿨습니다");
                            showList(template, closeMsg, !recMode);
                        });
                    } else if (which == 1) {
                        host.play("/voice?n=51");
                        showList(template, closeMsg, recMode);
                    } else if (which == base + list.size()) {
                        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                                .setMessage("녹음한 목소리를 모두 지울까요? 음성 방식은 기계 음성으로 바뀝니다.")
                                .setPositiveButton("지우기", (d2, w2) -> {
                                    File[] fs = dir.listFiles();
                                    if (fs != null) for (File f : fs) {
                                        //noinspection ResultOfMethodCallIgnored
                                        f.delete();
                                    }
                                    host.call("/config?voiceMode=tts&recVer=1", () -> host.msg("녹음을 모두 지웠습니다"));
                                })
                                .setNegativeButton("취소", null)
                                .show();
                    } else {
                        record(list.get(which - base), () -> showList(template, closeMsg, recMode));
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private static String shorten(String s) {
        return s.length() > 22 ? s.substring(0, 22) + "…" : s;
    }

    // ───────────────────────── 한 조각 녹음 ─────────────────────────
    private volatile boolean recording;
    private short[] captured;
    private short[] cleaned;

    private void record(final Clip clip, final Runnable after) {
        if (Build.VERSION.SDK_INT >= 23
                && a.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            a.requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 2);
            host.msg("마이크 사용을 허용한 뒤 다시 눌러 주세요");
            return;
        }
        cleaned = null;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(a, 20);
        box.setPadding(pad, pad / 2, pad, 0);

        TextView guide = Ui.text(a, "버튼을 누르고 아래 말을 한 번 또렷하게 말한 뒤 [정지]를 누르세요.", 15, Ui.MUTED, false);
        box.addView(guide);
        TextView say = Ui.text(a, "“" + clip.say + "”", clip.say.length() <= 2 ? 44 : 22, Ui.YELLOW, true);
        say.setGravity(Gravity.CENTER);
        say.setPadding(0, pad / 2, 0, pad / 2);
        box.addView(say);
        final TextView status = Ui.text(a, has(clip.key) ? "이미 녹음돼 있습니다. 다시 녹음하면 바뀝니다." : "아직 녹음하지 않았습니다.", 14, Ui.TEXT, false);
        status.setGravity(Gravity.CENTER);
        box.addView(status);

        LinearLayout row = new LinearLayout(a);
        row.setPadding(0, pad / 2, 0, 0);
        final Button rec = Ui.button(a, "● 녹음", 0xFFD9363E, 18);
        final Button play = Ui.button(a, "▶ 들어보기", Ui.KEY2, 18);
        play.setEnabled(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(a, 56), 1f);
        lp.setMargins(Ui.dp(a, 4), 0, Ui.dp(a, 4), 0);
        row.addView(rec, lp);
        row.addView(play, new LinearLayout.LayoutParams(lp));
        box.addView(row);

        final AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(clip.title + " 녹음")
                .setView(box)
                .setPositiveButton("저장", null)
                .setNegativeButton("취소", (d, w) -> { recording = false; after.run(); })
                .setCancelable(false)
                .create();
        dlg.setOnShowListener(x -> {
            final Button save = dlg.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setEnabled(false);
            save.setOnClickListener(v -> {
                if (cleaned == null) return;
                try {
                    Sound.writeWav(new File(dir, clip.key + ".wav"), cleaned, RATE);
                    host.call("/config?recVer=1", () -> host.msg("✔ " + clip.title + " 저장됨"));
                    dlg.dismiss();
                    after.run();
                } catch (Exception e) {
                    status.setText("저장하지 못했습니다");
                }
            });
            rec.setOnClickListener(v -> {
                if (!recording) {
                    startRecording(status, () -> {
                        // 녹음이 끝나면(정지·최대 시간) 다듬기
                        rec.setText("● 다시 녹음");
                        cleaned = Sound.cleanRecording(captured, RATE);
                        if (cleaned == null) {
                            status.setText("소리가 너무 작거나 없습니다. 휴대폰 마이크 가까이에서 다시 녹음해 주세요.");
                            play.setEnabled(false);
                            save.setEnabled(false);
                        } else {
                            status.setText(String.format(java.util.Locale.KOREA, "녹음됨 (%.1f초) — 들어보고 [저장]을 누르세요", cleaned.length / (double) RATE));
                            play.setEnabled(true);
                            save.setEnabled(true);
                        }
                    });
                    rec.setText("■ 정지");
                    play.setEnabled(false);
                    save.setEnabled(false);
                } else {
                    recording = false;
                }
            });
            play.setOnClickListener(v -> playPcm(cleaned));
        });
        dlg.show();
    }

    private void startRecording(final TextView status, final Runnable onStop) {
        recording = true;
        new Thread(() -> {
            AudioRecord ar = null;
            short[] all = new short[RATE * MAX_SECONDS];
            int n = 0;
            try {
                int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                ar = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE / 2));
                ar.startRecording();
                short[] buf = new short[RATE / 10];
                long lastUi = 0;
                while (recording && n < all.length) {
                    int r = ar.read(buf, 0, Math.min(buf.length, all.length - n));
                    if (r <= 0) break;
                    System.arraycopy(buf, 0, all, n, r);
                    n += r;
                    long now = System.currentTimeMillis();
                    if (now - lastUi > 150) {
                        lastUi = now;
                        int peak = 0;
                        for (int i = 0; i < r; i++) peak = Math.max(peak, Math.abs((int) buf[i]));
                        final int bars = Math.min(20, peak * 20 / 20000);
                        final double sec = n / (double) RATE;
                        ui.post(() -> status.setText(String.format(java.util.Locale.KOREA, "녹음 중 %.1f초  %s", sec,
                                new String(new char[bars]).replace('\0', '▮'))));
                    }
                }
            } catch (Exception e) {
                ui.post(() -> status.setText("마이크를 사용할 수 없습니다"));
            } finally {
                if (ar != null) {
                    try { ar.stop(); } catch (Exception ignored) {}
                    ar.release();
                }
            }
            short[] got = new short[n];
            System.arraycopy(all, 0, got, 0, n);
            captured = got;
            recording = false;
            ui.post(onStop);
        }, "didcall-rec").start();
    }

    @SuppressWarnings("deprecation")
    private void playPcm(short[] pcm) {
        if (pcm == null || pcm.length == 0) return;
        try {
            final AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, pcm.length * 2, AudioTrack.MODE_STATIC);
            at.write(pcm, 0, pcm.length);
            at.play();
            ui.postDelayed(() -> {
                try { at.stop(); } catch (Exception ignored) {}
                at.release();
            }, pcm.length * 1000L / RATE + 500);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unused")
    private static TextView big(TextView t, float sp) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        return t;
    }
}
