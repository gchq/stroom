package stroom.pipeline.xsltfunctions;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

class EncodingUtil {

    static String encodeUrl(final String string) {
        return URLEncoder.encode(string, StandardCharsets.UTF_8);
    }

    static String decodeUrl(final String string) {
        return URLDecoder.decode(string, StandardCharsets.UTF_8);
    }
}

