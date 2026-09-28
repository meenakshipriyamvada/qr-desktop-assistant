package com.qrassistant.urlcheck.checks;

import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.Indicator.Level;
import com.qrassistant.urlcheck.Ipv4Parser;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlCheck;

import java.util.List;
import java.util.OptionalLong;

/**
 * Does the URL point at THIS computer or the local network (a router, a printer, a college server)?
 * A QR code has no business sending you there. (U6)
 *
 * Only what's visible in the text is checked. A normal-looking name that secretly leads to a local
 * address can only be found with a DNS lookup - which this offline check deliberately never does.
 */
public class PrivateNetworkCheck implements UrlCheck {

    private static final Indicator LOCAL = new Indicator(Level.WARN,
            "Points to this computer or a device on the local network (like a router), not a public website.");

    @Override
    public List<Indicator> check(ParsedUrl url) {
        String host = url.host();
        if (url.ipv6Literal()) {
            return isPrivateIpv6(host) ? List.of(LOCAL) : List.of();
        }
        String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (name.equals("localhost") || name.endsWith(".localhost") || name.endsWith(".local")) {
            return List.of(LOCAL);
        }
        OptionalLong ipv4 = Ipv4Parser.parse(host);
        if (ipv4.isPresent() && isPrivateIpv4(ipv4.getAsLong())) {
            return List.of(LOCAL);
        }
        return List.of();
    }

    /** The address ranges reserved for "this computer" and private networks. */
    static boolean isPrivateIpv4(long ip) {
        return inRange(ip, "0.0.0.0", 8)          // "this network"
                || inRange(ip, "10.0.0.0", 8)     // private
                || inRange(ip, "100.64.0.0", 10)  // shared by internet providers (carrier-grade NAT)
                || inRange(ip, "127.0.0.0", 8)    // this computer (loopback)
                || inRange(ip, "169.254.0.0", 16) // link-local (also used by cloud "metadata" services)
                || inRange(ip, "172.16.0.0", 12)  // private
                || inRange(ip, "192.168.0.0", 16);// private (most home routers)
    }

    /** true if ip is inside network/prefixLength, e.g. 192.168.1.1 inside 192.168.0.0/16. */
    private static boolean inRange(long ip, String network, int prefixLength) {
        long base = Ipv4Parser.parse(network).orElseThrow();
        long mask = (0xFFFFFFFFL << (32 - prefixLength)) & 0xFFFFFFFFL;
        return (ip & mask) == base;
    }

    /** Simplified IPv6 check: loopback, unspecified, private (fc00::/7), link-local (fe80::/10), mapped IPv4. */
    static boolean isPrivateIpv6(String host) {
        String h = host.toLowerCase();
        if (h.equals("::1") || h.equals("::")) {
            return true;
        }
        if (h.startsWith("fc") || h.startsWith("fd")) {
            return true;
        }
        if (h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb")) {
            return true;
        }
        if (h.startsWith("::ffff:")) {   // an IPv4 address written as IPv6, e.g. ::ffff:192.168.1.1
            OptionalLong ipv4 = Ipv4Parser.parse(h.substring("::ffff:".length()));
            return ipv4.isPresent() && isPrivateIpv4(ipv4.getAsLong());
        }
        return false;
    }
}