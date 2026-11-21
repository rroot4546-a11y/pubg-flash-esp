package com.ofx.pubgesp;

import android.content.Context;
import android.app.Application;

/** Captures crashes before content providers or the UI can throw. */
public final class EspApp extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
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
