package com.longoipo.rc4;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * In-app bridge that lets a core without rc4-md5 talk to rc4-md5 Shadowsocks servers.
 *
 * {@link #rewrite(String)} takes the core's JSON config and, for every Shadowsocks outbound whose
 * method is rc4-md5, points it at a loopback listener with method "none". The listener speaks plain
 * Shadowsocks to the core and rc4-md5 Shadowsocks to the real server, over TCP and UDP. Both sides
 * use the same address header, so the bridge only adds/removes the IV and runs RC4.
 */
public final class Rc4Bridge {
    private static final String TAG = "[Rc4Bridge] ";
    private static final int MAX_UDP_FLOWS = 1024;
    private static final long UDP_IDLE_MS = 60_000L;

    private static final Map<String, Rc4Bridge> BRIDGES = new HashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ExecutorService POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "rc4bridge");
            t.setDaemon(true);
            return t;
        }
    });

    private final String host;
    private final int port;
    private final byte[] masterKey;
    private ServerSocket tcp;
    private DatagramSocket udp;
    private final ConcurrentHashMap<SocketAddress, Flow> flows = new ConcurrentHashMap<>();

    private Rc4Bridge(String host, int port, String password) {
        this.host = host;
        this.port = port;
        this.masterKey = Rc4Md5.deriveKey(password.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ config rewrite

    /** Returns the config with rc4-md5 Shadowsocks outbounds redirected to local bridges. */
    public static String rewrite(String json) {
        if (json == null || json.toLowerCase(Locale.ROOT).indexOf("rc4-md5") < 0) return json;
        try {
            JSONObject root = new JSONObject(json);
            JSONArray outbounds = root.optJSONArray("outbounds");
            if (outbounds == null) return json;
            boolean changed = false;
            for (int i = 0; i < outbounds.length(); i++) {
                JSONObject ob = outbounds.optJSONObject(i);
                if (ob == null || !"shadowsocks".equalsIgnoreCase(ob.optString("protocol"))) continue;
                JSONObject settings = ob.optJSONObject("settings");
                JSONArray servers = settings == null ? null : settings.optJSONArray("servers");
                if (servers == null) continue;
                for (int k = 0; k < servers.length(); k++) {
                    JSONObject server = servers.optJSONObject(k);
                    if (server == null || !"rc4-md5".equalsIgnoreCase(server.optString("method"))) continue;
                    if (!isPlainTcp(ob.optJSONObject("streamSettings"))) {
                        warn("outbound '" + ob.optString("tag") + "' uses non-plain transport; left unchanged");
                        continue;
                    }
                    String host = server.optString("address");
                    int port = server.optInt("port", -1);
                    if (host.isEmpty() || port < 1 || port > 65535) continue;
                    Rc4Bridge b = open(host, port, server.optString("password"));
                    server.put("address", "127.0.0.1");
                    server.put("port", b.getPort());
                    server.put("method", "none");
                    changed = true;
                }
            }
            return changed ? root.toString() : json;
        } catch (Throwable t) {
            warn("rewrite failed, config left unchanged: " + t);
            return json;
        }
    }

    private static boolean isPlainTcp(JSONObject ss) {
        if (ss == null) return true;
        String net = ss.optString("network", "tcp");
        if (!(net.isEmpty() || net.equalsIgnoreCase("tcp") || net.equalsIgnoreCase("raw"))) return false;
        String sec = ss.optString("security", "none");
        if (!(sec.isEmpty() || sec.equalsIgnoreCase("none"))) return false;
        JSONObject tcpSettings = ss.optJSONObject("tcpSettings");
        if (tcpSettings != null) {
            JSONObject header = tcpSettings.optJSONObject("header");
            if (header != null) {
                String type = header.optString("type", "none");
                if (!(type.isEmpty() || type.equalsIgnoreCase("none"))) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ bridge lifecycle

    /** Starts (or reuses) the bridge for one upstream server. */
    public static synchronized Rc4Bridge open(String host, int port, String password) {
        String id = host + "|" + port + "|" + password;
        Rc4Bridge b = BRIDGES.get(id);
        if (b == null || !b.isAlive()) {
            b = new Rc4Bridge(host, port, password);
            b.start();
            BRIDGES.put(id, b);
        }
        return b;
    }

    public int getPort() {
        return tcp.getLocalPort();
    }

    private boolean isAlive() {
        return tcp != null && !tcp.isClosed();
    }

    private void start() {
        try {
            InetAddress lo = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            for (int attempt = 0; attempt < 20 && udp == null; attempt++) {
                tcp = new ServerSocket(0, 64, lo);
                try {
                    udp = new DatagramSocket(new InetSocketAddress(lo, tcp.getLocalPort()));
                } catch (SocketException e) {
                    tcp.close();
                    tcp = null;
                }
            }
            if (tcp == null) {
                tcp = new ServerSocket(0, 64, lo);
                warn("no UDP port available next to TCP; UDP disabled for " + host + ":" + port);
            }
        } catch (IOException e) {
            throw new IllegalStateException("cannot bind loopback listener", e);
        }
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                acceptLoop();
            }
        });
        if (udp != null) {
            POOL.execute(new Runnable() {
                @Override
                public void run() {
                    udpLoop();
                }
            });
        }
        warn("bridge 127.0.0.1:" + getPort() + " -> " + host + ":" + port + " (rc4-md5)");
    }

    // ------------------------------------------------------------------ TCP

    private void acceptLoop() {
        while (!tcp.isClosed()) {
            try {
                final Socket c = tcp.accept();
                POOL.execute(new Runnable() {
                    @Override
                    public void run() {
                        handleTcp(c);
                    }
                });
            } catch (IOException e) {
                if (!tcp.isClosed()) warn("accept failed: " + e);
            }
        }
    }

    private void handleTcp(final Socket c) {
        Socket up = new Socket();
        try {
            c.setTcpNoDelay(true);
            up.connect(new InetSocketAddress(host, port), 10_000);
            up.setTcpNoDelay(true);
            final Socket fu = up;
            Future<?> upstream = POOL.submit(new Runnable() {
                @Override
                public void run() {
                    pumpToServer(c, fu);
                }
            });
            pumpToClient(c, up);
            upstream.get(60, TimeUnit.SECONDS);
        } catch (Throwable t) {
            // connection failed or was reset; fall through and close both sides
        } finally {
            closeQuietly(c);
            closeQuietly(up);
        }
    }

    /** client (plain) -> server (IV + RC4). */
    private void pumpToServer(Socket c, Socket up) {
        try {
            InputStream in = c.getInputStream();
            OutputStream out = up.getOutputStream();
            byte[] iv = new byte[Rc4Md5.IV_LEN];
            RANDOM.nextBytes(iv);
            Rc4Md5.Rc4 rc4 = Rc4Md5.stream(masterKey, iv, 0);
            byte[] buf = new byte[Rc4Md5.IV_LEN + 16384];
            System.arraycopy(iv, 0, buf, 0, Rc4Md5.IV_LEN);
            boolean first = true;
            int n;
            while ((n = in.read(buf, Rc4Md5.IV_LEN, 16384)) >= 0) {
                rc4.crypt(buf, Rc4Md5.IV_LEN, n);
                if (first) {
                    out.write(buf, 0, Rc4Md5.IV_LEN + n);
                    first = false;
                } else {
                    out.write(buf, Rc4Md5.IV_LEN, n);
                }
            }
            up.shutdownOutput();
        } catch (IOException e) {
            closeQuietly(c);
            closeQuietly(up);
        }
    }

    /** server (IV + RC4) -> client (plain). */
    private void pumpToClient(Socket c, Socket up) {
        try {
            InputStream in = up.getInputStream();
            OutputStream out = c.getOutputStream();
            byte[] iv = new byte[Rc4Md5.IV_LEN];
            if (!readFully(in, iv)) return;
            Rc4Md5.Rc4 rc4 = Rc4Md5.stream(masterKey, iv, 0);
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) >= 0) {
                rc4.crypt(buf, 0, n);
                out.write(buf, 0, n);
            }
            c.shutdownOutput();
        } catch (IOException e) {
            closeQuietly(c);
            closeQuietly(up);
        }
    }

    private static boolean readFully(InputStream in, byte[] dst) throws IOException {
        int off = 0;
        while (off < dst.length) {
            int n = in.read(dst, off, dst.length - off);
            if (n < 0) return false;
            off += n;
        }
        return true;
    }

    // ------------------------------------------------------------------ UDP

    private void udpLoop() {
        byte[] buf = new byte[65535];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        while (!udp.isClosed()) {
            try {
                p.setLength(buf.length);
                udp.receive(p);
                SocketAddress src = p.getSocketAddress();
                Flow f = flows.get(src);
                if (f == null) {
                    if (flows.size() >= MAX_UDP_FLOWS) continue;
                    f = new Flow(src);
                    flows.put(src, f);
                    final Flow nf = f;
                    POOL.execute(new Runnable() {
                        @Override
                        public void run() {
                            nf.readLoop();
                        }
                    });
                }
                f.sendToServer(buf, p.getLength());
            } catch (IOException e) {
                if (!udp.isClosed()) warn("udp receive failed: " + e);
            }
        }
    }

    /** One client UDP source; owns the upstream socket for that source. */
    private final class Flow {
        private final SocketAddress client;
        private final DatagramSocket up;
        private volatile long lastUsed = System.currentTimeMillis();

        Flow(SocketAddress client) throws IOException {
            this.client = client;
            this.up = new DatagramSocket();
            this.up.connect(new InetSocketAddress(host, port));
            this.up.setSoTimeout(1000);
        }

        void sendToServer(byte[] plain, int len) throws IOException {
            lastUsed = System.currentTimeMillis();
            byte[] out = new byte[Rc4Md5.IV_LEN + len];
            byte[] iv = new byte[Rc4Md5.IV_LEN];
            RANDOM.nextBytes(iv);
            System.arraycopy(iv, 0, out, 0, Rc4Md5.IV_LEN);
            System.arraycopy(plain, 0, out, Rc4Md5.IV_LEN, len);
            Rc4Md5.stream(masterKey, iv, 0).crypt(out, Rc4Md5.IV_LEN, len);
            up.send(new DatagramPacket(out, out.length));
        }

        void readLoop() {
            byte[] buf = new byte[65535];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                while (!up.isClosed() && System.currentTimeMillis() - lastUsed < UDP_IDLE_MS) {
                    try {
                        p.setLength(buf.length);
                        up.receive(p);
                    } catch (SocketTimeoutException e) {
                        continue;
                    }
                    int n = p.getLength() - Rc4Md5.IV_LEN;
                    if (n <= 0) continue;
                    lastUsed = System.currentTimeMillis();
                    Rc4Md5.stream(masterKey, buf, 0).crypt(buf, Rc4Md5.IV_LEN, n);
                    udp.send(new DatagramPacket(buf, Rc4Md5.IV_LEN, n, client));
                }
            } catch (IOException e) {
                // socket closed or send failed; end the flow
            } finally {
                up.close();
                flows.remove(client, this);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void closeQuietly(Socket s) {
        try {
            if (s != null) s.close();
        } catch (IOException ignored) {
            // nothing to do
        }
    }

    private static void warn(String msg) {
        System.err.println(TAG + msg);
    }
}
