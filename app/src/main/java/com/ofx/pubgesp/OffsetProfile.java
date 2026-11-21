package com.ofx.pubgesp;

/**
 * Every value that lives in the game binary is here, never scattered in code.
 * Source of the 4.6.1 Flash globals: public PUBG FLASH 4.6.1 64-bit offset list.
 * Struct member offsets follow the stock UE4 64-bit layout; adjust in the UI
 * when a game update moves a member.
 */
public final class OffsetProfile {
    public String name;
    public String packageName;
    public long worldPtrOff;
    public long worldDerefDelta;
    public long worldFinalAdd;
    public int worldMode;
    public long viewPtrOff;
    public long viewAdd;
    public long gnamePtrOff;
    public long gnameAdd;
    public long guobjectPtrOff;
    public long gnativePtrOff;
    public long levelOff;
    public long actorsOff;
    public long rootOff;
    public long locationOff;
    public long healthOff;
    public long teamOff;
    public long namePtrOff;
    public float headHeight;
    public int maxDistance;

    public static OffsetProfile flash461() {
        OffsetProfile p = new OffsetProfile();
        p.name = "PUBG FLASH 4.6.1 x64";
        p.packageName = "com.tencent.igfit";
        p.worldPtrOff = 0xF3B85F8L;
        p.worldDerefDelta = -0x20L;
        p.worldFinalAdd = 0x30L;
        p.worldMode = 0;
        p.viewPtrOff = 0xF5D8170L;
        p.viewAdd = 0xC0L;
        p.gnamePtrOff = 0xF06B820L;
        p.gnameAdd = 0x110L;
        p.guobjectPtrOff = 0xF33C900L;
        p.gnativePtrOff = 0xF06B3A8L;
        p.levelOff = 0x30L;
        p.actorsOff = 0xA0L;
        p.rootOff = 0x168L;
        p.locationOff = 0x11CL;
        p.healthOff = 0xE80L;
        p.teamOff = 0xC40L;
        p.namePtrOff = 0xBF0L;
        p.headHeight = 90f;
        p.maxDistance = 600;
        return p;
    }

    public static OffsetProfile pubgm461() {
        OffsetProfile p = flash461();
        p.name = "PUBG MOBILE 4.6 x64";
        p.packageName = "com.tencent.ig";
        p.worldPtrOff = 0xF621EF8L;
        p.worldDerefDelta = 0L;
        p.worldFinalAdd = 0L;
        p.worldMode = 1;
        p.viewPtrOff = 0xF5FBFF0L;
        p.viewAdd = 0L;
        p.gnamePtrOff = 0xF08F820L;
        p.gnameAdd = 0L;
        p.guobjectPtrOff = 0xF360880L;
        p.gnativePtrOff = 0xF08F3A8L;
        p.maxDistance = 800;
        return p;
    }

    public OffsetProfile copy() {
        OffsetProfile c = new OffsetProfile();
        c.name = name;
        c.packageName = packageName;
        c.worldPtrOff = worldPtrOff;
        c.worldDerefDelta = worldDerefDelta;
        c.worldFinalAdd = worldFinalAdd;
        c.worldMode = worldMode;
        c.viewPtrOff = viewPtrOff;
        c.viewAdd = viewAdd;
        c.gnamePtrOff = gnamePtrOff;
        c.gnameAdd = gnameAdd;
        c.guobjectPtrOff = guobjectPtrOff;
        c.gnativePtrOff = gnativePtrOff;
        c.levelOff = levelOff;
        c.actorsOff = actorsOff;
        c.rootOff = rootOff;
        c.locationOff = locationOff;
        c.healthOff = healthOff;
        c.teamOff = teamOff;
        c.namePtrOff = namePtrOff;
        c.headHeight = headHeight;
        c.maxDistance = maxDistance;
        return c;
    }
}
