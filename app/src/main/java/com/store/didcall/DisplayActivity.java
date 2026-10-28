package com.store.didcall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TV에 연결된 안드로이드 기기에서 실행.
 * 왼쪽: 이미지 슬라이드쇼 / 오른쪽: 현재 번호 + 최근 호출
 * 번호가 들어오면 화면 가운데에 팝업 + 딩동 + 음성 안내
 */
public class DisplayActivity extends Activity implements CallServer.Host {

    private static final String K_TTS = "tts", K_SUB = "sub", K_REPEAT = "repeat";
    private static final String K_SLIDE = "slide", K_FIT = "fit", K_POPUP = "popup";
    private static final String DEF_TTS = "{n}번 고객님, 주문하신 메뉴가 준비되었습니다.";
    private static final String DEF_SUB = "주문하신 메뉴가 준비되었습니다";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final CallStore store = new CallStore();
    private ImageStore images;
    private CallServer server;
    private SharedPreferences prefs;
    private TextToSpeech tts;
    private boolean ttsReady;
    private WifiManager.MulticastLock multicastLock;
    private short[] chimePcm;
    private int screenW, screenH;

    // 왼쪽 이미지
    private FrameLayout imageFrame;
    private ImageView imgA, imgB;
    private boolean aFront = true;
    private TextView placeholder;
    private List<File> slides = new ArrayList<>();
    private int slideIndex = -1;

    // 오른쪽 정보
    private TextView curView, addrView;
    private final TextView[] historyViews = new TextView[5];

    // 팝업
    private FrameLayout overlay;
    private LinearLayout card;
    private TextView popLabel, popNumber, popSub;
    private int flashLeft;
    private String speakNumber = "";

