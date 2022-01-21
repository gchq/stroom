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

import stroom.security.identity.config.IdentityConfig;
import stroom.security.openid.api.PublicJsonWebKeyProvider;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class TokenBuilderFactory {

    private final Provider<IdentityConfig> configProvider;
    private final PublicJsonWebKeyProvider publicJsonWebKeyProvider;

    @Inject
    public TokenBuilderFactory(final Provider<IdentityConfig> configProvider,
                               final PublicJsonWebKeyProvider publicJsonWebKeyProvider) {
        this.configProvider = configProvider;
        this.publicJsonWebKeyProvider = publicJsonWebKeyProvider;
    }

    public TokenBuilder builder() {
        final TokenBuilder tokenBuilder = new TokenBuilder();
        final IdentityConfig identityConfig = configProvider.get();
        tokenBuilder
                .issuer(identityConfig.getTokenConfig().getJwsIssuer())
                .privateVerificationKey(publicJsonWebKeyProvider.getFirst())
                .algorithm(identityConfig.getTokenConfig().getAlgorithm());
        return tokenBuilder;
    }
}
