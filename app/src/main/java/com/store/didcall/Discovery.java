package com.store.didcall;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * TV 화면 기기가 같은 와이파이에서 호출기(호스트)를 찾는 기능.
 *  1) 지난번 주소 확인
 *  2) UDP 브로드캐스트 (빠름, 공유기에 따라 막힐 수 있음)
 *  3) 같은 네트워크 주소 전체를 직접 확인 (느리지만 확실)
 */
final class Discovery {

    interface Progress { void on(String message); }

    private Discovery() {}

    /** @return "ip:port" 또는 못 찾으면 null (네트워크 작업이므로 백그라운드 스레드에서 호출) */
    static String find(String last, Progress p) {
        if (last != null && !last.isEmpty()) {
            p.on("지난번 호출기 주소 확인 중… " + last);
            if (ping(last, 1500)) return last;
        }
        p.on("호출기를 찾는 중… (빠른 검색)");
        String h = broadcast();
        if (h != null && ping(h, 1500)) return h;

        InterfaceAddress ia = Net.lanAddress();
        if (ia == null) {
            p.on("와이파이(또는 랜)에 연결되어 있지 않습니다");
            return null;
        }
        p.on("호출기를 찾는 중… (네트워크 전체 검색, 최대 10초)");
        return scan(ia);
    }

    /** 호출기 앱의 서버가 맞는지 확인 */
    static boolean ping(String hostPort, int timeoutMs) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL("http://" + hostPort + "/ping").openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setUseCaches(false);
            if (c.getResponseCode() != 200) return false;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[64];
            int r;
            while ((r = in.read(b)) > 0 && bo.size() < 256) bo.write(b, 0, r);
            return bo.toString("UTF-8").startsWith("DIDCALL");
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String broadcast() {
        DatagramSocket sock = null;
        try {
            InterfaceAddress ia = Net.lanAddress();
            sock = new DatagramSocket(null);
            sock.setBroadcast(true);
            // 와이파이 주소에 묶어서 보내야 모바일 데이터 쪽으로 나가지 않음
            sock.bind(new InetSocketAddress(ia != null ? ia.getAddress() : null, 0));
            sock.setSoTimeout(700);
            byte[] msg = Net.DISCOVER_MSG.getBytes("UTF-8");
            List<InetAddress> targets = new ArrayList<>();
            if (ia != null && ia.getBroadcast() != null) targets.add(ia.getBroadcast());
            targets.add(InetAddress.getByName("255.255.255.255"));
            for (int attempt = 0; attempt < 3; attempt++) {
                for (InetAddress t : targets) {
                    try { sock.send(new DatagramPacket(msg, msg.length, t, Net.DISCOVERY_PORT)); } catch (Exception ignored) {}
                }
                try {
                    byte[] buf = new byte[256];
                    DatagramPacket pk = new DatagramPacket(buf, buf.length);
                    sock.receive(pk);
                    String reply = new String(pk.getData(), 0, pk.getLength(), "UTF-8");
                    if (reply.startsWith(Net.DISCOVER_REPLY)) {
                        String[] parts = reply.split("\\|");
                        String port = parts.length > 1 ? parts[1] : String.valueOf(Net.HTTP_PORT);
                        return pk.getAddress().getHostAddress() + ":" + port;
                    }
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (sock != null) sock.close();
        }
        return null;
    }

    /** 같은 네트워크(최대 /24 = 254개 주소)의 8765 포트를 동시에 확인 */
    private static String scan(InterfaceAddress ia) {
        byte[] me = ia.getAddress().getAddress();
        final String base = (me[0] & 0xFF) + "." + (me[1] & 0xFF) + "." + (me[2] & 0xFF) + ".";
        final int self = me[3] & 0xFF;
        List<Callable<String>> tasks = new ArrayList<>();
        // 공유기가 흔히 주는 주소(2~50, 100~150)부터 먼저 확인
        List<Integer> order = new ArrayList<>();
        for (int i = 2; i <= 50; i++) order.add(i);
        for (int i = 100; i <= 150; i++) order.add(i);
        for (int i = 1; i <= 254; i++) if (!order.contains(i)) order.add(i);
        for (final int i : order) {
            if (i == self) continue;
            tasks.add(() -> {
                String h = base + i + ":" + Net.HTTP_PORT;
                if (ping(h, 700)) return h;
                throw new Exception("no");
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(48);
        try {
            return pool.invokeAny(tasks, 12, TimeUnit.SECONDS);
        } catch (Exception e) {
            return null;
        } finally {
            pool.shutdownNow();
        }
    }
}