    private final Runnable slideRunnable = this::nextSlide;
    private final Runnable hideRunnable = this::hidePopup;
    private final Runnable flashRunnable = this::flashStep;
    private final Runnable speakRunnable = this::speak;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        images = new ImageStore(new File(getFilesDir(), "images"));
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        screenW = Math.max(w, h);
        screenH = Math.min(w, h);

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
        imageFrame.post(this::reloadImages); // 크기가 정해진 뒤에 이미지 로드
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
        decoder.shutdownNow();
        if (server != null) server.stop();
        if (tts != null) tts.shutdown();
        if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
    }

    // ───────────────────────── 화면 구성 ─────────────────────────
    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (screenH * 0.03f);
        content.setPadding(pad, pad, pad, pad);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.HORIZONTAL);
        content.addView(main, new LinearLayout.LayoutParams(-1, 0, 1f));

        // ── 왼쪽: 이미지 ──
        imageFrame = new FrameLayout(this);
        imageFrame.setBackground(Ui.round(this, Color.BLACK, 24));
        imageFrame.setClipToOutline(true);
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(0, -1, 0.70f);
        lpL.rightMargin = pad;
        main.addView(imageFrame, lpL);

        imgA = new ImageView(this);
        imgB = new ImageView(this);
        imgB.setAlpha(0f);
        imageFrame.addView(imgA, new FrameLayout.LayoutParams(-1, -1));
        imageFrame.addView(imgB, new FrameLayout.LayoutParams(-1, -1));
        applyScaleType();

        placeholder = px(Ui.text(this,
                "여기에 매장 이미지가 표시됩니다\n\n호출기 앱 ⚙ → TV 이미지 추가\n또는 휴대폰 브라우저에서 아래 주소로 접속",
                0, Ui.MUTED, false), 0.04f);
        placeholder.setGravity(Gravity.CENTER);
        imageFrame.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));

        // ── 오른쪽: 현재 번호 + 최근 호출 ──
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_HORIZONTAL);
        right.setBackground(Ui.round(this, Ui.PANEL, 24));
        right.setPadding(pad / 2, pad, pad / 2, pad);
        main.addView(right, new LinearLayout.LayoutParams(0, -1, 0.30f));

        TextView curLabel = px(Ui.text(this, "현재 호출", 0, Ui.MUTED, true), 0.045f);
        curLabel.setGravity(Gravity.CENTER);
        right.addView(curLabel);

        curView = px(Ui.text(this, "", 0, Ui.YELLOW, true), 0.17f);
        curView.setGravity(Gravity.CENTER);
        curView.setSingleLine(true);
        curView.setIncludeFontPadding(false);
        right.addView(curView, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(Ui.KEY2);
        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(-1, Math.max(2, screenH / 400));
        dLp.setMargins(pad / 2, pad, pad / 2, pad / 2);
        right.addView(divider, dLp);

        TextView rTitle = px(Ui.text(this, "최근 호출", 0, Ui.MUTED, true), 0.04f);
        rTitle.setGravity(Gravity.CENTER);
        right.addView(rTitle);
        for (int i = 0; i < historyViews.length; i++) {
            TextView t = px(Ui.text(this, "", 0, Ui.TEXT, true), 0.07f);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            right.addView(t, new LinearLayout.LayoutParams(-1, 0, 1f));
            historyViews[i] = t;
        }

        // ── 하단: 연결 주소 ──
        addrView = px(Ui.text(this, "", 0, Ui.MUTED, false), 0.026f);
        addrView.setPadding(0, pad / 2, 0, 0);
        content.addView(addrView);

        // ── 호출 팝업 (평소엔 숨김) ──
        overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xB3000000);
        overlay.setVisibility(View.GONE);
        root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Ui.round(this, Ui.ACCENT, 40));
        card.setPadding(pad * 2, pad, pad * 2, pad * 3 / 2);
        card.setMinimumWidth((int) (screenW * 0.55f));
        card.setElevation(Ui.dp(this, 16));
        FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER);
        overlay.addView(card, cLp);

        popLabel = px(Ui.text(this, "호출 번호", 0, Color.WHITE, true), 0.06f);
        popLabel.setGravity(Gravity.CENTER);
        card.addView(popLabel);

        popNumber = px(Ui.text(this, "", 0, Color.WHITE, true), 0.36f);
        popNumber.setGravity(Gravity.CENTER);
        popNumber.setSingleLine(true);
        popNumber.setIncludeFontPadding(false);
        card.addView(popNumber);

        popSub = px(Ui.text(this, "", 0, Color.WHITE, false), 0.05f);
        popSub.setGravity(Gravity.CENTER);
        card.addView(popSub);

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
                + "     ·     휴대폰 브라우저 (호출·이미지 관리)  http://" + ip + ":" + Net.HTTP_PORT);
    }

    private void render() {
        String cur = store.current();
        curView.setText(cur.isEmpty() ? "—" : cur);
        float ratio = cur.length() <= 3 ? 0.17f : cur.length() == 4 ? 0.14f : 0.11f;
        curView.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenH * ratio);

        List<String> hist = store.history();
        for (int i = 0; i < historyViews.length; i++) {
            historyViews[i].setText(i < hist.size() ? hist.get(i) : "");
            historyViews[i].setAlpha(1f - i * 0.14f);
        }
    }

    // ───────────────────────── 이미지 슬라이드쇼 ─────────────────────────
    private void reloadImages() {
        slides = images.list();
        slideIndex = -1;
        nextSlide();
    }

    private void nextSlide() {
        ui.removeCallbacks(slideRunnable);
        if (slides.isEmpty()) {
            placeholder.setVisibility(View.VISIBLE);
            imgA.setImageDrawable(null);
            imgB.setImageDrawable(null);
            return;
        }
        placeholder.setVisibility(View.GONE);
        slideIndex = (slideIndex + 1) % slides.size();
        final File f = slides.get(slideIndex);
        final int tw = imageFrame.getWidth() > 0 ? imageFrame.getWidth() : screenW;
        final int th = imageFrame.getHeight() > 0 ? imageFrame.getHeight() : screenH;
        try {
            decoder.execute(() -> {
                final Bitmap bm = decode(f, tw, th);
                ui.post(() -> showBitmap(bm));
            });
        } catch (Exception ignored) {
            // 화면 종료 중
        }
        if (slides.size() > 1) ui.postDelayed(slideRunnable, slideSeconds() * 1000L);
    }

    private void showBitmap(Bitmap bm) {
        if (bm == null || isFinishing()) return;
        final ImageView front = aFront ? imgA : imgB;
        final ImageView back = aFront ? imgB : imgA;
        back.setImageBitmap(bm);
        back.animate().cancel();
        front.animate().cancel();
        back.setAlpha(0f);
        back.animate().alpha(1f).setDuration(700).start();
        front.animate().alpha(0f).setDuration(700).withEndAction(() -> front.setImageDrawable(null)).start();
        aFront = !aFront;
    }

    /** 화면 크기에 맞춰 줄여서 읽기 (메모리 부족 방지) */
    private static Bitmap decode(File f, int tw, int th) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= tw && o.outHeight / (sample * 2) >= th) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), d);
        } catch (Throwable t) {
            return null;
        }
    }

    private void applyScaleType() {
        ImageView.ScaleType st = "cover".equals(fit()) ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER;
        imgA.setScaleType(st);
        imgB.setScaleType(st);
    }

    // ───────────────────────── 호출 팝업 ─────────────────────────
    private void announce(String n) {
        showPopup(n);
        playChime();
        ui.removeCallbacks(speakRunnable);
        if (tts != null) tts.stop();
        speakNumber = n;
        if (repeat() > 0 && ttsReady) ui.postDelayed(speakRunnable, 1300);
    }

    private void speak() {
        int rep = repeat();
        String text = ttsTemplate().replace("{n}", speakNumber);
        for (int i = 0; i < rep; i++) {
            tts.speak(text, i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "call" + i);
            if (i < rep - 1) tts.playSilentUtterance(800, TextToSpeech.QUEUE_ADD, "gap" + i);
        }
    }

    private void showPopup(String n) {
        popNumber.setText(n);
        float ratio = n.length() <= 3 ? 0.36f : n.length() == 4 ? 0.30f : 0.24f;
        popNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenH * ratio);
        popSub.setText(subtitle());
        popSub.setVisibility(subtitle().trim().isEmpty() ? View.GONE : View.VISIBLE);

        ui.removeCallbacks(hideRunnable);
        ui.removeCallbacks(flashRunnable);
        overlay.animate().cancel();
        card.animate().cancel();

        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(0f);
        overlay.animate().alpha(1f).setDuration(250).start();
        card.setScaleX(0.5f);
        card.setScaleY(0.5f);
        card.animate().scaleX(1f).scaleY(1f).setDuration(450)
                .setInterpolator(new OvershootInterpolator(1.6f)).start();

        setCardColors(false);
        flashLeft = 6;
        ui.postDelayed(flashRunnable, 500);
        ui.postDelayed(hideRunnable, popupSeconds() * 1000L);
    }

    private void flashStep() {
        if (flashLeft <= 0) { setCardColors(false); return; }
        setCardColors(flashLeft % 2 == 0);
        flashLeft--;
        ui.postDelayed(flashRunnable, 380);
    }

    private void setCardColors(boolean yellow) {
        card.setBackground(Ui.round(this, yellow ? Ui.YELLOW : Ui.ACCENT, 40));
        int fg = yellow ? Ui.BG : Color.WHITE;
        popLabel.setTextColor(fg);
        popNumber.setTextColor(fg);
        popSub.setTextColor(fg);
    }

    private void hidePopup() {
        ui.removeCallbacks(hideRunnable);
        ui.removeCallbacks(flashRunnable);
        if (overlay.getVisibility() != View.VISIBLE) return;
        overlay.animate().cancel();
        overlay.animate().alpha(0f).setDuration(350)
                .withEndAction(() -> overlay.setVisibility(View.GONE)).start();
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
            ui.postDelayed(() -> {
                try { at.stop(); } catch (Exception ignored) {}
                at.release();
            }, 1600);
        } catch (Exception ignored) {
        }
    }

    // ───────────────────────── 설정 ─────────────────────────
    private String ttsTemplate() { return prefs.getString(K_TTS, DEF_TTS); }
    private String subtitle() { return prefs.getString(K_SUB, DEF_SUB); }
    private int repeat() { return prefs.getInt(K_REPEAT, 2); }
    private int slideSeconds() { return prefs.getInt(K_SLIDE, 8); }
    private int popupSeconds() { return prefs.getInt(K_POPUP, 10); }
    private String fit() { return prefs.getString(K_FIT, "contain"); }

    private static int clampInt(String v, int def, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(v.trim()))); }
        catch (Exception e) { return def; }
    }

    // ───────────────────────── CallServer.Host ─────────────────────────
    @Override public CallStore store() { return store; }
    @Override public ImageStore images() { return images; }

    @Override
    public void putConfig(JSONObject o) throws Exception {
        o.put("tts", ttsTemplate());
        o.put("sub", subtitle());
        o.put("repeat", repeat());
        o.put("slide", slideSeconds());
        o.put("popup", popupSeconds());
        o.put("fit", fit());
    }

    @Override
    public void applyConfig(Map<String, String> q) {
        SharedPreferences.Editor e = prefs.edit();
        if (q.containsKey("tts")) {
            String t = q.get("tts").trim();
            e.putString(K_TTS, t.isEmpty() ? DEF_TTS : t);
        }
        if (q.containsKey("sub")) e.putString(K_SUB, q.get("sub").trim());
        if (q.containsKey("repeat")) e.putInt(K_REPEAT, clampInt(q.get("repeat"), repeat(), 0, 5));
        if (q.containsKey("slide")) e.putInt(K_SLIDE, clampInt(q.get("slide"), slideSeconds(), 3, 300));
        if (q.containsKey("popup")) e.putInt(K_POPUP, clampInt(q.get("popup"), popupSeconds(), 3, 60));
        if (q.containsKey("fit")) e.putString(K_FIT, "cover".equals(q.get("fit")) ? "cover" : "contain");
        e.apply();
    }

    @Override
    public void onChanged(final String event) {
        ui.post(() -> {
            switch (event) {
                case "call":
                case "recall":
                    render();
                    announce(store.current());
                    break;
                case "clear":
                    render();
                    hidePopup();
                    break;
                case "images":
                    reloadImages();
                    break;
                case "config":
                    applyScaleType();
                    if (slides.size() > 1) {
                        ui.removeCallbacks(slideRunnable);
                        ui.postDelayed(slideRunnable, slideSeconds() * 1000L);
                    }
                    break;
                default:
                    render();
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
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && !slides.isEmpty()) { nextSlide(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (overlay.getVisibility() == View.VISIBLE) { hidePopup(); return; }
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
