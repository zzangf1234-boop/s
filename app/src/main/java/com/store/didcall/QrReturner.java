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

    private static final int RETURN_SECONDS = 20;

    private QrReturner() {
    }

    static void open(Activity a) {
        // 1) 크롬 앱 자체를 실행 (URL 을 넘기지 않음)
        //    - 크롬이 마지막 탭을 그대로 복원 → 근태 사이트의 로그인 세션이 유지됨
        //    - URL 로 열면 새 탭이 생겨 매번 재인증 필요 (테스트로 확인됨)
        //    - 처음 한 번은 크롬에서 근태 페이지를 열어 로그인해 두면,
        //      그 탭이 "마지막 탭"으로 남아 이후 실행마다 재사용됨
        try {
            // getLaunchIntentForPackage 는 크롬 바로가기가 만들어지면 ACTION_VIEW+URL 로
            // 재정의되는 사례가 있어(새 탭 생성됨) MAIN 인텐트를 직접 구성한다
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_LAUNCHER);
            i.setClassName("com.android.chrome", "com.google.android.apps.chrome.Main");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            // 크롬이 없으면 기본 브라우저로 URL 열기 (최후 fallback)
            try {
                a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(CallerActivity.QR_URL)));
            } catch (Exception e2) {
                toast(a, "브라우저를 열 수 없습니다");
                return;
            }
        }
        // 2) 30초 후 호출기 화면 복귀 예약
        final Context c = a.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> bringBack(c), RETURN_SECONDS * 1000L);
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
