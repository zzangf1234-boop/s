package com.store.didcall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * 업데이트 확인: 폰 업데이트 서버(server 세션 관리, 포트 8182)의 /api/version 과
 * 현재 앱 버전을 비교해 새 버전이면 APK 를 앱 안에서 바로 내려받아 설치 화면을 띄움.
 * 서버가 죽어 있으면 기존 방식(GitHub)으로 재시도.
 */
final class Updater {

    /** 업데이트 서버 — 같은 와이파이면 내부 주소, 아니면 외부 주소 */
    private static final String[] SERVERS = {
            "http://182.227.141.73:8182",
    };
    /** 서버의 다운로드 PIN */
    private static final String PIN = "1111";
    /** "안 함"을 고른 버전을 저장하는 설정 이름 */
    static final String PREFS = "updates";
    private static final String K_SKIP = "skip_";
    /** 시작 시 자동 알림 켜기/끄기 (업데이트 팝업의 "업데이트 중지"에서 토글) */
    static final String K_AUTO = "auto";
    /** 서버가 죽었을 때 쓰는 기존(GitHub) 주소 */
    private static final String FALLBACK_VERSION_URL =
            "https://raw.githubusercontent.com/zzangf1234-boop/s/main/VERSION";
    private static final String FALLBACK_APK_URL =
            "https://raw.githubusercontent.com/zzangf1234-boop/s/main/release/DID-Call.apk";

    private Updater() {}

    /** 서버 응답을 담는 그릇 */
    static class Remote {
        String version;        // 서버의 최신 버전
        String url;            // 최신 APK 다운로드 경로 (서버 기준 상대경로)
        String prevVersion;    // 직전 버전 (롤백용, 없으면 null)
        String prevUrl;        // 직전 APK 다운로드 경로
        boolean fromServer;    // true 면 폰 업데이트 서버, false 면 GitHub 폴백
    }

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

