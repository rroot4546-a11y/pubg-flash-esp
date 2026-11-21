package com.ofx.pubgesp.probe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

/** Keeps a mediaProjection-type foreground service alive so capture tokens are accepted. */
public final class ProbeFgs extends Service {
    private static final String CHANNEL = "probe_fgs";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
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
            Notification notification = b.setSmallIcon(android.R.drawable.ic_menu_save)
                    .setContentTitle("ESP Probe")
                    .setContentText("Capture service active")
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .build();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(10, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(10, notification);
            }
        } catch (Throwable t) {
            ProbeActivity.lineStatic("FGS FAILED: " + t);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        ProbeActivity.lineStatic("FGS stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
