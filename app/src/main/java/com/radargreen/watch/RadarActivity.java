package com.radargreen.watch;

import android.app.Activity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

public class RadarActivity extends Activity {
    private static final long AMBIENT_DELAY_MS = 15_000L;
    private static final long ACTIVE_FRAME_MS = 50L;   // ~20 FPS
    private static final long AMBIENT_FRAME_MS = 1000L; // update time/BAT once per second

    private RadarView radarView;
    private RadarData data;
    private boolean ambientMode;
    private long lastInteraction;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        data = new RadarData(this);
        radarView = new RadarView();
        setContentView(radarView);
        markInteraction();
        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 22);
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 22 && data != null) data.refreshWeather();
    }

    @Override protected void onResume() {
        super.onResume();
        data.start();
        markInteraction();
        radarView.post(frame);
    }

    @Override protected void onPause() {
        radarView.removeCallbacks(frame);
        data.stop();
        super.onPause();
    }

    private void markInteraction() {
        lastInteraction = SystemClock.elapsedRealtime();
        if (ambientMode) {
            ambientMode = false;
            if (radarView != null) radarView.invalidate();
        }
    }

    private void updateMode() {
        long idle = SystemClock.elapsedRealtime() - lastInteraction;
        ambientMode = idle >= AMBIENT_DELAY_MS;
    }

    private final Runnable frame = new Runnable() {
        @Override public void run() {
            if (radarView == null) return;
            updateMode();
            radarView.invalidate();
            radarView.postDelayed(this, ambientMode ? AMBIENT_FRAME_MS : ACTIVE_FRAME_MS);
        }
    };

    private final class RadarView extends View {
        private final RadarRenderer radar = new RadarRenderer(data);

        RadarView() { super(RadarActivity.this); setClickable(true); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            radar.draw(canvas, getWidth(), getHeight(), ambientMode);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                markInteraction();
                removeCallbacks(frame);
                post(frame);
                return true;
            }
            return super.onTouchEvent(event);
        }
    }
}
