package com.store.didcall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** TV에 연결된 안드로이드 기기에서 실행: 번호를 받아 크게 표시하고 딩동 + 음성으로 호출 */
public class DisplayActivity extends Activity implements CallServer.Host {

    private static final String K_TTS = "tts";
    private static final String K_SUB = "sub";
    private static final String K_REPEAT = "repeat";
    private static final String DEF_TTS = "{n}번 고객님, 주문하신 메뉴가 준비되었습니다.";
    private static final String DEF_SUB = "주문하신 메뉴가 준비되었습니다";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final CallStore store = new CallStore();
    private CallServer server;
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private boolean ttsReady;
    private WifiManager.MulticastLock multicastLock;
    private short[] chimePcm;

    private LinearLayout numberPanel;
    private TextView numberView, subView, addrView;
    private final TextView[] historyViews = new TextView[6];
    private int screenH;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        screenH = Math.min(getResources().getDisplayMetrics().heightPixels,
                getResources().getDisplayMetrics().widthPixels);

        buildUi();
        chimePcm = buildChime();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.KOREAN);
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                tts.setSpeechRate(0.95f);
            }
        });

        // 일부 기기는 이 락이 있어야 브로드캐스트(자동 검색)를 받음
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                multicastLock = wm.createMulticastLock("didcall");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
        } catch (Exception ignored) {
        }

        server = new CallServer(this);
        server.start();
        render();
        refreshAddress();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        refreshAddress();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        if (server != null) server.stop();
        if (tts != null) tts.shutdown();
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
    }

    // ───────────────────────── 화면 구성 ─────────────────────────
    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        int pad = (int) (screenH * 0.035f);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 왼쪽: 현재 호출 번호
        numberPanel = new LinearLayout(this);
        numberPanel.setOrientation(LinearLayout.VERTICAL);
        numberPanel.setGravity(Gravity.CENTER);
        numberPanel.setBackground(Ui.round(this, Ui.PANEL, 24));
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(0, -1, 0.68f);
        lpL.rightMargin = pad;
        main.addView(numberPanel, lpL);

        TextView label = px(Ui.text(this, "호출 번호", 0, Ui.MUTED, true), 0.065f);
        label.setGravity(Gravity.CENTER);
        numberPanel.addView(label);

        numberView = px(Ui.text(this, "", 0, Ui.YELLOW, true), 0.40f);
        numberView.setGravity(Gravity.CENTER);
        numberView.setIncludeFontPadding(false);
        numberView.setSingleLine(true);
        numberPanel.addView(numberView);

        subView = px(Ui.text(this, "", 0, Ui.TEXT, false), 0.055f);
        subView.setGravity(Gravity.CENTER);
        numberPanel.addView(subView);

        // 오른쪽: 최근 호출
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        right.setBackground(Ui.round(this, Ui.PANEL, 24));
        right.setPadding(pad / 2, pad, pad / 2, pad);
        main.addView(right, new LinearLayout.LayoutParams(0, -1, 0.32f));

        TextView rTitle = px(Ui.text(this, "최근 호출", 0, Ui.MUTED, true), 0.05f);
        rTitle.setGravity(Gravity.CENTER);
        right.addView(rTitle);
        for (int i = 0; i < historyViews.length; i++) {
            TextView t = px(Ui.text(this, "", 0, Ui.TEXT, true), 0.085f);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            right.addView(t, new LinearLayout.LayoutParams(-1, 0, 1f));
            historyViews[i] = t;
        }

        // 하단: 연결 주소
        addrView = px(Ui.text(this, "", 0, Ui.MUTED, false), 0.03f);
        addrView.setPadding(0, pad / 2, 0, 0);
        root.addView(addrView);

        setContentView(root);
        root.setFocusable(true);
        root.requestFocus();
    }

    /** TV 해상도와 상관없이 화면 높이 비율로 글자 크기 지정 */
    private TextView px(TextView t, float ratio) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenH * ratio);
        return t;
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void refreshAddress() {
        String ip = Net.localIp();
        addrView.setText("호출기 연결 주소  " + ip + ":" + Net.HTTP_PORT
                + "     ·     휴대폰 브라우저 호출  http://" + ip + ":" + Net.HTTP_PORT);
    }

    private void render() {
        String cur = store.current();
        numberView.setText(cur.isEmpty() ? "—" : cur);
        float ratio = cur.length() <= 3 ? 0.40f : cur.length() == 4 ? 0.34f : 0.26f;
        numberView.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenH * ratio);
        subView.setText(cur.isEmpty() ? "잠시만 기다려 주세요" : subtitle());

        List<String> hist = store.history();
        for (int i = 0; i < historyViews.length; i++) {
            historyViews[i].setText(i < hist.size() ? hist.get(i) : "");
            historyViews[i].setAlpha(1f - i * 0.12f);
        }
    }

    // ───────────────────────── 호출 연출 ─────────────────────────
    private void announce(String n) {
        ui.removeCallbacksAndMessages(null);
        if (tts != null) tts.stop();
        flash(6);
        playChime();
        final int rep = repeat();
        if (rep <= 0 || !ttsReady) return;
        final String text = ttsTemplate().replace("{n}", n);
        ui.postDelayed(() -> {
            for (int i = 0; i < rep; i++) {
                tts.speak(text, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "call" + i);
                if (i < rep - 1) tts.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, "gap" + i);
            }
        }, 1300);
    }

    private void flash(final int times) {
        if (times <= 0) {
            numberPanel.setBackground(Ui.round(this, Ui.PANEL, 24));
            numberView.setTextColor(Ui.YELLOW);
            return;
        }
        boolean on = times % 2 == 0;
        numberPanel.setBackground(Ui.round(this, on ? Ui.ACCENT : Ui.PANEL, 24));
        numberView.setTextColor(on ? Color.WHITE : Ui.YELLOW);
        ui.postDelayed(() -> flash(times - 1), 380);
    }

    /** '딩-동' 효과음을 직접 합성 (외부 음원 파일 불필요) */
    private static short[] buildChime() {
        int sr = 22050;
        double[] freqs = {659.25, 523.25}; // 미, 도
        int len = (int) (sr * 0.6);
        short[] buf = new short[len * 2];
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < len; i++) {
                double t = (double) i / sr;
                double env = Math.exp(-3.2 * t) * Math.min(1.0, i / 200.0);
                double v = Math.sin(2 * Math.PI * freqs[k] * t) * 0.7
                        + Math.sin(2 * Math.PI * freqs[k] * 2 * t) * 0.2
                        + Math.sin(2 * Math.PI * freqs[k] * 3 * t) * 0.05;
                buf[k * len + i] = (short) (v * env * 32767 * 0.75);
            }
        }
        return buf;
    }

    @SuppressWarnings("deprecation")
    private void playChime() {
        try {
            final AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, 22050,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    chimePcm.length * 2, AudioTrack.MODE_STATIC);
            at.write(chimePcm, 0, chimePcm.length);
            at.play();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { at.stop(); } catch (Exception ignored) {}
                at.release();
            }, 1600);
        } catch (Exception ignored) {
        }
    }

    // ───────────────────────── CallServer.Host ─────────────────────────
    @Override public CallStore store() { return store; }
    @Override public String ttsTemplate() { return prefs.getString(K_TTS, DEF_TTS); }
    @Override public String subtitle() { return prefs.getString(K_SUB, DEF_SUB); }
    @Override public int repeat() { return prefs.getInt(K_REPEAT, 2); }

    @Override
    public void saveConfig(String tts, String subtitle, int repeat) {
        if (tts == null || tts.trim().isEmpty()) tts = DEF_TTS;
        if (subtitle == null) subtitle = DEF_SUB;
        prefs.edit().putString(K_TTS, tts).putString(K_SUB, subtitle).putInt(K_REPEAT, repeat).apply();
    }

    @Override
    public void onChanged(final String event) {
        ui.post(() -> {
            render();
            if ("call".equals(event) || "recall".equals(event)) {
                announce(store.current());
            }
        });
    }

    // ───────────────────────── 리모컨 ─────────────────────────
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            if (!store.current().isEmpty()) announce(store.current()); // 리모컨 확인 버튼 = 재호출
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        // 실수로 꺼지는 것을 막기 위해 확인 창 표시
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("DID 번호호출")
                .setItems(new String[]{"계속 표시", "역할 다시 선택", "앱 종료"}, (d, which) -> {
                    if (which == 1) {
                        prefs.edit().remove(MainActivity.KEY_MODE).apply();
                        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_CHOOSE, true));
                        finish();
                    } else if (which == 2) {
                        finish();
                    }
                })
                .show();
    }
}
