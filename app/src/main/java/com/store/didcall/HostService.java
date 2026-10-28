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
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 호출기(호스트) 폰에서 백그라운드로 계속 실행되는 호출 서버.
 * 번호·최근 호출·이미지·동영상·설정을 보관하고, TV/PC 화면이 재생할 알림음·안내 음성을 만듭니다.
 * 화면이 꺼져도 동작하도록 포그라운드 서비스 + 와이파이 잠금을 사용합니다.
 */
public class HostService extends Service implements CallServer.Host {

    // ── 설정 키 ──
    static final String K_TTS = "tts", K_SUB = "sub", K_REPEAT = "repeat";
    static final String K_SLIDE = "slide", K_FIT = "fit", K_POPUP = "popup", K_KEEP = "keep";
    /** (이전 버전) 키패드 배열 — KeyMap 이 이어받음 */
    static final String K_KEYPAD = "keypad";
    static final String K_CHIME = "chime", K_CHIME_VER = "chimeVer", K_THEME = "theme";
    static final String K_VOICE_MODE = "voiceMode", K_VOICE = "voiceName", K_RATE = "rate", K_PITCH = "pitch";
    static final String K_ENGINE = "ttsEngine", K_REC_VER = "recVer", K_VIDEO_SOUND = "videoSound";
    static final String K_OPEN = "open", K_CLOSE = "close", K_CLOSE_MSG = "closeMsg", K_CLOSE_ON = "closeOn";
    /** 키오스크 주문서 받기: 사용 여부, 중계할 주방 프린터 주소, 주문번호 앞 글자 */
    static final String K_KIOSK_ON = "kioskOn", K_RELAY = "relay", K_ORDER_LABEL = "orderLabel";
    private static final String K_CLOSED = "closed", K_CLOSE_SEQ = "closeSeq", K_STATE = "callState", K_AUTH = "authDevices";

    static final String DEF_TTS = "{n}번 고객님, 주문하신 메뉴가 준비되었습니다.";
    static final String DEF_SUB = "주문하신 메뉴가 준비되었습니다";
    static final String DEF_CLOSE_MSG = "금일 영업이 마감되었습니다. 이용해 주셔서 감사합니다.";

    private static final String CHANNEL = "didcall_host";
    private static final String CHANNEL_AUTH = "didcall_auth";
    private static final int NOTI_ID = 7;
    private static final String ACTION_DECIDE = "com.store.didcall.DECIDE";

    static volatile boolean running;

