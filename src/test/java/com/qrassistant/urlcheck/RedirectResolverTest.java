package com.qrassistant.urlcheck;

import com.qrassistant.urlcheck.RedirectResolver.HopResponse;
import com.qrassistant.urlcheck.RedirectResult.Ending;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The redirect check, against a FAKE network: no real requests are made.
 * The fake remembers which addresses were fetched, so we can prove unsafe ones never were.
 */
class RedirectResolverTest {

    /** A pretend internet: host -> IP, address -> answer. */
    private static final class FakeNetwork implements RedirectResolver.Network {
        final Map<String, byte[]> dns = new HashMap<>();
        final Map<String, HopResponse> answers = new HashMap<>();
        final List<String> fetched = new ArrayList<>();
        IOException failWith;

        FakeNetwork host(String name, int a, int b, int c, int d) {
            dns.put(name, new byte[]{(byte) a, (byte) b, (byte) c, (byte) d});
            return this;
        }

        FakeNetwork redirect(String from, String to) {
            answers.put(from, new HopResponse(302, to));
            return this;
        }

        @Override
        public List<InetAddress> lookUp(String host) throws UnknownHostException {
            byte[] ip = dns.get(host);
            if (ip == null) {
                throw new UnknownHostException();
            }
            return List.of(InetAddress.getByAddress(host, ip));   // getByAddress never uses DNS
        }

        @Override
        public HopResponse fetch(URI uri) throws IOException {
            fetched.add(uri.toString());
            if (failWith != null) {
                throw failWith;
            }
            return answers.getOrDefault(uri.toString(), new HopResponse(200, null));
        }
    }

    private final FakeNetwork net = new FakeNetwork()
            .host("short.example", 93, 184, 216, 34)
            .host("middle.example", 93, 184, 216, 35)
            .host("final.example", 93, 184, 216, 36);
    private final RedirectResolver resolver = new RedirectResolver(net);

    @Test
    void noRedirect() {
        RedirectResult result = resolver.resolve("https://final.example/");
        assertEquals(Ending.NO_REDIRECT, result.ending());
        assertEquals(List.of("https://final.example/"), result.chain());
    }

    @Test
    void followsRedirectsIncludingRelativeOnes() {
        net.redirect("https://short.example/abc", "https://middle.example/go")
                .redirect("https://middle.example/go", "/landing?x=1");   // relative: same site
        RedirectResult result = resolver.resolve("https://short.example/abc");
        assertEquals(Ending.ARRIVED, result.ending());
        assertEquals(List.of("https://short.example/abc", "https://middle.example/go",
                "https://middle.example/landing?x=1"), result.chain());
    }

    @Test
    void neverVisitsLocalAddresses() {
        net.redirect("https://short.example/", "http://127.0.0.1:8080/admin");
        RedirectResult result = resolver.resolve("https://short.example/");
        assertEquals(Ending.STOPPED, result.ending());
        assertEquals("http://127.0.0.1:8080/admin", result.last());
        assertFalse(net.fetched.contains("http://127.0.0.1:8080/admin"));
    }

    @Test
    void neverVisitsNamesThatLeadToTheLocalNetwork() {
        // A normal-looking name whose DNS answer is a home-router address.
        net.host("router.example", 192, 168, 1, 1).redirect("https://short.example/", "https://router.example/");
        RedirectResult result = resolver.resolve("https://short.example/");
        assertEquals(Ending.STOPPED, result.ending());
        assertFalse(net.fetched.contains("https://router.example/"));
    }

    @Test
    void neverFollowsToOtherSchemes() {
        net.redirect("https://short.example/", "javascript:alert(1)");
        RedirectResult result = resolver.resolve("https://short.example/");
        assertEquals(Ending.STOPPED, result.ending());
        assertEquals(1, net.fetched.size());
    }

    @Test
    void neverFollowsToDisguisedIpAddresses() {
        net.redirect("https://short.example/", "http://0x7f.1/");   // 127.0.0.1 in disguise
        RedirectResult result = resolver.resolve("https://short.example/");
        assertEquals(Ending.STOPPED, result.ending());
        assertEquals(1, net.fetched.size());
    }

    @Test
    void stopsAfterTooManyRedirects() {
        net.redirect("https://short.example/", "https://middle.example/")
                .redirect("https://middle.example/", "https://short.example/");   // a loop
        RedirectResult result = resolver.resolve("https://short.example/");
        assertEquals(Ending.TOO_MANY_REDIRECTS, result.ending());
        assertEquals(RedirectResolver.MAX_REDIRECTS + 1, net.fetched.size());   // the original + 5 redirects
    }

    @Test
    void unknownNameIsANetworkError() {
        RedirectResult result = resolver.resolve("https://no-such-site.example/");
        assertEquals(Ending.NETWORK_ERROR, result.ending());
        assertTrue(net.fetched.isEmpty());
    }

    @Test
    void timeoutIsANetworkErrorWithoutTheAddressInTheReason() {
        net.failWith = new HttpTimeoutException("https://final.example/secret-token");
        RedirectResult result = resolver.resolve("https://final.example/secret-token");
        assertEquals(Ending.NETWORK_ERROR, result.ending());
        assertFalse(result.reason().contains("secret-token"));   // X8: the exception message is never used
    }

    @Test
    void redirectWithoutLocationCountsAsArrived() {
        net.answers.put("https://final.example/", new HopResponse(301, null));
        assertEquals(Ending.NO_REDIRECT, resolver.resolve("https://final.example/").ending());
    }

    @Test
    void publicAndPrivateAddresses() throws UnknownHostException {
        assertTrue(RedirectResolver.isPublic(InetAddress.getByName("8.8.8.8")));
        assertFalse(RedirectResolver.isPublic(InetAddress.getByName("127.0.0.1")));
        assertFalse(RedirectResolver.isPublic(InetAddress.getByName("10.1.2.3")));
        assertFalse(RedirectResolver.isPublic(InetAddress.getByName("100.64.0.1")));   // carrier-grade NAT
        assertFalse(RedirectResolver.isPublic(InetAddress.getByName("169.254.169.254")));   // cloud metadata
        assertFalse(RedirectResolver.isPublic(InetAddress.getByName("::1")));
        assertFalse(RedirectResolver.isPublic(InetAddress.getByName("fd00::1")));
        assertFalse(RedirectResolver.isPublic(InetAddress.getByName("fe80::1")));
        assertTrue(RedirectResolver.isPublic(InetAddress.getByName("2606:4700::1111")));
    }
}
