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
 * 근태 등록 QR 화면: 로그인 상태(쿠키·캐시)를 계속 유지하며 WebView 로 열고,
 * 30초 후 자동으로 닫혀 호출기 화면으로 돌아옵니다.
 */
public class QrActivity extends Activity {

    public static final String EXTRA_URL = "url";
    private static final int RETURN_SECONDS = 30;

    private WebView web;
    private TextView countdown;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private int left = RETURN_SECONDS;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
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

        // 로그인 상태 유지: 쿠키·저장소를 절대 지우지 않고 이어서 사용
        web = new WebView(this);
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setAcceptCookie(true);
            cm.setAcceptThirdPartyCookies(web, true);
            cm.flush();
        } catch (Exception ignored) {
        }
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT); // 캐시 활용 → 빠른 로딩, 로그인 유지
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient());
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root, new ViewGroup.LayoutParams(-1, -1));

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (url == null || url.isEmpty()) url = CallerActivity.QR_URL;
        web.loadUrl(url);

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
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (web != null) {
            web.loadUrl("about:blank");
            web.removeAllViews();
            web.destroy();
        }
        super.onDestroy();
    }
}
