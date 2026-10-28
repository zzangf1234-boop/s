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
                Intent i = new Intent(context, DisplayActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        } catch (Exception ignored) {
        }
    }
}
