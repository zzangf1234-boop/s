package com.store.didcall;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 호출기(호스트) 폰에서 백그라운드로 계속 실행되는 호출 서버.
 * 번호·최근 호출·이미지·설정을 보관하고, TV/PC 화면이 재생할 안내 음성 파일을 만듭니다.
 * 화면이 꺼져도 동작하도록 포그라운드 서비스 + 와이파이 잠금을 사용합니다.
 */
public class HostService extends Service implements CallServer.Host {

    static final String K_TTS = "tts", K_SUB = "sub", K_REPEAT = "repeat";
    static final String K_SLIDE = "slide", K_FIT = "fit", K_POPUP = "popup", K_KEEP = "keep";
    /** 키패드 배열: tenkey(. = 한 칸 지우기) / backspace(Backspace = 한 칸 지우기, . = 전체 삭제) */
    static final String K_KEYPAD = "keypad";
    static final String DEF_TTS = "{n}번 고객님, 주문하신 메뉴가 준비되었습니다.";
    static final String DEF_SUB = "주문하신 메뉴가 준비되었습니다";

    private static final String CHANNEL = "didcall_host";
    private static final int NOTI_ID = 7;

    static volatile boolean running;

    private final CallStore store = new CallStore();
    private ImageStore images;
    private CallServer server;
    private SharedPreferences prefs;

    private TextToSpeech fileTts;
    private volatile boolean fileTtsReady;
    private File voiceDir;
    private final ExecutorService voiceExec = Executors.newSingleThreadExecutor();
    private final Map<String, CountDownLatch> pendingVoice = new ConcurrentHashMap<>();
    private byte[] chimeWav;

    private WifiManager.MulticastLock multicastLock;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;

    static void start(Context c) {
        Intent i = new Intent(c, HostService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
        } catch (Exception ignored) {
        }
    }

