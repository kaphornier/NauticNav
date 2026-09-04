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

    // Astronomical sunrise/sunset calculation (NOAA-style), using the GPS position and current date.
    private String[] sunTimes() {
        if (last == null) return new String[]{"—", "—", "—"};
        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_YEAR);
        double lat = last.getLatitude();
        double lon = last.getLongitude();
        double zenith = 90.8333;
        double lngHour = lon / 15.0;
        double n = day;
        String rise = solarTime(n, lat, lngHour, zenith, true, cal.getTimeZone());
        String set = solarTime(n, lat, lngHour, zenith, false, cal.getTimeZone());
        String length = daylightLength(n, lat, zenith);
        return new String[]{rise, set, length};
    }

    private String solarTime(double n, double lat, double lngHour, double zenith, boolean rising, TimeZone tz) {
        double t = n + ((rising ? 6.0 : 18.0) - lngHour) / 24.0;
        double M = (0.9856 * t) - 3.289;
        double L = M + (1.916 * Math.sin(Math.toRadians(M))) + (0.020 * Math.sin(Math.toRadians(2 * M))) + 282.634;
        L = (L + 360) % 360;
        double RA = Math.toDegrees(Math.atan(0.91764 * Math.tan(Math.toRadians(L))));
        RA = (RA + 360) % 360;
        double Lquadrant = Math.floor(L / 90.0) * 90.0;
        double RAquadrant = Math.floor(RA / 90.0) * 90.0;
        RA = RA + (Lquadrant - RAquadrant);
        RA /= 15.0;
        double sinDec = 0.39782 * Math.sin(Math.toRadians(L));
        double cosDec = Math.cos(Math.asin(sinDec));
        double cosH = (Math.cos(Math.toRadians(zenith)) - sinDec * Math.sin(Math.toRadians(lat))) /
                (cosDec * Math.cos(Math.toRadians(lat)));
        if (cosH > 1 || cosH < -1) return "—";
        double H = rising ? 360 - Math.toDegrees(Math.acos(cosH)) : Math.toDegrees(Math.acos(cosH));
        H /= 15.0;
        double T = H + RA - (0.06571 * t) - 6.622;
        double utc = (T - lngHour) % 24.0;
        if (utc < 0) utc += 24.0;
        long now = System.currentTimeMillis();
        Calendar out = Calendar.getInstance(tz);
        out.setTimeInMillis(now);
        int offsetMs = tz.getOffset(out.getTimeInMillis());
        double local = utc + offsetMs / 3600000.0;
        local = (local + 24.0) % 24.0;
        int hour = (int)Math.floor(local);
        int minute = (int)Math.round((local - hour) * 60.0);
        if (minute >= 60) { minute = 0; hour = (hour + 1) % 24; }
        return String.format(Locale.GERMANY, "%02d:%02d", hour, minute);
    }

    private String daylightLength(double n, double lat, double zenith) {
        double decl = 23.45 * Math.sin(Math.toRadians(360.0 / 365.0 * (284.0 + n)));
        double cosH = (Math.cos(Math.toRadians(zenith)) - Math.sin(Math.toRadians(lat)) * Math.sin(Math.toRadians(decl))) /
                (Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(decl)));
        if (cosH >= 1) return "00:00";
        if (cosH <= -1) return "24:00";
        double hours = 2.0 * Math.toDegrees(Math.acos(cosH)) / 15.0;
        int totalMinutes = (int)Math.round(hours * 60.0);
        return String.format(Locale.GERMANY, "%02d:%02d", totalMinutes / 60, totalMinutes % 60);
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

        text(c, "NAUTICNAV", center, 25, 11, 1, true);
        fill(c, accent);
        c.drawRoundRect(d(center - 18), d(31), d(center + 18), d(34), d(2), d(2), p);
        fill(c, fg);
        text(c, "DISTANZ", center, 54, 12, 1, false);
        text(c, String.format(Locale.US, "%.2f sm", distanceNm), center, 91, 42, 1, true);

        float top = 106;
        float cardH = 76;
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

        top += cardH + 16;
        fill(c, fg);
        text(c, timeFmt.format(new Date()), center, top + 22, 22, 1, true);
        fill(c, muted);
        text(c, "FAHRTZEIT  " + trip(), center, top + 43, 12, 1, false);
        if (showPos) text(c, pos(), center, top + 62, 10, 1, false);

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

        // Elegant compact solar information line: sunrise, sunset and daylight length.
        top += 58;
        String[] sun = sunTimes();
        fill(c, muted);
        text(c, "SONNE", side + 4, top + 18, 9, 0, true);
        fill(c, fg);
        text(c, "AUFGANG  " + sun[0], center - 70, top + 18, 10, 1, true);
        text(c, "UNTERGANG  " + sun[1], center + 45, top + 18, 10, 1, true);
        fill(c, muted);
        text(c, "TAG  " + sun[2], w - side - 4, top + 18, 9, 2, false);

        top += 38;
        float controlH = 46;
        card(c, side, top, center - 5, top + controlH, surface, line);
        card(c, center + 5, top, w - side, top + controlH, surface, line);
        fill(c, fg);
        text(c, night ? "☀  TAGMODUS" : "☾  NACHTMODUS", center/2 + 1, top + 29, 11, 1, true);
        text(c, "HELLIGKEIT  " + Math.round(brightness * 100) + "%", center + (w-center)/2, top + 29, 11, 1, true);

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
