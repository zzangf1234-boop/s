package com.store.didcall;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 호출기 앱이 자기 서버(127.0.0.1)에 요청할 때 쓰는 간단한 HTTP 도우미 (백그라운드 스레드에서 호출) */
final class Http {
    interface Progress { void on(long sent, long total); }

    private Http() {}

    static String get(String url) throws Exception {
        return get(url, 4000, 8000);
    }

    static String get(String url, int connectMs, int readMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(connectMs);
        c.setReadTimeout(readMs);
        c.setUseCaches(false);
        try {
            return read(c);
        } finally {
            c.disconnect();
        }
    }

    static String post(String url, byte[] body, String type) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", type);
        c.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream os = c.getOutputStream()) { os.write(body); }
            return read(c);
        } finally {
            c.disconnect();
        }
    }

    /** 큰 파일(동영상)을 메모리에 올리지 않고 바로 전송 */
    static String postStream(String url, InputStream in, long len, String type, Progress p) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", type);
        c.setFixedLengthStreamingMode(len);
        try {
            try (OutputStream os = c.getOutputStream()) {
                byte[] buf = new byte[64 * 1024];
                long sent = 0, lastReport = 0;
                int r;
                while (sent < len && (r = in.read(buf, 0, (int) Math.min(buf.length, len - sent))) > 0) {
                    os.write(buf, 0, r);
                    sent += r;
                    if (p != null && sent - lastReport > 1024 * 1024) {
                        p.on(sent, len);
                        lastReport = sent;
                    }
                }
                if (sent < len) throw new Exception("파일을 끝까지 읽지 못했습니다");
            }
            return read(c);
        } finally {
            c.disconnect();
        }
    }

    private static String read(HttpURLConnection c) throws Exception {
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        if (in == null) return "";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int r;
        while ((r = in.read(b)) > 0) bo.write(b, 0, r);
        return bo.toString("UTF-8");
    }
}
