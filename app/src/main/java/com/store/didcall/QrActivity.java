package com.store.didcall;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 근태 등록 QR 화면. 사이트가 세션 쿠키만 써서 WebView 를 닫으면 인증이 풀리므로,
 * WebView 인스턴스를 앱이 살아 있는 동안 HOLD 에 보관해 닫지 않고 재사용합니다.
 * (30초 후 화면만 닫히고 WebView 는 메모리에 유지 → 다음에 열면 로그인 그대로)
 */
public class QrActivity extends Activity {

    public static final String EXTRA_URL = "url";
    private static final int RETURN_SECONDS = 30;

    /** 화면이 닫혀도 버리지 않는 공용 WebView (호출기 앱 프로세스가 살아 있는 동안 유지) */
    @SuppressLint("StaticFieldLeak")
    private static WebView HOLD;
    private static boolean HOLD_READY;

    private TextView countdown;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int left = RETURN_SECONDS;
    private LinearLayout root;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        // 위쪽 상태 줄: 남은 시간 + 지금 닫기
        LinearLayout top = new LinearLayout(this);
        top.setBackgroundColor(0xE6101010);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        countdown = Ui.text(this, "", 15, Ui.YELLOW, true);
        top.addView(countdown, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView close = Ui.button(this, "지금 닫기", Ui.KEY2, 15);
        close.setOnClickListener(v -> finish());
        top.addView(close, new LinearLayout.LayoutParams(-2, -2));
        root.addView(top, new LinearLayout.LayoutParams(-1, -2));

        if (HOLD == null) { // 첫 열기: 새로 만들고 (세션 유지를 위해 이후 절대 destroy 하지 않음)
            HOLD = new WebView(this);
            WebSettings s = HOLD.getSettings();
            s.setJavaScriptEnabled(true);
            s.setDomStorageEnabled(true);
            s.setDatabaseEnabled(true);
            s.setCacheMode(WebSettings.LOAD_DEFAULT);
            try {
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                cm.setAcceptThirdPartyCookies(HOLD, true);
            } catch (Exception ignored) {
            }
            HOLD.setWebViewClient(new WebViewClient());
            HOLD.setWebChromeClient(new WebChromeClient());
            root.addView(HOLD, new LinearLayout.LayoutParams(-1, 0, 1f));
            String url = getIntent().getStringExtra(EXTRA_URL);
            if (url == null || url.isEmpty()) url = CallerActivity.QR_URL;
            HOLD.loadUrl(url);
            HOLD_READY = true;
        } else {
            // 다시 열기: 같은 WebView 를 붙여 로그인 상태 그대로 재사용
            if (HOLD.getParent() instanceof ViewGroup) {
                ((ViewGroup) HOLD.getParent()).removeView(HOLD);
            }
            root.addView(HOLD, new LinearLayout.LayoutParams(-1, 0, 1f));
            if (!HOLD_READY) { // 이전 화면에서 닫힌 뒤라면 페이지를 다시 읽기
                HOLD.reload();
                HOLD_READY = true;
            }
        }

        setContentView(root, new ViewGroup.LayoutParams(-1, -1));
        tick();
    }

    private void tick() {
        if (isFinishing() || isDestroyed()) return;
        countdown.setText("근태 등록 — " + left + "초 후 호출기로 돌아갑니다");
        if (left <= 0) {
            finish();
            return;
        }
        left--;
        ui.postDelayed(this::tick, 1000);
    }

    @Override
    protected void onPause() {
        try { CookieManager.getInstance().flush(); } catch (Exception ignored) {}
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        // WebView 는 HOLD 에 남겨 세션 유지 — 화면(레이아웃)만 정리
        ui.removeCallbacksAndMessages(null);
        if (HOLD != null && HOLD.getParent() == root) {
            root.removeView(HOLD);
            HOLD_READY = false; // 다음 열기 때 reload 하도록
        }
        super.onDestroy();
    }
}
