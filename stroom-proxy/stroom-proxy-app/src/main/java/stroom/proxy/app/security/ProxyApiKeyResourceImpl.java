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

package stroom.proxy.app.security;

import stroom.security.shared.VerifyApiKeyRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserDesc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

public class ProxyApiKeyResourceImpl implements ProxyApiKeyResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyApiKeyResourceImpl.class);

    private final Provider<ProxyApiKeyService> proxyApiKeyServiceProvider;

    @Inject
    public ProxyApiKeyResourceImpl(final Provider<ProxyApiKeyService> proxyApiKeyServiceProvider) {
        this.proxyApiKeyServiceProvider = proxyApiKeyServiceProvider;
    }

    @Override
    public UserDesc verifyApiKey(final VerifyApiKeyRequest request) {
        LOGGER.debug("verifyApiKey() - request: {}", request);
        Objects.requireNonNull(request);
        // Null return is mapped to 204 status
        final UserDesc userDesc = proxyApiKeyServiceProvider.get().verifyApiKey(request)
                .orElse(null);
        LOGGER.debug("verifyApiKey() - Returning userDesc: {}, request: {}", userDesc, request);
        return userDesc;
    }
}
