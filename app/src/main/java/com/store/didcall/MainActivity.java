package com.store.didcall;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 처음 실행 시 이 기기의 역할(TV 화면 / 호출기)을 고르는 화면 */
public class MainActivity extends Activity {

    static final String PREFS = "didcall";
    static final String KEY_MODE = "mode";
    static final String MODE_TV = "tv";
    static final String MODE_CALLER = "caller";
    static final String EXTRA_CHOOSE = "choose";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences p = getSharedPreferences(PREFS, MODE_PRIVATE);
        String mode = p.getString(KEY_MODE, "");

        // 이미 역할이 정해졌으면 바로 해당 화면으로
        if (!getIntent().getBooleanExtra(EXTRA_CHOOSE, false)) {
            if (MODE_TV.equals(mode)) { open(DisplayActivity.class); return; }
            if (MODE_CALLER.equals(mode)) { open(CallerActivity.class); return; }
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Ui.BG);
        int pad = Ui.dp(this, 24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = Ui.text(this, "DID 번호호출", 32, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = Ui.text(this, "이 기기를 어떻게 사용할까요?", 18, Ui.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 28));
        root.addView(sub);

        Button caller = Ui.button(this, "📱  호출기 (호스트 · 번호 보내기)", Ui.ACCENT, 21);
        caller.setOnClickListener(v -> choose(MODE_CALLER));
        root.addView(caller, btnLp());

        Button tv = Ui.button(this, "📺  TV 화면 (호출 번호 표시)", Ui.KEY2, 21);
        tv.setOnClickListener(v -> choose(MODE_TV));
        root.addView(tv, btnLp());

        TextView hint = Ui.text(this,
                "카운터의 공기계 → 호출기 (번호·이미지·설정을 관리하는 서버)\nTV에 연결된 안드로이드 기기 → TV 화면 (호출기를 자동으로 찾음)\n윈도우 PC는 브라우저로 화면을 띄울 수 있습니다.\n모든 기기는 같은 와이파이(공유기)에 연결되어 있어야 합니다.",
                15, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, Ui.dp(this, 28), 0, 0);
        root.addView(hint);

        setContentView(root);
        // TV 리모컨으로 바로 선택 가능하도록 TV 기기에서는 'TV 화면'에 포커스
        if (getPackageManager().hasSystemFeature("android.software.leanback")) tv.requestFocus(); else caller.requestFocus();
    }

    private LinearLayout.LayoutParams btnLp() {
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int w = Math.min(Ui.dp(this, 420), screenW - Ui.dp(this, 48));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, Ui.dp(this, 76));
        lp.topMargin = Ui.dp(this, 14);
        return lp;
    }

    private void choose(String mode) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_MODE, mode).apply();
        if (MODE_TV.equals(mode)) {
            askAutoStartPermission();
            open(DisplayActivity.class);
        } else {
            open(CallerActivity.class);
        }
    }

    /** Android 10 이상에서 부팅 후 자동 실행되려면 '다른 앱 위에 표시' 권한이 필요 */
    private void askAutoStartPermission() {
        if (Build.VERSION.SDK_INT >= 29 && !Settings.canDrawOverlays(this)) {
            try {
                Toast.makeText(this, "부팅 시 자동 실행을 원하면 'DID 번호호출'을 허용해 주세요", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
                // 일부 TV 기기에는 이 설정 화면이 없음 — 무시
            }
        }
    }

    private void open(Class<?> cls) {
        startActivity(new Intent(this, cls));
        finish();
    }
}
