package com.store.didcall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * HDMI(디스플레이) 상태에 따라 TV 화면 앱을 켜고 끈다.
 *  - HDMI 연결됨 → TV 모드면 호출 화면을 띄움 (부팅 후 TV/HDMI가 늦게 연결돼도 자동으로 뜸)
 *  - HDMI 해제됨 → 화면이 없으니 호출 화면을 종료해 불필요한 동작을 멈춤
 */
public class HdmiReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String mode = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(MainActivity.KEY_MODE, "");
        if (!MainActivity.MODE_TV.equals(mode)) return;

        String action = intent.getAction();
        if (action == null) return;

        boolean plugged;
        if ("android.intent.action.ACTION_HDMI_PLUGGED".equals(action)) {
            plugged = intent.getBooleanExtra("state", false);
        } else if ("android.hardware.display.action.DISPLAY_CONNECTED".equals(action)) {
            plugged = true;
        } else if ("android.hardware.display.action.DISPLAY_DISCONNECTED".equals(action)) {
            plugged = false;
        } else {
            return;
        }

        try {
            if (plugged) {
                Intent i = new Intent(context, DisplayActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                context.startActivity(i);
            } else {
                // 화면이 끊겼으면 앱 화면을 닫는다 (다음 HDMI 연결 시 HdmiReceiver가 다시 띄움)
                Intent i = new Intent(context, DisplayActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i); // 인스턴스가 없으면 무시됨
                context.sendBroadcast(new Intent("com.store.didcall.FINISH_DISPLAY")
                        .setPackage(context.getPackageName()));
            }
        } catch (Exception ignored) {
        }
    }
}
