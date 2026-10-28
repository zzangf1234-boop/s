package com.store.didcall;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

/** 공통 네트워크 상수와 도우미 */
final class Net {
    static final int HTTP_PORT = 8765;       // TV가 호출을 받는 포트
    static final int DISCOVERY_PORT = 8766;  // 공기계가 TV를 자동으로 찾는 포트
    static final String DISCOVER_MSG = "DIDCALL_DISCOVER";
    static final String DISCOVER_REPLY = "DIDCALL_TV";

    private Net() {}

    /** 이 기기의 내부망 IPv4 주소 (와이파이/유선 모두) */
    static String localIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && a.isSiteLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "0.0.0.0";
    }

    /** 호출 번호로 허용할 문자: 숫자/영문/하이픈, 최대 6자 */
    static boolean validNumber(String n) {
        return n != null && n.matches("[0-9A-Za-z\\-]{1,6}");
    }
}
