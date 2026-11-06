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

        // 방법 1: 크롬 웹앱 전용 활동(WebView 활동)을 직접 지정해 실행
        //  - 크롬이 "홈 화면에 추가"로 만든 웹앱은 com.google.android.apps.chrome.WebApkActivity 또는
        //    WebApkMainActivity (웹앱 패키지: org.chromium.webapk.<해시>) 로 등록됨
        String[] webApkActivities = {
                "org.chromium.webapk.lib.common.WebApkIntentFactory", // 없을 수 있음
        };
        // 웹앱 패키지는 org.chromium.webapk.* — 그 패키지의 런처 활동을 찾아 실행하는 게 가장 확실
        java.util.List<android.content.pm.ApplicationInfo> apps =
                pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA);
        for (android.content.pm.ApplicationInfo ai : apps) {
            if (ai.packageName != null && ai.packageName.startsWith("org.chromium.webapk")) {
                Intent launch = pm.getLaunchIntentForPackage(ai.packageName);
                if (launch != null) {
                    // 웹앱이 여러 개일 수 있으니 URL이 맞는지는 실행 후 사이트가 알아서 처리
                    try {
                        a.startActivity(launch);
                        return true;
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        return false;
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
