package stroom.proxy.app.handler;

import stroom.proxy.app.ProxyConfig;
import stroom.util.NullSafe;
import stroom.util.io.HomeDirProvider;
import stroom.util.logging.LogUtil;
import stroom.util.net.HostNameUtil;

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
    private static final String UNSAFE_CHARS_REGEX = "[^" + ALLOWED_CHARS + "]";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyId.class);
    private static final String PROXY_ID_FILE = "proxy-id.txt";
    private static final String PROXY_ID = "Proxy-";
    private static final String LOCALHOST = "localhost";

    private final HomeDirProvider homeDirProvider;
    private final String id;

    @Inject
    public ProxyId(final ProxyConfig proxyConfig,
                   final HomeDirProvider homeDirProvider) {
        this.homeDirProvider = homeDirProvider;

        final String source;
        final String proxyId = createSafeString(proxyConfig.getProxyId());
        if (NullSafe.isBlankString(proxyId)) {
            LOGGER.info("No proxy id is configured");
            final String storedId = readProxyId();
            if (NullSafe.isBlankString(storedId)) {
                final String createdId = createProxyId();
                writeProxyId(createdId);
                LOGGER.info("No stored proxy ID found in '{}', created and stored new proxy id: {}",
                        PROXY_ID_FILE, createdId);
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

        if (!Pattern.compile(PROXY_ID_REGEX).matcher(id).matches()) {
            throw new RuntimeException(LogUtil.message("Proxy ID '{}' (source: {}), does not match pattern '{}'",
                    id, source, PROXY_ID_REGEX));
        }
    }

    public String getId() {
        return id;
    }

    /**
     * @return A unique receipt ID that includes the proxy ID
     */
    public ReceiptId generateReceiptId() {
        return ReceiptId.generate(id);
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
                storedId = Files.readString(path);
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

    private String createProxyId() {
        final String hostName = HostNameUtil.determineHostName();
        if (hostName != null && !hostName.isBlank()) {
            final String safeHostName = createSafeString(hostName);
            if (!LOCALHOST.equalsIgnoreCase(safeHostName)
                && Character.isAlphabetic(safeHostName.charAt(0))) {

                return PROXY_ID + safeHostName;
            }
        }
        // No suitable hostname so just give it a UUID
        return PROXY_ID + UUID.randomUUID();
    }

    private String createSafeString(final String in) {
        if (in == null) {
            return null;
        } else {
            return in.replaceAll(UNSAFE_CHARS_REGEX, "-");
        }
    }
}
