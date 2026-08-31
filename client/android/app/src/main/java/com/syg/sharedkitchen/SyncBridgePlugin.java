package com.syg.sharedkitchen;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 设备直连同步桥（去中心化，无服务器参与）：
 * - localIps: 本机局域网 IPv4 列表
 * - startAnnounce/stopAnnounce: UDP 广播本机存在（类 LocalSend 发现），收到他人广播回抛 peerFound
 * - listen/stopListen: TCP 服务端，收一行 JSON 回抛 request 事件，JS 用 respond 回一行
 * - request: TCP 客户端，发一行收一行
 * 消息均为单行 JSON（换行分隔），传输量小（操作日志），一连接一问一答足够。
 */
@CapacitorPlugin(name = "SyncBridge")
public class SyncBridgePlugin extends Plugin {

    public static final int UDP_PORT = 51821;
    public static final int TCP_PORT = 51820;

    private final AtomicBoolean announcing = new AtomicBoolean(false);
    private Thread announceThread;
    private DatagramSocket announceSocket;

    private final AtomicBoolean listening = new AtomicBoolean(false);
    private Thread listenThread;
    private ServerSocket serverSocket;
    private final java.util.concurrent.ConcurrentHashMap<String, Socket> openSockets =
            new java.util.concurrent.ConcurrentHashMap<>();

    private volatile String selfDeviceId = "";

    // ---------- 本机地址 / 设备信息 ----------

