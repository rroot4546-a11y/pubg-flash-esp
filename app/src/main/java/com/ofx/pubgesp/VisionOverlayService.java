package com.ofx.pubgesp;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.firebase.components.ComponentRegistrar;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rootless ESP engine. Captures the game's own framebuffer with MediaProjection,
 * finds people with the bundled on-device ML Kit detector and draws boxes on top.
 */
public final class VisionOverlayService extends Service {
    public static final String ACTION_START = "com.ofx.pubgesp.VISION_START";
    public static final String ACTION_STOP = "com.ofx.pubgesp.VISION_STOP";
    public static final String EXTRA_RESULT_CODE = "projection_result_code";
    public static final String EXTRA_RESULT_DATA = "projection_result_data";

    private static final String CHANNEL = "vision_esp";
    private static final int NOTIFICATION_ID = 2;
    private static final int MAX_EDGE = 720;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy = new AtomicBoolean(false);

    private WindowManager windowManager;
    private VisionOverlayView overlay;
    private MediaProjection projection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private ObjectDetector detector;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private float captureScale = 1f;
    private int rotationDegrees;
    private volatile boolean running;

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    private boolean enterForeground() {
        try {
            Notification notification = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
            return true;
        } catch (Throwable t) {
            String message = "foreground service failed: " + CrashLog.describe(t);
            CrashLog.recordError(this, message);
            ui.post(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
            return false;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Toast.makeText(this, "screen capture permission missing", Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!enterForeground()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        running = true;

        try {
            // No startup provider and no manifest component discovery: register the
            // exact set of registrars bundled with this dependency graph by hand.
            CrashLog.mark(this, "mlkit: begin");
            try {
                com.google.mlkit.common.sdkinternal.MlKitContext.getInstance();
            } catch (IllegalStateException notInitialised) {
                List<ComponentRegistrar> registrars = Arrays.asList(
                        new com.google.mlkit.common.internal.CommonComponentRegistrar(),
                        new com.google.mlkit.vision.common.internal.VisionCommonRegistrar(),
                        new com.google.mlkit.vision.vkp.VkpRegistrar(),
                        new com.google.mlkit.vision.objects.defaults.internal.DefaultObjectsRegistrar());
                com.google.mlkit.common.sdkinternal.MlKitContext.initialize(
                        getApplicationContext(), registrars);
            }
            CrashLog.mark(this, "mlkit: context ready");
            detector = ObjectDetection.getClient(new ObjectDetectorOptions.Builder()
                    .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                    .enableMultipleObjects()
                    .enableClassification()
                    .build());
            CrashLog.mark(this, "mlkit: detector ready");
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(resultCode, resultData);
            if (projection == null) throw new IllegalStateException("MediaProjection is null");
            CrashLog.mark(this, "capture: projection ready");
            startCapture();
            CrashLog.mark(this, "capture: started");
            ui.post(this::addOverlay);
        } catch (Throwable t) {
            String message = "vision engine failed: " + CrashLog.describe(t)
                    + " — tap VIEW ERRORS";
            CrashLog.recordError(this, message + "\n" + CrashLog.stackTrace(t));
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_STICKY;
    }

    private void startCapture() {
        Display display = windowManager.getDefaultDisplay();
        Point screen = new Point();
        display.getRealSize(screen);
        captureScale = Math.min(1f, (float) MAX_EDGE / (float) Math.max(screen.x, screen.y));
        int width = Math.max(2, Math.round(screen.x * captureScale));
        int height = Math.max(2, Math.round(screen.y * captureScale));
        int density = getResources().getDisplayMetrics().densityDpi;

        switch (display.getRotation()) {
            case Surface.ROTATION_90 -> rotationDegrees = 90;
            case Surface.ROTATION_180 -> rotationDegrees = 180;
            case Surface.ROTATION_270 -> rotationDegrees = 270;
            default -> rotationDegrees = 0;
        }

        captureThread = new HandlerThread("vision-capture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        // Android 14+ rejects createVirtualDisplay() unless a callback is registered first.
        // Registering it everywhere also gives us a reliable stop signal from the system.
        if (Build.VERSION.SDK_INT >= 29 && projection != null) {
            projectionCallback = new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    ui.post(() -> {
                        if (!running) return;
                        Toast.makeText(VisionOverlayService.this,
                                "screen capture stopped", Toast.LENGTH_SHORT).show();
                        stopSelf();
                    });
                }
            };
            projection.registerCallback(projectionCallback, captureHandler);
        }

        imageReader = ImageReader.newInstance(width, height, ImageFormat.YUV_420_888, 2);
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image == null) return;
            if (!busy.compareAndSet(false, true)) {
                image.close();
                return;
            }
            try {
                InputImage input = InputImage.fromMediaImage(image, rotationDegrees);
                detector.process(input)
                        .addOnSuccessListener(this::publish)
                        .addOnFailureListener(e -> { })
                        .addOnCompleteListener(task -> {
                            busy.set(false);
                            image.close();
                        });
            } catch (Throwable t) {
                CrashLog.recordError(this, "vision frame failed: " + CrashLog.describe(t));
                busy.set(false);
                image.close();
            }
        }, captureHandler);

        virtualDisplay = projection.createVirtualDisplay("ofx-vision-capture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, captureHandler);
    }

    private void publish(List<DetectedObject> detected) {
        if (!running || detected == null) return;
        List<VisionOverlayView.Box> boxes = new ArrayList<>();
        for (DetectedObject object : detected) {
            if (object == null) continue;
            boolean person = false;
            for (DetectedObject.Label label : object.getLabels()) {
                if (label.getText().toLowerCase(Locale.US).contains("person")
                        && label.getConfidence() >= 0.25f) {
                    person = true;
                    break;
                }
            }
            if (!person) continue;
            android.graphics.Rect r = object.getBoundingBox();
            boxes.add(new VisionOverlayView.Box(
                    r.left / captureScale,
                    r.top / captureScale,
                    r.right / captureScale,
                    r.bottom / captureScale,
                    object.getTrackingId()));
        }
        ui.post(() -> {
            if (overlay != null) overlay.setBoxes(boxes);
        });
    }

    private void addOverlay() {
        if (overlay != null) return;
        overlay = new VisionOverlayView(this, null);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSPARENT);
        lp.format = PixelFormat.TRANSPARENT;
        try {
            windowManager.addView(overlay, lp);
        } catch (RuntimeException e) {
            Toast.makeText(this, "overlay permission missing", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Vision ESP",
                    NotificationManager.IMPORTANCE_LOW);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return b.setSmallIcon(R.drawable.ic_stat_esp)
                .setContentTitle("PUBG Flash ESP — vision")
                .setContentText("Rootless overlay active")
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (virtualDisplay != null) {
            try {
                virtualDisplay.release();
            } catch (RuntimeException ignored) { }
            virtualDisplay = null;
        }
        if (imageReader != null) {
            try {
                imageReader.setOnImageAvailableListener(null, null);
                imageReader.close();
            } catch (RuntimeException ignored) { }
            imageReader = null;
        }
        if (projection != null) {
            if (projectionCallback != null && Build.VERSION.SDK_INT >= 29) {
                try {
                    projection.unregisterCallback(projectionCallback);
                } catch (RuntimeException ignored) { }
            }
            projectionCallback = null;
            try {
                projection.stop();
            } catch (IllegalStateException ignored) { }
            projection = null;
        }
        if (detector != null) {
            try {
                detector.close();
            } catch (RuntimeException ignored) { }
            detector = null;
        }
        if (overlay != null && windowManager != null) {
            try {
                windowManager.removeViewImmediate(overlay);
            } catch (IllegalArgumentException ignored) { }
            overlay = null;
        }
        if (captureThread != null) {
            try {
                captureThread.quitSafely();
            } catch (RuntimeException ignored) { }
            captureThread = null;
        }
        captureHandler = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
