package com.store.didcall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;

/**
 * 업데이트 확인: GitHub 의 VERSION 파일과 현재 앱 버전을 비교해
 * 새 버전이면 Actions Artifacts 안내 (공개 저장소이므로 GitHub 에서 직접 받는 게 가장 간단).
 * 기기별로 다운로드·설치 화면까지 자동으로 밀어 주지는 않는다 — 사장님이 APK 를 받아 열면 된다.
 */
final class Updater {

    /** 저장소의 맨 위 VERSION 파일 (raw.githubusercontent.com) */
    private static final String VERSION_URL =
            "https://raw.githubusercontent.com/zzangf1234-boop/s/main/VERSION";
    private static final String ACTIONS_URL =
            "https://github.com/zzangf1234-boop/s/actions";

    private Updater() {}

    /** 이 앱의 버전 이름 (build.gradle 의 versionName = VERSION 파일) */
    static String current(android.content.Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    /** 새 버전이 있는지 물어봄. result 는 "ok", "latest", 또는 오류 메시지 */
    static String check(android.content.Context c) {
        try {
            String remote = Http.get(VERSION_URL, 6000, 6000).trim();
            if (remote.isEmpty()) return "버전 정보를 못 읽었습니다";
            int cmp = compare(current(c), remote);
            if (cmp < 0) return "update:" + remote;
            return "latest";
        } catch (Exception e) {
            String m = e.getMessage();
            return m != null && !m.isEmpty() ? m : "인터넷에 연결할 수 없습니다";
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

    /** 호출기 앱 메뉴에서 부름: 결과를 대화상자로 보여 주고, 새 버전이면 GitHub Actions 로 */
    static void showResult(Activity a, String result) {
        if (result.startsWith("update:")) {
            String ver = result.substring(7);
            new AlertDialog.Builder(a, R.style.DidDialog)
                    .setTitle("새 버전이 있습니다")
                    .setMessage("현재 " + current(a) + " → 새 버전 " + ver
                            + "\n\n1) APK 받기 — GitHub 사이트의 최근 빌드에서 DID-number-call-apk 를 받아 설치합니다.\n"
                            + "2) 설치 후 앱을 켜면, 이 폰이 새 APK 를 자기 서버에 올려 TV 기기에 자동으로 배포합니다.\n\n다운로드 페이지를 열까요?")
                    .setPositiveButton("페이지 열기", (d, w) -> {
                        try { a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ACTIONS_URL))); }
                        catch (Exception ignored) {}
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
