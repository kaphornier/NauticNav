package de.nauticnav;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity implements LocationListener {
    private NavView navView;
    private LocationManager lm;

    private final GnssStatus.Callback gnss = new GnssStatus.Callback() {
        @Override public void onSatelliteStatusChanged(GnssStatus s) {
            int n = 0;
            for (int i = 0; i < s.getSatelliteCount(); i++) if (s.usedInFix(i)) n++;
            if (navView != null) navView.setSatellites(n);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(Color.rgb(246, 249, 251));
        getWindow().setNavigationBarColor(Color.BLACK);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        navView = new NavView(this);
        setContentView(navView);
        lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
        } else startGps();
    }

    private void startGps() {
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0.5f, this);
            lm.registerGnssStatusCallback(gnss, new android.os.Handler(getMainLooper()));
        } catch (Exception ignored) {}
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == 7 && g.length > 0 && g[0] == PackageManager.PERMISSION_GRANTED) startGps();
    }

    @Override public void onLocationChanged(Location l) { navView.updateLocation(l); }
    @Override public void onProviderEnabled(String p) {}
    @Override public void onProviderDisabled(String p) {}

    @Override protected void onDestroy() {
        if (lm != null) {
            try { lm.removeUpdates(this); lm.unregisterGnssStatusCallback(gnss); } catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    public void setBrightness(float value) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = Math.max(0.08f, Math.min(1f, value));
        getWindow().setAttributes(lp);
    }
}
