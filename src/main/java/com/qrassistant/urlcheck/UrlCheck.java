package com.qrassistant.urlcheck;

import java.util.List;

/**
 * ONE small check on a URL. Each check is its own tiny class, so it can be read,
 * tested and changed on its own.
 *
 * RULES: no network access, ever (X6). Return an empty list if there's nothing to say.
 */
public interface UrlCheck {

    List<Indicator> check(ParsedUrl url);
}