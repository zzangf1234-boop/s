package com.store.didcall;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import android.os.Build;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TV에 연결된 안드로이드 기기에서 실행 (화면 표시 전용).
 * 같은 와이파이의 호출기(호스트)를 자동으로 찾아 호출 화면(/display)을 전체화면으로 띄웁니다.
 * PC 브라우저 화면과 완전히 같은 화면·소리가 나옵니다.
 */
public class DisplayActivity extends Activity {

    private static final String K_HOST = "host_addr";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newCachedThreadPool();
    private SharedPreferences prefs;

    private WebView web;
    private LinearLayout searchPane;
    private TextView searchTitle, searchMsg;
    private Button retryBtn;

    private String host = "";
    private boolean searching;
    private int fails;
    private int screenH;

    /** 10초마다 호출기 연결 확인, 끊기면 다시 찾기 (호출기 주소가 바뀌어도 자동 복구) */
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            final String h = host;
            if (h.isEmpty() || searching) return;
            bg.execute(() -> {
                boolean ok = Discovery.ping(h, 2500);
                ui.post(() -> {
                    if (!h.equals(host) || searching) return;
                    if (ok) {
                        if (fails >= 1) web.reload(); // 끊겼다가 돌아오면 화면 새로 고침
                        fails = 0;
                    } else if (++fails >= 3) {
                        startSearch();
                        return;
                    }
                    ui.postDelayed(watchdog, 10_000);
                });
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        int w = getResources().getDisplayMetrics().widthPixels;
        int h = getResources().getDisplayMetrics().heightPixels;
        screenH = Math.min(w, h);
        buildUi();
        host = prefs.getString(K_HOST, "");
        startSearch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        web.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        bg.shutdownNow();
        web.destroy();
        super.onDestroy();
    }

    // ───────────────────────── 화면 ─────────────────────────
    @SuppressLint("SetJavaScriptEnabled")
    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);