    @PluginMethod
    public void localIps(PluginCall call) {
        try {
            List<String> ips = new ArrayList<>();
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress addr = ia.getAddress();
                    if (addr.isSiteLocalAddress() && addr.getHostAddress().contains(".")) {
                        String ip = addr.getHostAddress();
                        if (!ips.contains(ip)) ips.add(ip);
                    }
                }
            }
            JSObject ret = new JSObject();
            ret.put("ips", com.getcapacitor.JSArray.from(ips.toArray()));
            ret.put("model", android.os.Build.MODEL);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("枚举网卡失败: " + e.getMessage());
        }
    }

    // ---------- UDP 广播发现 ----------

    @PluginMethod
    public void startAnnounce(PluginCall call) {
        String deviceId = call.getString("deviceId", "");
        String deviceName = call.getString("deviceName", "");
        String kitchenId = call.getString("kitchenId", "");
        if (deviceId.isEmpty()) {
            call.reject("deviceId 必填");
            return;
        }
        selfDeviceId = deviceId;
        if (announcing.compareAndSet(false, true)) {
            announceThread = new Thread(() -> announceLoop(deviceId, deviceName, kitchenId));
            announceThread.setDaemon(true);
            announceThread.start();
        }
        call.resolve();
    }

    @PluginMethod
    public void stopAnnounce(PluginCall call) {
        announcing.set(false);
        if (announceSocket != null) announceSocket.close();
        call.resolve();
    }

    private void announceLoop(String deviceId, String deviceName, String kitchenId) {
        try {
            announceSocket = new DatagramSocket();
            announceSocket.setBroadcast(true);
            announceSocket.setReuseAddress(true);
            byte[] buf = ("{\"type\":\"announce\",\"deviceId\":\"" + deviceId
                    + "\",\"deviceName\":\"" + deviceName
                    + "\",\"kitchenId\":\"" + kitchenId
                    + "\",\"tcp\":" + TCP_PORT + "}").getBytes();
            while (announcing.get()) {
                try {
                    List<InetAddress> targets = new ArrayList<>();
                    targets.add(InetAddress.getByName("255.255.255.255"));
                    Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
                    while (nis.hasMoreElements()) {
                        NetworkInterface ni = nis.nextElement();
                        if (!ni.isUp() || ni.isLoopback()) continue;
                        for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                            InetAddress bcast = ia.getBroadcast();
                            if (bcast != null && !targets.contains(bcast)) targets.add(bcast);
                        }
                    }
                    for (InetAddress target : targets) {
                        try {
                            announceSocket.send(new DatagramPacket(buf, buf.length, target, UDP_PORT));
                        } catch (Exception ignore) { /* 单个广播目标失败忽略 */ }
                    }
                    // 收别人的广播 → 回抛 peerFound
                    byte[] rx = new byte[2048];
                    DatagramPacket pkt = new DatagramPacket(rx, rx.length);
                    try {
                        announceSocket.setSoTimeout(2000);
                        announceSocket.receive(pkt);
                        String line = new String(pkt.getData(), 0, pkt.getLength()).trim();
                        JSObject peer = parsePeer(line, pkt.getAddress().getHostAddress());
                        if (peer != null) notifyListeners("peerFound", peer);
                    } catch (SocketTimeoutException ignore) { /* 2 秒无广播正常 */ }
                } catch (Exception e) {
                    if (!announcing.get()) break;
                    try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
                }
            }
        } catch (Exception ignore) { /* socket 建立失败由 UI 兜底提示 */ }
    }

    /** 解析他人广播；过滤自己（deviceId 相同）。 */
    private JSObject parsePeer(String line, String ip) {
        try {
            if (!line.contains("\"announce\"")) return null;
            org.json.JSONObject o = new org.json.JSONObject(line);
            String peerId = o.optString("deviceId", "");
            if (peerId.isEmpty() || peerId.equals(selfDeviceId)) return null;
            JSObject peer = new JSObject();
            peer.put("deviceId", peerId);
            peer.put("deviceName", o.optString("deviceName", "未知设备"));
            peer.put("kitchenId", o.optString("kitchenId", ""));
            peer.put("ip", ip);
            peer.put("tcp", o.optInt("tcp", TCP_PORT));
            return peer;
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- TCP 服务端 ----------

    @PluginMethod
    public void listen(PluginCall call) {
        if (listening.get()) {
            call.resolve();
            return;
        }
        listening.set(true);
        listenThread = new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                while (listening.get()) {
                    Socket sock = serverSocket.accept();
                    String requestId = UUID.randomUUID().toString();
                    openSockets.put(requestId, sock);
                    Thread handler = new Thread(() -> readOne(requestId, sock));
                    handler.setDaemon(true);
                    handler.start();
                }
            } catch (Exception e) {
                if (listening.get()) {
                    listening.set(false);
                    notifyListeners("listenError", new JSObject());
                }
            }
        });
        listenThread.setDaemon(true);
        listenThread.start();
        call.resolve();
    }

    /** 读一行（\n 结尾）→ 回抛 request 事件，等 JS respond。 */
    private void readOne(String requestId, Socket sock) {
        try {
            sock.setSoTimeout(15000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String line = reader.readLine();
            if (line != null) {
                JSObject data = new JSObject();
                data.put("requestId", requestId);
                data.put("ip", sock.getInetAddress().getHostAddress());
                data.put("line", line);
                notifyListeners("request", data);
            } else {
                closeQuietly(requestId);
            }
        } catch (Exception e) {
            closeQuietly(requestId);
        }
    }

    @PluginMethod
    public void respond(PluginCall call) {
        String requestId = call.getString("requestId", "");
        String line = call.getString("line", "");
        Socket sock = openSockets.remove(requestId);
        if (sock == null) {
            call.reject("连接已关闭");
            return;
        }
        Thread t = new Thread(() -> {
            try {
                OutputStream out = sock.getOutputStream();
                out.write((line + "\n").getBytes());
                out.flush();
                closeQuietly(requestId);
                call.resolve();
            } catch (Exception e) {
                closeQuietly(requestId);
                call.reject("回写失败: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @PluginMethod
    public void stopListen(PluginCall call) {
        listening.set(false);
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignore) { /* 已关 */ }
        call.resolve();
    }

    // ---------- TCP 客户端 ----------

    @PluginMethod
    public void request(PluginCall call) {
        String ip = call.getString("ip", "");
        int port = call.getInt("port", TCP_PORT);
        String line = call.getString("line", "");
        int timeoutMs = call.getInt("timeoutMs", 8000);
        Thread t = new Thread(() -> {
            try (Socket sock = new Socket()) {
                sock.connect(new java.net.InetSocketAddress(ip, port), 4000);
                sock.setSoTimeout(timeoutMs);
                OutputStream out = sock.getOutputStream();
                out.write((line + "\n").getBytes());
                out.flush();
                BufferedReader reader = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                String reply = reader.readLine();
                if (reply == null) {
                    call.reject("对端无响应");
                } else {
                    JSObject ret = new JSObject();
                    ret.put("line", reply);
                    call.resolve(ret);
                }
            } catch (Exception e) {
                call.reject("连接失败: " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void closeQuietly(String requestId) {
        Socket s = openSockets.remove(requestId);
        if (s != null) {
            try { s.close(); } catch (Exception ignore) { /* 已关 */ }
        }
    }
}