    /** '내 목소리' 녹음 조각이 저장되는 폴더 (호출기 앱 녹음 화면과 공유) */
    static File recDir(Context c) {
        File d = new File(c.getFilesDir(), "rec");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private final CallStore store = new CallStore();
    private MediaStore media;
    private Auth auth;
    private CallServer server;
    private PrintReceiver receiver;
    /** 최근 받은 주문서 기록 (호출기 설정 화면에 표시) */
    private final java.util.LinkedList<JSONObject> kioskLog = new java.util.LinkedList<>();
    private SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    // 안내 음성 (파일로 만들어 화면들에 전달)
    private final Object ttsLock = new Object();
    private TextToSpeech fileTts;
    private volatile boolean fileTtsReady;
    private String ttsEngineInUse = "";
    private File voiceDir;
    private final ExecutorService voiceExec = Executors.newSingleThreadExecutor();
    private final Map<String, CountDownLatch> pendingVoice = new ConcurrentHashMap<>();
    private final Map<String, byte[]> chimeCache = new ConcurrentHashMap<>();

    // 영업 마감
    private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
    private Boolean autoClosed; // 마지막으로 자동 판단한 상태 (처음엔 null)

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
        media = new MediaStore(new File(getFilesDir(), "images"));
        store.setKeepMinutes(keepMinutes());
        store.load(prefs.getString(K_STATE, ""));
        auth = new Auth(new Auth.Listener() {
            @Override public void onRequest(Auth.Request r) { main.post(() -> notifyRequest(r)); }
            @Override public void onSave(String data) { prefs.edit().putString(K_AUTH, data).apply(); }
        }, prefs.getString(K_AUTH, ""));
        voiceDir = new File(getCacheDir(), "voice");
        //noinspection ResultOfMethodCallIgnored
        voiceDir.mkdirs();
        initTts();

        acquireLocks();
        server = new CallServer(this);
        server.start();
        receiver = new PrintReceiver(new PrintReceiver.Listener() {
            @Override public void onReceipt(byte[] raw, String from, String relayResult) { handleReceipt(raw, from, relayResult); }
            @Override public String relayTarget() { return prefs.getString(K_RELAY, ""); }
        });
        if (prefs.getBoolean(K_KIOSK_ON, false)) receiver.start();
        ticker.scheduleWithFixedDelay(this::checkClosing, 3, 15, TimeUnit.SECONDS);
        running = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_DECIDE.equals(intent.getAction())) {
            String id = intent.getStringExtra("id");
            auth.decide(id, intent.getBooleanExtra("ok", false));
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && id != null) nm.cancel(id.hashCode());
            return START_STICKY;
        }
        startForegroundCompat(); // 알림의 주소를 최신으로
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        ticker.shutdownNow();
        if (server != null) server.stop();
        if (receiver != null) receiver.stop();
        synchronized (ttsLock) {
            if (fileTts != null) fileTts.shutdown();
        }
        voiceExec.shutdownNow();
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ───────────────────────── 알림 / 잠금 ─────────────────────────
    private void createChannels(NotificationManager nm) {
        if (Build.VERSION.SDK_INT < 26 || nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "호출 서버", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        NotificationChannel au = new NotificationChannel(CHANNEL_AUTH, "기기 승인 요청", NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(au);
    }

    private PendingIntent openCaller(int code) {
        Intent open = new Intent(this, CallerActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(this, code, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
    }

    private void startForegroundCompat() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createChannels(nm);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        Notification n = b.setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setContentTitle("DID 호출 서버 실행 중")
                .setContentText("TV·PC 화면 주소  http://" + Net.localIp() + ":" + Net.HTTP_PORT + "/display")
                .setContentIntent(openCaller(0))
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTI_ID, n);
        }
    }

    /** 새 기기가 호출 권한을 요청 → [허용] [거부] 알림 (호출기 앱 화면에도 대화상자가 뜸) */
    private void notifyRequest(Auth.Request r) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        createChannels(nm);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent allow = PendingIntent.getService(this, r.id.hashCode() * 2,
                new Intent(this, HostService.class).setAction(ACTION_DECIDE).putExtra("id", r.id).putExtra("ok", true), flags);
        PendingIntent deny = PendingIntent.getService(this, r.id.hashCode() * 2 + 1,
                new Intent(this, HostService.class).setAction(ACTION_DECIDE).putExtra("id", r.id).putExtra("ok", false), flags);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_AUTH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("새 기기가 호출 권한을 요청합니다")
                .setContentText(r.name + " (" + r.ip + ")")
                .setContentIntent(openCaller(1))
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(0, "허용", allow).build())
                .addAction(new Notification.Action.Builder(0, "거부", deny).build());
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH).setDefaults(Notification.DEFAULT_ALL);
        try { nm.notify(r.id.hashCode(), b.build()); } catch (Exception ignored) {}
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

    // ───────────────────────── 설정 값 ─────────────────────────
    private String ttsTemplate() { return prefs.getString(K_TTS, DEF_TTS); }
    private String subtitle() { return prefs.getString(K_SUB, DEF_SUB); }
    private int repeat() { return prefs.getInt(K_REPEAT, 2); }
    private int slideSeconds() { return prefs.getInt(K_SLIDE, 8); }
    private int popupSeconds() { return prefs.getInt(K_POPUP, 10); }
    private String fit() { return prefs.getString(K_FIT, "contain"); }
    private int keepMinutes() { return prefs.getInt(K_KEEP, 5); }
    private String chimeType() { return prefs.getString(K_CHIME, "dingdong"); }
    private String voiceMode() { return prefs.getString(K_VOICE_MODE, "tts"); }
    private String voiceName() { return prefs.getString(K_VOICE, ""); }
    private int rate() { return prefs.getInt(K_RATE, 95); }
    private int pitch() { return prefs.getInt(K_PITCH, 100); }
    private String openTime() { return prefs.getString(K_OPEN, "09:00"); }
    private String closeTime() { return prefs.getString(K_CLOSE, "22:00"); }
    private String closeMsg() { return prefs.getString(K_CLOSE_MSG, DEF_CLOSE_MSG); }
    private boolean closeOn() { return prefs.getBoolean(K_CLOSE_ON, false); }

    private static int clampInt(String v, int def, int min, int max) {
        try { return Math.max(min, Math.min(max, Integer.parseInt(v.trim()))); }
        catch (Exception e) { return def; }
    }

    private boolean isChimeType(String t) {
        for (String[] c : Sound.CHIMES) if (c[0].equals(t)) return true;
        return "custom".equals(t) && customChime() != null;
    }

    /** 녹음 조각이 모두 있는지 (번호 숫자·단위 + 뒷문장) */
    private boolean recReady() {
        File d = recDir(this);
        for (String[] c : Sound.CLIPS) if (!new File(d, c[0] + ".wav").isFile()) return false;
        return new File(d, "suffix.wav").isFile();
    }

    // ───────────────────────── CallServer.Host ─────────────────────────
    @Override public CallStore store() { return store; }
    @Override public MediaStore media() { return media; }
    @Override public Auth auth() { return auth; }

    @Override
    public void putConfig(JSONObject o) throws Exception {
        o.put("tts", ttsTemplate());
        o.put("sub", subtitle());
        o.put("repeat", repeat());
        o.put("slide", slideSeconds());
        o.put("popup", popupSeconds());
        o.put("fit", fit());
        o.put("keep", keepMinutes());
        o.put("voice", fileTtsReady || ("rec".equals(voiceMode()) && recReady()));
        o.put("keys", KeyMap.current(prefs));
        o.put("chime", chimeType());
        o.put("chimeVer", prefs.getInt(K_CHIME_VER, 0));
        o.put("customChime", customChime() != null);
        o.put("theme", prefs.getString(K_THEME, "vivid"));
        o.put("voiceMode", voiceMode());
        o.put("voiceName", voiceName());
        o.put("rate", rate());
        o.put("pitch", pitch());
        o.put("ttsEngine", prefs.getString(K_ENGINE, ""));
        o.put("recReady", recReady());
        o.put("videoSound", prefs.getBoolean(K_VIDEO_SOUND, false));
        o.put("open", openTime());
        o.put("close", closeTime());
        o.put("closeMsg", closeMsg());
        o.put("closeOn", closeOn());
        o.put("closed", prefs.getBoolean(K_CLOSED, false));
        o.put("closeSeq", prefs.getLong(K_CLOSE_SEQ, 0));
        o.put("kioskOn", prefs.getBoolean(K_KIOSK_ON, false));
        o.put("kioskPort", PrintReceiver.PORT);
        o.put("relay", prefs.getString(K_RELAY, ""));
        o.put("orderLabel", prefs.getString(K_ORDER_LABEL, "주문번호"));
        JSONArray log = new JSONArray();
        synchronized (kioskLog) { for (JSONObject j : kioskLog) log.put(j); }
        o.put("kioskLog", log);
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
        if (q.containsKey("keys")) {
            String k = KeyMap.sanitize(q.get("keys"));
            if (k != null) e.putString(KeyMap.K_KEYS, k);
        }
        if (q.containsKey("chime") && isChimeType(q.get("chime")) && !q.get("chime").equals(chimeType())) {
            e.putString(K_CHIME, q.get("chime"));
            e.putInt(K_CHIME_VER, prefs.getInt(K_CHIME_VER, 0) + 1);
        }
        if (q.containsKey("theme")) e.putString(K_THEME, "calm".equals(q.get("theme")) ? "calm" : "vivid");
        if (q.containsKey("voiceMode")) e.putString(K_VOICE_MODE, "rec".equals(q.get("voiceMode")) ? "rec" : "tts");
        if (q.containsKey("voiceName")) {
            String v = q.get("voiceName");
            if (v.length() <= 120 && v.matches("[A-Za-z0-9_\\-#.:@ ]*")) e.putString(K_VOICE, v);
        }
        if (q.containsKey("rate")) e.putInt(K_RATE, clampInt(q.get("rate"), rate(), 50, 200));
        if (q.containsKey("pitch")) e.putInt(K_PITCH, clampInt(q.get("pitch"), pitch(), 50, 200));
        boolean engineChanged = false;
        if (q.containsKey("ttsEngine")) {
            String en = q.get("ttsEngine");
            if (en.isEmpty() || en.matches("[A-Za-z0-9_.]{1,100}")) {
                engineChanged = !en.equals(prefs.getString(K_ENGINE, ""));
                e.putString(K_ENGINE, en);
                if (engineChanged) e.putString(K_VOICE, ""); // 엔진이 바뀌면 목소리는 기본으로
            }
        }
        if (q.containsKey("recVer")) e.putInt(K_REC_VER, prefs.getInt(K_REC_VER, 0) + 1);
        if (q.containsKey("videoSound")) e.putBoolean(K_VIDEO_SOUND, "1".equals(q.get("videoSound")) || "true".equals(q.get("videoSound")));
        String hhmm = "([01][0-9]|2[0-3]):[0-5][0-9]";
        boolean closingChanged = false;
        if (q.containsKey("open") && q.get("open").matches(hhmm)) { e.putString(K_OPEN, q.get("open")); closingChanged = true; }
        if (q.containsKey("close") && q.get("close").matches(hhmm)) { e.putString(K_CLOSE, q.get("close")); closingChanged = true; }
        if (q.containsKey("closeMsg")) {
            String m = q.get("closeMsg").trim();
            e.putString(K_CLOSE_MSG, m.isEmpty() ? DEF_CLOSE_MSG : (m.length() > 150 ? m.substring(0, 150) : m));
        }
        if (q.containsKey("closeOn")) {
            e.putBoolean(K_CLOSE_ON, "1".equals(q.get("closeOn")) || "true".equals(q.get("closeOn")));
            closingChanged = true;
        }
        boolean kioskChanged = false;
        if (q.containsKey("kioskOn")) {
            boolean on = "1".equals(q.get("kioskOn")) || "true".equals(q.get("kioskOn"));
            kioskChanged = on != prefs.getBoolean(K_KIOSK_ON, false);
            e.putBoolean(K_KIOSK_ON, on);
        }
        if (q.containsKey("relay")) {
            String r = q.get("relay").trim();
            if (r.isEmpty() || r.matches("[0-9A-Za-z.\\-]{1,100}(:[0-9]{1,5})?")) e.putString(K_RELAY, r);
        }
        if (q.containsKey("orderLabel")) {
            String l = q.get("orderLabel").trim();
            e.putString(K_ORDER_LABEL, l.isEmpty() || l.length() > 20 ? "주문번호" : l);
        }
        e.apply();
        if (kioskChanged) {
            if (prefs.getBoolean(K_KIOSK_ON, false)) receiver.start(); else receiver.stop();
        }
        store.setKeepMinutes(keepMinutes());
        if (closingChanged) autoClosed = null; // 다음 확인 때 조용히 다시 판단
        if (engineChanged) main.post(this::initTts);
    }

    @Override
    public void onChanged(String event) {
        if ("call".equals(event) || "recall".equals(event) || "clear".equals(event) || "waiting".equals(event)) {
            prefs.edit().putString(K_STATE, store.save()).apply(); // 재시작해도 번호 유지
        }
        // 화면들이 곧 요청할 안내 음성을 미리 만들어 둠
        if (("call".equals(event) || "recall".equals(event)) && repeat() > 0) {
            final String n = store.current();
            try { voiceExec.execute(() -> voiceFor(n)); } catch (Exception ignored) {}
        }
    }

    // ───────────────────────── 키오스크 주문서 ─────────────────────────
    /** 주문서 한 장을 받으면: 주문번호를 읽어 조리중에 추가 (중복·최근 호출 번호는 무시) */
    private void handleReceipt(byte[] raw, String from, String relayResult) {
        Receipt r = Receipt.parse(raw, prefs.getString(K_ORDER_LABEL, "주문번호"));
        boolean added = false;
        if (r.number != null) { // 재발행도 읽음 — 이미 조리중이거나 최근에 부른 번호면 addWaiting 이 무시
            added = store.addWaiting(r.number, r.summary());
            if (added) prefs.edit().putString(K_STATE, store.save()).apply();
        }
        try {
            JSONObject j = new JSONObject();
            j.put("time", System.currentTimeMillis());
            j.put("n", r.number == null ? "" : r.number);
            j.put("reprint", r.reprint);
            j.put("added", added);
            j.put("items", r.summary());
            j.put("from", from);
            j.put("bytes", raw.length);
            String id = saveReceipt(raw);
            if (id != null) j.put("id", id);
            if (relayResult != null) j.put("relay", relayResult);
            synchronized (kioskLog) {
                kioskLog.addFirst(j);
                while (kioskLog.size() > 10) kioskLog.removeLast();
            }
        } catch (Exception ignored) {
        }
    }

    /** 받은 주문서 원본을 최근 30장까지 보관 (진단용: 앱·브라우저에서 내용 보기·원본 받기) */
    private File receiptDir() {
        File d = new File(getFilesDir(), "receipts");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private String saveReceipt(byte[] raw) {
        try {
            if (raw.length > 2 * 1024 * 1024) return null;
            File d = receiptDir();
            long id = System.currentTimeMillis();
            File f;
            while ((f = new File(d, id + ".bin")).exists()) id++;
            try (java.io.FileOutputStream o = new java.io.FileOutputStream(f)) { o.write(raw); }
            File[] all = d.listFiles((x, n) -> n.endsWith(".bin"));
            if (all != null && all.length > 30) {
                java.util.Arrays.sort(all, (a, b) -> a.getName().compareTo(b.getName()));
                for (int i = 0; i < all.length - 30; i++) //noinspection ResultOfMethodCallIgnored
                    all[i].delete();
            }
            return String.valueOf(id);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public File receiptFile(String id) {
        if (id == null || !id.matches("[0-9]{1,20}")) return null;
        File f = new File(receiptDir(), id + ".bin");
        return f.isFile() ? f : null;
    }

    @Override
    public String receiptDiagnose(File f) throws java.io.IOException {
        byte[] b = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int off = 0, r;
            while (off < b.length && (r = in.read(b, off, b.length - off)) > 0) off += r;
        }
        return Receipt.diagnose(b, prefs.getString(K_ORDER_LABEL, "주문번호"));
    }

    // ───────────────────────── 영업 마감 ─────────────────────────
    @Override
    public void setClosed(boolean closed) {
        applyClosed(closed, closed);
    }

    private synchronized void applyClosed(boolean closed, boolean announce) {
        if (closed) {
            store.clearWaiting(); // 마감하면 조리중 목록 비우기
            prefs.edit().putString(K_STATE, store.save()).apply();
        }
        SharedPreferences.Editor e = prefs.edit().putBoolean(K_CLOSED, closed);
        if (announce) e.putLong(K_CLOSE_SEQ, prefs.getLong(K_CLOSE_SEQ, 0) + 1);
        e.apply();
        if (closed && announce) {
            try { voiceExec.execute(this::closingVoice); } catch (Exception ignored) {}
        }
    }

    private static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    /** 영업 시간 안인지 (마감이 자정을 넘는 경우 포함) */
    static boolean isOpenAt(int now, int open, int close) {
        if (open == close) return true;
        if (open < close) return now >= open && now < close;
        return now >= open || now < close;
    }

    private void checkClosing() {
        try {
            if (!closeOn()) { autoClosed = null; return; }
            Calendar c = Calendar.getInstance();
            int now = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
            boolean shouldClose = !isOpenAt(now, minutes(openTime()), minutes(closeTime()));
            if (autoClosed == null) {
                // 앱이 켜졌거나 설정이 바뀐 직후: 소리 없이 현재 상태만 맞춤
                autoClosed = shouldClose;
                if (prefs.getBoolean(K_CLOSED, false) != shouldClose) applyClosed(shouldClose, false);
                return;
            }
            if (autoClosed != shouldClose) {
                autoClosed = shouldClose;
                applyClosed(shouldClose, shouldClose); // 마감될 때만 안내
            }
        } catch (Exception ignored) {
        }
    }

    // ───────────────────────── 알림음 ─────────────────────────
    @Override
    public byte[] chimeWav(String type) {
        if (type == null || type.isEmpty()) type = chimeType();
        if ("custom".equals(type)) return null;
        boolean known = false;
        for (String[] c : Sound.CHIMES) if (c[0].equals(type)) known = true;
        if (!known) type = "dingdong";
        byte[] w = chimeCache.get(type);
        if (w == null) {
            w = Sound.toWav(Sound.chime(type), Sound.RATE);
            chimeCache.put(type, w);
        }
        return w;
    }

    @Override
    public File customChime() {
        File[] fs = getFilesDir().listFiles((d, name) -> name.startsWith("chime_custom."));
        return fs == null || fs.length == 0 ? null : fs[0];
    }

    @Override
    public String saveCustomChime(InputStream in, long len) throws IOException {
        if (len > 5L * 1024 * 1024) return "알림음 파일은 5MB 이하만 올릴 수 있습니다";
        File tmp = new File(getFilesDir(), "chime_upload.tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[32 * 1024];
            long left = len;
            while (left > 0) {
                int r = in.read(buf, 0, (int) Math.min(buf.length, left));
                if (r < 0) throw new IOException("unexpected end");
                out.write(buf, 0, r);
                left -= r;
            }
        }
        byte[] h = new byte[12];
        try (FileInputStream fin = new FileInputStream(tmp)) {
            if (fin.read(h) < 12) h = null;
        }
        String ext = null;
        if (h != null) {
            if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W') ext = "wav";
            else if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') ext = "mp3";
            else if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0) ext = "mp3";
            else if (h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S') ext = "ogg";
            else if (h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') ext = "m4a";
        }
        if (ext == null) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "mp3·wav·ogg·m4a 소리 파일만 올릴 수 있습니다";
        }
        File old = customChime();
        if (old != null) {
            //noinspection ResultOfMethodCallIgnored
            old.delete();
        }
        if (!tmp.renameTo(new File(getFilesDir(), "chime_custom." + ext))) return "저장에 실패했습니다";
        prefs.edit().putString(K_CHIME, "custom").putInt(K_CHIME_VER, prefs.getInt(K_CHIME_VER, 0) + 1).apply();
        return null;
    }

    // ───────────────────────── 안내 음성 ─────────────────────────
    private void initTts() {
        String engine = prefs.getString(K_ENGINE, "");
        synchronized (ttsLock) {
            fileTtsReady = false;
            if (fileTts != null) {
                try { fileTts.shutdown(); } catch (Exception ignored) {}
            }
            ttsEngineInUse = engine;
            final TextToSpeech[] holder = new TextToSpeech[1];
            TextToSpeech.OnInitListener l = status -> {
                synchronized (ttsLock) {
                    TextToSpeech t = holder[0];
                    if (t == null || t != fileTts || status != TextToSpeech.SUCCESS) return;
                    int r = t.setLanguage(Locale.KOREAN);
                    t.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) {}
                        @Override public void onDone(String id) { finishVoice(id, true); }
                        @Override public void onError(String id) { finishVoice(id, false); }
                    });
                    fileTtsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                }
            };
            fileTts = engine.isEmpty() ? new TextToSpeech(this, l) : new TextToSpeech(this, l, engine);
            holder[0] = fileTts;
        }
    }

    @Override
    public File voiceFor(String n) {
        if (n == null || n.isEmpty()) return null;
        if ("rec".equals(voiceMode())) {
            File f = recordedVoice(n);
            if (f != null) return f;
        }
        return ttsFile(ttsTemplate().replace("{n}", n), voiceName(), rate(), pitch());
    }

    @Override
    public File closingVoice() {
        if ("rec".equals(voiceMode())) {
            File f = new File(recDir(this), "closing.wav");
            if (f.isFile()) return f;
        }
        return ttsFile(closeMsg(), voiceName(), rate(), pitch());
    }

    @Override
    public File previewVoice(String n, String voice, int rate, int pitch) {
        return ttsFile(ttsTemplate().replace("{n}", n), voice == null ? voiceName() : voice,
                Math.max(50, Math.min(200, rate)), Math.max(50, Math.min(200, pitch)));
    }

    /** '내 목소리' 녹음 조각을 이어 붙인 안내 음성 (조각이 모자라면 null) */
    private File recordedVoice(String n) {
        File dir = recDir(this);
        boolean prefix = new File(dir, "prefix.wav").isFile();
        String key = "r" + prefs.getInt(K_REC_VER, 0) + "_" + (prefix ? "p" : "") + "_" + n;
        File f = new File(voiceDir, key + ".wav");
        if (f.length() > 44) return f;
        short[] pcm = Sound.stitch(dir, n, prefix);
        if (pcm == null) return null;
        try {
            pruneVoices();
            File tmp = new File(voiceDir, key + ".part");
            Sound.writeWav(tmp, pcm, Sound.RATE);
            //noinspection ResultOfMethodCallIgnored
            tmp.renameTo(f);
            return f.length() > 44 ? f : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** 기계 음성을 WAV 파일로 (같은 문장·목소리는 재사용). 서버 스레드에서 호출되며 최대 8초 대기 */
    private File ttsFile(String text, String voice, int rate, int pitch) {
        if (!fileTtsReady || text == null || text.isEmpty()) return null;
        String sig = ttsEngineInUse + "|" + voice + "|" + rate + "|" + pitch + "|" + text;
        String key = "v" + Integer.toHexString(sig.hashCode()) + "_" + text.length();
        File f = new File(voiceDir, key + ".wav");
        if (f.length() > 44) return f;
        CountDownLatch latch;
        synchronized (ttsLock) {
            if (!fileTtsReady || fileTts == null) return null;
            latch = pendingVoice.get(key);
            if (latch == null) {
                latch = new CountDownLatch(1);
                pendingVoice.put(key, latch);
                pruneVoices();
                try {
                    applyVoice(voice);
                    fileTts.setSpeechRate(rate / 100f);
                    fileTts.setPitch(pitch / 100f);
                    int r = fileTts.synthesizeToFile(text, new Bundle(), new File(voiceDir, key + ".tmp"), key);
                    if (r != TextToSpeech.SUCCESS) {
                        pendingVoice.remove(key);
                        return null;
                    }
                } catch (Exception ex) {
                    pendingVoice.remove(key);
                    return null;
                }
            }
        }
        try { latch.await(8, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return f.length() > 44 ? f : null;
    }

    private void applyVoice(String name) {
        if (name != null && !name.isEmpty()) {
            try {
                for (Voice v : fileTts.getVoices()) {
                    if (v.getName().equals(name)) {
                        fileTts.setVoice(v);
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        fileTts.setLanguage(Locale.KOREAN);
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

    /** 음성 파일은 최근 80개 정도만 보관 */
    private void pruneVoices() {
        File[] fs = voiceDir.listFiles((d, name) -> name.endsWith(".wav"));
        if (fs == null || fs.length < 90) return;
        Arrays.sort(fs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        for (int i = 0; i < fs.length - 80; i++) {
            //noinspection ResultOfMethodCallIgnored
            fs[i].delete();
        }
    }

    @Override
    public JSONObject voices() throws Exception {
        JSONObject o = new JSONObject();
        JSONArray engines = new JSONArray();
        JSONArray voices = new JSONArray();
        synchronized (ttsLock) {
            if (fileTts != null) {
                try {
                    for (TextToSpeech.EngineInfo ei : fileTts.getEngines()) {
                        JSONObject e = new JSONObject();
                        e.put("name", ei.name);
                        e.put("label", ei.label);
                        engines.put(e);
                    }
                } catch (Exception ignored) {
                }
                if (fileTtsReady) {
                    try {
                        List<Voice> ko = new ArrayList<>();
                        for (Voice v : fileTts.getVoices()) {
                            Locale l = v.getLocale();
                            if (l != null && ("ko".equals(l.getLanguage()) || "kor".equals(l.getISO3Language()))) ko.add(v);
                        }
                        Collections.sort(ko, (a, b) -> a.getName().compareTo(b.getName()));
                        int i = 1;
                        for (Voice v : ko) {
                            JSONObject e = new JSONObject();
                            e.put("name", v.getName());
                            boolean net = v.isNetworkConnectionRequired();
                            e.put("network", net);
                            e.put("label", "목소리 " + (i++) + (net ? " (인터넷 필요)" : "") + "  ·  " + v.getName());
                            voices.put(e);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        }
        o.put("ok", true);
        o.put("engines", engines);
        o.put("engine", ttsEngineInUse);
        o.put("voices", voices);
        o.put("ready", fileTtsReady);
        JSONArray chimes = new JSONArray();
        for (String[] c : Sound.CHIMES) {
            JSONObject e = new JSONObject();
            e.put("name", c[0]);
            e.put("label", c[1]);
            chimes.put(e);
        }
        o.put("chimes", chimes);
        return o;
    }
}