        web = new WebView(this);
        web.setBackgroundColor(Ui.BG);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); // 딩동·음성 자동 재생
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        web.setFocusable(false);
        web.setVisibility(View.INVISIBLE);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        searchPane = new LinearLayout(this);
        searchPane.setOrientation(LinearLayout.VERTICAL);
        searchPane.setGravity(Gravity.CENTER);
        searchPane.setBackgroundColor(Ui.BG);
        int pad = screenH / 20;
        searchPane.setPadding(pad, pad, pad, pad);
        root.addView(searchPane, new FrameLayout.LayoutParams(-1, -1));

        searchTitle = px(Ui.text(this, "호출기를 찾는 중…", 0, Ui.YELLOW, true), 0.07f);
        searchTitle.setGravity(Gravity.CENTER);
        searchPane.addView(searchTitle);

        searchMsg = px(Ui.text(this, "", 0, Ui.TEXT, false), 0.035f);
        searchMsg.setGravity(Gravity.CENTER);
        searchMsg.setPadding(0, pad / 2, 0, pad / 2);
        searchPane.addView(searchMsg);

        TextView hint = px(Ui.text(this,
                "호출기 폰에서 DID 앱이 켜져 있고, 이 기기와 같은 와이파이(공유기)에 연결되어 있어야 합니다.\n"
                        + "호출기 앱 상단에 표시된 주소를 '주소 직접 입력'으로 넣어도 됩니다.",
                0, Ui.MUTED, false), 0.03f);
        hint.setGravity(Gravity.CENTER);
        searchPane.addView(hint);

        LinearLayout btns = new LinearLayout(this);
        btns.setGravity(Gravity.CENTER);
        btns.setPadding(0, pad, 0, 0);
        searchPane.addView(btns);
        retryBtn = Ui.button(this, "다시 찾기", Ui.ACCENT, 0);
        retryBtn.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenH * 0.035f);
        retryBtn.setOnClickListener(v -> startSearch());
        Button manual = Ui.button(this, "주소 직접 입력", Ui.KEY2, 0);
        manual.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenH * 0.035f);
        manual.setOnClickListener(v -> showManualDialog());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (screenH * 0.5f), (int) (screenH * 0.11f));
        lp.setMargins(pad / 3, 0, pad / 3, 0);
        btns.addView(retryBtn, lp);
        btns.addView(manual, new LinearLayout.LayoutParams(lp));

        setContentView(root);
    }

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

    // ───────────────────────── 호출기 찾기 / 연결 ─────────────────────────
    private final Runnable retrySearch = this::startSearch;

    private void startSearch() {
        if (searching) return;
        searching = true;
        ui.removeCallbacks(watchdog);
        ui.removeCallbacks(retrySearch);
        searchPane.setVisibility(View.VISIBLE);
        searchTitle.setText("호출기를 찾는 중…");
        searchTitle.setTextColor(Ui.YELLOW);
        retryBtn.setEnabled(false);
        final String last = host;
        bg.execute(() -> {
            final String found = Discovery.find(last, m -> ui.post(() -> searchMsg.setText(m)));
            ui.post(() -> {
                searching = false;
                retryBtn.setEnabled(true);
                if (found != null) {
                    connect(found);
                } else {
                    searchTitle.setText("호출기를 찾지 못했습니다");
                    searchTitle.setTextColor(0xFFFF5A5A);
                    searchMsg.setText("이 기기 주소: " + Net.localIp() + "   ·   20초 후 다시 찾습니다");
                    retryBtn.requestFocus();
                    ui.postDelayed(retrySearch, 20_000);
                }
            });
        });
    }

    private void connect(String h) {
        ui.removeCallbacks(retrySearch);
        ui.removeCallbacks(watchdog);
        host = h;
        fails = 0;
        prefs.edit().putString(K_HOST, h).apply();
        searchPane.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        web.loadUrl("http://" + h + "/display?app=1");
        ui.postDelayed(this::unlockAudio, 2500);
        ui.postDelayed(watchdog, 10_000);
    }

    /** 일부 기기는 화면을 한 번 눌러야 소리가 켜지므로, 화면 가운데를 자동으로 한 번 누름 */
    private void unlockAudio() {
        long t = SystemClock.uptimeMillis();
        float x = web.getWidth() / 2f, y = web.getHeight() / 2f;
        MotionEvent down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(t, t + 50, MotionEvent.ACTION_UP, x, y, 0);
        web.dispatchTouchEvent(down);
        web.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private void showManualDialog() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        et.setHint("예) 192.168.0.23");
        et.setText(host.replace(":" + Net.HTTP_PORT, ""));
        et.setTextColor(Color.WHITE);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("호출기 주소")
                .setMessage("호출기 앱 상단에 표시된 주소를 입력하세요.")
                .setView(et)
                .setPositiveButton("연결", (d, w) -> {
                    String h = et.getText().toString().trim().replace("http://", "");
                    int slash = h.indexOf('/');
                    if (slash >= 0) h = h.substring(0, slash);
                    if (h.isEmpty()) return;
                    if (!h.contains(":")) h = h + ":" + Net.HTTP_PORT;
                    final String target = h;
                    searchMsg.setText(target + " 확인 중…");
                    bg.execute(() -> {
                        boolean ok = Discovery.ping(target, 3000);
                        ui.post(() -> {
                            if (ok) connect(target);
                            else searchMsg.setText(target + " 에 연결할 수 없습니다. 주소와 와이파이를 확인하세요");
                        });
                    });
                })
                .setNegativeButton("취소", null)
                .show();
    }

    // ───────────────────────── 리모컨 ─────────────────────────
    private static final String K_TOKEN = "tv_token";
    private boolean approving;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean showing = searchPane.getVisibility() != View.VISIBLE;
        if (showing && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)) {
            if (event.getRepeatCount() == 0) recallFromRemote(); // 리모컨 확인 버튼 = 재호출
            return true;
        }
        if (showing && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            web.evaluateJavascript("window.next&&next()", null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /** 재호출은 승인된 기기만 가능 → 처음 한 번 호출기에서 [허용] 받기 */
    private void recallFromRemote() {
        final String h = host;
        final String token = prefs.getString(K_TOKEN, "");
        bg.execute(() -> {
            int code = token.isEmpty() ? 403 : httpCode("http://" + h + "/recall?t=" + token);
            if (code != 403) return;
            prefs.edit().remove(K_TOKEN).apply();
            requestApproval(h);
        });
    }

    private void requestApproval(String h) {
        if (approving) return;
        approving = true;
        ui.post(() -> Toast.makeText(this, "리모컨으로 재호출하려면 호출기 폰에서 [허용]을 눌러 주세요", Toast.LENGTH_LONG).show());
        try {
            String name = URLEncoder.encode("TV 화면 리모컨 (" + Build.MODEL + ")", "UTF-8");
            JSONObject r = new JSONObject(httpGet("http://" + h + "/auth/request?name=" + name));
            String id = r.getString("id");
            for (int i = 0; i < 90; i++) { // 최대 3분 기다림
                Thread.sleep(2000);
                JSONObject c = new JSONObject(httpGet("http://" + h + "/auth/check?id=" + id));
                String st = c.optString("state");
                if ("ok".equals(st)) {
                    prefs.edit().putString(K_TOKEN, c.getString("token")).apply();
                    ui.post(() -> Toast.makeText(this, "허용되었습니다. 이제 확인 버튼으로 재호출할 수 있습니다", Toast.LENGTH_LONG).show());
                    httpCode("http://" + h + "/recall?t=" + c.getString("token"));
                    break;
                }
                if (!"pending".equals(st)) {
                    ui.post(() -> Toast.makeText(this, "호출기에서 허용되지 않았습니다", Toast.LENGTH_SHORT).show());
                    break;
                }
            }
        } catch (Exception ignored) {
        } finally {
            approving = false;
        }
    }

    private static int httpCode(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(2500);
            c.setReadTimeout(2500);
            int code = c.getResponseCode();
            c.disconnect();
            return code;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
        try {
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[2048];
            int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            return bo.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ───────────────────────── 화면 방향 · 화면 정보 ─────────────────────────
    private void chooseRotation() {
        // 화면 내용을 시계 방향으로 90° 돌리면(90) TV를 왼쪽(반시계)으로 돌려 단 경우에 바로 보임
        String[] items = {"가로 (기본)", "세로 — TV를 왼쪽으로 돌려 단 경우", "세로 — TV를 오른쪽으로 돌려 단 경우"};
        final int[] values = {0, 90, 270};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("화면 방향")
                .setItems(items, (d, which) -> {
                    web.evaluateJavascript("setRot(" + values[which] + ")", null);
                    ui.postDelayed(this::unlockAudio, 2500);
                })
                .show();
    }

    @SuppressWarnings("deprecation")
    private void showScreenInfo() {
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        StringBuilder b = new StringBuilder();
        b.append("앱이 그리는 해상도: ").append(dm.widthPixels).append(" × ").append(dm.heightPixels)
                .append("\n화면 밀도: ").append(dm.densityDpi).append(" dpi");
        if (Build.VERSION.SDK_INT >= 23) {
            android.view.Display.Mode m = getWindowManager().getDefaultDisplay().getMode();
            b.append("\nTV로 내보내는 해상도: ").append(m.getPhysicalWidth()).append(" × ").append(m.getPhysicalHeight())
                    .append(" (").append(Math.round(m.getRefreshRate())).append("Hz)");
        }
        if (Math.max(dm.widthPixels, dm.heightPixels) < 1600) {
            b.append("\n\n⚠ 이 기기는 720p 이하로 화면을 그립니다. TV에서 흐려 보일 수 있습니다.")
                    .append("\n기기 설정 → 디스플레이에서 해상도를 1080p 이상으로 바꿀 수 있는지 확인하세요.");
        }
        b.append("\n\n기기: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append("\n안드로이드: ").append(Build.VERSION.RELEASE)
                .append("\n호출기: ").append(host.isEmpty() ? "연결 안 됨" : host);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("화면 정보")
                .setMessage(b.toString())
                .setPositiveButton("확인", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        // 실수로 꺼지는 것을 막기 위해 확인 창 표시
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("DID 호출 화면")
                .setItems(new String[]{"계속 표시", "화면 방향 (세로 거치)", "화면 정보", "호출기 다시 찾기", "호출기 주소 직접 입력", "역할 다시 선택", "앱 종료"}, (d, which) -> {
                    if (which == 1) {
                        chooseRotation();
                    } else if (which == 2) {
                        showScreenInfo();
                    } else if (which == 3) {
                        web.setVisibility(View.INVISIBLE);
                        startSearch();
                    } else if (which == 4) {
                        searchPane.setVisibility(View.VISIBLE);
                        showManualDialog();
                    } else if (which == 5) {
                        prefs.edit().remove(MainActivity.KEY_MODE).apply();
                        startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_CHOOSE, true));
                        finish();
                    } else if (which == 6) {
                        finish();
                    }
                })
                .show();
    }
}
