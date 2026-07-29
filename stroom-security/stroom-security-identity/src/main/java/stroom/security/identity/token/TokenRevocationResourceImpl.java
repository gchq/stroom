/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.identity.token;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.shared.TokenRevocationResource;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

/**
 * @see TokenRevocationResource
 */
@AutoLogged(OperationType.UNLOGGED)
class TokenRevocationResourceImpl implements TokenRevocationResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TokenRevocationResourceImpl.class);

    private final Provider<TokenRevocationService> tokenRevocationServiceProvider;

    @Inject
    TokenRevocationResourceImpl(final Provider<TokenRevocationService> tokenRevocationServiceProvider) {
        this.tokenRevocationServiceProvider = tokenRevocationServiceProvider;
    }

    @Override
    public Boolean invalidateCache(final String nodeName) {
        // Unlogged: this is inter-node plumbing that carries no decision of its own. The revocation that
        // caused it is audited where it was made, and logging every peer notification would bury that.
        LOGGER.debug(() -> "invalidateCache() called for node " + nodeName);
        tokenRevocationServiceProvider.get().invalidateCacheOnThisNode();
        return true;
    }
}
