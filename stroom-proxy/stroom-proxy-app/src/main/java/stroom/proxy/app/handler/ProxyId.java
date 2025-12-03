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

package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.util.io.HomeDirProvider;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

@Singleton
public class ProxyId {

    private static final String ALLOWED_CHARS = "A-Za-z0-9-";
    public static final String PROXY_ID_REGEX = "^[" + ALLOWED_CHARS + "]+$";
    public static final Pattern PROXY_ID_PATTERN = Pattern.compile(PROXY_ID_REGEX);
    private static final String UNSAFE_CHARS_REGEX = "[^" + ALLOWED_CHARS + "]";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyId.class);
    private static final String PROXY_ID_FILE = "proxy-id.txt";
    private static final String PROXY_ID_PREFIX = "Proxy-";

    private final HomeDirProvider homeDirProvider;
    private final String id;

    @Inject
    public ProxyId(final ProxyConfig proxyConfig,
                   final HomeDirProvider homeDirProvider) {
        this.homeDirProvider = homeDirProvider;

        final String source;
        final String proxyId = createSafeString(proxyConfig.getProxyId());
        if (NullSafe.isBlankString(proxyId)) {
            LOGGER.warn("No proxy id is configured. " +
                        "Unless this is a test environment or the only proxy in the environment, you are " +
                        "recommended to set '{}' in config as it will be used in all receipt IDs returned by " +
                        "this proxy.", ProxyConfig.PROP_NAME_PROXY_ID);
            final String storedId = readProxyId();
            if (NullSafe.isBlankString(storedId) || !PROXY_ID_PATTERN.matcher(storedId).matches()) {
                final String createdId = createUuidBasedProxyId();
                writeProxyId(createdId);
                LOGGER.info("No or invalid stored proxy ID '{}' found in '{}', created and stored new proxy id: {}",
                        storedId, PROXY_ID_FILE, createdId);
                id = createdId;
                source = "generated";
            } else {
                LOGGER.info("Retrieved stored proxy id: " + storedId);
                id = storedId;
                source = getProxyIdFilePath().toString();
            }
        } else {
            LOGGER.info("Using configured proxy id: " + proxyId);
            id = proxyId;
            source = "config";
        }

        if (!PROXY_ID_PATTERN.matcher(id).matches()) {
            throw new RuntimeException(LogUtil.message("Proxy ID '{}' (source: {}), does not match pattern '{}'",
                    id, source, PROXY_ID_REGEX));
        }
    }

    public String getId() {
        return id;
    }

    private Path getProxyIdFilePath() {
        return homeDirProvider.get()
                .resolve(PROXY_ID_FILE);
    }

    private String readProxyId() {
        String storedId = null;
        final Path path = getProxyIdFilePath();
        if (Files.exists(path)) {
            try {
                storedId = Files.readString(path).trim();
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return storedId;
    }

    private void writeProxyId(final String storedId) {
        final Path path = getProxyIdFilePath();
        try {
            Files.writeString(path, storedId);
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private String createUuidBasedProxyId() {
        return PROXY_ID_PREFIX + UUID.randomUUID();
    }

    static String createSafeString(final String in) {
        if (in == null) {
            return null;
        } else {
            return in.replaceAll(UNSAFE_CHARS_REGEX, "-");
        }
    }
}
