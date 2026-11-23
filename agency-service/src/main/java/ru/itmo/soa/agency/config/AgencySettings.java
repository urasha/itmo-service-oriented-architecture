package ru.itmo.soa.agency.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties("agency")
public record AgencySettings(
        URI flatsPublicBaseUrl, Duration connectTimeout, Duration requestTimeout, String allowedOrigin) {
    public AgencySettings {
        https(flatsPublicBaseUrl.toString());
        allowedOrigin = allowedOrigin(allowedOrigin);
    }

    private static String allowedOrigin(String origin) {
        URI originUri = https(origin);
        if (!(originUri.getRawPath().isEmpty() || originUri.getRawPath().equals("/"))) {
            throw new IllegalArgumentException("CORS must be an HTTPS origin without a path");
        }
        return originUri.getScheme() + "://" + originUri.getRawAuthority();
    }

    private static URI https(String value) {
        URI uri = URI.create(value);
        if (!"https".equals(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Expected an absolute HTTPS URL without credentials, query or fragment");
        }
        return uri;
    }

    public static URI flatUri(URI base, long id) {
        String value = base.toASCIIString();
        return URI.create(value.replaceAll("/+$", "") + "/flats/" + id);
    }
}
