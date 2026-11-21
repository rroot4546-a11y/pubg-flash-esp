package com.ofx.pubgesp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.Toast;

/** Foreground service that owns the overlay window and the helper polling loop. */
public final class EspOverlayService extends Service {
    public static final String ACTION_START = "com.ofx.pubgesp.START";
    public static final String ACTION_STOP = "com.ofx.pubgesp.STOP";
    private static final String CHANNEL = "esp_overlay";

    private WindowManager windowManager;
    private EspOverlayView overlay;
    private RootHelperClient client;
    private Thread worker;
    private volatile boolean running;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        try {
            startForeground(1, buildNotification());
        } catch (Throwable t) {
            String message = "foreground service failed: " + CrashLog.describe(t);
            CrashLog.recordError(this, message + "\n" + CrashLog.stackTrace(t));
            ui.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;
        running = true;
        final OffsetProfile profile = Settings.load(this);
        final boolean box = Settings.bool(this, "box", true);
        final boolean name = Settings.bool(this, "name", true);
        final boolean health = Settings.bool(this, "health", true);
        final boolean distance = Settings.bool(this, "distance", true);
        final boolean noBots = Settings.bool(this, "no_bots", false);
        final boolean noTeam = Settings.bool(this, "no_teammates", true);
        final int myTeam = Settings.integer(this, "my_team", Integer.MIN_VALUE);

        ui.post(() -> addOverlay(box, name, health, distance, noBots, noTeam, myTeam, profile));

        worker = new Thread(() -> {
            client = new RootHelperClient(getApplicationContext());
            if (!client.start(profile.packageName, profile)) {
                ui.post(() -> Toast.makeText(this, "root helper failed to start", Toast.LENGTH_LONG).show());
                stopSelf();
                return;
            }
            while (running) {
                EspProtocol.Frame f = client.frame();
                if (f != null && f.status == 0) {
                    ui.post(() -> {
                        if (overlay != null) {
                            overlay.configure(f, myTeam, box, name, health, distance,
                                    noBots, noTeam, profile.maxDistance);
                        }
                    });
                }
                try {
                    Thread.sleep(45);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            client.stop();
        }, "esp-helper-poll");
        worker.start();
        return START_STICKY;
    }

    private void addOverlay(boolean box, boolean name, boolean health, boolean distance,
                            boolean noBots, boolean noTeam, int myTeam, OffsetProfile profile) {
        if (overlay != null) return;
        overlay = new EspOverlayView(this, null);
        overlay.configure(null, myTeam, box, name, health, distance, noBots, noTeam,
                profile.maxDistance);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSPARENT);
        lp.format = PixelFormat.TRANSPARENT;
        try {
            windowManager.addView(overlay, lp);
        } catch (Exception e) {
            Toast.makeText(this, "overlay permission missing", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "ESP overlay",
                    NotificationManager.IMPORTANCE_LOW);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_stat_esp)
                .setContentTitle("PUBG Flash ESP")
                .setContentText("Overlay active")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(1200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (client != null) client.stop();
        if (overlay != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(overlay);
            } catch (IllegalArgumentException ignored) {}
            overlay = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
