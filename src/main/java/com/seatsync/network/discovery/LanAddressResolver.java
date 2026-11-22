package com.seatsync.network.discovery;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/**
 * Utility to identify and advertise a reachable LAN interface IP for SeatSync UDP discovery.
 * <p>
 * Ensures that 0.0.0.0 is never advertised over discovery, and prioritizes
 * RFC 1918 site-local addresses (10.x, 172.16-31.x, 192.168.x) over public or loopback addresses.
 */
public final class LanAddressResolver {

    private LanAddressResolver() {
        // utility class
    }

    /**
     * Resolves the most appropriate reachable IPv4 address to advertise.
     *
     * @param manualIp Optional user-configured override; if non-null and not blank, this is returned.
     * @return Reachable IPv4 string (never 0.0.0.0; falls back to 127.0.0.1 if no LAN interface exists).
     */
    public static String resolveAdvertisedIp(String manualIp) {
        if (manualIp != null && !manualIp.isBlank() && !"0.0.0.0".equals(manualIp.trim())) {
            return manualIp.trim();
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface nif = interfaces.nextElement();
                    if (!nif.isUp() || nif.isLoopback() || nif.isVirtual() || nif.isPointToPoint()) {
                        continue;
                    }

                    Enumeration<InetAddress> addresses = nif.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof Inet4Address inet4 && !addr.isLoopbackAddress() && !addr.isLinkLocalAddress()) {
                            if (inet4.isSiteLocalAddress()) {
                                return inet4.getHostAddress();
                            }
                        }
                    }
                }
            }

            // Fallback: check getLocalHost
            InetAddress localHost = InetAddress.getLocalHost();
            if (localHost instanceof Inet4Address && !localHost.isLoopbackAddress() && !localHost.isAnyLocalAddress()) {
                return localHost.getHostAddress();
            }
        } catch (Exception ignored) {
        }

        // Final safe localhost fallback
        return "127.0.0.1";
    }
}
