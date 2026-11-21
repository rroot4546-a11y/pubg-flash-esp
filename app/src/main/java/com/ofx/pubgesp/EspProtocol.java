package com.ofx.pubgesp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Binary protocol shared with native/esphelper.c. */
public final class EspProtocol {
    public static final int CONFIG_SIZE = 140;
    public static final int HEADER_SIZE = 82;
    public static final int ENTRY_SIZE = 64;
    public static final int MAX_ACTORS = 512;

    private EspProtocol() {}

    public static byte[] packConfig(OffsetProfile p) {
        ByteBuffer b = ByteBuffer.allocate(CONFIG_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        b.putLong(p.worldPtrOff);
        b.putLong(p.worldDerefDelta);
        b.putLong(p.worldFinalAdd);
        b.putInt(p.worldMode);
        b.putLong(p.viewPtrOff);
        b.putLong(p.viewAdd);
        b.putLong(p.gnamePtrOff);
        b.putLong(p.gnameAdd);
        b.putLong(p.guobjectPtrOff);
        b.putLong(p.gnativePtrOff);
        b.putLong(p.levelOff);
        b.putLong(p.actorsOff);
        b.putLong(p.rootOff);
        b.putLong(p.locationOff);
        b.putLong(p.healthOff);
        b.putLong(p.teamOff);
        b.putLong(p.namePtrOff);
        b.putFloat(p.headHeight);
        b.putInt(0);
        return b.array();
    }

    public static final class Entry {
        public float x, y, z, headZ, health;
        public int teamId;
        public boolean bot;
        public String name;
    }

    public static final class Frame {
        public int status;
        public float[] view = new float[16];
        public Entry[] entries = new Entry[0];
    }

    public static Frame parseFrame(byte[] data) {
        Frame frame = new Frame();
        if (data == null || data.length < HEADER_SIZE) {
            frame.status = 99;
            return frame;
        }
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (b.get() != 'F') { frame.status = 98; return frame; }
        frame.status = b.get() & 0xFF;
        int count = b.getInt();
        b.getFloat(); b.getFloat(); b.getFloat();
        for (int i = 0; i < 16; i++) frame.view[i] = b.getFloat();

        if (count < 0) count = 0;
        if (count > MAX_ACTORS) count = MAX_ACTORS;
        if (data.length < HEADER_SIZE + count * ENTRY_SIZE) count = 0;

        frame.entries = new Entry[count];
        for (int i = 0; i < count; i++) {
            int base = HEADER_SIZE + i * ENTRY_SIZE;
            ByteBuffer e = ByteBuffer.wrap(data, base, ENTRY_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            Entry entry = new Entry();
            entry.x = e.getFloat();
            entry.y = e.getFloat();
            entry.z = e.getFloat();
            entry.headZ = e.getFloat();
            entry.health = e.getFloat();
            entry.teamId = e.getInt();
            entry.bot = (e.get() & 0xFF) != 0;
            byte[] nameBytes = new byte[32];
            e.get(nameBytes);
            entry.name = new String(nameBytes, java.nio.charset.StandardCharsets.US_ASCII).trim();
            frame.entries[i] = entry;
        }
        return frame;
    }

    /** Returns null when the point is behind the camera; otherwise screen x/y and depth. */
    public static float[] worldToScreen(float[] m, float x, float y, float z,
                                        int screenW, int screenH) {
        float w = m[3] * x + m[7] * y + m[11] * z + m[15];
        if (w <= 0.01f) return null;
        float sx = m[0] * x + m[4] * y + m[8] * z + m[12];
        float sy = m[1] * x + m[5] * y + m[9] * z + m[13];
        return new float[]{
                screenW * 0.5f + screenW * 0.5f * sx / w,
                screenH * 0.5f - screenH * 0.5f * sy / w,
                w
        };
    }

    public static float distance(float[] view, float x, float y, float z) {
        float dx = x - view[12], dy = y - view[13], dz = z - view[14];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / 100f;
    }
}
