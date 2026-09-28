package com.qrassistant.urlcheck;

import com.qrassistant.urlcheck.checks.HostShapeCheck;
import com.qrassistant.urlcheck.checks.InternationalDomainCheck;
import com.qrassistant.urlcheck.checks.IpAddressCheck;
import com.qrassistant.urlcheck.checks.LengthCheck;
import com.qrassistant.urlcheck.checks.PortCheck;
import com.qrassistant.urlcheck.checks.PrivateNetworkCheck;
import com.qrassistant.urlcheck.checks.SchemeCheck;
import com.qrassistant.urlcheck.checks.SensitiveParamCheck;
import com.qrassistant.urlcheck.checks.ShortLinkCheck;
import com.qrassistant.urlcheck.checks.UserInfoCheck;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Runs every URL check and collects the findings into a UrlReport.
 *
 * 100% offline: nothing here contacts the internet or does DNS lookups. (X6)
 * The only class allowed to use the network is RedirectResolver (step 12), and only on request.
 */
public class UrlSafetyChecker {

    private static final Logger LOG = Logger.getLogger(UrlSafetyChecker.class.getName());

    private final List<UrlCheck> checks;

    public UrlSafetyChecker() {
        this(List.of(
                new SchemeCheck(),
                new UserInfoCheck(),
                new IpAddressCheck(),
                new PrivateNetworkCheck(),
                new InternationalDomainCheck(),
                new HostShapeCheck(),
                new ShortLinkCheck(),
                new PortCheck(),
                new SensitiveParamCheck(),
                new LengthCheck()
        ));
    }

    /** For tests, or to try a different set of checks. */
    public UrlSafetyChecker(List<UrlCheck> checks) {
        this.checks = List.copyOf(checks);
    }

    public UrlReport check(String url) {
        Optional<ParsedUrl> parsed = ParsedUrl.parse(url);
        if (parsed.isEmpty()) {
            return new UrlReport(url, Optional.empty(), List.of(new Indicator(Indicator.Level.DANGER,
                    "This couldn't be read as a normal web address, so it can't be opened - only copied.")));
        }

        List<Indicator> found = new ArrayList<>();
        for (UrlCheck check : checks) {
            try {
                found.addAll(check.check(parsed.get()));
            } catch (RuntimeException e) {
                // A bug in one check must not hide the others. Type only - never the URL. (X8)
                LOG.warning(check.getClass().getSimpleName() + " failed: " + e.getClass().getName());
            }
        }
        found.sort(Comparator.comparing(Indicator::level));   // DANGER first, OK last
        return new UrlReport(url, parsed, found);
    }
}