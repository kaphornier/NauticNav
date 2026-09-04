package de.nauticnav;

import android.content.Context;
import android.graphics.*;
import android.location.Location;
import android.view.MotionEvent;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;

public class NavView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean night = false, showPos = true;
    private int sats = 0;
    private float brightness = 0.65f;
    private double distanceNm = 0, sog = 0, cog = 0;
    private long startMs = 0;
    private Location last;
    private final ArrayDeque<Point> history = new ArrayDeque<>();

    private static class Point {
        double sog, cog, seg;
        Point(double sog, double cog, double seg) { this.sog = sog; this.cog = cog; this.seg = seg; }
    }

    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY);

    public NavView(Context c) {
        super(c);
        p.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        setFocusable(true);
    }

    public void setSatellites(int n) { sats = n; invalidate(); }

    public void updateLocation(Location l) {
        if (startMs == 0) startMs = System.currentTimeMillis();
        double seg = last == null ? 0 : last.distanceTo(l) / 1852.0;
        distanceNm += seg;
        sog = Math.max(0, l.hasSpeed() ? l.getSpeed() * 1.943844 : 0);
        if (l.hasBearing()) cog = l.getBearing();
        history.addLast(new Point(sog, cog, seg));
        while (totalHistory() > 0.5 && history.size() > 1) history.removeFirst();
        last = new Location(l);
        invalidate();
    }

    private double totalHistory() { double s = 0; for (Point x : history) s += x.seg; return s; }
    private double avgSog() { if (history.isEmpty()) return 0; double v = 0; for (Point x : history) v += x.sog; return v / history.size(); }
    private double avgCog() {
        if (history.isEmpty()) return 0;
        double x = 0, y = 0;
        for (Point a : history) { double r = Math.toRadians(a.cog); x += Math.sin(r); y += Math.cos(r); }
        return (Math.toDegrees(Math.atan2(x, y)) + 360) % 360;
    }
    private String pos() { return last == null ? "—" : String.format(Locale.US, "%.5f°, %.5f°", last.getLatitude(), last.getLongitude()); }
    private String trip() {
        long sec = startMs == 0 ? 0 : (System.currentTimeMillis() - startMs) / 1000;
        return String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, (sec / 60) % 60, sec % 60);
    }

    private float d(float v) { return v * getResources().getDisplayMetrics().density; }
    private void fill(Canvas c, int color) { p.setStyle(Paint.Style.FILL); p.setColor(color); }
    private void stroke(Canvas c, int color, float widthDp) { p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(d(widthDp)); p.setColor(color); }
    private void rounded(Canvas c, float l, float t, float r, float b, float radiusDp) { c.drawRoundRect(d(l), d(t), d(r), d(b), d(radiusDp), d(radiusDp), p); }
    private void text(Canvas c, String s, float x, float y, float sizeSp, int align, boolean bold) {
        p.setStyle(Paint.Style.FILL);
        p.setTextSize(sizeSp * getResources().getDisplayMetrics().scaledDensity);
        p.setTextAlign(align == 0 ? Paint.Align.LEFT : align == 1 ? Paint.Align.CENTER : Paint.Align.RIGHT);
        p.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        c.drawText(s, d(x), d(y), p);
    }

    private void card(Canvas c, float l, float t, float r, float b, int fillColor, int lineColor) {
        fill(c, fillColor); rounded(c, l, t, r, b, 16);
        stroke(c, lineColor, 1); rounded(c, l, t, r, b, 16);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float den = getResources().getDisplayMetrics().density;
        float w = getWidth() / den, h = getHeight() / den;

        int bg = night ? Color.rgb(7, 12, 18) : Color.rgb(246, 249, 251);
        int surface = night ? Color.rgb(13, 20, 28) : Color.WHITE;
        int fg = night ? Color.rgb(242, 231, 215) : Color.rgb(24, 39, 52);
        int muted = night ? Color.rgb(157, 143, 128) : Color.rgb(103, 122, 136);
        int accent = night ? Color.rgb(222, 137, 67) : Color.rgb(25, 126, 218);
        int line = night ? Color.rgb(66, 57, 49) : Color.rgb(202, 214, 221);

        fill(c, bg); c.drawRect(0, 0, getWidth(), getHeight(), p);

        float side = 14, gap = 10;
        float contentW = w - side * 2;
        float bw = (contentW - gap) / 2;
        float center = w / 2;

        // Header
        text(c, "NAUTICNAV", center, 25, 11, 1, true);
        fill(c, accent);
        c.drawRoundRect(d(center - 18), d(31), d(center + 18), d(34), d(2), d(2), p);
        fill(c, fg);
        text(c, "DISTANZ", center, 54, 12, 1, false);
        text(c, String.format(Locale.US, "%.2f sm", distanceNm), center, 91, 42, 1, true);

        float top = 106;
        float cardH = 76;
        // SOG cards
        card(c, side, top, side + bw, top + cardH, surface, line);
        card(c, side + bw + gap, top, w - side, top + cardH, surface, line);
        fill(c, accent);
        text(c, "SOG AKTUELL", side + bw/2, top + 24, 11, 1, true);
        text(c, String.format(Locale.US, "%.1f kn", sog), side + bw/2, top + 54, 24, 1, true);
        fill(c, muted); text(c, "aktuell", side + bw/2, top + 68, 9, 1, false);
        fill(c, accent);
        text(c, "SOG Ø", side + bw + gap + bw/2, top + 24, 11, 1, true);
        text(c, String.format(Locale.US, "%.1f kn", avgSog()), side + bw + gap + bw/2, top + 54, 24, 1, true);
        fill(c, muted); text(c, "letzte 0,5 sm", side + bw + gap + bw/2, top + 68, 9, 1, false);

        top += cardH + 10;
        // COG cards
        card(c, side, top, side + bw, top + cardH, surface, line);
        card(c, side + bw + gap, top, w - side, top + cardH, surface, line);
        fill(c, accent);
        text(c, "COG AKTUELL", side + bw/2, top + 24, 11, 1, true);
        text(c, String.format(Locale.US, "%03.0f°", cog), side + bw/2, top + 54, 24, 1, true);
        fill(c, muted); text(c, "Kurs über Grund", side + bw/2, top + 68, 9, 1, false);
        fill(c, fg);
        text(c, "COG Ø", side + bw + gap + bw/2, top + 24, 11, 1, true);
        text(c, String.format(Locale.US, "%03.0f°", avgCog()), side + bw + gap + bw/2, top + 54, 24, 1, true);
        fill(c, muted); text(c, "letzte 0,5 sm", side + bw + gap + bw/2, top + 68, 9, 1, false);

        // Time / position block
        top += cardH + 16;
        fill(c, fg);
        text(c, timeFmt.format(new Date()), center, top + 22, 22, 1, true);
        fill(c, muted);
        text(c, "FAHRTZEIT  " + trip(), center, top + 43, 12, 1, false);
        if (showPos) text(c, pos(), center, top + 62, 10, 1, false);

        // GPS strip
        top += 75;
        card(c, side, top, w - side, top + 48, surface, line);
        fill(c, fg);
        text(c, "GPS", side + 18, top + 20, 9, 0, true);
        fill(c, accent);
        text(c, String.valueOf(sats) + " SAT", side + 48, top + 20, 12, 0, true);
        fill(c, muted);
        text(c, last == null ? "Genauigkeit —" : String.format(Locale.US, "±%.0f m", last.getAccuracy()), center, top + 20, 11, 1, false);
        fill(c, last != null && last.hasAccuracy() ? accent : muted);
        text(c, last != null && last.hasAccuracy() ? "3D FIX" : "NO FIX", w - side - 18, top + 20, 11, 2, true);
        fill(c, muted);
        text(c, "GPS-Status", center, top + 36, 8, 1, false);

        // Controls
        top += 62;
        float controlH = 46;
        card(c, side, top, center - 5, top + controlH, surface, line);
        card(c, center + 5, top, w - side, top + controlH, surface, line);
        fill(c, fg);
        text(c, night ? "☀  TAGMODUS" : "☾  NACHTMODUS", center/2 + 1, top + 29, 11, 1, true);
        text(c, "HELLIGKEIT  " + Math.round(brightness * 100) + "%", center + (w-center)/2, top + 29, 11, 1, true);

        // Reset is anchored to the bottom and gets a generous touch target.
        float resetH = 58;
        float rb = h - resetH - 14;
        fill(c, accent); rounded(c, side, rb, w - side, rb + resetH, 18);
        fill(c, Color.WHITE);
        text(c, "RESET", center, rb + 25, 10, 1, true);
        text(c, "DISTANZ & FAHRTZEIT", center, rb + 43, 14, 1, true);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float den = getResources().getDisplayMetrics().density;
        float x = e.getX() / den, y = e.getY() / den;
        float w = getWidth() / den, h = getHeight() / den;
        if (y > h - 85) {
            distanceNm = 0; startMs = System.currentTimeMillis(); history.clear(); last = null; sog = 0; cog = 0; invalidate(); return true;
        }
        if (y > h - 160) {
            if (x < w/2) { night = !night; invalidate(); }
            else { brightness -= 0.15f; if (brightness < 0.25f) brightness = 1f; if (getContext() instanceof MainActivity) ((MainActivity)getContext()).setBrightness(brightness); invalidate(); }
            return true;
        }
        return true;
    }
}
