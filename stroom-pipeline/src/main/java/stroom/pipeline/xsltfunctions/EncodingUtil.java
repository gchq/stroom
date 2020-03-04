package stroom.pipeline.xsltfunctions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

class EncodingUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(EncodingUtil.class);
    private static final String CHARSET = StandardCharsets.UTF_8.name();

    static String encodeUrl(final String string) {
        try {
            // TODO : @66 After Java10 use of charset constants is supported.
            return URLEncoder.encode(string, CHARSET);
        } catch (final UnsupportedEncodingException e) {
            LOGGER.debug(e.getMessage(), e);
            return string;
        }
    }

    static String decodeUrl(final String string) {
        try {
            // TODO : @66 After Java10 use of charset constants is supported.
            return URLDecoder.decode(string, CHARSET);
        } catch (final UnsupportedEncodingException e) {
            LOGGER.debug(e.getMessage(), e);
            return string;
        }
    }
}