    /** 이 버전을 "안 함"으로 건너뛰었는지 */
    static boolean skipped(Context c, String ver) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_SKIP + ver, false);
    }

    /** 서버에 새 버전이 있는지 물어봄. 실패 시 GitHub 으로 재시도.
     *  반환 Remote 가 null 이면 확인 실패 (reason 에 이유) */
    static Remote check(Context c, StringBuilder reason) {
        // ① 폰 업데이트 서버 (이름은 현재 버전으로 물어보면 서버가 항상 최신 정보를 돌려줌)
        for (String base : SERVERS) {
            try {
                // 파일명은 고정(DID-Call.apk) — 서버가 같은 이름 재업로드를 새 버전으로 인식해 이전 버전을 자동 보관
                JSONObject o = new JSONObject(Http.get(base + "/api/version?name=DID-Call.apk&pin=" + PIN, 4000, 6000));
                Remote r = new Remote();
                // 서버는 버전을 "v1.10.1" 처럼 v 를 붙여 돌려주므로 떼고 비교
                r.version = o.optString("version", "").replaceFirst("^v", "");
                r.url = o.optString("url", "");
                if (!o.optString("prevVersion", "").isEmpty()) {
                    r.prevVersion = o.optString("prevVersion").replaceFirst("^v", "");
                    r.prevUrl = o.optString("prevUrl", "");
                }
                r.fromServer = true;
                return r; // 새 버전이든 최신이든 롤백 메뉴에서 prev 를 쓰므로 그대로 반환
            } catch (Exception ignored) {
                // 다음 주소로
            }
        }
        // ② GitHub 폴백
        try {
            String remote = Http.get(FALLBACK_VERSION_URL, 6000, 6000).trim();
            if (remote.isEmpty()) { reason.append("버전 정보를 못 읽었습니다"); return null; }
            Remote r = new Remote();
            r.version = remote;
            r.url = FALLBACK_APK_URL;
            r.fromServer = false;
            if (compare(current(c), remote) < 0) return r;
            r.version = ""; // 최신 표시용
            return r;
        } catch (Exception e) {
            String m = e.getMessage();
            reason.append(m != null && !m.isEmpty() ? m : "인터넷에 연결할 수 없습니다");
            return null;
        }
    }

    /** APK 를 받아 설치 화면을 띄움. urlPath 는 check() 이 돌려준 url (http 로 시작하면 그대로, 아니면 서버 기준 상대경로).
     *  실패 이유(사람이 읽는 글), 성공이면 null */
    static String downloadAndInstall(Activity a, String urlPath) {
        try {
            String url = urlPath;
            if (!url.startsWith("http")) url = SERVERS[0] + url; // 상대경로면 내부 주소에 붙임
            File dst = new File(a.getCacheDir(), "update.apk");
            byte[] data = fetchFile(url);
            if (data.length < 1024) return "APK 를 받지 못했습니다 (네트워크 확인)";
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

    /** 새 버전 알림 창. [업데이트] 바로 받아 설치, [나중에(안 함)]은 이 버전을 다시 묻지 않음.
     *  auto 가 true 면 앱 시작 자동 확인 (최신이면 아무 창도 안 띄움) */
    static void showResult(Activity a, Remote r, boolean auto) {
        boolean newer = r != null && r.version != null && !r.version.isEmpty()
                && compare(current(a), r.version) < 0;
        if (auto && !newer) return; // 자동 확인은 새 버전일 때만 알림
        if (r == null) {
            if (auto) return;
            new AlertDialog.Builder(a, R.style.DidDialog)
                    .setTitle("업데이트 확인 실패")
                    .setMessage("인터넷(와이파이) 연결을 확인해 주세요.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        if (!newer) {
            if (auto) return;
            new AlertDialog.Builder(a, R.style.DidDialog)
                    .setTitle("업데이트 확인")
                    .setMessage("최신 버전입니다 (현재 " + current(a) + ")")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        if (skipped(a, r.version)) { if (!auto) unskipAsk(a, r.version); return; }
        new AlertDialog.Builder(a, R.style.DidDialog)
                .setTitle("새 버전이 있습니다")
                .setMessage("현재 " + current(a) + " → 새 버전 " + r.version
                        + "\n\n[업데이트]를 누르면 바로 받아 설치 화면을 열어 드립니다."
                        + "\n설치가 끝나면 앱이 잠시 꺼졌다 켜지고, TV 기기도 자동으로 따라 업데이트됩니다.")
                .setPositiveButton("업데이트", (d, w) -> downloadFlow(a, r.version, r.url))
                .setNegativeButton("나중에 (이 버전 안 물어봄)", (d, w) -> {
                    a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_SKIP + r.version, true).apply();
                })
                .show();
    }

    /** 다운로드 → 설치 화면. 진행 표시와 실패 창까지 처리 */
    static void downloadFlow(Activity a, String ver, String url) {
        android.widget.Toast.makeText(a, "다운로드 중…", android.widget.Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            final String err = downloadAndInstall(a, url);
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
    }

    /** 설정 메뉴용: 이전 버전으로 되돌리기. 서버에 prev 가 없으면 안내만 */
    static void rollback(Activity a, Remote r) {
        if (r == null || r.prevVersion == null || r.prevUrl == null) {
            new AlertDialog.Builder(a, R.style.DidDialog)
                    .setTitle("이전 버전으로 되돌리기")
                    .setMessage("서버에 이전 버전이 없습니다.\n잠시 후 다시 시도해 주세요.")
                    .setPositiveButton("확인", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(a, R.style.DidDialog)
                .setTitle("이전 버전으로 되돌리기")
                .setMessage("현재 " + current(a) + " → 이전 버전 " + r.prevVersion
                        + "\n\n되돌리면 지금 버전의 새 기능이 사라집니다. 되돌릴까요?")
                .setPositiveButton("되돌리기", (d, w) -> {
                    // 건너뛰기 기록에 이전 버전이 남아 있으면 업데이트 알림이 다시 안 뜨므로 지움
                    a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(K_SKIP + r.prevVersion).apply();
                    downloadFlow(a, r.prevVersion, r.prevUrl);
                })
                .setNegativeButton("취소", null)
                .show();
    }

    /** 수동 확인에서 이 버전을 건너뛰어 둔 경우 — 다시 묻게 해줄지 물어봄 */
    private static void unskipAsk(Activity a, String ver) {
        new AlertDialog.Builder(a, R.style.DidDialog)
                .setTitle("건너뛴 버전")
                .setMessage(ver + " 은(는) '나중에 (안 물어봄)'으로 건너뛰어 둔 버전입니다.\n다시 알림을 받게 할까요?")
                .setPositiveButton("다시 알림", (d, w) -> {
                    a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(K_SKIP + ver).apply();
                    android.widget.Toast.makeText(a, "다음 확인부터 다시 알려 드립니다", android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("그대로", null)
                .show();
    }

    /** 앱 시작 자동 확인 — 자동 알림이 켜져 있고, 새 버전 && 건너뛰지 않았을 때만 알림 창 */
    static void autoCheck(Activity a) {
        if (!a.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_AUTO, true)) return;
        new Thread(() -> {
            StringBuilder reason = new StringBuilder();
            final Remote r = check(a, reason);
            a.runOnUiThread(() -> showResult(a, r, true));
        }).start();
    }
}
