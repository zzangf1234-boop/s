package com.store.didcall;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.TextView;

/** 레이아웃 XML 없이 코드로 화면을 만들기 위한 도우미 */
final class Ui {
    static final int BG = Color.parseColor("#111418");
    static final int PANEL = Color.parseColor("#1C2127");
    static final int KEY = Color.parseColor("#262B31");
    static final int KEY2 = Color.parseColor("#33404D");
    static final int ACCENT = Color.parseColor("#FF8A00");
    static final int YELLOW = Color.parseColor("#FFCC33");
    static final int TEXT = Color.parseColor("#F2F2F2");
    static final int MUTED = Color.parseColor("#9AA3AD");

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
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        StateListDrawable sl = new StateListDrawable();
        sl.addState(new int[]{android.R.attr.state_pressed}, round(c, lighten(color), 14));
        sl.addState(new int[]{android.R.attr.state_focused}, round(c, lighten(color), 14));
        sl.addState(new int[]{}, round(c, color, 14));
        b.setBackground(sl);
        b.setStateListAnimator(null);
        return b;
    }

    static int lighten(int color) {
        int r = Math.min(255, (int) (Color.red(color) * 1.35f + 20));
        int g = Math.min(255, (int) (Color.green(color) * 1.35f + 20));
        int bl = Math.min(255, (int) (Color.blue(color) * 1.35f + 20));
        return Color.rgb(r, g, bl);
    }
}
