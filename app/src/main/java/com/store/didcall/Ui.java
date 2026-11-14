package com.store.didcall;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.TextView;

/** 레이아웃 XML 없이 코드로 화면을 만들기 위한 도우미 */
final class Ui {
    // 차분한 남색 계열 + 주황 포인트
    static final int BG = Color.parseColor("#0E1116");
    static final int PANEL = Color.parseColor("#171C23");
    static final int PANEL2 = Color.parseColor("#1D242D");
    static final int KEY = Color.parseColor("#232B35");
    static final int KEY2 = Color.parseColor("#2E3A47");
    static final int STROKE = Color.parseColor("#333D49");
    static final int ACCENT = Color.parseColor("#FF8A00");
    static final int YELLOW = Color.parseColor("#FFCC33");
    static final int TEXT = Color.parseColor("#F4F5F7");
    static final int MUTED = Color.parseColor("#98A2AE");

    private Ui() {}

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    /** 둥근 모서리 + 옅은 테두리 (패널·입력칸용) */
    static GradientDrawable panel(Context c, int fill, float radiusDp) {
        GradientDrawable d = round(c, fill, radiusDp);
        d.setStroke(dp(c, 1), STROKE);
        return d;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static Button button(Context c, String label, int color, float sp) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setIncludeFontPadding(false);
        b.setTextColor(color == ACCENT ? Color.WHITE : TEXT);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        // 기본 패딩을 지우고 여유 있게 — 글씨 잘림 방지
        b.setPadding(dp(c, 8), 0, dp(c, 8), 0);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, round(c, lighten(color), 16));
        sl.addState(new int[]{android.R.attr.state_focused}, ring(c, lighten(color)));
        sl.addState(new int[]{}, color == KEY || color == KEY2 ? ring(c, color) : round(c, color, 16));
        b.setBackground(sl);
        b.setStateListAnimator(null);
        // 길게 써도 한 줄로 줄어듦 (글씨 잘림 방지)
        // max가 min보다 크게 강제됨 — Android 16 TV에서 max(0)≤min(20) 예외로 크래시하는 기기가 있어 방어
        if (Build.VERSION.SDK_INT >= 26 && sp > 10) {
            try {
                b.setAutoSizeTextTypeUniformWithConfiguration(10, (int) sp, 2, TypedValue.COMPLEX_UNIT_SP);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return b;
    }

    /** 채우지 않고 테두리만 있는 버튼 (보조 키 느낌) */
    private static GradientDrawable ring(Context c, int color) {
        GradientDrawable d = round(c, shade(color), 16);
        d.setStroke(dp(c, 1), lighten(color));
        return d;
    }

    static int lighten(int color) {
        int r = Math.min(255, (int) (Color.red(color) * 1.35f + 20));
        int g = Math.min(255, (int) (Color.green(color) * 1.35f + 20));
        int bl = Math.min(255, (int) (Color.blue(color) * 1.35f + 20));
        return Color.rgb(r, g, bl);
    }

    static int shade(int color) {
        int r = (int) (Color.red(color) * 0.55f);
        int g = (int) (Color.green(color) * 0.55f);
        int bl = (int) (Color.blue(color) * 0.55f);
        return Color.rgb(r, g, bl);
    }
}
