package com.store.didcall;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.AudioManager;
import android.media.ExifInterface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 카운터 공기계에서 실행: 호출 서버(호스트)를 켜고, 번호를 입력해 TV·PC 화면에 호출 */
public class CallerActivity extends Activity implements VoiceRecorder.Host {

    private static final String K_LAST = "last_sent";
    private static final String K_ORIENT = "caller_orient";   // auto / portrait / landscape
    private static final String K_KEYPAD_VIS = "caller_keypad"; // 가로 모드에서 화면 키패드 보이기
    private static final int REQ_IMAGES = 41, REQ_VIDEOS = 42, REQ_CHIME = 43;
    private static final int MAX_SIDE = 2560;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService up = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private TextView statusView, addrView, inputView, currentView, msgView;
    private String input = "";
    private int lastSent;
    private JSONObject last = new JSONObject();
    private final Set<String> shownRequests = new HashSet<>();
    private MediaPlayer player;

    /** 호출 서버는 이 폰 안에서 돌아가므로 항상 자기 자신에게 요청 */
    private static final String BASE = "http://127.0.0.1:" + Net.HTTP_PORT;
    private final Runnable poller = new Runnable() {
        @Override public void run() { checkConnection(); ui.postDelayed(this, 3000); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        lastSent = prefs.getInt(K_LAST, 0);
        if (savedInstanceState != null) input = savedInstanceState.getString("input", "");
        showOverLockScreen();
        applyOrientation();
        buildUi();
        HostService.start(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("input", input);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!HostService.running) HostService.start(this);
        ui.removeCallbacks(poller);
        ui.postDelayed(poller, 600);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(poller);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        io.shutdownNow();
        up.shutdownNow();
    }

    /** 폰이 잠겨 있어도 호출기 화면이 보이고, 키패드 입력으로 화면이 켜지게 */
    @SuppressWarnings("deprecation")
    private void showOverLockScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    private void applyOrientation() {
        String o = prefs.getString(K_ORIENT, "auto");
        int req = "portrait".equals(o) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                : "landscape".equals(o) ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_FULL_USER;
        if (getRequestedOrientation() != req) setRequestedOrientation(req);
    }

    private boolean isLandscape() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    // ───────────────────────── 화면 구성 ─────────────────────────
    private void buildUi() {
        if (isLandscape()) buildLandscape(); else buildPortrait();
        showInput();
        applyStatus(last);
    }

    private View header() {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setOnClickListener(v -> showConnectInfo());
        statusView = Ui.text(this, "호출 서버 시작 중…", 15, Ui.MUTED, true);
        addrView = Ui.text(this, "", 14, Ui.TEXT, false);
        info.addView(statusView);
        info.addView(addrView);
        top.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        Button menu = Ui.button(this, "⚙", Ui.KEY, 18);
        menu.setOnClickListener(v -> showMenu());
        top.addView(menu, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 44)));
        return top;
    }

    private TextView makeCurrent() {
        currentView = Ui.text(this, "현재 호출 번호: -", 15, Ui.MUTED, false);
        currentView.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        return currentView;
    }

    private TextView makeInput(float sp) {
        inputView = Ui.text(this, "", sp, Ui.YELLOW, true);
        inputView.setGravity(Gravity.CENTER);
        inputView.setBackground(Ui.round(this, 0xFF000000, 14));
        inputView.setLetterSpacing(0.1f);
        inputView.setSingleLine(true);
        return inputView;
    }

    private LinearLayout makeKeypad() {
        int g = Ui.dp(this, 8);
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"←", "0", "C"}};
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        for (String[] row : keys) {
            LinearLayout r = new LinearLayout(this);
            pad.addView(r, new LinearLayout.LayoutParams(-1, 0, 1f));
            for (String k : row) {
                Button b = Ui.button(this, k, Ui.KEY, 30);
                b.setOnClickListener(v -> { tap(v); key(k); });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
                lp.setMargins(g / 2, g / 2, g / 2, g / 2);
                r.addView(b, lp);
            }
        }
        return pad;
    }

    private Button makeCall() {
        Button call = Ui.button(this, "호출", Ui.ACCENT, 30);
        call.setOnClickListener(v -> { tap(v); sendCall(); });
        return call;
    }

    private LinearLayout makeAux() {
        LinearLayout row = new LinearLayout(this);
        addAux(row, "재호출", v -> request("/recall", "재호출했습니다"));
        addAux(row, "다음 번호", v -> { input = String.valueOf(lastSent + 1); showInput(); });
        addAux(row, "화면 비우기", v -> request("/clear", "TV 화면을 비웠습니다"));
        return row;
    }

    private TextView makeMsg() {
        msgView = Ui.text(this, "", 15, Ui.MUTED, false);
        msgView.setGravity(Gravity.CENTER);
        msgView.setPadding(0, Ui.dp(this, 8), 0, 0);
        return msgView;
    }

    private void buildPortrait() {
        int g = Ui.dp(this, 8);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        root.addView(header());
        root.addView(makeCurrent());
        root.addView(makeInput(60), new LinearLayout.LayoutParams(-1, Ui.dp(this, 100)));
        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        padLp.topMargin = g;
        root.addView(makeKeypad(), padLp);
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 76));
        cLp.topMargin = g;
        root.addView(makeCall(), cLp);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 56));
        rLp.topMargin = g;
        root.addView(makeAux(), rLp);
        root.addView(makeMsg());
        setContentView(root);
    }

    /** 가로 모드: 왼쪽 = 번호·상태·보조 버튼 / 오른쪽 = 화면 키패드(보이기·숨기기) + 호출 */
    private void buildLandscape() {
        int g = Ui.dp(this, 8);
        boolean kp = prefs.getBoolean(K_KEYPAD_VIS, true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(header());
        left.addView(makeCurrent());
        left.addView(makeInput(kp ? 64 : 90), new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 52));
        aLp.topMargin = g;
        left.addView(makeAux(), aLp);
        left.addView(makeMsg());
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(0, -1, 1.2f);
        lLp.rightMargin = Ui.dp(this, 12);
        root.addView(left, lLp);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        Button toggle = Ui.button(this, kp ? "키패드 숨기기 ▸" : "◂ 키패드", Ui.KEY, 15);
        toggle.setOnClickListener(v -> {
            prefs.edit().putBoolean(K_KEYPAD_VIS, !prefs.getBoolean(K_KEYPAD_VIS, true)).apply();
            buildUi();
        });
        right.addView(toggle, new LinearLayout.LayoutParams(-1, Ui.dp(this, 40)));
        if (kp) {
            LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(-1, 0, 1f);
            pLp.topMargin = g / 2;
            right.addView(makeKeypad(), pLp);
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 64));
            cLp.topMargin = g / 2;
            right.addView(makeCall(), cLp);
        } else {
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, 0, 1f);
            cLp.topMargin = g;
            right.addView(makeCall(), cLp);
        }
        root.addView(right, new LinearLayout.LayoutParams(0, -1, kp ? 1f : 0.45f));
        setContentView(root);
    }

    private void addAux(LinearLayout row, String label, View.OnClickListener l) {
        Button b = Ui.button(this, label, Ui.KEY2, 17);
        b.setOnClickListener(v -> { tap(v); l.onClick(v); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        row.addView(b, lp);
    }

    private void tap(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
    }

    private void key(String k) {
        if ("←".equals(k)) {
            if (!input.isEmpty()) input = input.substring(0, input.length() - 1);
        } else if ("C".equals(k)) {
            input = "";
        } else if (input.length() < 6) {
            input += k;
        }
        showInput();
    }

    private void showInput() {
        if (inputView != null) inputView.setText(input);
    }

    @Override
    public void msg(String s) {
        if (msgView != null) msgView.setText(s);
    }

    // ───────────────────────── 블루투스/USB 키패드 (키 설정 가능) ─────────────────────────
    private boolean keyboardHintShown;

    private Map<String, List<String>> keyMap() {
        return KeyMap.parse(KeyMap.current(prefs));
    }

    /** 화면의 버튼보다 먼저 키 입력을 받아 처리 (Enter가 포커스된 버튼을 누르는 것 방지) */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        String code = KeyMap.codeOf(e);
        String action = KeyMap.actionFor(keyMap(), code);
        String digit = action == null ? KeyMap.digitOf(code) : null;
        if (action == null && digit == null) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        boolean repeat = e.getRepeatCount() > 0;
        if (!keyboardHintShown) {
            keyboardHintShown = true;
            msg("⌨ 키패드 연결됨 · 키 설정은 ⚙ → 키 설정");
        }
        if (digit != null) {
            if (!repeat) key(digit);
            return true;
        }
        switch (action) {
            case "call": if (!repeat) sendCall(); break;
            case "back": key("←"); break;
            case "clear": key("C"); break;
            case "next": if (!repeat) { input = String.valueOf(lastSent + 1); showInput(); } break;
            case "recall": if (!repeat) request("/recall", "재호출했습니다"); break;
            case "blank": if (!repeat) request("/clear", "TV 화면을 비웠습니다"); break;
            default: break;
        }
        return true;
    }

    // ───────────────────────── 전송 ─────────────────────────
    private void sendCall() {
        if (input.isEmpty()) { msg("번호를 입력하세요"); return; }
        final String n = input;
        try {
            request("/call?n=" + URLEncoder.encode(n, "UTF-8"), n + "번 호출 완료", () -> {
                try { lastSent = Integer.parseInt(n); } catch (NumberFormatException ignored) {}
                prefs.edit().putInt(K_LAST, lastSent).apply();
                input = "";
                showInput();
            });
        } catch (Exception ignored) {
        }
    }

    private void request(String path, String okMsg) {
        request(path, okMsg, null);
    }

    private void request(final String path, final String okMsg, final Runnable onOk) {
        msg("전송 중…");
        io.execute(() -> {
            try {
                JSONObject s = new JSONObject(Http.get(BASE + path));
                ui.post(() -> {
                    applyStatus(s);
                    if (s.optBoolean("ok")) {
                        msg("✔ " + okMsg);
                        if (onOk != null) onOk.run();
                    } else {
                        msg(s.optString("error", "오류가 발생했습니다"));
                    }
                });
            } catch (Exception e) {
                ui.post(() -> {
                    setStatus(false, 0);
                    msg("✖ 호출 서버가 응답하지 않습니다. 잠시 후 다시 눌러 주세요");
                    HostService.start(this);
                });
            }
        });
    }

    /** VoiceRecorder 등에서 쓰는 간단 요청 */
    @Override
    public void call(final String path, final Runnable onDone) {
        io.execute(() -> {
            try {
                JSONObject s = new JSONObject(Http.get(BASE + path));
                ui.post(() -> {
                    applyStatus(s);
                    if (onDone != null) onDone.run();
                });
            } catch (Exception e) {
                ui.post(() -> msg("✖ 호출 서버가 응답하지 않습니다"));
            }
        });
    }

    private void checkConnection() {
        io.execute(() -> {
            try {
                JSONObject s = new JSONObject(Http.get(BASE + "/status", 2000, 4000));
                ui.post(() -> applyStatus(s));
            } catch (Exception e) {
                ui.post(() -> setStatus(false, 0));
            }
        });
    }

    private void applyStatus(JSONObject s) {
        if (s == null || !s.has("current")) return;
        last = s;
        setStatus(true, s.optInt("displays", 0));
        String cur = s.optString("current", "");
        if (currentView != null) {
            currentView.setText("현재 호출 번호: " + (cur.isEmpty() ? "-" : cur)
                    + (s.optBoolean("closed") ? "   ·   🌙 영업 마감 표시 중" : ""));
        }
        try { if (lastSent == 0 && !cur.isEmpty()) lastSent = Integer.parseInt(cur); } catch (Exception ignored) {}
        JSONArray pend = s.optJSONArray("pending");
        if (pend != null) {
            for (int i = 0; i < pend.length(); i++) {
                JSONObject r = pend.optJSONObject(i);
                if (r != null && shownRequests.add(r.optString("id"))) askApproval(r);
            }
        }
    }

    private void setStatus(boolean ok, int displays) {
        if (statusView == null) return;
        String ip = Net.localIp();
        if (!ok) {
            statusView.setText("● 호출 서버 시작 중…");
            statusView.setTextColor(0xFFFFCC33);
        } else if (displays == 0) {
            statusView.setText("● 서버 켜짐 · 연결된 TV/PC 화면 없음");
            statusView.setTextColor(0xFFFFCC33);
        } else {
            statusView.setText("● 서버 켜짐 · TV/PC 화면 " + displays + "대 연결됨");
            statusView.setTextColor(0xFF4CD964);
        }
        addrView.setText("0.0.0.0".equals(ip) ? "와이파이에 연결되어 있지 않습니다"
                : "이 폰 주소  " + ip + "   (눌러서 연결 방법 보기)");
    }

    // ───────────────────────── 기기 승인 ─────────────────────────
    private void askApproval(final JSONObject r) {
        final String id = r.optString("id");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("새 기기가 호출 권한을 요청합니다")
                .setMessage(r.optString("name") + "\n(" + r.optString("ip") + ")\n\n허용하면 이 기기에서 번호 호출·설정 변경을 할 수 있습니다.\n모르는 기기라면 [거부]를 누르세요.")
                .setPositiveButton("허용", (d, w) -> call("/auth/decide?id=" + id + "&ok=1", () -> msg("✔ " + r.optString("name") + " 허용됨")))
                .setNegativeButton("거부", (d, w) -> call("/auth/decide?id=" + id + "&ok=0", () -> msg("거부했습니다")))
                .setCancelable(false)
                .show();
    }

    private void showDevices() {
        io.execute(() -> {
            try {
                JSONObject o = new JSONObject(Http.get(BASE + "/auth/devices"));
                final JSONArray arr = o.optJSONArray("devices");
                ui.post(() -> {
                    if (arr == null || arr.length() == 0) {
                        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                                .setTitle("허용된 기기")
                                .setMessage("아직 허용된 기기가 없습니다.\n\n다른 폰·PC에서 호출 페이지를 열고 호출하려고 하면 이 폰에 허용 요청이 뜹니다. TV 화면·PC 화면은 허용 없이 볼 수 있습니다.")
                                .setPositiveButton("확인", null)
                                .show();
                        return;
                    }
                    String[] items = new String[arr.length()];
                    long now = System.currentTimeMillis();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject d = arr.optJSONObject(i);
                        long mins = (now - d.optLong("last")) / 60000;
                        items[i] = d.optString("name") + "\n" + d.optString("ip") + " · 마지막 사용 "
                                + (mins < 1 ? "방금" : mins < 60 ? mins + "분 전" : mins < 1440 ? (mins / 60) + "시간 전" : (mins / 1440) + "일 전");
                    }
                    new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("허용된 기기 (눌러서 권한 빼기)")
                            .setItems(items, (dd, which) -> {
                                final JSONObject d = arr.optJSONObject(which);
                                new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                                        .setMessage(d.optString("name") + " 의 호출 권한을 뺄까요?\n다시 쓰려면 허용 요청을 새로 받아야 합니다.")
                                        .setPositiveButton("권한 빼기", (x, y) -> call("/auth/revoke?token=" + d.optString("token"), () -> msg("권한을 뺐습니다")))
                                        .setNegativeButton("취소", null)
                                        .show();
                            })
                            .setNegativeButton("닫기", null)
                            .show();
                });
            } catch (Exception e) {
                ui.post(() -> msg("기기 목록을 불러오지 못했습니다"));
            }
        });
    }

    // ───────────────────────── 소리 미리 듣기 ─────────────────────────
    @SuppressWarnings("deprecation")
    @Override
    public void play(String path) {
        releasePlayer();
        try {
            final MediaPlayer mp = new MediaPlayer();
            player = mp;
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDataSource(BASE + path);
            mp.setOnPreparedListener(MediaPlayer::start);
            mp.setOnErrorListener((m, what, extra) -> { msg("소리를 재생할 수 없습니다 (음성 엔진·파일 확인)"); return true; });
            mp.prepareAsync();
            msg("▶ 재생 중…");
        } catch (Exception e) {
            msg("소리를 재생할 수 없습니다");
        }
    }

    private void releasePlayer() {
        if (player != null) {
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    // ───────────────────────── 메뉴 ─────────────────────────
    private void showMenu() {
        String[] items = {
                "🖼 TV 이미지 추가 (갤러리)",
                "🎬 TV 동영상 추가 (갤러리)",
                "🔔 알림음 선택",
                "🗣 목소리 설정 (목소리·속도·높낮이)",
                "🎙 내 목소리 녹음",
                "🌙 영업 시간 · 마감 안내",
                "↻ 화면 방향 (세로 / 가로)",
                "⌨ 키 설정 (키패드·키보드)",
                "🔐 허용된 기기",
                "🌐 이미지·동영상 관리 · 기타 설정 (브라우저)",
                "📺 TV·PC 화면 연결 방법",
                "번호 초기화 (다음 번호 = 1)",
                "꺼짐 방지 (배터리 최적화 제외)",
                "역할 다시 선택",
                "호출 서버 끄고 종료",
        };
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: pickMedia("image/*", REQ_IMAGES, "TV에 띄울 이미지 선택"); break;
                        case 1: pickMedia("video/*", REQ_VIDEOS, "TV에 띄울 동영상 선택"); break;
                        case 2: showChimes(); break;
                        case 3: showVoiceSettings(); break;
                        case 4: new VoiceRecorder(this, this).showList(last.optString("tts", HostService.DEF_TTS),
                                last.optString("closeMsg", HostService.DEF_CLOSE_MSG), "rec".equals(last.optString("voiceMode"))); break;
                        case 5: showClosing(); break;
                        case 6: chooseOrientation(); break;
                        case 7: showKeySettings(); break;
                        case 8: showDevices(); break;
                        case 9:
                            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(BASE + "/"))); }
                            catch (Exception e) { msg("브라우저를 열 수 없습니다"); }
                            break;
                        case 10: showConnectInfo(); break;
                        case 11:
                            lastSent = 0;
                            prefs.edit().putInt(K_LAST, 0).apply();
                            msg("번호를 초기화했습니다");
                            break;
                        case 12: askBatteryExemption(); break;
                        case 13:
                            HostService.stop(this);
                            prefs.edit().remove(MainActivity.KEY_MODE).apply();
                            startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_CHOOSE, true));
                            finish();
                            break;
                        default:
                            HostService.stop(this);
                            finish();
                    }
                })
                .show();
    }

    private void chooseOrientation() {
        final String[] values = {"auto", "portrait", "landscape"};
        String[] items = {"자동 회전 (폰을 돌리는 대로)", "세로 고정", "가로 고정 (키패드 연결 시 추천)"};
        String cur = prefs.getString(K_ORIENT, "auto");
        int checked = cur.equals("portrait") ? 1 : cur.equals("landscape") ? 2 : 0;
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("호출기 화면 방향")
                .setSingleChoiceItems(items, checked, (d, which) -> {
                    prefs.edit().putString(K_ORIENT, values[which]).apply();
                    d.dismiss();
                    applyOrientation();
                })
                .show();
    }

    // ───────────────────────── 알림음 ─────────────────────────
    private void showChimes() {
        io.execute(() -> {
            try {
                JSONObject v = new JSONObject(Http.get(BASE + "/voices"));
                ui.post(() -> chimeDialog(v.optJSONArray("chimes")));
            } catch (Exception e) {
                ui.post(() -> msg("호출 서버가 응답하지 않습니다"));
            }
        });
    }

    private void chimeDialog(JSONArray chimes) {
        if (chimes == null) return;
        final List<String> names = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (int i = 0; i < chimes.length(); i++) {
            names.add(chimes.optJSONObject(i).optString("name"));
            labels.add(chimes.optJSONObject(i).optString("label"));
        }
        if (last.optBoolean("customChime")) { names.add("custom"); labels.add("직접 올린 소리"); }
        names.add("upload");
        labels.add("📁 내 소리 파일 올리기 (mp3·wav, 5MB 이하)");
        final int[] sel = {Math.max(0, names.indexOf(last.optString("chime", "dingdong")))};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("알림음 (누르면 미리 듣기)")
                .setSingleChoiceItems(labels.toArray(new String[0]), sel[0], (d, which) -> {
                    if ("upload".equals(names.get(which))) {
                        d.dismiss();
                        pickMedia("audio/*", REQ_CHIME, "알림음으로 쓸 소리 파일 선택");
                        return;
                    }
                    sel[0] = which;
                    play("/chime?type=" + names.get(which));
                })
                .setPositiveButton("저장", (d, w) -> call("/config?chime=" + names.get(sel[0]), () -> msg("✔ 알림음을 바꿨습니다")))
                .setNegativeButton("취소", null)
                .show();
    }

    // ───────────────────────── 목소리 ─────────────────────────
    private void showVoiceSettings() {
        msg("목소리 목록을 불러오는 중…");
        io.execute(() -> {
            try {
                JSONObject v = new JSONObject(Http.get(BASE + "/voices"));
                ui.post(() -> voiceDialog(v));
            } catch (Exception e) {
                ui.post(() -> msg("호출 서버가 응답하지 않습니다"));
            }
        });
    }

    private void voiceDialog(final JSONObject v) {
        msg("");
        final JSONArray engines = v.optJSONArray("engines");
        final JSONArray voices = v.optJSONArray("voices");
        String engineName = v.optString("engine", "");
        String engineLabel = "기본 음성 엔진";
        if (engines != null) for (int i = 0; i < engines.length(); i++) {
            if (engines.optJSONObject(i).optString("name").equals(engineName)) engineLabel = engines.optJSONObject(i).optString("label");
        }
        final String voiceName = last.optString("voiceName", "");
        String voiceLabel = "기본 목소리";
        if (voices != null) for (int i = 0; i < voices.length(); i++) {
            if (voices.optJSONObject(i).optString("name").equals(voiceName)) voiceLabel = voices.optJSONObject(i).optString("label");
        }
        final int rate = last.optInt("rate", 95), pitch = last.optInt("pitch", 100);
        String[] items = {
                "음성 엔진:  " + engineLabel,
                "목소리:  " + voiceLabel,
                "말하기 속도:  " + rate + "%",
                "목소리 높낮이:  " + pitch + "%",
                "▶ 51번 호출 미리 듣기",
                "↺ 속도·높낮이 기본값으로",
        };
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("목소리 설정" + (v.optBoolean("ready") ? "" : " (음성 엔진 준비 중…)"))
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: chooseEngine(engines); break;
                        case 1: chooseVoice(voices, voiceName, rate, pitch); break;
                        case 2: chooseLevel("말하기 속도", "rate", rate, voiceName, rate, pitch); break;
                        case 3: chooseLevel("목소리 높낮이", "pitch", pitch, voiceName, rate, pitch); break;
                        case 4: play("/voice?n=51"); showVoiceSettings(); break;
                        default: call("/config?rate=95&pitch=100", () -> { msg("기본값으로 되돌렸습니다"); showVoiceSettings(); });
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void chooseEngine(final JSONArray engines) {
        if (engines == null || engines.length() == 0) { msg("설치된 음성 엔진이 없습니다 (설정에서 Google TTS 설치)"); return; }
        String[] items = new String[engines.length() + 1];
        items[0] = "기본 음성 엔진 (폰 설정 따름)";
        for (int i = 0; i < engines.length(); i++) items[i + 1] = engines.optJSONObject(i).optString("label");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("음성 엔진")
                .setItems(items, (d, which) -> {
                    String name = which == 0 ? "" : engines.optJSONObject(which - 1).optString("name");
                    call("/config?ttsEngine=" + enc(name), () -> {
                        msg("음성 엔진을 바꿨습니다. 잠시 후 목소리 목록이 바뀝니다");
                        ui.postDelayed(this::showVoiceSettings, 2500);
                    });
                })
                .show();
    }

    private void chooseVoice(final JSONArray voices, String current, final int rate, final int pitch) {
        if (voices == null || voices.length() == 0) { msg("고를 수 있는 한국어 목소리가 없습니다"); return; }
        final String[] names = new String[voices.length() + 1];
        String[] labels = new String[voices.length() + 1];
        names[0] = "";
        labels[0] = "기본 목소리";
        int checked = 0;
        for (int i = 0; i < voices.length(); i++) {
            names[i + 1] = voices.optJSONObject(i).optString("name");
            labels[i + 1] = voices.optJSONObject(i).optString("label");
            if (names[i + 1].equals(current)) checked = i + 1;
        }
        final int[] sel = {checked};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("목소리 (누르면 미리 듣기)")
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    sel[0] = which;
                    play("/voice/preview?n=51&voice=" + enc(names[which]) + "&rate=" + rate + "&pitch=" + pitch);
                })
                .setPositiveButton("저장", (d, w) -> call("/config?voiceName=" + enc(names[sel[0]]), () -> { msg("✔ 목소리를 바꿨습니다"); showVoiceSettings(); }))
                .setNegativeButton("취소", null)
                .show();
    }

    private void chooseLevel(String title, final String key, int value, final String voice, final int rate, final int pitch) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);
        final TextView label = Ui.text(this, value + "%", 22, Ui.YELLOW, true);
        label.setGravity(Gravity.CENTER);
        box.addView(label);
        final SeekBar bar = new SeekBar(this);
        bar.setMax(30); // 50% ~ 200%, 5% 단위
        bar.setProgress((value - 50) / 5);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) { label.setText((50 + p * 5) + "%"); }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(bar);
        TextView hint = Ui.text(this, "100% = 보통. 왼쪽은 느리게/낮게, 오른쪽은 빠르게/높게", 13, Ui.MUTED, false);
        hint.setGravity(Gravity.CENTER);
        box.addView(hint);
        final AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("저장", (d, w) -> call("/config?" + key + "=" + (50 + bar.getProgress() * 5), () -> { msg("✔ 저장했습니다"); showVoiceSettings(); }))
                .setNeutralButton("▶ 들어보기", null)
                .setNegativeButton("취소", null)
                .create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(b -> {
            int val = 50 + bar.getProgress() * 5;
            int r = "rate".equals(key) ? val : rate, p = "pitch".equals(key) ? val : pitch;
            play("/voice/preview?n=51&voice=" + enc(voice) + "&rate=" + r + "&pitch=" + p);
        }));
        dlg.show();
    }

    private static String enc(String s) {
        try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Exception e) { return ""; }
    }

    // ───────────────────────── 영업 시간 · 마감 안내 ─────────────────────────
    private void showClosing() {
        final String[] open = {last.optString("open", "09:00")};
        final String[] close = {last.optString("close", "22:00")};
        final boolean closedNow = last.optBoolean("closed");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, pad / 2, pad, 0);

        final Switch auto = new Switch(this);
        auto.setText("영업 시간에 맞춰 자동으로 마감 안내");
        auto.setTextColor(Color.WHITE);
        auto.setChecked(last.optBoolean("closeOn"));
        box.addView(auto);

        LinearLayout times = new LinearLayout(this);
        times.setPadding(0, pad / 2, 0, pad / 2);
        final Button openBtn = Ui.button(this, "시작 " + open[0], Ui.KEY2, 16);
        final Button closeBtn = Ui.button(this, "마감 " + close[0], Ui.KEY2, 16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1f);
        lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        times.addView(openBtn, lp);
        times.addView(closeBtn, new LinearLayout.LayoutParams(lp));
        box.addView(times);
        openBtn.setOnClickListener(v -> pickTime(open[0], t -> { open[0] = t; openBtn.setText("시작 " + t); }));
        closeBtn.setOnClickListener(v -> pickTime(close[0], t -> { close[0] = t; closeBtn.setText("마감 " + t); }));

        box.addView(Ui.text(this, "마감 멘트 (화면 표시 + 음성 안내)", 14, Ui.MUTED, false));
        final EditText text = new EditText(this);
        text.setText(last.optString("closeMsg", HostService.DEF_CLOSE_MSG));
        text.setTextColor(Color.WHITE);
        text.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(text);
        TextView info = Ui.text(this, "마감 시각이 되면 딩동 + 마감 멘트를 한 번 안내하고 TV·PC 화면에 표시합니다. 다음 영업 시작 시각에 자동으로 사라집니다.\n현재: "
                + (closedNow ? "🌙 마감 표시 중" : "영업 중"), 13, Ui.MUTED, false);
        info.setPadding(0, pad / 2, 0, 0);
        box.addView(info);

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("영업 시간 · 마감 안내")
                .setView(box)
                .setPositiveButton("저장", (d, w) -> call("/config?closeOn=" + (auto.isChecked() ? "1" : "0") + "&open=" + open[0]
                        + "&close=" + close[0] + "&closeMsg=" + enc(text.getText().toString()), () -> msg("✔ 영업 시간을 저장했습니다")))
                .setNeutralButton(closedNow ? "마감 표시 끄기" : "지금 마감 표시", (d, w) -> call("/closing?on=" + (closedNow ? "0" : "1"),
                        () -> msg(closedNow ? "마감 표시를 껐습니다" : "🌙 마감을 안내했습니다")))
                .setNegativeButton("취소", null)
                .show();
    }

    private interface TimeSet { void on(String hhmm); }

    private void pickTime(String cur, final TimeSet cb) {
        int h = 9, m = 0;
        try { h = Integer.parseInt(cur.substring(0, 2)); m = Integer.parseInt(cur.substring(3, 5)); } catch (Exception ignored) {}
        new TimePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert,
                (v, hh, mm) -> cb.on(String.format(java.util.Locale.US, "%02d:%02d", hh, mm)), h, m, true).show();
    }

    // ───────────────────────── 연결 안내 · 배터리 ─────────────────────────
    private void showConnectInfo() {
        String ip = Net.localIp();
        String base = "http://" + ip + ":" + Net.HTTP_PORT;
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("TV·PC 화면 연결 방법")
                .setMessage("이 폰이 호출 서버입니다. TV·PC는 같은 와이파이에 연결하세요.\n\n"
                        + "📺 안드로이드 TV 기기\nDID 앱 → 'TV 화면' 선택 → 자동으로 이 폰을 찾습니다.\n"
                        + "못 찾으면 주소 직접 입력:  " + ip + "\n\n"
                        + "💻 윈도우 PC (크롬·엣지)\n" + base + "/display\n\n"
                        + "📱 다른 폰·PC에서 호출하기\n" + base + "\n(처음 한 번 이 폰에서 [허용]을 눌러야 합니다)\n\n"
                        + "※ 주소가 바뀌지 않도록 공유기에서 이 폰의 IP를 고정(DHCP 예약)해 두면 좋습니다.")
                .setPositiveButton("확인", null)
                .show();
    }

    @SuppressLint("BatteryLife")
    private void askBatteryExemption() {
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
                if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                    msg("이미 꺼짐 방지가 설정되어 있습니다");
                    return;
                }
                startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName())));
            }
        } catch (Exception e) {
            msg("설정 → 앱 → DID 번호호출 → 배터리 → '제한 없음'으로 바꿔 주세요");
        }
    }

    // ───────────────────────── 이미지·동영상·알림음 올리기 ─────────────────────────
    private void pickMedia(String type, int req, String title) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType(type);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        if (req != REQ_CHIME) i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, title), req);
        } catch (Exception e) {
            msg("파일을 고를 수 없습니다");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;
        final List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int k = 0; k < data.getClipData().getItemCount(); k++) uris.add(data.getClipData().getItemAt(k).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        if (requestCode == REQ_IMAGES) uploadImages(uris);
        else if (requestCode == REQ_VIDEOS) uploadVideos(uris);
        else if (requestCode == REQ_CHIME) uploadChime(uris.get(0));
    }

    private void uploadImages(final List<Uri> uris) {
        up.execute(() -> {
            int ok = 0;
            String lastErr = null;
            for (int k = 0; k < uris.size(); k++) {
                final int idx = k + 1;
                ui.post(() -> msg("이미지 보내는 중… " + idx + "/" + uris.size()));
                try {
                    byte[] jpg = shrink(uris.get(k));
                    JSONObject s = new JSONObject(Http.post(BASE + "/media", jpg, "image/jpeg"));
                    if (s.optBoolean("ok")) ok++; else lastErr = s.optString("error");
                } catch (Throwable e) {
                    lastErr = "이미지를 처리하지 못했습니다";
                }
            }
            final int okCount = ok;
            final String err = lastErr;
            ui.post(() -> msg(err == null ? "✔ 이미지 " + okCount + "장을 추가했습니다" : "이미지 " + okCount + "장 추가 · 실패: " + err));
        });
    }

    private long sizeOf(Uri uri) {
        try (AssetFileDescriptor fd = getContentResolver().openAssetFileDescriptor(uri, "r")) {
            if (fd != null && fd.getLength() > 0) return fd.getLength();
        } catch (Exception ignored) {
        }
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) {
        }
        return -1;
    }

    private void uploadVideos(final List<Uri> uris) {
        up.execute(() -> {
            int ok = 0;
            String lastErr = null;
            for (int k = 0; k < uris.size(); k++) {
                final int idx = k + 1;
                final Uri uri = uris.get(k);
                long len = sizeOf(uri);
                if (len <= 0) { lastErr = "파일 크기를 알 수 없습니다"; continue; }
                if (len > MediaStore.MAX_VIDEO) { lastErr = "동영상은 300MB 이하만 올릴 수 있습니다"; continue; }
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    JSONObject s = new JSONObject(Http.postStream(BASE + "/media", in, len, "video/mp4",
                            (sent, total) -> ui.post(() -> msg("동영상 보내는 중… " + idx + "/" + uris.size() + "  " + (sent * 100 / total) + "%"))));
                    if (s.optBoolean("ok")) ok++; else lastErr = s.optString("error");
                } catch (Exception e) {
                    lastErr = "동영상을 보내지 못했습니다";
                }
            }
            final int okCount = ok;
            final String err = lastErr;
            ui.post(() -> msg(err == null ? "✔ 동영상 " + okCount + "개를 추가했습니다 (mp4·1080p 이하 권장)" : "동영상 " + okCount + "개 추가 · 실패: " + err));
        });
    }

    private void uploadChime(final Uri uri) {
        up.execute(() -> {
            try {
                long len = sizeOf(uri);
                if (len > 5L * 1024 * 1024) { ui.post(() -> msg("알림음 파일은 5MB 이하만 올릴 수 있습니다")); return; }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    byte[] b = new byte[32 * 1024];
                    int r;
                    while ((r = in.read(b)) > 0 && bo.size() <= 5 * 1024 * 1024) bo.write(b, 0, r);
                }
                JSONObject s = new JSONObject(Http.post(BASE + "/chime/upload", bo.toByteArray(), "application/octet-stream"));
                ui.post(() -> {
                    if (s.optBoolean("ok")) {
                        applyStatus(s);
                        msg("✔ 내 소리 파일을 알림음으로 설정했습니다");
                        play("/chime?type=custom");
                    } else {
                        msg(s.optString("error", "올리지 못했습니다"));
                    }
                });
            } catch (Exception e) {
                ui.post(() -> msg("소리 파일을 올리지 못했습니다"));
            }
        });
    }

    /** 사진을 최대 2560px, 32비트 색상, 품질 92 JPEG로 (TV에서 선명하게) + 회전 정보 반영 */
    private byte[] shrink(Uri uri) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            BitmapFactory.decodeStream(in, null, o);
        }
        if (o.outWidth <= 0) throw new Exception("not image");
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        Bitmap bm;
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            bm = BitmapFactory.decodeStream(in, null, d);
        }
        if (bm == null) throw new Exception("decode failed");

        float scale = Math.min(1f, (float) MAX_SIDE / Math.max(bm.getWidth(), bm.getHeight()));
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        if (Build.VERSION.SDK_INT >= 24) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (ori == ExifInterface.ORIENTATION_ROTATE_90) m.postRotate(90);
                else if (ori == ExifInterface.ORIENTATION_ROTATE_180) m.postRotate(180);
                else if (ori == ExifInterface.ORIENTATION_ROTATE_270) m.postRotate(270);
            } catch (Exception ignored) {
            }
        }
        Bitmap out = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
        // 투명 배경(PNG)은 흰색으로 채움 (32비트 색상 유지)
        Bitmap flat = Bitmap.createBitmap(out.getWidth(), out.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(flat);
        c.drawColor(Color.WHITE);
        c.drawBitmap(out, 0, 0, null);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        flat.compress(Bitmap.CompressFormat.JPEG, 92, bo);
        if (out != bm) out.recycle();
        bm.recycle();
        flat.recycle();
        return bo.toByteArray();
    }

    // ───────────────────────── 키 설정 ─────────────────────────
    /** 키 설정: 동작을 고른 뒤 키패드에서 원하는 키를 누르면 지정 */
    private void showKeySettings() {
        final Map<String, List<String>> m = keyMap();
        String[] items = new String[KeyMap.ACTIONS.length + 2];
        for (int i = 0; i < KeyMap.ACTIONS.length; i++) {
            items[i] = KeyMap.LABELS[i] + "  :  " + KeyMap.describe(m, KeyMap.ACTIONS[i]);
        }
        items[KeyMap.ACTIONS.length] = "↺ 텐키형 기본값으로 (. = 한 칸 지우기)";
        items[KeyMap.ACTIONS.length + 1] = "↺ 백스페이스형 기본값으로 (← = 한 칸 지우기)";
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("키 설정 (숫자 키는 항상 번호 입력)")
                .setItems(items, (d, which) -> {
                    if (which < KeyMap.ACTIONS.length) {
                        captureKey(which);
                    } else {
                        prefs.edit().putString(KeyMap.K_KEYS,
                                which == KeyMap.ACTIONS.length ? KeyMap.TENKEY : KeyMap.BACKSPACE).apply();
                        msg("키 설정을 기본값으로 되돌렸습니다");
                        showKeySettings();
                    }
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    private void captureKey(final int idx) {
        final String action = KeyMap.ACTIONS[idx];
        final Map<String, List<String>> m = keyMap();
        final AlertDialog dlg = new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("'" + KeyMap.LABELS[idx] + "' 키 지정")
                .setMessage(captureText(m, action))
                .setPositiveButton("완료", (d, w) -> showKeySettings())
                .setNeutralButton("이 동작의 키 모두 지우기", (d, w) -> {
                    m.get(action).clear();
                    prefs.edit().putString(KeyMap.K_KEYS, KeyMap.serialize(m)).apply();
                    showKeySettings();
                })
                .create();
        dlg.setOnKeyListener((d, keyCode, e) -> {
            String code = KeyMap.codeOf(e);
            if (KeyMap.isModifier(code)) return false; // 뒤로 버튼 등은 그대로
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
                KeyMap.assign(m, action, code);
                prefs.edit().putString(KeyMap.K_KEYS, KeyMap.serialize(m)).apply();
                dlg.setMessage(captureText(m, action) + "\n\n✔ " + KeyMap.label(code) + " 지정됨");
            }
            return true; // 키가 대화상자 버튼을 누르지 않도록
        });
        dlg.show();
    }

    private static String captureText(Map<String, List<String>> m, String action) {
        return "키패드에서 원하는 키를 누르세요. 여러 개를 지정할 수 있습니다.\n"
                + "(다른 동작에 지정돼 있던 키는 이 동작으로 옮겨집니다)\n\n현재: " + KeyMap.describe(m, action);
    }
}
