package com.store.didcall;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.DhcpInfo;
import android.os.Build;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 카운터 공기계에서 실행: 번호를 입력해 TV로 전송 */
public class CallerActivity extends Activity {

    private static final String K_HOST = "tv_host";
    private static final String K_LAST = "last_sent";
    private static final int REQ_IMAGES = 41;
    private static final int MAX_SIDE = 1920;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;

    private TextView statusView, inputView, currentView, msgView;
    private String input = "";
    private String host = "";
    private int lastSent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        host = prefs.getString(K_HOST, "");
        lastSent = prefs.getInt(K_LAST, 0);
        buildUi();

        if (host.isEmpty()) discover(); else checkConnection();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!host.isEmpty()) checkConnection();
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

        // 상단: 연결 상태 (누르면 TV 주소 설정) + 설정 버튼
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        statusView = Ui.text(this, "TV 찾는 중…", 15, Ui.MUTED, false);
        statusView.setOnClickListener(v -> showHostDialog());
        top.addView(statusView, new LinearLayout.LayoutParams(0, -2, 1f));
        Button menu = Ui.button(this, "⚙", Ui.KEY, 18);
        menu.setOnClickListener(v -> showMenu());
        top.addView(menu, new LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 44)));
        root.addView(top);

        currentView = Ui.text(this, "TV 현재 번호: -", 15, Ui.MUTED, false);
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
        if (host.isEmpty()) { msg("TV가 연결되지 않았습니다. 상단을 눌러 설정하세요"); return; }
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
                    setStatus(false);
                    msg("✖ TV에 연결할 수 없습니다. 같은 와이파이인지 확인하세요");
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
                ui.post(() -> setStatus(false));
            }
        });
    }

    private void applyStatus(JSONObject s) {
        setStatus(true);
        String cur = s.optString("current", "");
        currentView.setText("TV 현재 번호: " + (cur.isEmpty() ? "-" : cur));
        try { if (lastSent == 0 && !cur.isEmpty()) lastSent = Integer.parseInt(cur); } catch (Exception ignored) {}
    }

    private void setStatus(boolean ok) {
        statusView.setText((ok ? "● 연결됨  " : "● 연결 끊김  ") + host + "  (변경)");
        statusView.setTextColor(ok ? 0xFF4CD964 : 0xFFFF5A5A);
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

    // ───────────────────────── TV 자동 찾기 ─────────────────────────
    private void discover() {
        statusView.setText("TV 찾는 중…");
        statusView.setTextColor(Ui.MUTED);
        io.execute(() -> {
            String found = null;
            DatagramSocket sock = null;
            try {
                sock = new DatagramSocket();
                sock.setBroadcast(true);
                sock.setSoTimeout(1200);
                byte[] msg = Net.DISCOVER_MSG.getBytes("UTF-8");
                InetAddress[] targets = {InetAddress.getByName("255.255.255.255"), subnetBroadcast()};
                for (int attempt = 0; attempt < 3 && found == null; attempt++) {
                    for (InetAddress t : targets) {
                        if (t != null) sock.send(new DatagramPacket(msg, msg.length, t, Net.DISCOVERY_PORT));
                    }
                    try {
                        byte[] buf = new byte[256];
                        DatagramPacket p = new DatagramPacket(buf, buf.length);
                        sock.receive(p);
                        String reply = new String(p.getData(), 0, p.getLength(), "UTF-8");
                        if (reply.startsWith(Net.DISCOVER_REPLY)) {
                            String[] parts = reply.split("\\|");
                            String port = parts.length > 1 ? parts[1] : String.valueOf(Net.HTTP_PORT);
                            found = p.getAddress().getHostAddress() + ":" + port;
                        }
                    } catch (java.net.SocketTimeoutException ignored) {
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (sock != null) sock.close();
            }
            final String result = found;
            ui.post(() -> {
                if (result != null) {
                    host = result;
                    prefs.edit().putString(K_HOST, host).apply();
                    msg("TV를 찾았습니다");
                    checkConnection();
                } else {
                    statusView.setText("TV를 찾지 못했습니다 (눌러서 주소 입력)");
                    statusView.setTextColor(0xFFFF5A5A);
                    msg("TV 화면 하단에 표시된 주소를 직접 입력해 주세요");
                }
            });
        });
    }

    private InetAddress subnetBroadcast() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            DhcpInfo d = wm == null ? null : wm.getDhcpInfo();
            if (d == null || d.ipAddress == 0) return null;
            int mask = d.netmask == 0 ? 0x00FFFFFF : d.netmask;
            int bc = (d.ipAddress & mask) | ~mask;
            byte[] q = new byte[4];
            for (int k = 0; k < 4; k++) q[k] = (byte) ((bc >> (k * 8)) & 0xFF);
            return InetAddress.getByAddress(q);
        } catch (Exception e) {
            return null;
        }
    }

    // ───────────────────────── TV 이미지 업로드 ─────────────────────────
    private void pickImages() {
        if (host.isEmpty()) { msg("먼저 TV를 연결하세요"); return; }
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
                    lastErr = "TV에 연결할 수 없습니다";
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
    private void showHostDialog() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        et.setHint("예) 192.168.0.10:8765");
        et.setText(host);
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("TV 주소")
                .setMessage("TV 화면 하단에 표시된 주소를 입력하세요.")
                .setView(et)
                .setPositiveButton("저장", (d, w) -> {
                    String h = et.getText().toString().trim().replace("http://", "");
                    if (h.endsWith("/")) h = h.substring(0, h.length() - 1);
                    if (!h.isEmpty() && !h.contains(":")) h = h + ":" + Net.HTTP_PORT;
                    host = h;
                    prefs.edit().putString(K_HOST, host).apply();
                    checkConnection();
                })
                .setNeutralButton("자동 찾기", (d, w) -> discover())
                .setNegativeButton("취소", null)
                .show();
    }

    private void showMenu() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setItems(new String[]{"TV 이미지 추가 (갤러리)", "TV 이미지 관리 · 안내 설정 (브라우저)", "TV 주소 변경 / 자동 찾기", "번호 초기화 (다음 번호 = 1)", "역할 다시 선택"},
                        (d, which) -> {
                            if (which == 0) {
                                pickImages();
                            } else if (which == 1) {
                                if (host.isEmpty()) { msg("먼저 TV를 연결하세요"); return; }
                                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + host + "/"))); }
                                catch (Exception e) { msg("브라우저를 열 수 없습니다"); }
                            } else if (which == 2) {
                                showHostDialog();
                            } else if (which == 3) {
                                lastSent = 0;
                                prefs.edit().putInt(K_LAST, 0).apply();
                                msg("번호를 초기화했습니다");
                            } else {
                                prefs.edit().remove(MainActivity.KEY_MODE).apply();
                                startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.EXTRA_CHOOSE, true));
                                finish();
                            }
                        })
                .show();
    }
}
