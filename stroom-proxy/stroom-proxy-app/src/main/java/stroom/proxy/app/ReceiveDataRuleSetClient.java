package stroom.proxy.app;

import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.receive.rules.shared.HashedReceiveDataRules;
import stroom.security.api.UserIdentityFactory;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.StatusType;

import java.util.Map;
import java.util.Optional;

/**
 * Separate the rest call from {@link RemoteReceiveDataRuleSetServiceImpl} to make testing easier
 */
public class ReceiveDataRuleSetClient {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReceiveDataRuleSetClient.class);
    private static final String GET_FEED_STATUS_PATH = "/fetchHashedReceiveDataRules";

    private final JerseyClientFactory jerseyClientFactory;
    private final UserIdentityFactory userIdentityFactory;
    private final Provider<ContentSyncConfig> contentSyncConfigProvider;

    @Inject
    public ReceiveDataRuleSetClient(final JerseyClientFactory jerseyClientFactory,
                                    final UserIdentityFactory userIdentityFactory,
                                    final Provider<ContentSyncConfig> contentSyncConfigProvider) {
        this.jerseyClientFactory = jerseyClientFactory;
        this.userIdentityFactory = userIdentityFactory;
        this.contentSyncConfigProvider = contentSyncConfigProvider;
    }

    public Optional<HashedReceiveDataRules> getHashedReceiveDataRules() {
        Optional<HashedReceiveDataRules> optHashedReceiveDataRules = Optional.empty();

        final ContentSyncConfig contentSyncConfig = contentSyncConfigProvider.get();
        final String url = contentSyncConfig.getReceiveDataRulesUrl();
        if (NullSafe.isNonBlankString(url)) {
            try {
                final WebTarget webTarget = jerseyClientFactory.createWebTarget(JerseyClientName.CONTENT_SYNC, url)
                        .path(GET_FEED_STATUS_PATH);
                try (Response response = getResponse(contentSyncConfig, webTarget)) {
                    final StatusType statusInfo = response.getStatusInfo();
                    if (statusInfo.getStatusCode() != Status.OK.getStatusCode()) {
                        LOGGER.error("Error fetching receive data rules using url '{}', got response {} - {}",
                                url, statusInfo.getStatusCode(), statusInfo.getReasonPhrase());
                    } else {
                        optHashedReceiveDataRules = Optional.ofNullable(
                                response.readEntity(HashedReceiveDataRules.class));
                        LOGGER.debug("getHashedReceiveDataRules() - optHashedReceiveDataRules from upstream: {}",
                                optHashedReceiveDataRules);
                    }
                }
            } catch (Throwable e) {
                LOGGER.error("Error fetching receive data rules using url '{}': {}",
                        url, LogUtil.exceptionMessage(e), e);
            }
        }
        LOGGER.debug("getHashedReceiveDataRules() - returning {}", optHashedReceiveDataRules);
        return optHashedReceiveDataRules;
    }

    private Response getResponse(final ContentSyncConfig contentSyncConfig,
                                 final WebTarget webTarget) {
        return webTarget
                .request(MediaType.APPLICATION_JSON)
                .headers(getHeaders(contentSyncConfig))
                .get();
    }

    private MultivaluedMap<String, Object> getHeaders(final ContentSyncConfig contentSyncConfig) {
        final Map<String, String> headers;

        if (!NullSafe.isBlankString(contentSyncConfig.getApiKey())) {
            // Intended for when stroom is using its internal IDP. Create the API Key in stroom UI
            // and add it to config.
            LOGGER.debug(() -> LogUtil.message("Using API key from config prop {}",
                    contentSyncConfig.getFullPathStr(FeedStatusConfig.PROP_NAME_API_KEY)));
            headers = userIdentityFactory.getAuthHeaders(contentSyncConfig.getApiKey());
        } else {
            // Use a token from the external IDP
            headers = userIdentityFactory.getServiceUserAuthHeaders();
        }
        return new MultivaluedHashMap<>(headers);
    }
}
