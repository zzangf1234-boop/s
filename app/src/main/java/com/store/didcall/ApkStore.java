package com.store.didcall;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 새 앱(APK) 파일을 호출기 폰에 보관하고 TV 기기에 배포하기 위한 저장소.
 * 호출기 폰이 새 APK 를 POST /apk 로 올리면 filesDir/apk/app.apk 에 저장,
 * TV 기기는 GET /apk/info 로 버전을 물어보고 새 버전이면 GET /apk 로 받아 설치.
 */
final class ApkStore {

    private final File file;      // filesDir/apk/app.apk
    private String version = "";  // 보관 중인 APK 의 버전이름 (올릴 때 같이 받음)
    private long size;

    ApkStore(Context c) {
        File d = new File(c.getFilesDir(), "apk");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        file = new File(d, "app.apk");
        if (file.isFile()) size = file.length();
    }

    /** 호출기 폰의 자기 APK 를 배포본으로 등록 (폰이 이미 새 버전이면 그 APK 가 곧 배포본).
     *  sourceApkPath: /data/app/.../base.apk 등. 실패해도 치명적이지 않아 조용히 넘어감 */
    void copyFromSelf(Context c) {
        try {
            String src = c.getPackageManager().getApplicationInfo(c.getPackageName(), 0).sourceDir;
            if (src == null) return;
            File in = new File(src);
            // 이미 같은 크기면 복사 생략 (매 시작 때 불필요한 쓰기 방지)
            if (file.isFile() && file.length() == in.length()) {
                size = file.length();
                return;
            }
            try (InputStream s = new java.io.FileInputStream(in);
                 OutputStream o = new FileOutputStream(file)) {
                byte[] buf = new byte[64 * 1024];
                int r;
                while ((r = s.read(buf)) > 0) o.write(buf, 0, r);
            }
            size = file.length();
        } catch (Exception ignored) {
        }
    }

    String version() { return version; }
    long size() { return size; }
    boolean has() { return file.isFile(); }
    File file() { return file; }

    /** 저장된 APK 의 버전을 기억 (설정 파일에서 복원용) */
    void setVersion(String v) { version = v == null ? "" : v; }

    /** 새 APK 를 통째로 저장 (기존 것을 덮음). 임시 파일에 다 받은 뒤 원자적으로 바꿈 */
    String save(InputStream in, long len) {
        if (len <= 0 || len > 150L * 1024 * 1024) return "APK 는 150MB 이하만 올릴 수 있습니다";
        File tmp = new File(file.getParentFile(), "upload.tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[64 * 1024];
            long left = len;
            while (left > 0) {
                int r = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (r < 0) throw new IOException("unexpected end");
                out.write(buf, 0, r);
                left -= r;
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "받는 중 오류: " + e.getMessage();
        }
        // APK 인지 가볍게 확인 (ZIP 헤더 PK)
        try (InputStream t = new java.io.FileInputStream(tmp)) {
            byte[] h = new byte[2];
            if (t.read(h) < 2 || h[0] != 'P' || h[1] != 'K') {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
                return "APK 파일이 아닙니다";
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "파일을 확인하지 못했습니다";
        }
        File old = file.isFile() ? new File(file.getParentFile(), "old.apk") : null;
        if (old != null) { //noinspection ResultOfMethodCallIgnored
            old.delete();
        }
        if (!tmp.renameTo(file)) return "저장에 실패했습니다";
        size = file.length();
        return null;
    }

    /** 오래된 이전 버전 APK 정리 (설치 성공 확인 뒤) */
    void cleanup() {
        //noinspection ResultOfMethodCallIgnored
        new File(file.getParentFile(), "old.apk").delete();
    }
}
