package com.qrassistant.urlcheck.checks;

import com.qrassistant.urlcheck.Indicator;
import com.qrassistant.urlcheck.Indicator.Level;
import com.qrassistant.urlcheck.ParsedUrl;
import com.qrassistant.urlcheck.UrlCheck;

import java.util.List;

/**
 * The "@" trick (U8): in https://google.com@evil.com everything before the '@' is just a
 * "username". The site you actually visit is evil.com.
 */
public class UserInfoCheck implements UrlCheck {

    @Override
    public List<Indicator> check(ParsedUrl url) {
        if (url.userInfo().isEmpty()) {
            return List.of();
        }
        return List.of(new Indicator(Level.DANGER,
                "The address has a hidden \"name@\" part. The site you would really visit is "
                        + url.hostUnicode() + ", not what appears before the @."));
    }
}