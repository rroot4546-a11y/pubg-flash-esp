package com.ofx.pubgesp;

import android.content.Context;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Launches the root helper and speaks its framed protocol over an AF_UNIX socket. */
public final class RootHelperClient {
    private static final String SOCKET_PATH = "/data/local/tmp/ofx_esp.sock";
    private static final String HELPER = "esphelper64";

    private final Context context;
    private Process su;
    private LocalSocket socket;
    private InputStream in;
    private OutputStream out;
    private boolean ready;

    public RootHelperClient(Context context) {
        this.context = context;
    }

    public boolean isReady() {
        return ready;
    }

    public synchronized boolean start(String packageName, OffsetProfile profile) {
        stop();
        try {
            File binary = new File(context.getFilesDir(), HELPER);
            try (InputStream src = context.getAssets().open(HELPER);
                 FileOutputStream dst = new FileOutputStream(binary)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = src.read(buf)) > 0) dst.write(buf, 0, n);
            }
            if (!binary.setExecutable(true)) return false;

            ProcessBuilder pb = new ProcessBuilder("su", "-c",
                    binary.getAbsolutePath() + " " + packageName + " >/dev/null 2>&1");
            File devNull = new File("/dev/null");
            pb.redirectOutput(ProcessBuilder.Redirect.to(devNull));
            pb.redirectError(ProcessBuilder.Redirect.to(devNull));
            su = pb.start();

            File sock = new File(SOCKET_PATH);
            long deadline = System.currentTimeMillis() + 6000;
            while (!sock.exists() && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            if (!sock.exists()) return false;

            socket = new LocalSocket();
            socket.connect(new LocalSocketAddress(SOCKET_PATH,
                    LocalSocketAddress.Namespace.FILESYSTEM));
            socket.setSoTimeout(3000);
            in = socket.getInputStream();
            out = socket.getOutputStream();

            out.write('H');
            out.flush();
            byte[] hello = new byte[14];
            readFully(hello);
            if (hello[0] != 'H') return false;

            out.write('C');
            out.write(EspProtocol.packConfig(profile));
            out.flush();
            if (readByte() != 0) return false;

            ready = true;
            return true;
        } catch (Exception e) {
            stop();
            return false;
        }
    }

    public synchronized EspProtocol.Frame frame() {
        if (!ready) return null;
        try {
            out.write('F');
            out.flush();
            byte[] header = new byte[EspProtocol.HEADER_SIZE];
            readFully(header);
            int count = java.nio.ByteBuffer.wrap(header, 5, 4)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
            if (count < 0 || count > EspProtocol.MAX_ACTORS) count = 0;
            byte[] body = new byte[count * EspProtocol.ENTRY_SIZE];
            if (body.length > 0) readFully(body);
            byte[] packet = new byte[header.length + body.length];
            System.arraycopy(header, 0, packet, 0, header.length);
            System.arraycopy(body, 0, packet, header.length, body.length);
            return EspProtocol.parseFrame(packet);
        } catch (Exception e) {
            ready = false;
            return null;
        }
    }

    public synchronized boolean dump(long address, int length) {
        if (!ready) return false;
        try {
            out.write('D');
            java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(12)
                    .order(java.nio.ByteOrder.LITTLE_ENDIAN);
            b.putLong(address).putInt(length);
            out.write(b.array());
            out.flush();
            return readByte() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void stop() {
        ready = false;
        if (out != null) {
            try { out.write('X'); out.flush(); } catch (IOException ignored) {}
        }
        closeQuietly(socket);
        socket = null;
        in = null;
        out = null;
        if (su != null) {
            su.destroy();
            try { su.waitFor(1, TimeUnit.SECONDS); } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            su = null;
        }
        new File(SOCKET_PATH).delete();
    }

    private int readByte() throws IOException {
        return readFullyOne()[0] & 0xFF;
    }

    private byte[] readFullyOne() throws IOException {
        byte[] one = new byte[1];
        readFully(one);
        return one;
    }

    private void readFully(byte[] dst) throws IOException {
        int off = 0;
        while (off < dst.length) {
            int n = in.read(dst, off, dst.length - off);
            if (n < 0) throw new IOException("helper closed connection");
            off += n;
        }
    }

    private static void closeQuietly(LocalSocket s) {
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
    }

    public static boolean rootAvailable() {
        try {
            Process p = new ProcessBuilder("su", "-c", "id -u")
                    .redirectErrorStream(true).start();
            byte[] buf = new byte[16];
            int n = p.getInputStream().read(buf);
            p.waitFor(2, TimeUnit.SECONDS);
            return n > 0 && new String(buf, 0, n, StandardCharsets.US_ASCII).trim().equals("0");
        } catch (Exception e) {
            return false;
        }
    }
}
