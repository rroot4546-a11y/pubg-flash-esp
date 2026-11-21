package com.ofx.pubgesp;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the chosen profile and overlay toggles. */
public final class Settings {
    private static final String PREF = "esp_settings";
    private Settings() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context c, OffsetProfile p, boolean box, boolean name,
                            boolean health, boolean distance, boolean noBots,
                            boolean noTeammates, int myTeam, int worldMode) {
        SharedPreferences.Editor e = prefs(c).edit();
        e.putString("package", p.packageName);
        e.putLong("world_ptr", p.worldPtrOff);
        e.putLong("world_deref", p.worldDerefDelta);
        e.putLong("world_add", p.worldFinalAdd);
        e.putInt("world_mode", worldMode);
        e.putLong("view_ptr", p.viewPtrOff);
        e.putLong("view_add", p.viewAdd);
        e.putLong("gname_ptr", p.gnamePtrOff);
        e.putLong("gname_add", p.gnameAdd);
        e.putLong("guobject", p.guobjectPtrOff);
        e.putLong("gnative", p.gnativePtrOff);
        e.putLong("level", p.levelOff);
        e.putLong("actors", p.actorsOff);
        e.putLong("root", p.rootOff);
        e.putLong("location", p.locationOff);
        e.putLong("health", p.healthOff);
        e.putLong("team", p.teamOff);
        e.putLong("name_ptr", p.namePtrOff);
        e.putFloat("head_height", p.headHeight);
        e.putInt("max_dist", p.maxDistance);
        e.putBoolean("box", box);
        e.putBoolean("name", name);
        e.putBoolean("health", health);
        e.putBoolean("distance", distance);
        e.putBoolean("no_bots", noBots);
        e.putBoolean("no_teammates", noTeammates);
        e.putInt("my_team", myTeam);
        e.apply();
    }

    public static OffsetProfile load(Context c) {
        SharedPreferences s = prefs(c);
        if (!s.contains("package")) return OffsetProfile.flash461();
        OffsetProfile p = OffsetProfile.flash461();
        p.packageName = s.getString("package", p.packageName);
        p.worldPtrOff = s.getLong("world_ptr", p.worldPtrOff);
        p.worldDerefDelta = s.getLong("world_deref", p.worldDerefDelta);
        p.worldFinalAdd = s.getLong("world_add", p.worldFinalAdd);
        p.worldMode = s.getInt("world_mode", p.worldMode);
        p.viewPtrOff = s.getLong("view_ptr", p.viewPtrOff);
        p.viewAdd = s.getLong("view_add", p.viewAdd);
        p.gnamePtrOff = s.getLong("gname_ptr", p.gnamePtrOff);
        p.gnameAdd = s.getLong("gname_add", p.gnameAdd);
        p.guobjectPtrOff = s.getLong("guobject", p.guobjectPtrOff);
        p.gnativePtrOff = s.getLong("gnative", p.gnativePtrOff);
        p.levelOff = s.getLong("level", p.levelOff);
        p.actorsOff = s.getLong("actors", p.actorsOff);
        p.rootOff = s.getLong("root", p.rootOff);
        p.locationOff = s.getLong("location", p.locationOff);
        p.healthOff = s.getLong("health", p.healthOff);
        p.teamOff = s.getLong("team", p.teamOff);
        p.namePtrOff = s.getLong("name_ptr", p.namePtrOff);
        p.headHeight = s.getFloat("head_height", p.headHeight);
        p.maxDistance = s.getInt("max_dist", p.maxDistance);
        return p;
    }

    public static boolean bool(Context c, String key, boolean def) {
        return prefs(c).getBoolean(key, def);
    }
    public static int integer(Context c, String key, int def) {
        return prefs(c).getInt(key, def);
    }
}
