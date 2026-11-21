package com.ofx.pubgesp.probe;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Minimal side-by-side diagnostic: every risky operation reports its own result on screen. */
public final class ProbeActivity extends Activity {
    private static final int REQ_CAPTURE = 77;

    private TextView log;
    private View probeOverlay;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("ESP Probe 1.0");

        log = new TextView(this);
        log.setTextSize(12f);
        log.setTextIsSelectable(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);
        root.addView(log);
        root.addView(button("0. BOOT INFO", v -> bootInfo()));
        root.addView(button("1. OVERLAY PERMISSION", v -> overlayPermission()));
        root.addView(button("2. ADD TEST OVERLAY", v -> addOverlay()));
        root.addView(button("3. REMOVE TEST OVERLAY", v -> removeOverlay()));
        root.addView(button("4. TEST SCREEN CAPTURE", v -> requestCapture()));
        root.addView(button("5. DUMP LOGCAT", v -> dumpLogcat()));
        root.addView(button("6. CLEAR LOG", v -> log.setText("")));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        bootInfo();
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(listener);
        return b;
    }

    private void line(String text) {
        log.append(text + "\n");
    }

    private void failure(String what, Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        line(what + " FAILED: " + t.getClass().getName() + ": " + t.getMessage());
        line(sw.toString());
    }

    private void bootInfo() {
        line("PROBE BOOT OK");
        line("package=" + getPackageName());
        line("android=" + Build.VERSION.RELEASE + " sdk=" + Build.VERSION.SDK_INT);
        line("device=" + Build.MANUFACTURER + " " + Build.MODEL);
        line("abis=" + String.join(",", Build.SUPPORTED_ABIS));
        line("overlay=" + Settings.canDrawOverlays(this));
        line("notifications=" + (Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED));
        line("---");
    }

    private void overlayPermission() {
        boolean granted = Settings.canDrawOverlays(this);
        line("overlay permission=" + granted);
        if (!granted) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t) {
                failure("open overlay settings", t);
            }
        }
    }

    private void addOverlay() {
        try {
            if (probeOverlay != null) {
                line("overlay already added");
                return;
            }
            View v = new View(this);
            v.setBackgroundColor(Color.RED);
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(200, 200,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSPARENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            wm.addView(v, lp);
            probeOverlay = v;
            line("overlay ADD OK");
        } catch (Throwable t) {
            failure("overlay add", t);
        }
    }

    private void removeOverlay() {
        try {
            if (probeOverlay == null) {
                line("no probe overlay");
                return;
            }
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            wm.removeViewImmediate(probeOverlay);
            probeOverlay = null;
            line("overlay REMOVE OK");
        } catch (Throwable t) {
            failure("overlay remove", t);
        }
    }

    private void requestCapture() {
        try {
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
            line("capture consent opened");
        } catch (Throwable t) {
            failure("open capture consent", t);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        line("capture result=" + resultCode + " data=" + (data != null));
        if (resultCode != RESULT_OK || data == null) {
            line("consent denied");
            return;
        }
        try {
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(resultCode, data);
            line("getMediaProjection=" + (projection != null));
            if (projection == null) return;

            if (Build.VERSION.SDK_INT >= 29) {
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        line("projection onStop");
                    }
                }, new Handler(Looper.getMainLooper()));
                line("registerCallback OK");
            }

            imageReader = ImageReader.newInstance(320, 180,
                    android.graphics.ImageFormat.YUV_420_888, 2);
            line("ImageReader OK");

            virtualDisplay = projection.createVirtualDisplay("probe", 320, 180, 160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, new Handler(Looper.getMainLooper()));
            line("createVirtualDisplay OK");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                releaseCapture();
                line("capture release OK — ALL CAPTURE TESTS PASSED");
            }, 1500);
        } catch (Throwable t) {
            failure("capture pipeline", t);
            releaseCapture();
        }
    }

    private void releaseCapture() {
        try {
            if (virtualDisplay != null) virtualDisplay.release();
        } catch (Throwable ignored) { }
        try {
            if (imageReader != null) imageReader.close();
        } catch (Throwable ignored) { }
        try {
            if (projection != null) projection.stop();
        } catch (Throwable ignored) { }
        virtualDisplay = null;
        imageReader = null;
        projection = null;
    }

    private void dumpLogcat() {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"logcat", "-d", "-t", "200", "*:E"});
            InputStream in = process.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            process.destroy();
            String text = out.toString("UTF-8");
            line(text.isEmpty() ? "logcat empty" : text.substring(0, Math.min(5000, text.length())));
        } catch (Throwable t) {
            failure("logcat", t);
        }
    }

    @Override
    protected void onDestroy() {
        releaseCapture();
        removeOverlay();
        super.onDestroy();
    }
}
