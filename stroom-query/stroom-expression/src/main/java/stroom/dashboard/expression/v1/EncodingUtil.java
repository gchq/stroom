package stroom.dashboard.expression.v1;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

class EncodingUtil {
    static String encodeUrl(final String string) {
        try {
            return URLEncoder.encode(string, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException ex) {
            throw new RuntimeException("Unable to find UTF-8 Character encoding", ex);
        }
    }

    static String decodeUrl(final String string) {
        try {
            return URLDecoder.decode(string, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException ex) {
            throw new RuntimeException("Unable to find UTF-8 Character encoding", ex);
        }
    }
}
