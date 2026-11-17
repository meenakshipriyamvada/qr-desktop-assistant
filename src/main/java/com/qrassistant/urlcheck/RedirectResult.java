package com.qrassistant.urlcheck;

import java.util.List;

/**
 * What the redirect check saw. Facts only, never a verdict: a site can send the app one way
 * and a browser another, so this is what it APPEARED to do, once. (X2)
 *
 * @param chain  the addresses in order: [0] is the QR's own address, then each redirect target.
 *               The last one is where the check ended (or the one it refused to visit). UNTRUSTED.
 * @param ending why the check stopped
 * @param reason for STOPPED / NETWORK_ERROR: a short explanation in the app's own words.
 *               May quote an address part, so show it through DisplaySanitizer. Empty otherwise.
 */
public record RedirectResult(List<String> chain, Ending ending, String reason) {

    public enum Ending {
        NO_REDIRECT,         // the first address answered without redirecting
        ARRIVED,             // followed one or more redirects to an address that didn't redirect again
        TOO_MANY_REDIRECTS,  // gave up after the maximum number of redirects
        STOPPED,             // refused to visit an address (not http/https, local network, ...)
        NETWORK_ERROR        // couldn't connect, timed out, name not found, ...
    }

    public RedirectResult {
        chain = List.copyOf(chain);
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("chain must start with the original address");
        }
    }

    /** The last address seen. */
    public String last() {
        return chain.get(chain.size() - 1);
    }

    /** How many redirects were seen. */
    public int redirects() {
        return chain.size() - 1;
    }
}
