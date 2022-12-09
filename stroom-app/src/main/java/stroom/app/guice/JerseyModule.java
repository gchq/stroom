package stroom.app.guice;

import stroom.app.errors.NodeCallExceptionMapper;
import stroom.dropwizard.common.PermissionExceptionMapper;
import stroom.dropwizard.common.TokenExceptionMapper;
import stroom.receive.common.RequestAuthenticator;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.util.guice.GuiceUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.jersey.WebTargetProxy;
import stroom.util.shared.BuildInfo;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;

import java.util.Map;
import javax.inject.Provider;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ExceptionMapper;

public class JerseyModule extends AbstractModule {
//    private static final Logger LOGGER = LoggerFactory.getLogger(JerseyModule.class);
//
//    // This name is used by dropwizard metrics
//    private static final String JERSEY_CLIENT_NAME = "stroom_jersey_client";
//    private static final String JERSEY_CLIENT_USER_AGENT_PREFIX = "stroom/";
//
//    private final Environment environment;
//    private final JerseyClientConfiguration jerseyClientConfiguration;
//
//    public JerseyModule(final Environment environment, final JerseyClientConfiguration jerseyClientConfiguration) {
//        this.environment = environment;
//        this.jerseyClientConfiguration = jerseyClientConfiguration;
//    }


    @Override
    protected void configure() {
        GuiceUtil.buildMultiBinder(binder(), ExceptionMapper.class)
                .addBinding(NodeCallExceptionMapper.class)
                .addBinding(PermissionExceptionMapper.class)
                .addBinding(TokenExceptionMapper.class);
    }

    @SuppressWarnings("unused")
    @Provides
    @Singleton
    Client provideJerseyClient(final Provider<BuildInfo> buildInfoProvider) {
//        Objects.requireNonNull(environment, "Null environment");
//
//        // If the userAgent has not been explicitly set in the config then set it based
//        // on the build version
//        if (!jerseyClientConfiguration.getUserAgent().isPresent()) {
//            final String userAgent = JERSEY_CLIENT_USER_AGENT_PREFIX + buildInfoProvider.get().getBuildVersion();
//            LOGGER.info("Setting jersey client user agent string to [{}]", userAgent);
//            jerseyClientConfiguration.setUserAgent(Optional.of(userAgent));
//        }
//
//        LOGGER.info("Creating jersey client {}", JERSEY_CLIENT_NAME);
//        return new JerseyClientBuilder(environment)
//                .using(jerseyClientConfiguration)
//                .build(JERSEY_CLIENT_NAME)
//                .register(LoggingFeature.class);

        return ClientBuilder.newClient(new ClientConfig().register(LoggingFeature.class));
    }

    @SuppressWarnings("unused")
    @Provides
    @Singleton
    WebTargetFactory provideJerseyRequestBuilder(final Client client,
                                                 final SecurityContext securityContext,
                                                 final UserIdentityFactory userIdentityFactory) {
        return url -> {
            final WebTarget webTarget = client.target(url);
            return (WebTarget) new WebTargetProxy(webTarget) {
                @Override
                public Builder request() {
                    final Builder builder = super.request();
                    addAuthHeader(builder);
                    return builder;
                }

                @Override
                public Builder request(final String... acceptedResponseTypes) {
                    final Builder builder = super.request(acceptedResponseTypes);
                    addAuthHeader(builder);
                    return builder;
                }

                @Override
                public Builder request(final MediaType... acceptedResponseTypes) {
                    final Builder builder = super.request(acceptedResponseTypes);
                    addAuthHeader(builder);
                    return builder;
                }

                private void addAuthHeader(final Builder builder) {
                    final UserIdentity userIdentity = securityContext.getUserIdentity();
                    final Map<String, String> authHeaders = userIdentityFactory.getAuthHeaders(userIdentity);
                    authHeaders.forEach(builder::header);
                }
            };
        };
    }
}