    static void stop(Context c) {
        c.stopService(new Intent(c, HostService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundCompat();
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        images = new ImageStore(new File(getFilesDir(), "images"));
        store.setKeepMinutes(keepMinutes());
        chimeWav = toWav(buildChime(), 22050);
        voiceDir = new File(getCacheDir(), "voice");
        //noinspection ResultOfMethodCallIgnored
        voiceDir.mkdirs();

        fileTts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.SUCCESS) return;
            int r = fileTts.setLanguage(Locale.KOREAN);
            fileTts.setSpeechRate(0.95f);
            fileTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { finishVoice(id, true); }
                @Override public void onError(String id) { finishVoice(id, false); }
            });
            fileTtsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
        });

        acquireLocks();
        server = new CallServer(this);
        server.start();
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundCompat(); // 알림의 주소를 최신으로
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (server != null) server.stop();
        if (fileTts != null) fileTts.shutdown();
        voiceExec.shutdownNow();
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ───────────────────────── 알림 / 잠금 ─────────────────────────
    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "호출 서버", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, CallerActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("DID 호출 서버 실행 중")
                .setContentText("TV·PC 화면 주소  http://" + Net.localIp() + ":" + Net.HTTP_PORT + "/display")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTI_ID, n);
        }
    }

    @SuppressWarnings("deprecation")
    private void acquireLocks() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                multicastLock = wm.createMulticastLock("didcall");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "didcall");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "didcall:host");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Exception ignored) {
        }
    }

    private void releaseLocks() {
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); } catch (Exception ignored) {}
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) {}
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
    }

    // ───────────────────────── 설정 ─────────────────────────
    private String ttsTemplate() { return prefs.getString(K_TTS, DEF_TTS); }
    private String subtitle() { return prefs.getString(K_SUB, DEF_SUB); }
    private int repeat() { return prefs.getInt(K_REPEAT, 2); }
    private int slideSeconds() { return prefs.getInt(K_SLIDE, 8); }
    private int popupSeconds() { return prefs.getInt(K_POPUP, 10); }
    private String fit() { return prefs.getString(K_FIT, "contain"); }
    private int keepMinutes() { return prefs.getInt(K_KEEP, 5); }

    private static int clampInt(String v, int def, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(v.trim()))); }
        catch (Exception e) { return def; }
    }

    // ───────────────────────── CallServer.Host ─────────────────────────
    @Override public CallStore store() { return store; }
    @Override public ImageStore images() { return images; }
    @Override public byte[] chimeWav() { return chimeWav; }

    @Override
    public void putConfig(JSONObject o) throws Exception {
        o.put("tts", ttsTemplate());
        o.put("sub", subtitle());
        o.put("repeat", repeat());
        o.put("slide", slideSeconds());
        o.put("popup", popupSeconds());
        o.put("fit", fit());
        o.put("keep", keepMinutes());
        o.put("voice", fileTtsReady);
        o.put("keypad", prefs.getString(K_KEYPAD, "tenkey"));
    }

    @Override
    public void applyConfig(Map<String, String> q) {
        SharedPreferences.Editor e = prefs.edit();
        if (q.containsKey("tts")) {
            String t = q.get("tts").trim();
            e.putString(K_TTS, t.isEmpty() ? DEF_TTS : t);
        }
        if (q.containsKey("sub")) e.putString(K_SUB, q.get("sub").trim());
        if (q.containsKey("repeat")) e.putInt(K_REPEAT, clampInt(q.get("repeat"), repeat(), 0, 5));
        if (q.containsKey("slide")) e.putInt(K_SLIDE, clampInt(q.get("slide"), slideSeconds(), 3, 300));
        if (q.containsKey("popup")) e.putInt(K_POPUP, clampInt(q.get("popup"), popupSeconds(), 3, 60));
        if (q.containsKey("fit")) e.putString(K_FIT, "cover".equals(q.get("fit")) ? "cover" : "contain");
        if (q.containsKey("keep")) e.putInt(K_KEEP, clampInt(q.get("keep"), keepMinutes(), 0, 120));
        if (q.containsKey("keypad")) e.putString(K_KEYPAD, "backspace".equals(q.get("keypad")) ? "backspace" : "tenkey");
        e.apply();
        store.setKeepMinutes(keepMinutes());
    }

    @Override
    public void onChanged(String event) {
        // 화면들이 곧 요청할 안내 음성을 미리 만들어 둠
        if (("call".equals(event) || "recall".equals(event)) && repeat() > 0) {
            final String n = store.current();
            try { voiceExec.execute(() -> voiceFor(n)); } catch (Exception ignored) {}
        }
    }

    // ───────────────────────── 안내 음성 파일 ─────────────────────────
    /** 안내 음성을 WAV 파일로 (같은 문장은 재사용). 서버 스레드에서 호출되며 최대 8초 대기 */
    @Override
    public File voiceFor(String n) {
        if (!fileTtsReady || fileTts == null || n == null || n.isEmpty()) return null;
        String text = ttsTemplate().replace("{n}", n);
        String key = "v" + Integer.toHexString(text.hashCode()) + "_" + text.length();
        File f = new File(voiceDir, key + ".wav");
        if (f.length() > 44) return f;
        CountDownLatch latch;
        synchronized (pendingVoice) {
            latch = pendingVoice.get(key);
            if (latch == null) {
                latch = new CountDownLatch(1);
                pendingVoice.put(key, latch);
                pruneVoices();
                int r = fileTts.synthesizeToFile(text, new Bundle(), new File(voiceDir, key + ".tmp"), key);
                if (r != TextToSpeech.SUCCESS) {
                    pendingVoice.remove(key);
                    return null;
                }
            }
        }
        try { latch.await(8, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return f.length() > 44 ? f : null;
    }

    private void finishVoice(String key, boolean ok) {
        File tmp = new File(voiceDir, key + ".tmp");
        if (ok && tmp.length() > 44) {
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(new File(voiceDir, key + ".wav"));
        } else {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        CountDownLatch l = pendingVoice.remove(key);
        if (l != null) l.countDown();
    }

    /** 음성 파일은 최근 50개 정도만 보관 */
    private void pruneVoices() {
        File[] fs = voiceDir.listFiles((d, name) -> name.endsWith(".wav"));
        if (fs == null || fs.length < 60) return;
        Arrays.sort(fs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < fs.length - 50; i++) {
            //noinspection ResultOfMethodCallIgnored
            fs[i].delete();
        }
    }

    // ───────────────────────── 딩동 효과음 ─────────────────────────
    /** '딩-동' 효과음을 직접 합성 (외부 음원 파일 불필요) */
    private static short[] buildChime() {
        int sr = 22050;
        double[] freqs = {659.25, 523.25}; // 미, 도
        int len = (int) (sr * 0.6);
        short[] buf = new short[len * 2];
        for (int k = 0; k < 2; k++) {
            for (int i = 0; i < len; i++) {
                double t = (double) i / sr;
                double env = Math.exp(-3.2 * t) * Math.min(1.0, i / 200.0);
                double v = Math.sin(2 * Math.PI * freqs[k] * t) * 0.7
                        + Math.sin(2 * Math.PI * freqs[k] * 2 * t) * 0.2
                        + Math.sin(2 * Math.PI * freqs[k] * 3 * t) * 0.05;
                buf[k * len + i] = (short) (v * env * 32767 * 0.75);
            }
        }
        return buf;
    }

    private static byte[] toWav(short[] pcm, int rate) {
        int dataLen = pcm.length * 2;
        ByteArrayOutputStream b = new ByteArrayOutputStream(44 + dataLen);
        writeStr(b, "RIFF"); writeInt(b, 36 + dataLen); writeStr(b, "WAVE");
        writeStr(b, "fmt "); writeInt(b, 16); writeShort(b, 1); writeShort(b, 1);
        writeInt(b, rate); writeInt(b, rate * 2); writeShort(b, 2); writeShort(b, 16);
        writeStr(b, "data"); writeInt(b, dataLen);
        for (short v : pcm) writeShort(b, v);
        return b.toByteArray();
    }
    private static void writeStr(ByteArrayOutputStream b, String s) { for (char c : s.toCharArray()) b.write(c); }
    private static void writeInt(ByteArrayOutputStream b, int v) { b.write(v); b.write(v >> 8); b.write(v >> 16); b.write(v >> 24); }
    private static void writeShort(ByteArrayOutputStream b, int v) { b.write(v); b.write(v >> 8); }
}
