package com.qrassistant.urlcheck;

import com.qrassistant.urlcheck.checks.IpAddressCheck;
import com.qrassistant.urlcheck.checks.PrivateNetworkCheck;
import com.qrassistant.urlcheck.checks.SchemeCheck;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Finds out where a web address APPEARS to lead, by following its redirects one at a time.
 *
 * The ONLY class in the app that uses the network, and it runs ONLY when the user clicks
 * "Check where this goes". (Security rule 5)
 *
 * Safety rules:
 *  - We follow redirects ourselves (the HTTP client's own following is OFF), so EVERY hop is
 *    checked before we connect: http/https only, no disguised IPs, no local-network addresses.
 *  - The name is looked up in DNS and every address it gives must be public, BEFORE connecting.
 *    Honest limit: the HTTP client looks the name up AGAIN when it connects. A hostile DNS server
 *    could give a public address the first time and a local one the second ("DNS rebinding").
 *    This check makes that harder, it doesn't make it impossible.
 *  - At most MAX_REDIRECTS redirects, a timeout per request, no cookies, nothing downloaded:
 *    we read the status and the Location header, then close the connection.
 *
 * Nothing about the address is ever logged. (X8)
 */
public class RedirectResolver {

    private static final Logger LOG = Logger.getLogger(RedirectResolver.class.getName());

    public static final int MAX_REDIRECTS = 5;
    static final Duration TIMEOUT = Duration.ofSeconds(4);

    private static final Set<Integer> REDIRECT_CODES = Set.of(301, 302, 303, 307, 308);

    /** The three checks every hop must pass. They're offline, like all URL checks. */
    private static final List<UrlCheck> HOP_CHECKS =
            List.of(new SchemeCheck(), new IpAddressCheck(), new PrivateNetworkCheck());

    /** The two things we need from the network. An interface, so tests can use a fake network. */
    public interface Network {

        /** DNS lookup: every address the name points to. */
        List<InetAddress> lookUp(String host) throws UnknownHostException;

        /** ONE request, never following redirects. */
        HopResponse fetch(URI uri) throws IOException, InterruptedException;
    }

    /** @param location the Location header, or null if there was none */
    public record HopResponse(int status, String location) {
    }

    private final Network network;

    public RedirectResolver() {
        this(new RealNetwork());
    }

    /** For tests. */
    public RedirectResolver(Network network) {
        this.network = network;
    }

    /** Follows the redirects of a web address. Blocks; call it on the worker thread. Never throws. */
    public RedirectResult resolve(String url) {
        List<String> chain = new ArrayList<>(List.of(url));
        String current = url;

        while (true) {
            Optional<String> problem;
            try {
                problem = whyNotVisit(current);
            } catch (UnknownHostException e) {
                return new RedirectResult(chain, RedirectResult.Ending.NETWORK_ERROR,
                        "the site's name couldn't be found");
            }
            if (problem.isPresent()) {
                return new RedirectResult(chain, RedirectResult.Ending.STOPPED, problem.get());
            }

            HopResponse response;
            try {
                response = network.fetch(new URI(current));
            } catch (URISyntaxException e) {
                return new RedirectResult(chain, RedirectResult.Ending.STOPPED, "the address couldn't be read");
            } catch (IOException e) {
                LOG.info("Redirect check stopped: " + e.getClass().getName());
                return new RedirectResult(chain, RedirectResult.Ending.NETWORK_ERROR, describe(e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // the app is shutting down
                return new RedirectResult(chain, RedirectResult.Ending.NETWORK_ERROR, "the check was cancelled");
            }

            boolean redirected = REDIRECT_CODES.contains(response.status()) && response.location() != null;
            if (!redirected) {
                RedirectResult.Ending ending = chain.size() == 1
                        ? RedirectResult.Ending.NO_REDIRECT
                        : RedirectResult.Ending.ARRIVED;
                return new RedirectResult(chain, ending, "");
            }

            Optional<String> next = nextAddress(current, response.location());
            if (next.isEmpty()) {
                return new RedirectResult(chain, RedirectResult.Ending.STOPPED,
                        "the next address couldn't be read");
            }
            chain.add(next.get());
            if (chain.size() - 1 > MAX_REDIRECTS) {
                return new RedirectResult(chain, RedirectResult.Ending.TOO_MANY_REDIRECTS, "");
            }
            current = next.get();
        }
    }

    /**
     * The safety checks for one hop, BEFORE connecting.
     *
     * @return why we won't visit this address, or empty if it's fine to visit
     */
    private Optional<String> whyNotVisit(String url) throws UnknownHostException {
        Optional<ParsedUrl> parsed = ParsedUrl.parse(url);
        if (parsed.isEmpty()) {
            return Optional.of("it couldn't be read as a web address");
        }
        for (UrlCheck check : HOP_CHECKS) {
            for (Indicator indicator : check.check(parsed.get())) {
                // DANGER from any check, or ANY finding from the local-network check, stops us.
                if (indicator.level() == Indicator.Level.DANGER || check instanceof PrivateNetworkCheck) {
                    return Optional.of(indicator.message());
                }
            }
        }
        for (InetAddress address : network.lookUp(parsed.get().host())) {
            if (!isPublic(address)) {
                return Optional.of("its name leads to this computer or a local network address");
            }
        }
        return Optional.empty();
    }

    /** A Location header may be relative ("/login"), so it's resolved against the current address. */
    private static Optional<String> nextAddress(String current, String location) {
        try {
            return Optional.of(new URI(current).resolve(new URI(location.strip())).toString());
        } catch (URISyntaxException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Is this a normal internet address, not this computer, the local network or a special range? */
    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            long ip = ((b[0] & 255L) << 24) | ((b[1] & 255L) << 16) | ((b[2] & 255L) << 8) | (b[3] & 255L);
            return !PrivateNetworkCheck.isPrivateIpv4(ip);
        }
        return (b[0] & 0xFE) != 0xFC;   // IPv6 "unique local" fc00::/7: the IPv6 version of 192.168.x.x
    }

    /** Explains a network error by its TYPE only: the message could contain the address. (X8) */
    private static String describe(IOException e) {
        if (e instanceof HttpTimeoutException) {
            return "the site took too long to answer";
        }
        if (e instanceof SSLException) {
            return "the secure (HTTPS) connection failed";
        }
        if (e instanceof ConnectException) {
            return "the site couldn't be reached";
        }
        return "a network error happened (" + e.getClass().getSimpleName() + ")";
    }

    // --- The real network ---

    private static final class RealNetwork implements Network {

        /** No cookie handler is set, so no cookies are stored or sent. Redirects are NOT followed. */
        private final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(TIMEOUT)
                .build();

        @Override
        public List<InetAddress> lookUp(String host) throws UnknownHostException {
            return List.of(InetAddress.getAllByName(host));
        }

        @Override
        public HopResponse fetch(URI uri) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(TIMEOUT)
                    .header("User-Agent", "QR-Desktop-Assistant link check")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            // Closing the body without reading it: we only wanted the status and headers.
            try (InputStream body = response.body()) {
                return new HopResponse(response.statusCode(), response.headers().firstValue("location").orElse(null));
            }
        }
    }
}
