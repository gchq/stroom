package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public record FeedKey(String feed, String type) {

    public static final String DELIMITER = "=";

    public String encodeKey() {
        return encode(feed) + DELIMITER + encode(type);
    }

    public static FeedKey decodeKey(final String string) {
        String feed = null;
        String type = null;

        final String[] parts = string.split(DELIMITER);
        if (parts.length > 0) {
            feed = decode(parts[0]);
        }
        if (parts.length > 1) {
            type = decode(parts[1]);
        }
        return new FeedKey(feed, type);
    }

    public static FeedKey from(final AttributeMap attributeMap) {
        Objects.requireNonNull(attributeMap);
        final String feed = attributeMap.get(StandardHeaderArguments.FEED);
        final String type = attributeMap.get(StandardHeaderArguments.TYPE);
        return new FeedKey(feed, type);
    }

    private static String encode(final String string) {
        if (string == null) {
            return "";
        }
        return URLEncoder.encode(string, StandardCharsets.UTF_8);
    }

    private static String decode(final String string) {
        if (string == null) {
            return null;
        }
        return URLDecoder.decode(string, StandardCharsets.UTF_8);
    }
}
