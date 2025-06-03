package stroom.proxy.app.security;

import stroom.proxy.app.AbstractDownstreamClient;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.security.api.UserIdentityFactory;
import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.Response.StatusType;

import java.util.Optional;

public class ProxyApiKeyCheckClient extends AbstractDownstreamClient {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyApiKeyCheckClient.class);

    private final Provider<DownstreamHostConfig> downstreamHostConfigProvider;

    @Inject
    public ProxyApiKeyCheckClient(
            final JerseyClientFactory jerseyClientFactory,
            final UserIdentityFactory userIdentityFactory,
            final Provider<DownstreamHostConfig> downstreamHostConfigProvider) {
        super(jerseyClientFactory,
                userIdentityFactory,
                downstreamHostConfigProvider);
        this.downstreamHostConfigProvider = downstreamHostConfigProvider;
    }

    @Override
    protected Optional<String> getConfiguredUrl() {
        return NullSafe.nonBlank(downstreamHostConfigProvider.get().getApiKeyVerificationUrl());
    }

    @Override
    protected String getDefaultPath() {
        return DownstreamHostConfig.DEFAULT_API_KEY_VERIFICATION_URL_PATH;
    }

    Optional<UserDesc> fetchApiKeyValidity(final VerifyApiKeyRequest request) {
        final String url = getFullUrl();
        Optional<UserDesc> optUserDesc = Optional.empty();
        if (NullSafe.isNonBlankString(url)) {
            try (Response response = getResponse(builder -> builder.post(Entity.json(request)))) {
                final StatusType statusInfo = response.getStatusInfo();
                if (statusInfo.getStatusCode() == Status.OK.getStatusCode()) {
                    if (response.hasEntity()) {
                        optUserDesc = Optional.ofNullable(response.readEntity(UserDesc.class));
                        LOGGER.debug("fetchApiKeyValidity() - optUserDesc: {}, request: {}", optUserDesc, request);
                    } else {
                        LOGGER.debug("fetchApiKeyValidity() - No response entity from {}", url);
                    }
                } else {
                    LOGGER.error("Error fetching API Key validity using url '{}', " +
                                 "got response {} - {}, request: {}",
                            url, statusInfo.getStatusCode(), statusInfo.getReasonPhrase(), request);
                }
            } catch (NotFoundException e) {
                LOGGER.debug("fetchApiKeyValidity() - Not found exception");
            }
        } else {
            LOGGER.warn("No url configured for API key verification.");
        }
        return optUserDesc;
    }

}
