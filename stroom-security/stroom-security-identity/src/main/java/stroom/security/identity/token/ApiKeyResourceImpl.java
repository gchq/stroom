/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.security.identity.token;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.identity.config.TokenConfig;

import com.codahale.metrics.annotation.Timed;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;

@AutoLogged
public class ApiKeyResourceImpl implements ApiKeyResource {

    private final Provider<ApiKeyService> serviceProvider;
    private final Provider<TokenConfig> tokenConfigProvider;

    @Inject
    public ApiKeyResourceImpl(final Provider<ApiKeyService> serviceProvider,
                              final Provider<TokenConfig> tokenConfigProvider) {
        this.serviceProvider = serviceProvider;
        this.tokenConfigProvider = tokenConfigProvider;
    }

    @Timed
    @Override
    public ApiKeyResultPage search(final HttpServletRequest httpServletRequest, final SearchApiKeyRequest request) {
        return serviceProvider.get().search(request);
    }

    @Timed
    @Override
    public final ApiKey create(final HttpServletRequest httpServletRequest,
                               final CreateApiKeyRequest createApiKeyRequest) {
        return serviceProvider.get().create(createApiKeyRequest);
    }

    @Override
    public ApiKey fetch(final Integer id) {
        return read(null, id);
    }


    @Timed
    @Override
    public final ApiKey read(final HttpServletRequest httpServletRequest,
                             final String data) {
        return serviceProvider.get().read(data).orElseThrow(NotFoundException::new);
    }

    @Timed
    @Override
    public final ApiKey read(final HttpServletRequest httpServletRequest,
                             final int tokenId) {
        return serviceProvider.get().read(tokenId).orElseThrow(NotFoundException::new);
    }

    @Timed
    @Override
    @AutoLogged(OperationType.UPDATE)
    public final Integer toggleEnabled(final HttpServletRequest httpServletRequest,
                                       final int tokenId,
                                       final boolean enabled) {
        return serviceProvider.get().toggleEnabled(tokenId, enabled);
    }

    @Timed
    @Override
    public final Integer deleteAll(final HttpServletRequest httpServletRequest) {
        return serviceProvider.get().deleteAll();
    }

    @Timed
    @Override
    public final Integer delete(final HttpServletRequest httpServletRequest,
                                final int tokenId) {
        return serviceProvider.get().delete(tokenId);
    }

    @Override
    public Integer delete(final HttpServletRequest httpServletRequest, final String content) {
        return serviceProvider.get().delete(content);
    }

    @Override
    public Long getDefaultApiKeyExpirySeconds() {
        return tokenConfigProvider.get().getDefaultApiKeyExpiryTime().toMillis() / 1000;
    }
}
