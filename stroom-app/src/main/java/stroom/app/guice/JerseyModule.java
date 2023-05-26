package stroom.app.guice;

import stroom.app.errors.NodeCallExceptionMapper;
import stroom.dropwizard.common.PermissionExceptionMapper;
import stroom.dropwizard.common.TokenExceptionMapper;
import stroom.security.api.SecurityContext;
import stroom.util.guice.GuiceUtil;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.ExceptionMapper;

public class JerseyModule extends AbstractModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JerseyModule.class);

    @Override
    protected void configure() {
        bind(JerseyClientFactory.class).to(JerseyClientFactoryImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExceptionMapper.class)
                .addBinding(NodeCallExceptionMapper.class)
                .addBinding(PermissionExceptionMapper.class)
                .addBinding(TokenExceptionMapper.class);
    }

    /**
     * Provides a Jersey {@link WebTargetFactory} with added Authorization header containing the
     * user's access token.
     * Only use this when you want {@link WebTarget} with the Authorization header already added to it,
     * else just get a Client and create a {@link WebTarget} from that.
     */
    @SuppressWarnings("unused") // Guice injected
    @Provides
    @Singleton
    WebTargetFactory provideJerseyRequestBuilder(final JerseyClientFactoryImpl jerseyClientFactory,
                                                 final SecurityContext securityContext) {
        return url -> {
            final JerseyClientName clientName = JerseyClientName.STROOM;
            final Client client = jerseyClientFactory.getNamedClient(clientName);
            final WebTarget webTarget = client.target(url);
            LOGGER.debug("Building WebTarget for client: '{}', url: '{}'", clientName, url);
            final WebTarget webTargetProxy = new WebTargetProxy(webTarget) {
                @Override
                public Builder request() {
                    final Builder builder = super.request();
                    securityContext.addAuthorisationHeader(builder);
                    return builder;
                }

                @Override
                public Builder request(final String... acceptedResponseTypes) {
                    final Builder builder = super.request(acceptedResponseTypes);
                    securityContext.addAuthorisationHeader(builder);
                    return builder;
                }

                @Override
                public Builder request(final MediaType... acceptedResponseTypes) {
                    final Builder builder = super.request(acceptedResponseTypes);
                    securityContext.addAuthorisationHeader(builder);
                    return builder;
                }
            };
            return webTargetProxy;
        };
    }
}
