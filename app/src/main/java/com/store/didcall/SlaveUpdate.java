package com.store.didcall;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * TV(슬레이브) 기기의 자동 업데이트:
 * 호출기 서버에 보관된 APK 버전이 자기보다 새면 APK 를 받아 설치 화면으로 연결.
 * TV 기기는 보통 사람이 대기하는 화면이라, 시작할 때 한 번 확인해서 설치 확인 창만 띄운다.
 */
final class SlaveUpdate {

    private SlaveUpdate() {}

    /** 서버의 /apk/info 응답을 해석해 새 버전이 있으면 APK 를 받아 설치 요청.
     *  결과: null 이면 할 일 없음, 새 버전 문자열이면 설치 창을 띄운 것 */
    static String checkAndInstall(Context c, String baseUrl) {
        try {
            JSONObject info = new JSONObject(Http.get(baseUrl + "/apk/info", 4000, 6000));
            if (!info.optBoolean("ok")) return null;
            String ver = info.optString("version", "");
            if (ver.isEmpty() || Updater.compare(versionOf(c), ver) >= 0) return null; // 같거나 최신이면 통과
            File dst = new File(c.getCacheDir(), "update.apk");
            // 이미 같은 버전을 받아 둔 경우 재다운로드 생략 (설치를 미룬 뒤 10분 뒤 확인 때 또 받는 것을 막음)
            long want = info.optLong("size", 0);
            if (dst.isFile() && want > 0 && dst.length() == want) {
                install(c, dst);
                return ver;
            }
            try (InputStream in = new java.net.URL(baseUrl + "/apk").openStream();
                 FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
            if (dst.length() < 1024) return null; // 받기 실패 — 조용히 포기
            install(c, dst);
            return ver;
        } catch (Exception e) {
            return null; // 조용히 포기 (다음 확인 때 다시)
        }
    }

    /** 패키지 설치 화면 띄우기 (사용자가 '설치'만 누르면 됨) */
    static void install(Context c, File apk) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = new Uri.Builder()
                        .scheme("content")
                        .authority(c.getPackageName() + ".apk")
                        .path(apk.getName())
                        .build();
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                uri = Uri.fromFile(apk);
            }
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    static String versionOf(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }
}
