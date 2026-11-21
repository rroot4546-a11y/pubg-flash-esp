package com.ofx.pubgesp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

/** Stores the last crash and the last engine error where the app can show them without adb. */
public final class CrashLog {
    private static final String PREFS = "ofx_crash";
    private static final String LAST_CRASH = "last_crash";
    private static final String LAST_ERROR = "last_error";
    private static final int LIMIT = 6000;

    private CrashLog() { }

    public static void recordCrash(Context c, Throwable t) {
        if (c == null || t == null) return;
        write(c, LAST_CRASH, stackTrace(t));
        Log.e("ofx-esp", "uncaught crash", t);
    }

    public static void recordError(Context c, String message) {
        if (c == null || message == null) return;
        write(c, LAST_ERROR, message);
        Log.e("ofx-esp", message);
    }

    /** Persists a readable report outside prefs and posts it in the shade so it survives a crash loop. */
    public static void notifyCrash(Context c, Throwable t) {
        if (c == null) return;
        String text = describe(t) + "\n" + stackTrace(t);
        try {
            File dir = c.getExternalFilesDir(null);
            if (dir != null) {
                File report = new File(dir, "crash.txt");
                FileWriter writer = new FileWriter(report, false);
                try {
                    writer.write(text);
                } finally {
                    writer.close();
                }
            }
        } catch (Throwable ignored) { }

        try {
            NotificationManager nm =
                    (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(
                        "crash", "Crash diagnostics", NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            Intent open = new Intent(c, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(c, 42, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(c, "crash")
                    : new Notification.Builder(c);
            b.setSmallIcon(R.drawable.ic_stat_esp)
                    .setContentTitle("PUBG Flash ESP crashed")
                    .setContentText(describe(t))
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(99, b.build());
        } catch (Throwable ignored) { }
    }

    public static String lastCrash(Context c) {
        return read(c, LAST_CRASH);
    }

    public static String lastError(Context c) {
        return read(c, LAST_ERROR);
    }

    public static void clear(Context c) {
        if (c == null) return;
        try {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .remove(LAST_CRASH).remove(LAST_ERROR).apply();
        } catch (Throwable ignored) { }
    }

    public static String describe(Throwable t) {
        if (t == null) return "unknown error";
        String message = t.getMessage();
        String type = t.getClass().getName();
        return message == null ? type : type + ": " + message;
    }

    public static String stackTrace(Throwable t) {
        if (t == null) return "unknown error";
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return trim(sw.toString());
    }

    private static void write(Context c, String key, String value) {
        try {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(key, trim(value)).apply();
        } catch (Throwable ignored) { }
    }

    private static String read(Context c, String key) {
        if (c == null) return null;
        try {
            return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String trim(String s) {
        if (s == null) return "";
        return s.length() > LIMIT ? s.substring(s.length() - LIMIT) : s;
    }
}
