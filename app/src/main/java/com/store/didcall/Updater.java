package com.store.didcall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 업데이트 확인: GitHub 의 VERSION 파일과 현재 앱 버전을 비교해
 * 새 버전이면 최신 Release 에서 APK 를 앱 안에서 바로 내려받아 설치 화면을 띄움.
 * (로그인 없이 받을 수 있는 주소: github Releases /releases/download/…)
 */
final class Updater {

    /** 저장소의 맨 위 VERSION 파일 (raw.githubusercontent.com) */
    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/zzangf1234-boop/s/main/VERSION";
    /**
     * 새 APK 내려받기 주소 — 저장소의 release/DID-Call.apk (raw.githubusercontent.com, 로그인 불필요).
     * Actions 빌드가 끝나면 알아서 이 파일을 갱신하므로 앱 수정 없이 항상 최신.
     */
    private static final String APK_URL =
            "https://raw.githubusercontent.com/zzangf1234-boop/s/main/release/DID-Call.apk";

    private Updater() {}

    /** 이 앱의 버전 이름 (build.gradle 의 versionName = VERSION 파일) */
    static String current(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** "1.2.2" 처럼 점으로 구분된 숫자 비교 (짧은 쪽은 0으로) */
    static int compare(String a, String b) {
        try {
            String[] as = a.split("\\."), bs = b.split("\\.");
            for (int i = 0; i < Math.max(as.length, bs.length); i++) {
                int x = i < as.length ? Integer.parseInt(as[i]) : 0;
                int y = i < bs.length ? Integer.parseInt(bs[i]) : 0;
                if (x != y) return Integer.compare(x, y);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** 새 버전이 있는지 물어봄. result 는 "update:<버전>", "latest", 또는 오류 메시지 */
    static String check(Context c) {
        try {
            String remote = Http.get(VERSION_URL, 6000, 6000).trim();
            if (remote.isEmpty()) return "버전 정보를 못 읽었습니다";
            if (compare(current(c), remote) < 0) return "update:" + remote;
            return "latest";
        } catch (Exception e) {
            String m = e.getMessage();
            return m != null && !m.isEmpty() ? m : "인터넷에 연결할 수 없습니다";
        }
    }

    /** APK 를 받아 설치 화면을 띄움. 실패 이유(사람이 읽는 글), 성공이면 null */
    static String downloadAndInstall(Activity a, String ver) {
        try {
            File dst = new File(a.getCacheDir(), "update.apk");
            byte[] data = fetchFile(APK_URL);
            if (data.length < 1024) {
                // 드라이브의 "바이러스 검사" 확인 페이지 — confirm·uuid 를 읽어 다시 받음
                String html = new String(data, "UTF-8");
                String confirm = match(html, "name=\"confirm\"[^>]*value=\"([^\"]*)\"");
                String uuid = match(html, "name=\"uuid\"[^>]*value=\"([^\"]*)\"");
                if (confirm == null) return "APK 받기에 실패했습니다 (드라이브 응답을 해석하지 못했습니다)";
                StringBuilder u = new StringBuilder("https://drive.usercontent.google.com/download?")
                        .append("confirm=").append(java.net.URLEncoder.encode(confirm, "UTF-8"));
                if (uuid != null) u.append("&uuid=").append(java.net.URLEncoder.encode(uuid, "UTF-8"));
                data = fetchFile(u.toString());
            }
            if (data.length < 1024) return "APK 를 받지 못했습니다 (인터넷 확인)";
            try (FileOutputStream out = new FileOutputStream(dst)) {
                out.write(data);
            }
            SlaveUpdate.install(a, dst);
            return null;
        } catch (Exception e) {
            return "다운로드 중 오류: " + e.getMessage();
        }
    }

    /** 주소에서 파일을 통째로 받음 */
    private static byte[] fetchFile(String url) throws Exception {
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        try (InputStream in = new java.net.URL(url).openStream()) {
            byte[] buf = new byte[64 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        }
        return bo.toByteArray();
    }

    /** html 에서 첫 정규식 그룹, 없으면 null */
    private static String match(String html, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(html);
        return m.find() ? m.group(1) : null;
    }

    /** 호출기 앱 메뉴에서 부름: 새 버전이면 바로 받아 설치 화면 */
    static void showResult(Activity a, String result) {
        if (result.startsWith("update:")) {
            final String ver = result.substring(7);
            new AlertDialog.Builder(a, R.style.DidDialog)
                    .setTitle("새 버전이 있습니다")
                    .setMessage("현재 " + current(a) + " → 새 버전 " + ver
                            + "\n\n[업데이트]를 누르면 바로 받아 설치 화면을 열어 드립니다."
                            + "\n설치가 끝나면 앱이 잠시 꺼졌다 켜지고, TV 기기도 자동으로 따라 업데이트됩니다.")
                    .setPositiveButton("업데이트", (d, w) -> {
                        android.widget.Toast.makeText(a, "다운로드 중…", android.widget.Toast.LENGTH_SHORT).show();
                        new Thread(() -> {
                            final String err = downloadAndInstall(a, ver);
                            a.runOnUiThread(() -> {
                                if (err == null) {
                                    android.widget.Toast.makeText(a, ver + " 설치 화면을 열었습니다 — [설치]를 눌러 주세요", android.widget.Toast.LENGTH_LONG).show();
                                } else {
                                    new AlertDialog.Builder(a, R.style.DidDialog)
                                            .setTitle("업데이트 실패")
                                            .setMessage(err)
                                            .setPositiveButton("확인", null)
                                            .show();
                                }
                            });
                        }).start();
                    })
                    .setNegativeButton("나중에", null)
                    .show();
        } else if ("latest".equals(result)) {
            new AlertDialog.Builder(a, R.style.DidDialog)
                    .setTitle("업데이트 확인")
                    .setMessage("최신 버전입니다 (현재 " + current(a) + ")")
                    .setPositiveButton("확인", null)
                    .show();
        } else {
            new AlertDialog.Builder(a, R.style.DidDialog)
                    .setTitle("업데이트 확인 실패")
                    .setMessage("인터넷(와이파이) 연결을 확인해 주세요.\n" + result)
                    .setPositiveButton("확인", null)
                    .show();
        }
    }
}
