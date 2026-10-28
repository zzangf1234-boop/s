package com.store.didcall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** TV 모드로 설정된 기기는 전원이 켜지면 호출 화면을 자동으로 띄움 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String mode = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
                .getString(MainActivity.KEY_MODE, "");
        if (!MainActivity.MODE_TV.equals(mode)) return;
        try {
            Intent i = new Intent(context, DisplayActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
        } catch (Exception ignored) {
        }
    }
}
