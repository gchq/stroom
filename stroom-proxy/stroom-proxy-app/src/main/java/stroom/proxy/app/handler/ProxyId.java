package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.util.io.HomeDirProvider;
import stroom.util.net.HostNameUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProxyId {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyId.class);
    private static final String PROXY_ID_FILE = "proxy-id.txt";
    private static final String PROXY_ID = "ProxyId-";
    private static final String LOCALHOST = "localhost";

    private final HomeDirProvider homeDirProvider;
    private final String id;

    @Inject
    public ProxyId(final ProxyConfig proxyConfig,
                   final HomeDirProvider homeDirProvider) {
        this.homeDirProvider = homeDirProvider;

        final String proxyId = createSafeString(proxyConfig.getProxyId());
        if (proxyId == null || proxyId.isBlank()) {
            LOGGER.info("No proxy id is configured");
            final String storedId = readProxyId();
            if (storedId == null || storedId.isBlank()) {
                final String createdId = createProxyId();
                LOGGER.info("Created new proxy id: " + createdId);
                writeProxyId(createdId);
                id = createdId;
            } else {
                LOGGER.info("Retrieved stored proxy id: " + storedId);
                id = storedId;
            }
        } else {
            LOGGER.info("Using configured proxy id: " + proxyId);
            id = proxyId;
        }
    }

    public String getId() {
        return id;
    }

    private String readProxyId() {
        String storedId = null;
        final Path path = homeDirProvider.get().resolve(PROXY_ID_FILE);
        if (Files.exists(path)) {
            try {
                storedId = Files.readString(path);
            } catch (final IOException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        return storedId;
    }

    private void writeProxyId(final String storedId) {
        final Path path = homeDirProvider.get().resolve(PROXY_ID_FILE);
        try {
            Files.writeString(path, storedId);
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    private String createProxyId() {
        final String hostName = HostNameUtil.determineHostName();
        if (hostName != null && !hostName.isBlank()) {
            final String safe = createSafeString(hostName);
            if (!LOCALHOST.equalsIgnoreCase(safe) && Character.isAlphabetic(safe.charAt(0))) {
                return PROXY_ID + safe;
            }
        }
        return PROXY_ID + UUID.randomUUID();
    }

    private String createSafeString(final String in) {
        if (in == null) {
            return null;
        }
        return in.replaceAll("[^A-Za-z0-9]", "_");
    }
}
