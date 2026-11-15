package com.store.didcall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 전원이 켜지면 자동 실행:
 *  - 호출기(호스트) 폰 → 호출 서버를 백그라운드로 시작
 *  - TV 화면 기기 → 호출 화면을 띄움
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String mode = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(MainActivity.KEY_MODE, "");
        try {
            if (MainActivity.MODE_CALLER.equals(mode)) {
                HostService.start(context);
            } else if (MainActivity.MODE_TV.equals(mode)) {
                // 부팅 직후 시스템이 다른 홈(런처)을 먼저 띄우는 기기가 있어,
                // 우리 화면이 아직 앞에 없으면 잠시 뒤 다시 띄우는 걸 몇 번 반복한다
                launchTv(context, 0);
            }
        } catch (Exception ignored) {
        }
    }

    private static void launchTv(Context context, final int attempt) {
        try {
            Intent i = new Intent(context, DisplayActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            context.startActivity(i);
        } catch (Exception ignored) {
        }
        // 부팅 직후엔 와이파이·시스템이 아직 준비 중일 수 있어 5초 뒤 한 번 더 띄워 본다 (최대 5회)
        if (attempt < 5) {
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.postDelayed(() -> {
                try {
                    // 앞에 떠 있지 않으면(런처가 덮었으면) 다시 띄움
                    android.app.ActivityManager am = (android.app.ActivityManager)
                            context.getSystemService(Context.ACTIVITY_SERVICE);
                    String top = null;
                    java.util.List<android.app.ActivityManager.RunningTaskInfo> tasks =
                            am.getRunningTasks(1);
                    if (!tasks.isEmpty()) top = tasks.get(0).topActivity != null
                            ? tasks.get(0).topActivity.getPackageName() : null;
                    if (!"com.store.didcall".equals(top)) launchTv(context, attempt + 1);
                } catch (Exception ignored) {
                }
            }, 5000);
        }
    }
}
