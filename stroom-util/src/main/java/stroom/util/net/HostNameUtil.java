package stroom.util.net;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class HostNameUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(HostNameUtil.class);

    private static volatile String cachedHostName;

    private HostNameUtil() {
    }

    public static String determineHostName() {
        // Doesn't really matter if this gets done by multiple threads that all see it as null
        if (cachedHostName == null) {
            // If running in a docker container then these env vars can be set so
            // the container knows who its host is

            String hostName;
            try {
                hostName = getEnvVar("DOCKER_HOST_HOSTNAME");

                if (hostName == null || hostName.isEmpty()) {
                    hostName = getEnvVar("DOCKER_HOST_IP");
                }

                if (hostName == null || hostName.isEmpty()) {
                    hostName = InetAddress.getLocalHost().getHostName();
                }

                if (hostName == null || hostName.isEmpty()) {
                    hostName = "Unknown";
                }
            } catch (UnknownHostException e) {
                LOGGER.error("Unable to determine hostname, using 'Unknown'", e);
                hostName = "Unknown";
            }

            LOGGER.info("Determined hostname to be {}. Caching value until app restart.", hostName);
            cachedHostName = hostName;
        }
        return cachedHostName;
    }

    private static String getEnvVar(final String envVarName) {
        final String value = System.getenv(envVarName);

        LOGGER.debug("{}: {}", envVarName, value);

        return value;
    }
}
