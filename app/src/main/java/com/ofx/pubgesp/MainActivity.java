package com.ofx.pubgesp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

/** Control panel: root/overlay checks, profile selection and overlay lifecycle. */
public final class MainActivity extends Activity {
    private static final int REQ_SCREEN_CAPTURE = 9001;

    private Spinner engineSpinner;
    private EditText packageField;
    private EditText teamField;
    private Spinner profileSpinner;
    private Spinner modeSpinner;
    private CheckBox boxToggle, nameToggle, healthToggle, distanceToggle,
            botToggle, teammateToggle;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("PUBG Flash ESP — ofx");
        try {
            buildUi();
        } catch (Throwable t) {
            CrashLog.recordCrash(this, t);
            CrashLog.notifyCrash(this, t);
            showFatalUi(t);
        }
    }

    private void buildUi() {
        OffsetProfile p = Settings.load(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = 24;
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextSize(15f);
        root.addView(status);

        root.addView(label("ESP engine"));
        engineSpinner = new Spinner(this);
        engineSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Vision ESP (no root)", "Memory ESP (root)"}));
        root.addView(engineSpinner);

        root.addView(label("Target package"));
        packageField = new EditText(this);
        packageField.setSingleLine(true);
        packageField.setText(p.packageName);
        root.addView(packageField);

        root.addView(label("Profile"));
        profileSpinner = new Spinner(this);
        profileSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"PUBG FLASH 4.6.1 x64", "PUBG MOBILE 4.6 x64"}));
        root.addView(profileSpinner);

        root.addView(label("GWorld chain mode"));
        modeSpinner = new Spinner(this);
        modeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"deref -delta then +add", "pointer +add", "deref -delta"}));
        modeSpinner.setSelection(Math.max(0, Math.min(2, p.worldMode)));
        root.addView(modeSpinner);

        boxToggle = check(root, "Player boxes", Settings.bool(this, "box", true));
        nameToggle = check(root, "Player names", Settings.bool(this, "name", true));
        healthToggle = check(root, "Health bar", Settings.bool(this, "health", true));
        distanceToggle = check(root, "Distance", Settings.bool(this, "distance", true));
        botToggle = check(root, "Hide bots", Settings.bool(this, "no_bots", false));
        teammateToggle = check(root, "Hide teammates", Settings.bool(this, "no_teammates", true));

        root.addView(label("My team ID (-1 = auto off)"));
        teamField = new EditText(this);
        teamField.setSingleLine(true);
        teamField.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        int savedTeam = Settings.integer(this, "my_team", -1);
        teamField.setText(String.valueOf(savedTeam));
        root.addView(teamField);

        Button start = new Button(this);
        start.setText("START OVERLAY");
        start.setOnClickListener(v -> startOverlay());
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("STOP");
        stop.setOnClickListener(v -> {
            Intent memory = new Intent(this, EspOverlayService.class);
            memory.setAction(EspOverlayService.ACTION_STOP);
            startService(memory);
            Intent vision = new Intent(this, VisionOverlayService.class);
            vision.setAction(VisionOverlayService.ACTION_STOP);
            startService(vision);
            status.setText("stopped");
        });
        root.addView(stop);

        Button offsets = new Button(this);
        offsets.setText("EDIT OFFSETS");
        offsets.setOnClickListener(v -> showOffsetEditor());
        root.addView(offsets);

        Button refresh = new Button(this);
        refresh.setText("REFRESH STATUS");
        refresh.setOnClickListener(v -> updateStatus());
        root.addView(refresh);

        Button errors = new Button(this);
        errors.setText("VIEW ERRORS");
        errors.setOnClickListener(v -> {
            String crash = CrashLog.lastCrash(this);
            String error = CrashLog.lastError(this);
            String text = (crash == null ? "no crashes recorded" : "CRASH:\n" + crash)
                    + "\n\nENGINE:\n" + (error == null ? "no engine errors" : error);
            showText("Diagnostics", text);
        });
        root.addView(errors);

        Button clearErrors = new Button(this);
        clearErrors.setText("CLEAR ERRORS");
        clearErrors.setOnClickListener(v -> {
            CrashLog.clear(this);
            Toast.makeText(this, "logs cleared", Toast.LENGTH_SHORT).show();
            updateStatus();
        });
        root.addView(clearErrors);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        updateStatus();

        String startupCrash = CrashLog.lastCrash(this);
        if (startupCrash != null) {
            showText("App crashed — diagnostics", startupCrash);
        }

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    /** Last-resort screen: always opens, even when the control panel itself throws. */
    private void showFatalUi(Throwable t) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 24, 24, 24);

        TextView heading = new TextView(this);
        heading.setText("Startup failed — diagnostics");
        heading.setTextSize(18f);
        box.addView(heading);

        TextView body = new TextView(this);
        body.setText(CrashLog.stackTrace(t));
        body.setTextIsSelectable(true);
        body.setTextSize(11f);
        box.addView(body);

        Button copy = new Button(this);
        copy.setText("COPY ERROR");
        copy.setOnClickListener(v -> {
            try {
                ClipboardManager cm =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText(
                            "ofx-esp", CrashLog.stackTrace(t)));
                }
                Toast.makeText(this, "copied", Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) { }
        });
        box.addView(copy);

        scroll.addView(box);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (status != null) updateStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SCREEN_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            if (status != null) status.setText("screen capture consent denied");
            return;
        }
        Intent service = new Intent(this, VisionOverlayService.class);
        service.setAction(VisionOverlayService.ACTION_START);
        service.putExtra(VisionOverlayService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(VisionOverlayService.EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        if (status != null) status.setText("starting vision overlay — check the notification");
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13f);
        t.setPadding(0, 18, 0, 4);
        return t;
    }

    private CheckBox check(LinearLayout parent, String text, boolean value) {
        CheckBox c = new CheckBox(this);
        c.setText(text);
        c.setChecked(value);
        parent.addView(c);
        return c;
    }

    private void updateStatus() {
        if (status == null) return;
        boolean root = RootHelperClient.rootAvailable();
        boolean overlay = android.provider.Settings.canDrawOverlays(this);
        String engine = engineSpinner != null && engineSpinner.getSelectedItemPosition() == 0
                ? "vision (no root)" : "memory (root)";
        String error = CrashLog.lastError(this);
        String crash = CrashLog.lastCrash(this);
        status.setText("engine: " + engine
                + "\nroot: " + (root ? "YES" : "NO")
                + "\noverlay permission: " + (overlay ? "YES" : "NO")
                + "\nvision: MediaProjection + on-device person detector"
                + "\nmemory: /data/local/tmp/ofx_esp.sock"
                + "\nlast error: " + (error == null ? "none" : error.split("\n")[0])
                + "\nlast crash: " + (crash == null ? "none" : crash.split("\n")[0]));
    }

    private void showText(String title, String text) {
        ScrollView scroll = new ScrollView(this);
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextIsSelectable(true);
        view.setTextSize(11f);
        view.setPadding(16, 8, 16, 8);
        scroll.addView(view);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("COPY", (d, which) -> {
                    try {
                        ClipboardManager cm =
                                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("ofx-esp", text));
                        Toast.makeText(this, "copied", Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignored) { }
                })
                .setNegativeButton("CLOSE", null)
                .show();
    }

    private void startOverlay() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
            Toast.makeText(this, "grant overlay permission, then start again", Toast.LENGTH_LONG).show();
            return;
        }

        if (engineSpinner.getSelectedItemPosition() == 0) {
            MediaProjectionManager mpm =
                    (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_SCREEN_CAPTURE);
            status.setText("waiting for screen capture consent");
            return;
        }

        if (!RootHelperClient.rootAvailable()) {
            Toast.makeText(this,
                    "memory mode needs root — pick Vision ESP (no root)", Toast.LENGTH_LONG).show();
            return;
        }

        OffsetProfile p = Settings.load(this);
        p.packageName = packageField.getText().toString().trim();
        int profileIndex = profileSpinner.getSelectedItemPosition();
        if (profileIndex == 1 && p.packageName.equals("com.tencent.igfit")) {
            p = OffsetProfile.pubgm461();
        }
        int worldMode = modeSpinner.getSelectedItemPosition();
        int team;
        try {
            team = Integer.parseInt(teamField.getText().toString().trim());
        } catch (NumberFormatException e) {
            team = -1;
        }
        if (team == -1) team = Integer.MIN_VALUE;

        Settings.save(this, p,
                boxToggle.isChecked(), nameToggle.isChecked(), healthToggle.isChecked(),
                distanceToggle.isChecked(), botToggle.isChecked(), teammateToggle.isChecked(),
                team, worldMode);

        Intent i = new Intent(this, EspOverlayService.class);
        i.setAction(EspOverlayService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i);
        else startService(i);
        status.setText("starting — watch the notification");
    }

    private void showOffsetEditor() {
        OffsetProfile p = Settings.load(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(24, 12, 24, 12);

        final EditText[] fields = new EditText[18];
        String[] labels = {
                "world ptr", "world deref delta", "world final add", "view ptr", "view add",
                "gname ptr", "gname add", "guobject", "gnative", "level", "actors",
                "root component", "relative location", "health", "team id", "name ptr",
                "head height (dec)", "max distance (dec)"
        };
        String[] values = {
                hex(p.worldPtrOff), hex(p.worldDerefDelta), hex(p.worldFinalAdd),
                hex(p.viewPtrOff), hex(p.viewAdd), hex(p.gnamePtrOff), hex(p.gnameAdd),
                hex(p.guobjectPtrOff), hex(p.gnativePtrOff), hex(p.levelOff), hex(p.actorsOff),
                hex(p.rootOff), hex(p.locationOff), hex(p.healthOff), hex(p.teamOff),
                hex(p.namePtrOff), String.valueOf(p.headHeight), String.valueOf(p.maxDistance)
        };
        for (int i = 0; i < labels.length; i++) {
            TextView t = new TextView(this);
            t.setText(labels[i]);
            form.addView(t);
            fields[i] = new EditText(this);
            fields[i].setSingleLine(true);
            fields[i].setText(values[i]);
            form.addView(fields[i]);
        }
        scroll.addView(form);

        new AlertDialog.Builder(this)
                .setTitle("Offsets (hex or 0x...)")
                .setView(scroll)
                .setPositiveButton("SAVE", (d, which) -> {
                    OffsetProfile q = p.copy();
                    try {
                        q.worldPtrOff = parse(fields[0]);
                        q.worldDerefDelta = parse(fields[1]);
                        q.worldFinalAdd = parse(fields[2]);
                        q.viewPtrOff = parse(fields[3]);
                        q.viewAdd = parse(fields[4]);
                        q.gnamePtrOff = parse(fields[5]);
                        q.gnameAdd = parse(fields[6]);
                        q.guobjectPtrOff = parse(fields[7]);
                        q.gnativePtrOff = parse(fields[8]);
                        q.levelOff = parse(fields[9]);
                        q.actorsOff = parse(fields[10]);
                        q.rootOff = parse(fields[11]);
                        q.locationOff = parse(fields[12]);
                        q.healthOff = parse(fields[13]);
                        q.teamOff = parse(fields[14]);
                        q.namePtrOff = parse(fields[15]);
                        q.headHeight = Float.parseFloat(fields[16].getText().toString());
                        q.maxDistance = Integer.parseInt(fields[17].getText().toString());
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "bad number", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Settings.save(this, q,
                            boxToggle.isChecked(), nameToggle.isChecked(),
                            healthToggle.isChecked(), distanceToggle.isChecked(),
                            botToggle.isChecked(), teammateToggle.isChecked(),
                            Settings.integer(this, "my_team", -1),
                            modeSpinner.getSelectedItemPosition());
                    Toast.makeText(this, "offsets saved", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private static String hex(long v) {
        return "0x" + Long.toHexString(v).toUpperCase(java.util.Locale.US);
    }

    private static long parse(EditText field) {
        String s = field.getText().toString().trim();
        if (s.startsWith("-0x")) return -Long.parseLong(s.substring(3), 16);
        if (s.startsWith("0x")) return Long.parseLong(s.substring(2), 16);
        return Long.decode(s);
    }
}
