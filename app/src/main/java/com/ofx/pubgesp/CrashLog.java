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
    private static final int LIMIT = 12000;

    private CrashLog() { }

    private static Thread.UncaughtExceptionHandler previous;

    /** Installed from Activity.attachBaseContext so launch failures are recorded too. */
    public static void installEarlyHandler(Context c) {
        try {
            final Thread.UncaughtExceptionHandler old =
                    Thread.getDefaultUncaughtExceptionHandler();
            previous = old;
            Thread.setDefaultUncaughtExceptionHandler((thread, t) -> {
                try {
                    mark(c, "UNCAUGHT: " + stackTrace(t));
                    recordCrash(c, t);
                    notifyCrash(c, t);
                } catch (Throwable ignored) { }
                if (previous != null) previous.uncaughtException(thread, t);
                else Runtime.getRuntime().exit(1);
            });
            mark(c, "early handler installed");
        } catch (Throwable ignored) { }
    }

    /** Appends a boot-stage marker with a full flush so a crash loop leaves a trail. */
    public static void mark(Context c, String stage) {
        if (c == null) return;
        try {
            java.io.File file = new java.io.File(c.getFilesDir(), "boot.txt");
            java.io.FileWriter writer = new java.io.FileWriter(file, true);
            try {
                writer.write(System.currentTimeMillis() + " " + stage + "\n");
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) { }
        Log.i("ofx-esp", "boot: " + stage);
    }

    public static String bootLog(Context c) {
        if (c == null) return "no context";
        StringBuilder sb = new StringBuilder();
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(new java.io.File(c.getFilesDir(), "boot.txt")));
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            reader.close();
        } catch (Throwable ignored) { }
        return sb.length() == 0 ? "no boot markers" : trim(sb.toString());
    }

    public static void recordCrash(Context c, Throwable t) {
        if (c == null || t == null) return;
        String report = describe(t) + "\n" + stackTrace(t)
                + "\n\n--- logcat ---\n" + captureLogcat();
        writeNow(c, LAST_CRASH, report);
        writeInternal(c, "crash.txt", report);
        Log.e("ofx-esp", "uncaught crash", t);
    }

    /** logcat survives native crashes that never reach a Java exception handler. */
    public static String captureLogcat() {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"logcat", "-d", "-t", "300", "*:E"});
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.io.InputStream in = process.getInputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            process.destroy();
            return trim(out.toString("UTF-8"));
        } catch (Throwable ignored) {
            return "";
        }
    }

    public static void recordError(Context c, String message) {
        if (c == null || message == null) return;
        write(c, LAST_ERROR, message);
        Log.e("ofx-esp", message);
    }

    /** Persists a readable report outside prefs and posts it in the shade so it survives a crash loop. */
    public static void notifyCrash(Context c, Throwable t) {
        if (c == null) return;
        String text = describe(t) + "\n" + stackTrace(t)
                + "\n\n--- logcat ---\n" + captureLogcat();
        writeInternal(c, "crash.txt", text);
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

    /** commit() blocks until the data is on disk — apply() is lost when a crash kills the process. */
    private static void writeNow(Context c, String key, String value) {
        try {
            c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(key, trim(value)).commit();
        } catch (Throwable ignored) { }
    }

    private static void writeInternal(Context c, String name, String value) {
        try {
            java.io.File file = new java.io.File(c.getFilesDir(), name);
            java.io.FileWriter writer = new java.io.FileWriter(file, false);
            try {
                writer.write(trim(value));
            } finally {
                writer.close();
            }
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
