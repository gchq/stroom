package stroom.proxy.app.guice;

import stroom.security.api.RequestAuthenticator;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.jersey.WebTargetProxy;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.util.Map;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

public class ProxyJerseyModule extends AbstractModule {

    @Override
    protected void configure() {

        bind(Client.class).toProvider(ProxyJerseyClientProvider.class);
    }

    @SuppressWarnings("unused")
    @Provides
    @Singleton
    WebTargetFactory provideJerseyRequestBuilder(final Client client,
                                                 final RequestAuthenticator requestAuthenticator) {
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
                    final Map<String, String> authHeaders = requestAuthenticator.getServiceUserAuthHeaders();
                    authHeaders.forEach(builder::header);
                }
            };
        };
    }
}
