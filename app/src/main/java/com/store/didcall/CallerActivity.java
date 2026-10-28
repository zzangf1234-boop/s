package com.store.didcall;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Build;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 카운터 공기계에서 실행: 호출 서버(호스트)를 켜고, 번호를 입력해 TV·PC 화면에 호출 */
public class CallerActivity extends Activity {

    private static final String K_LAST = "last_sent";
    private static final int REQ_IMAGES = 41;
    private static final int MAX_SIDE = 1920;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private TextView statusView, addrView, inputView, currentView, msgView;
    private String input = "";
    /** 호출 서버는 이 폰 안에서 돌아가므로 항상 자기 자신에게 요청 */
    private final String host = "127.0.0.1:" + Net.HTTP_PORT;
    private final Runnable poller = new Runnable() {
        @Override public void run() { checkConnection(); ui.postDelayed(this, 3000); }
    };
    private int lastSent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        lastSent = prefs.getInt(K_LAST, 0);
        buildUi();
        HostService.start(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
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
        io.shutdownNow();
    }

    // ───────────────────────── 화면 구성 ─────────────────────────
    private void buildUi() {
        int g = Ui.dp(this, 8);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));

        // 상단: 서버 상태 + TV·PC 화면 주소 (누르면 연결 방법) + 설정 버튼
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
        root.addView(top);

        currentView = Ui.text(this, "현재 호출 번호: -", 15, Ui.MUTED, false);
        currentView.setPadding(0, Ui.dp(this, 4), 0, g);
        root.addView(currentView);

        // 입력 번호 표시
        inputView = Ui.text(this, "", 60, Ui.YELLOW, true);
        inputView.setGravity(Gravity.CENTER);
        inputView.setBackground(Ui.round(this, 0xFF000000, 14));
        inputView.setLetterSpacing(0.1f);
        inputView.setSingleLine(true);
        root.addView(inputView, new LinearLayout.LayoutParams(-1, Ui.dp(this, 100)));

        // 숫자 키패드
        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"←", "0", "C"}};
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(-1, 0, 1f);
        padLp.topMargin = g;
        root.addView(pad, padLp);
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

        // 호출 버튼
        Button call = Ui.button(this, "호출", Ui.ACCENT, 30);
        call.setOnClickListener(v -> { tap(v); sendCall(); });
        LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 76));
        cLp.topMargin = g;
        root.addView(call, cLp);

        // 보조 버튼
        LinearLayout row = new LinearLayout(this);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 56));
        rLp.topMargin = g;
        root.addView(row, rLp);
        addAux(row, "재호출", v -> request("/recall", "재호출했습니다"));
        addAux(row, "다음 번호", v -> { input = String.valueOf(lastSent + 1); showInput(); });
        addAux(row, "화면 비우기", v -> request("/clear", "TV 화면을 비웠습니다"));

        msgView = Ui.text(this, "", 15, Ui.MUTED, false);
        msgView.setGravity(Gravity.CENTER);
        msgView.setPadding(0, g, 0, 0);
        root.addView(msgView);

        setContentView(root);
        showInput();
    }

    private void addAux(LinearLayout row, String label, View.OnClickListener l) {
        Button b = Ui.button(this, label, Ui.KEY2, 17);
        b.setOnClickListener(v -> { tap(v); l.onClick(v); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1f);
        lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        row.addView(b, lp);
    }

    // ───────────────────────── 블루투스/USB 숫자 키패드 ─────────────────────────
    private boolean keyboardHintShown;

    /**
     * 화면의 버튼보다 먼저 키 입력을 받아 처리 (Enter가 포커스된 버튼을 누르는 것 방지).
     *  (텐키 기준) 숫자 = 입력, Enter = 호출, . = 한 칸 지우기, - = 전체 지우기,
     *  + = 다음 번호, * = 재호출, / = 화면 비우기
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        String action = keyAction(code, e);
        if (action == null) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        boolean repeat = e.getRepeatCount() > 0;
        if (!keyboardHintShown) {
            keyboardHintShown = true;
            msg("⌨ 텐키: Enter 호출 · + 다음 번호 · * 재호출 · / 화면 비우기 · . 한 칸 지우기 · - 전체 삭제");
        }
        switch (action) {
            case "call": if (!repeat) sendCall(); break;
            case "back": key("←"); break;
            case "clear": key("C"); break;
            case "next": if (!repeat) { input = String.valueOf(lastSent + 1); showInput(); } break;
            case "recall": if (!repeat) request("/recall", "재호출했습니다"); break;
            case "blank": if (!repeat) request("/clear", "TV 화면을 비웠습니다"); break;
            default: if (!repeat) key(action); // 숫자
        }
        return true;
    }

    private static String keyAction(int code, KeyEvent e) {
        // 텐키는 NumLock이 꺼져 있으면 화살표·End 등으로 들어오므로, 물리 키 위치(스캔 코드)로 먼저 판단
        switch (e.getScanCode()) {
            case 82: return "0";
            case 79: return "1";
            case 80: return "2";
            case 81: return "3";
            case 75: return "4";
            case 76: return "5";
            case 77: return "6";
            case 71: return "7";
            case 72: return "8";
            case 73: return "9";
            case 96: return "call";    // 텐키 Enter
            case 78: return "next";    // +
            case 55: return "recall";  // *
            case 74: return "clear";   // -
            case 98: return "blank";   // /
            case 83:                   // .
            case 121: return "back";   // , (일부 텐키)
            default: break;
        }
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return String.valueOf(code - KeyEvent.KEYCODE_0);
        if (code >= KeyEvent.KEYCODE_NUMPAD_0 && code <= KeyEvent.KEYCODE_NUMPAD_9) return String.valueOf(code - KeyEvent.KEYCODE_NUMPAD_0);
        switch (code) {
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                return "call";
            case KeyEvent.KEYCODE_NUMPAD_DOT:     // 텐키 . = 한 칸 지우기
            case KeyEvent.KEYCODE_PERIOD:
            case KeyEvent.KEYCODE_NUMPAD_COMMA:   // 일부 텐키는 . 자리에 , 가 있음
            case KeyEvent.KEYCODE_COMMA:
            case KeyEvent.KEYCODE_FORWARD_DEL:    // NumLock 꺼진 상태의 . 키
            case KeyEvent.KEYCODE_DEL:            // 일반 키보드 Backspace
                return "back";
            case KeyEvent.KEYCODE_NUMPAD_SUBTRACT: // 텐키 - = 전체 삭제
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_MINUS:
            case KeyEvent.KEYCODE_CLEAR:
                return "clear";
            case KeyEvent.KEYCODE_NUMPAD_ADD:
            case KeyEvent.KEYCODE_PLUS:
                return "next";
            case KeyEvent.KEYCODE_NUMPAD_MULTIPLY:
            case KeyEvent.KEYCODE_STAR:
                return "recall";
            case KeyEvent.KEYCODE_NUMPAD_DIVIDE:
            case KeyEvent.KEYCODE_SLASH:
                return "blank";
            case KeyEvent.KEYCODE_EQUALS:         // 일반 키보드의 '+' 키 (Shift 없이)
                return e.isShiftPressed() ? "next" : null;
            default:
                return null;
        }
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
        inputView.setText(input);
    }

    private void msg(String s) {
        msgView.setText(s);
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
                JSONObject s = new JSONObject(get("http://" + host + path));
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

    private void checkConnection() {
        io.execute(() -> {
            try {
                JSONObject s = new JSONObject(get("http://" + host + "/status"));
                ui.post(() -> applyStatus(s));
            } catch (Exception e) {
                ui.post(() -> setStatus(false, 0));
            }
        });
    }

    private void applyStatus(JSONObject s) {
        setStatus(true, s.optInt("displays", 0));
        String cur = s.optString("current", "");
        currentView.setText("현재 호출 번호: " + (cur.isEmpty() ? "-" : cur));
        try { if (lastSent == 0 && !cur.isEmpty()) lastSent = Integer.parseInt(cur); } catch (Exception ignored) {}
    }

    private void setStatus(boolean ok, int displays) {
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

    private void showConnectInfo() {
        String ip = Net.localIp();
        String base = "http://" + ip + ":" + Net.HTTP_PORT;
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("TV·PC 화면 연결 방법")
                .setMessage("이 폰이 호출 서버입니다. TV·PC는 같은 와이파이에 연결하세요.\n\n"
                        + "📺 안드로이드 TV 기기\nDID 앱 → 'TV 화면' 선택 → 자동으로 이 폰을 찾습니다.\n"
                        + "못 찾으면 주소 직접 입력:  " + ip + "\n\n"
                        + "💻 윈도우 PC (크롬·엣지)\n" + base + "/display\n\n"
                        + "📱 다른 폰·PC에서 호출하기\n" + base + "\n\n"
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

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(3000);
        try {
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[2048];
            int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            return bo.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ───────────────────────── TV 이미지 업로드 ─────────────────────────
    private void pickImages() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, "TV에 띄울 이미지 선택"), REQ_IMAGES);
        } catch (Exception e) {
            msg("갤러리를 열 수 없습니다");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMAGES || resultCode != RESULT_OK || data == null) return;
        final List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int k = 0; k < data.getClipData().getItemCount(); k++) uris.add(data.getClipData().getItemAt(k).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) return;
        final String target = "http://" + host + "/image";
        io.execute(() -> {
            int ok = 0;
            String lastErr = null;
            for (int k = 0; k < uris.size(); k++) {
                final int idx = k + 1;
                ui.post(() -> msg("이미지 보내는 중… " + idx + "/" + uris.size()));
                try {
                    byte[] jpg = shrink(uris.get(k));
                    JSONObject s = new JSONObject(post(target, jpg));
                    if (s.optBoolean("ok")) ok++; else lastErr = s.optString("error");
                } catch (Exception e) {
                    lastErr = "호출 서버에 저장하지 못했습니다";
                }
            }
            final int okCount = ok;
            final String err = lastErr;
            ui.post(() -> msg(err == null ? "✔ 이미지 " + okCount + "장을 TV에 추가했습니다"
                    : "이미지 " + okCount + "장 추가 · 실패: " + err));
        });
    }

    /** 사진을 최대 1920px JPEG로 줄이고 회전 정보를 반영 */
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
        // 투명 배경(PNG)은 흰색으로 채움
        Bitmap flat = Bitmap.createBitmap(out.getWidth(), out.getHeight(), Bitmap.Config.RGB_565);
        Canvas c = new Canvas(flat);
        c.drawColor(Color.WHITE);
        c.drawBitmap(out, 0, 0, null);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        flat.compress(Bitmap.CompressFormat.JPEG, 88, bo);
        bm.recycle();
        if (out != bm) out.recycle();
        flat.recycle();
        return bo.toByteArray();
    }

    private static String post(String url, byte[] body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(20000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "image/jpeg");
        c.setFixedLengthStreamingMode(body.length);
        try {
            try (OutputStream os = c.getOutputStream()) { os.write(body); }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[2048];
            int r;
            while ((r = in.read(b)) > 0) bo.write(b, 0, r);
            return bo.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ───────────────────────── 대화상자 ─────────────────────────
    private void showMenu() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setItems(new String[]{"TV 이미지 추가 (갤러리)", "이미지 관리 · 안내 설정 (브라우저)", "TV·PC 화면 연결 방법",
                                "번호 초기화 (다음 번호 = 1)", "꺼짐 방지 (배터리 최적화 제외)", "역할 다시 선택", "호출 서버 끄고 종료"},
                        (d, which) -> {
                            if (which == 0) {
                                pickImages();
                            } else if (which == 1) {
                                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + host + "/"))); }
                                catch (Exception e) { msg("브라우저를 열 수 없습니다"); }
                            } else if (which == 2) {
                                showConnectInfo();
                            } else if (which == 3) {
                                lastSent = 0;
                                prefs.edit().putInt(K_LAST, 0).apply();
                                msg("번호를 초기화했습니다");
                            } else if (which == 4) {
                                askBatteryExemption();
                            } else if (which == 5) {
                                HostService.stop(this);
                                prefs.edit().remove(MainActivity.KEY_MODE).apply();
                                startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_CHOOSE, true));
                                finish();
                            } else {
                                HostService.stop(this);
                                finish();
                            }
                        })
                .show();
    }
}
