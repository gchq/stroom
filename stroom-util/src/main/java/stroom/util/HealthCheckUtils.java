package stroom.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

public class HealthCheckUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckUtils.class);

    public static String validateHttpConnection(final String httpMethod, final String urlStr) {
        Preconditions.checkNotNull(httpMethod, "Missing a httpMethod, e.g. GET");
        Preconditions.checkNotNull(urlStr, "Missing a url");

        URL url = null;
        try {
            url = new URL(urlStr);
        } catch (MalformedURLException e) {
            return LogUtil.message("Malformed URL: [{}]", e.getMessage());
        }

        URLConnection connection = null;
        try {
            connection = url.openConnection();
        } catch (IOException e) {
            return LogUtil.message("Invalid URL: [{}]", e.getMessage());
        }

        if (connection instanceof HttpURLConnection) {
            HttpURLConnection http = (HttpURLConnection) connection;
            try {
                http.setRequestMethod(httpMethod);
            } catch (ProtocolException e) {
                return LogUtil.message("Invalid protocol during test: [{}]", e.getMessage());
            }
            http.setDoOutput(true);

            try {
                http.connect();
            } catch (IOException e) {
                return LogUtil.message("Unable to connect: [{}]", e.getMessage());
            }

            try {
                int responseCode = http.getResponseCode();
                return String.valueOf(responseCode);
            } catch (IOException e) {
                return LogUtil.message("Unable to get response code: [{}]", e.getMessage());
            }
        } else {
            return LogUtil.message("Unknown connection type: [{}]",
                    connection.getClass().getName());
        }
    }

    /**
     * Converts a java bean into a nested HashMap. Useful for dumping a bean
     * as detail in a health check.
     */
    public static Map<String, Object> beanToMap(final Object object) {

        // far from the most efficient way to do this but sufficient for a rarely used
        // health check page
        final String json = JsonUtil.writeValueAsString(object);

        LOGGER.debug("json\n{}", json);

        Map<String, Object> map;

        try {
            map = JsonUtil.getMapper().readValue(json, new TypeReference<Map<String, Object>>(){});
        } catch (IOException e) {
            final String msg = LogUtil.message("Unable to convert object {} of type {}",
                    object, object.getClass().getName());
            LOGGER.error(msg, e);
            map = new HashMap<>();
            map.put("ERROR", msg + " due to: " + e.getMessage());
        }
        return map;
    }
}
