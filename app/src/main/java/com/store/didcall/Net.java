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
}
