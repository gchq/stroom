package stroom.util;

import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HealthCheckUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckUtils.class);

    public static String validateHttpConnection(final String httpMethod, final String urlStr) {
        Preconditions.checkNotNull(httpMethod, "Missing a httpMethod, e.g. GET");
        Preconditions.checkNotNull(urlStr, "Missing a url");

        try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
            final URI uri;
            try {
                uri = URI.create(urlStr);
            } catch (final Exception e) {
                return LogUtil.message("Malformed URL: [{}]", e.getMessage());
            }
            final HttpUriRequestBase httpPost = new HttpUriRequestBase(httpMethod, uri);

            try {
                final int responseCode = httpClient.execute(httpPost, HttpResponse::getCode);
                return String.valueOf(responseCode);
            } catch (final IOException e) {
                return LogUtil.message("Unable to get response code: [{}]", e.getMessage());
            }

        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Converts a java bean into a nested HashMap. Useful for dumping a bean
     * as detail in a health check.
     */
    public static Map<String, Object> beanToMap(final Object object) {

        Map<String, Object> map;
        if (object != null) {
            // far from the most efficient way to do this but sufficient for a rarely used
            // health check page
            final String json = JsonUtil.writeValueAsString(object);

            LOGGER.debug("json\n{}", json);


            try {
                map = JsonUtil.getMapper().readValue(json, new TypeReference<Map<String, Object>>() {
                });
            } catch (final IOException e) {
                final String msg = LogUtil.message("Unable to convert object {} of type {}",
                        object, object.getClass().getName());
                LOGGER.error(msg, e);
                map = new HashMap<>();
                map.put("ERROR", msg + " due to: " + e.getMessage());
            }
        } else {
            map = Collections.emptyMap();
        }
        return map;
    }

    /**
     * Replaces any values with '***' if the key is a string and contains 'password' or 'apikey' or 'token'
     */
    public static void maskPasswords(final Map<String, Object> map) {
        map.forEach((key, value) -> {
            if (value instanceof String) {
                if (key.toLowerCase().contains("password")) {
                    LOGGER.debug("Masking entry with key {}", key);
                    map.put(key, "****");
                } else if (key.toLowerCase().contains("apikey") || key.toLowerCase().contains("token")) {
                    LOGGER.debug("Masking entry with key {}", key);
                    final String oldValue = (String) value;
                    if (oldValue.length() <= 8) {
                        map.put(key, "****");
                    } else {
                        final String newValue = oldValue.substring(0, 4)
                                                + "****"
                                                + oldValue.substring(oldValue.length() - 4);
                        map.put(key, newValue);
                    }
                }
            } else if (value instanceof Map) {
                maskPasswords((Map<String, Object>) value);
            } else if (value instanceof List) {
                for (final Object item : (List<Object>) value) {
                    if (item instanceof Map) {
                        maskPasswords((Map<String, Object>) item);
                    }
                }
            }
        });
    }
}
