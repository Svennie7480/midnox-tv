package de.midnox.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.io.*;
import java.math.BigInteger;
import java.net.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.*;
import javax.crypto.Cipher;

/**
 * Minimaler ADB-Client zum eigenen Geraet (127.0.0.1:5555), um Tastendruecke zu senden.
 * Beim ersten Mal fragt der Fire TV "USB-Debugging zulassen?" – einmal mit "Immer zulassen" bestaetigen.
 */
public class Adb {
    static final int CNXN = 0x4e584e43, AUTH = 0x48545541, OPEN = 0x4e45504f, OKAY = 0x59414b4f, CLSE = 0x45534c43, WRTE = 0x45545257;
    static Adb inst;
    static volatile String status = "";

    Socket s; DataInputStream in; OutputStream out;
    int nextId = 1;
    final KeyPair keys;

    Adb(KeyPair k) { keys = k; }

    static synchronized Adb get(Context c) throws Exception {
        if (inst != null && inst.s != null && !inst.s.isClosed()) return inst;
        Adb a = new Adb(keys(c));
        a.connect();
        inst = a;
        return a;
    }

    /** Befehl in der Geraete-Shell ausfuehren (z. B. "input keyevent 23"). */
    static synchronized String shell(Context c, String cmd) throws Exception {
        try { return get(c).run(cmd); }
        catch (Exception e) { close(); return get(c).run(cmd); }   // einmal neu verbinden
    }
    static synchronized void close() { if (inst != null) try { inst.s.close(); } catch (Exception ignored) {} inst = null; }

    // ------------------------------------------------------------ Schluessel
    static KeyPair keys(Context c) throws Exception {
        SharedPreferences pr = Style.prefs(c);
        String pub = pr.getString("adb_pub", ""), priv = pr.getString("adb_priv", "");
        KeyFactory kf = KeyFactory.getInstance("RSA");
        if (!pub.isEmpty() && !priv.isEmpty()) {
            return new KeyPair(kf.generatePublic(new X509EncodedKeySpec(Base64.decode(pub, Base64.NO_WRAP))),
                    kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(priv, Base64.NO_WRAP))));
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair k = g.generateKeyPair();
        pr.edit().putString("adb_pub", Base64.encodeToString(k.getPublic().getEncoded(), Base64.NO_WRAP))
                 .putString("adb_priv", Base64.encodeToString(k.getPrivate().getEncoded(), Base64.NO_WRAP)).apply();
        return k;
    }

    /** Oeffentlicher Schluessel im ADB-Format (android_pubkey_encode) + Name. */
    static byte[] adbPublicKey(RSAPublicKey k) throws Exception {
        BigInteger n = k.getModulus(), r32 = BigInteger.ONE.shiftLeft(32);
        int words = 64;   // 2048 Bit
        BigInteger n0inv = r32.subtract(n.mod(r32).modInverse(r32));
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(524).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putInt(words);
        b.putInt(n0inv.intValue());
        b.put(le(n, 256));
        b.put(le(rr, 256));
        b.putInt(k.getPublicExponent().intValue());
        String s = Base64.encodeToString(b.array(), Base64.NO_WRAP) + " midnox@firetv";
        byte[] t = s.getBytes("UTF-8"), r = new byte[t.length + 1];
        System.arraycopy(t, 0, r, 0, t.length);
        return r;
    }
    static byte[] le(BigInteger v, int len) {
        byte[] be = v.toByteArray(), out = new byte[len];
        for (int i = 0; i < len && i < be.length; i++) out[i] = be[be.length - 1 - i];
        return out;
    }

    // ------------------------------------------------------------ Protokoll
    void send(int cmd, int a0, int a1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length, sum = 0;
        if (data != null) for (byte x : data) sum += x & 0xFF;
        java.nio.ByteBuffer b = java.nio.ByteBuffer.allocate(24 + len).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.putInt(cmd).putInt(a0).putInt(a1).putInt(len).putInt(sum).putInt(cmd ^ 0xFFFFFFFF);
        if (data != null) b.put(data);
        out.write(b.array()); out.flush();
    }
    int[] head = new int[4]; byte[] payload;
    int read() throws IOException {
        byte[] h = new byte[24];
        in.readFully(h);
        java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(h).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int cmd = b.getInt(); head[0] = cmd; head[1] = b.getInt(); head[2] = b.getInt(); int len = b.getInt();
        payload = new byte[len];
        in.readFully(payload);
        return cmd;
    }

    void connect() throws Exception {
        s = new Socket();
        s.connect(new InetSocketAddress("127.0.0.1", 5555), 3000);
        s.setSoTimeout(5000);
        in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        out = s.getOutputStream();
        send(CNXN, 0x01000000, 4096, "host::midnox\0".getBytes("UTF-8"));
        boolean sentSig = false, sentKey = false;
        while (true) {
            int cmd;
            try { cmd = read(); }
            catch (SocketTimeoutException e) { status = "Bitte am Fernseher „USB-Debugging zulassen“ bestätigen"; throw e; }
            if (cmd == CNXN) { status = "verbunden"; s.setSoTimeout(15000); return; }
            if (cmd != AUTH) continue;
            if (!sentSig) {   // Token signieren (SHA1-Kennung + Token, PKCS#1)
                byte[] prefix = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};
                byte[] msg = new byte[prefix.length + payload.length];
                System.arraycopy(prefix, 0, msg, 0, prefix.length);
                System.arraycopy(payload, 0, msg, prefix.length, payload.length);
                Cipher ci = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                ci.init(Cipher.ENCRYPT_MODE, keys.getPrivate());
                send(AUTH, 2, 0, ci.doFinal(msg));
                sentSig = true;
            } else if (!sentKey) {   // Schluessel unbekannt -> anbieten, Fire TV fragt einmal nach
                send(AUTH, 3, 0, adbPublicKey((RSAPublicKey) keys.getPublic()));
                sentKey = true;
                status = "Bitte am Fernseher „USB-Debugging zulassen“ bestätigen";
                s.setSoTimeout(60000);
            } else throw new IOException("Zugriff abgelehnt");
        }
    }

    String run(String cmd) throws IOException {
        int local = nextId++;
        send(OPEN, local, 0, ("shell:" + cmd + "\0").getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        int remote = 0;
        while (true) {
            int c = read();
            if (head[2] != local) continue;
            if (c == OKAY) remote = head[1];
            else if (c == WRTE) { sb.append(new String(payload, "UTF-8")); send(OKAY, local, head[1], null); }
            else if (c == CLSE) { send(CLSE, local, head[1] == 0 ? remote : head[1], null); return sb.toString(); }
        }
    }
}
