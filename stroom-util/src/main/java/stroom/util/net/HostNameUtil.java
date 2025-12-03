/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
            } catch (final UnknownHostException e) {
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
