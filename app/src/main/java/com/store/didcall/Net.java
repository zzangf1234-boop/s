package com.store.didcall;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** 공통 네트워크 상수와 도우미 */
final class Net {
    static final int HTTP_PORT = 8765;       // 호출기(호스트)가 요청을 받는 포트
    static final int DISCOVERY_PORT = 8766;  // TV가 호출기를 자동으로 찾는 포트
    static final String DISCOVER_MSG = "DIDCALL_DISCOVER";
    static final String DISCOVER_REPLY = "DIDCALL_HOST";

    private Net() {}

    /** 와이파이/유선 랜의 IPv4 주소. 모바일 데이터·VPN 주소는 제외 */
    static String localIp() {
        InterfaceAddress a = lanAddress();
        return a == null ? "0.0.0.0" : a.getAddress().getHostAddress();
    }

    /** 같은 공유기 네트워크의 주소 정보 (주소 + 서브넷 길이) */
    static InterfaceAddress lanAddress() {
        InterfaceAddress best = null;
        int bestScore = -1;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String name = ni.getName().toLowerCase();
                // 모바일 데이터, VPN, 터널 등은 TV와 같은 네트워크가 아님
                if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp")
                        || name.startsWith("tun") || name.startsWith("ppp") || name.startsWith("dummy")
                        || name.startsWith("v4-") || name.startsWith("clat") || name.startsWith("ipsec")
                        || name.startsWith("docker") || name.startsWith("veth")) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress ad = ia.getAddress();
                    if (!(ad instanceof Inet4Address) || !ad.isSiteLocalAddress()) continue;
                    int score = 1;
                    if (name.startsWith("wlan") || name.startsWith("eth") || name.startsWith("en")) score = 3;
                    else if (name.startsWith("ap") || name.startsWith("swlan")) score = 2; // 핫스팟
                    if (score > bestScore) { best = ia; bestScore = score; }
                }
            }
        } catch (Exception ignored) {
        }
        return best;
    }

    /** 호출 번호로 허용할 문자: 숫자/영문/하이픈, 최대 6자 */
    static boolean validNumber(String n) {
        return n != null && n.matches("[0-9A-Za-z\\-]{1,6}");
    }

    /** 연속 호출 최대 개수 */
    static final int MAX_GROUP = 10;

    /** "1~4" → [1,2,3,4] (숫자만, 앞이 뒤보다 작거나 같고 최대 10개). 연속 호출이 아니면 null */
    static java.util.List<String> group(String s) {
        if (s == null || !s.matches("[0-9]{1,6}~[0-9]{1,6}")) return null;
        String[] p = s.split("~");
        long a = Long.parseLong(p[0]), b = Long.parseLong(p[1]);
        if (a > b || b - a + 1 > MAX_GROUP) return null;
        java.util.List<String> out = new java.util.ArrayList<>();
        for (long i = a; i <= b; i++) out.add(String.valueOf(i));
        return out;
    }

    /** 한 번호 또는 연속 호출 "1~4" */
    static boolean validCall(String s) {
        return validNumber(s) || (s != null && s.matches("[0-9]{1,6}~[0-9]{1,6}") && group(s) != null);
    }

    /** 호출에 들어있는 번호들 (한 번호면 [n]) */
    static java.util.List<String> members(String s) {
        java.util.List<String> g = group(s);
        if (g != null) return g;
        java.util.List<String> one = new java.util.ArrayList<>();
        if (s != null && !s.isEmpty()) one.add(s);
        return one;
    }

    /** 안내 문구의 {n} 자리에 넣을 말: "1~4" → "1번 2번 3번 4" (문구의 "{n}번" 과 이어져 "…4번") */
    static String spoken(String s) {
        java.util.List<String> g = group(s);
        if (g == null) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < g.size(); i++) {
            if (i > 0) b.append("번 ");
            b.append(g.get(i));
        }
        return b.toString();
    }
}
