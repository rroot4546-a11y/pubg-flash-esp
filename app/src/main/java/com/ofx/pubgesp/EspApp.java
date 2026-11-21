package com.ofx.pubgesp;

import android.app.Application;

/** Captures every uncaught exception so the control panel can show the real stack trace. */
public final class EspApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            CrashLog.recordCrash(getApplicationContext(), throwable);
            CrashLog.notifyCrash(getApplicationContext(), throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            } else {
                Runtime.getRuntime().exit(2);
            }
        });
    }
}
