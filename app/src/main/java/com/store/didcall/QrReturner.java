package com.store.didcall;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.TextView;

/**
 * 근태 등록 QR: 크롬(기본 브라우저)으로 열어 로그인을 영구 저장하고,
 * 30초 후 호출기 화면을 다시 앞으로 가져와 자동 복귀.
 *  - 자동 복귀는 "다른 앱 위에 그리기" 권한(오버레이)이 있을 때 확실히 동작
 *    (권한이 없으면 그냥 startActivity 로 띄움 — 폰 설정에 따라 막힐 수 있음)
 *  - 크롬은 뒤로 숨는 것뿐이라 꺼지지 않고, 로그인 상태는 유지됨
 */
final class QrReturner {

    private static final int RETURN_SECONDS = 30;

    private QrReturner() {
    }

    static void open(Activity a) {
        // 1) 크롬 웹앱("홈 화면에 추가"로 만든 바로가기)을 직접 열기
        //    - 홈 아이콘을 누르는 것과 완전히 같은 창·세션 → 로그인 유지
        //    - 웹앱 활동명: com.google.android.apps.chrome.Main 의 "웹앱 단축키" 활동
        if (openWebApp(a)) {
            // 2) 30초 후 호출기 화면 복귀 예약
            final Context c = a.getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(() -> bringBack(c), RETURN_SECONDS * 1000L);
            return;
        }
        // 웹앱을 못 찾으면 크롬으로 그냥 열기 (기존 방식 — 로그인 매번 필요할 수 있음)
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(CallerActivity.QR_URL));
            i.setPackage("com.android.chrome");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            toast(a, "브라우저를 열 수 없습니다");
            return;
        }
        final Context c = a.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> bringBack(c), RETURN_SECONDS * 1000L);
    }

    /** 크롬 웹앱(홈 화면 바로가기)을 찾아 실행. 찾으면 true */
    private static boolean openWebApp(Activity a) {
        android.content.pm.PackageManager pm = a.getPackageManager();
        // 크롬이 만든 웹앱 활동은 URL 로 매칭되는 활동이 별도 등록되어 있음
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse(CallerActivity.QR_URL));
        probe.setPackage("com.android.chrome");
        java.util.List<android.content.pm.ResolveInfo> acts = pm.queryIntentActivities(probe, 0);
        // 웹앱 활동이 여러 개면 이름에 "main" 혹은 URL 해시가 붙은 활동 — 브라우저 본체가 아닌 것을 고름
        String chosen = null;
        for (android.content.pm.ResolveInfo ri : acts) {
            String n = ri.activityInfo.name;
            if (n != null && !n.equals("com.google.android.apps.chrome.Main")
                    && !n.endsWith(".Main") && n.contains("com.google.android.apps.chrome")) {
                chosen = ri.activityInfo.packageName + "/" + n;
                break;
            }
        }
        if (chosen == null) return false;
        try {
            Intent i = new Intent();
            i.setClassName(acts.get(0).activityInfo.packageName, chosen.split("/", 2)[1]);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 호출기 화면을 크롬 위로 다시 띄우기 */
    private static void bringBack(Context c) {
        Class<?> cls;
        try {
            cls = Class.forName("com.store.didcall.CallerActivity");
        } catch (Exception e) {
            return;
        }
        Intent back = new Intent(c, cls);
        back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(c)) {
                // 오버레이 권한이 없으면 일반 방식(일부 폰에서 막힐 수 있음)
                c.startActivity(back);
                return;
            }
            // 오버레이 권한이 있으면 최상단으로 확실히 띄움
            try {
                Class<?> cls2 = Class.forName("android.app.ActivityOptions");
                java.lang.reflect.Method m = cls2.getMethod("makeBasic");
                Object opts = m.invoke(null);
                @SuppressWarnings("unchecked")
                android.os.Bundle b = (android.os.Bundle) cls2.getMethod("toBundle").invoke(opts);
                c.startActivity(back, b);
                return;
            } catch (Exception ignored) {
            }
            c.startActivity(back);
        } catch (Exception ignored) {
        }
    }

    private static void toast(Context c, String s) {
        android.widget.Toast.makeText(c, s, android.widget.Toast.LENGTH_SHORT).show();
    }
}
