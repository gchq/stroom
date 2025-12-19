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

package stroom.app.guice;

import stroom.app.HttpClientCacheImpl;
import stroom.app.errors.NodeCallExceptionMapper;
import stroom.dropwizard.common.PermissionExceptionMapper;
import stroom.dropwizard.common.TokenExceptionMapper;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.util.guice.GuiceUtil;
import stroom.util.jersey.HttpClientCache;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.jersey.WebTargetProxy;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Invocation.Builder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.ExceptionMapper;

import java.util.Map;

public class JerseyModule extends AbstractModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JerseyModule.class);

    @Override
    protected void configure() {
        bind(JerseyClientFactory.class).to(JerseyClientFactoryImpl.class);
        bind(HttpClientCache.class).to(HttpClientCacheImpl.class);
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
                                                 final SecurityContext securityContext,
                                                 final Provider<UserIdentityFactory> userIdentityFactoryProvider) {
        return url -> {
            final JerseyClientName clientName = JerseyClientName.STROOM;
            final Client client = jerseyClientFactory.getNamedClient(clientName);
            final WebTarget delegateWebTarget = client.target(url);
            LOGGER.debug("Building WebTarget for client: '{}', url: '{}'", clientName, url);

            return (WebTarget) new WebTargetProxy(delegateWebTarget) {
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

                    final UserIdentityFactory userIdentityFactory = userIdentityFactoryProvider.get();
                    if (!userIdentityFactory.isServiceUser(userIdentity)) {
                        // We are running as a user who is not the service/proc user so need to put their
                        // identity in the headers. We can't use the human user identity as they may have
                        // an AWS token that we can't refresh.
                        builder.header(UserIdentityFactory.RUN_AS_USER_HEADER, securityContext.getUserRef().getUuid());
                    }
                    // Always authenticate as the proc user
                    final Map<String, String> authHeaders = userIdentityFactory.getServiceUserAuthHeaders();

                    LOGGER.debug(() -> LogUtil.message("Adding auth headers to request, keys: '{}', userType: {}",
                            String.join(", ", NullSafe.map(authHeaders).keySet()),
                            NullSafe.get(userIdentity, Object::getClass, Class::getSimpleName)));

                    authHeaders.forEach(builder::header);
                }
            };
        };
    }
}
