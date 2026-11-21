package com.ofx.pubgesp.probe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.ImageFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Runs the whole capture pipeline inside the mediaProjection-type foreground service. */
public final class ProbeFgs extends Service {
    private static final String CHANNEL = "probe_fgs";
    private static final int NOTIFICATION_ID = 10;

    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startForegroundWithNotification();
            ProbeActivity.CAPTURE_RESULT = "FGS active — startForeground returned";
        } catch (Throwable t) {
            ProbeActivity.CAPTURE_RESULT = "startForeground FAILED: " + trace(t);
            stopSelf();
            return START_NOT_STICKY;
        }

        int code = intent == null ? 0 : intent.getIntExtra("code", 0);
        Intent data = intent == null ? null : intent.getParcelableExtra("data");

        try {
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            ProbeActivity.CAPTURE_RESULT = "getMediaProjection OK";

            if (Build.VERSION.SDK_INT >= 29) {
                projection.registerCallback(new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        ProbeActivity.CAPTURE_RESULT = "projection onStop";
                    }
                }, new Handler(Looper.getMainLooper()));
            }

            imageReader = ImageReader.newInstance(320, 180, ImageFormat.YUV_420_888, 2);
            virtualDisplay = projection.createVirtualDisplay("probe", 320, 180, 160,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, new Handler(Looper.getMainLooper()));
            ProbeActivity.CAPTURE_RESULT = "ALL CAPTURE TESTS PASSED";
        } catch (Throwable t) {
            ProbeActivity.CAPTURE_RESULT = "capture pipeline FAILED: " + trace(t);
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            release();
            stopSelf();
        }, 1500);
        return START_NOT_STICKY;
    }

    private void startForegroundWithNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && nm != null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Probe capture", NotificationManager.IMPORTANCE_LOW));
        }
        Intent open = new Intent(this, ProbeActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 3, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        Notification notification = b.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("ESP Probe")
                .setContentText("Capture service active")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private static String trace(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private void release() {
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

    @Override
    public void onDestroy() {
        release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
