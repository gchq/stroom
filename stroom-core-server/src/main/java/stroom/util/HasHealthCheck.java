package stroom.util;

import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public interface HasHealthCheck {

    Logger LOGGER = LoggerFactory.getLogger(HasHealthCheck.class);

    HealthCheck.Result getHealth();

    default HealthCheck getHealthCheck() {
        return new HealthCheck() {
            @Override
            protected Result check() throws Exception {
                return getHealth();
            }
        };
    }

    /**
     * Converts a java bean into a nested HashMap. Useful for dumping a bean
     * as detail in a health check.
     */
    static Map<String, Object> beanToMap(final Object object) {

        // far from the most efficient way to do this but sufficient for a rarely used
        // health check page
        final String json = JsonUtil.writeValueAsString(object);

        LOGGER.debug("json\n{}", json);

        Map<String, Object> map;

        try {
            map = JsonUtil.getMapper().readValue(json, new TypeReference<Map<String, Object>>(){});
        } catch (IOException e) {
            final String msg = LambdaLogger.buildMessage("Unable to convert object {} of type {}",
                    object, object.getClass().getName());
            LOGGER.error(msg, e);
            map = new HashMap<>();
            map.put("ERROR", msg + " due to: " + e.getMessage());
        }
        return map;
    }
}