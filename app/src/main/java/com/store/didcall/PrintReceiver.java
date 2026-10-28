package com.store.didcall;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * 네트워크 영수증 프린터 흉내 (raw TCP 9100).
 * 키오스크·포스의 분기 프로그램이 이 폰 주소:9100 으로 주문서를 보내면 받아서 해석하고,
 * '주방 프린터 주소'가 설정돼 있으면 받은 그대로 그 프린터로 넘겨줌(중계).
 */
final class PrintReceiver {

    static final int PORT = 9100;

    interface Listener {
        /** 주문서 한 장을 받음 (서버 스레드) */
        void onReceipt(byte[] raw, String from, String relayResult);
        /** 중계할 주방 프린터 주소 "ip:port" (없으면 null) */
        String relayTarget();
    }

    private final Listener listener;
    private volatile boolean running;
    private ServerSocket server;

    PrintReceiver(Listener listener) {
        this.listener = listener;
    }

    void start() {
        if (running) return;
        running = true;
        new Thread(this::loop, "didcall-9100").start();
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Exception ignored) {}
    }

    boolean isRunning() {
        return running;
    }

    private void loop() {
        while (running) {
            try {
                server = new ServerSocket();
                server.setReuseAddress(true);
                server.bind(new InetSocketAddress(PORT));
                while (running) {
                    final Socket s = server.accept();
                    new Thread(() -> handle(s), "didcall-print").start();
                }
            } catch (Exception e) {
                if (!running) return;
                try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            } finally {
                try { if (server != null) server.close(); } catch (Exception ignored) {}
            }
        }
    }

    private void handle(Socket s) {
        String from = s.getInetAddress().getHostAddress();
        Socket relay = null;
        String relayResult = null;
        try {
            s.setSoTimeout(1500);
            InputStream in = s.getInputStream();
            final OutputStream back = s.getOutputStream();

            // 주방 프린터로 중계
            String target = listener.relayTarget();
            OutputStream toPrinter = null;
            if (target != null && !target.isEmpty()) {
                try {
                    String host = target, port = "9100";
                    int c = target.lastIndexOf(':');
                    if (c > 0) { host = target.substring(0, c); port = target.substring(c + 1); }
                    relay = new Socket();
                    relay.connect(new InetSocketAddress(host, Integer.parseInt(port)), 3000);
                    toPrinter = relay.getOutputStream();
                    final InputStream fromPrinter = relay.getInputStream();
                    // 프린터의 답(상태 등)은 그대로 키오스크 쪽으로 돌려줌
                    new Thread(() -> {
                        byte[] b = new byte[512];
                        int r;
                        try { while ((r = fromPrinter.read(b)) > 0) back.write(b, 0, r); } catch (Exception ignored) {}
                    }, "didcall-relay-back").start();
                    relayResult = "ok";
                } catch (Exception e) {
                    relayResult = "주방 프린터(" + target + ")에 연결하지 못했습니다";
                    relay = null;
                    toPrinter = null;
                }
            }

            ByteArrayOutputStream job = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long idleSince = System.currentTimeMillis();
            while (running) {
                int r;
                try {
                    r = in.read(buf);
                } catch (SocketTimeoutException t) {
                    // 1.5초 동안 더 오는 게 없으면 한 장 끝
                    if (job.size() > 0) {
                        listener.onReceipt(job.toByteArray(), from, relayResult);
                        job.reset();
                    }
                    if (System.currentTimeMillis() - idleSince > 60_000) break; // 1분 넘게 조용하면 연결 종료
                    continue;
                }
                if (r < 0) break;
                idleSince = System.currentTimeMillis();
                job.write(buf, 0, r);
                if (toPrinter != null) {
                    try { toPrinter.write(buf, 0, r); toPrinter.flush(); }
                    catch (Exception e) { relayResult = "주방 프린터로 보내는 중 끊겼습니다"; toPrinter = null; }
                }
                // 중계하지 않을 때는 프린터 상태 물음(DLE EOT n)에 '정상'으로 답해 줌
                if (toPrinter == null) {
                    for (int i = 0; i + 2 < r; i++) {
                        if (buf[i] == 0x10 && buf[i + 1] == 0x04) back.write(buf[i + 2] == 1 ? 0x16 : 0x12);
                    }
                    back.flush();
                }
            }
            if (job.size() > 0) listener.onReceipt(job.toByteArray(), from, relayResult);
        } catch (Exception ignored) {
        } finally {
            try { s.close(); } catch (Exception ignored) {}
            try { if (relay != null) relay.close(); } catch (Exception ignored) {}
        }
    }
}
