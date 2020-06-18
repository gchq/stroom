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

package stroom.authentication.token;

import stroom.authentication.config.TokenConfig;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;

// TODO : @66 Add audit logging
public class TokenResourceImpl implements TokenResource {
    private final Provider<TokenService> serviceProvider;
    private final TokenEventLog tokenEventLog;

    @Inject
    public TokenResourceImpl(final Provider<TokenService> serviceProvider,
                             final TokenEventLog tokenEventLog) {
        this.serviceProvider = serviceProvider;
        this.tokenEventLog = tokenEventLog;
    }

    /**
     * Default ordering is by ISSUED_ON date, in descending order so the most recent tokens are shown first.
     * If orderBy is specified but orderDirection is not this will default to ascending.
     * <p>
     * The user must have the 'Manage Users' permission to call this.
     */
    @Override
    public final SearchResponse search(final HttpServletRequest httpServletRequest,
                                       final SearchRequest searchRequest) {
        try {
            final SearchResponse searchResponse = serviceProvider.get().search(searchRequest);
            tokenEventLog.search(searchRequest, searchResponse, null);
            return searchResponse;
        } catch (final RuntimeException e) {
            tokenEventLog.search(searchRequest, null, e);
            throw e;
        }
    }

    @Override
    public final Token create(final HttpServletRequest httpServletRequest,
                              final CreateTokenRequest createTokenRequest) {
        try {
            final Token token = serviceProvider.get().create(createTokenRequest);
            tokenEventLog.create(createTokenRequest, token, null);
            return token;
        } catch (final RuntimeException e) {
            tokenEventLog.create(createTokenRequest, null, e);
            throw e;
        }
    }

    @Override
    public final Integer deleteAll(final HttpServletRequest httpServletRequest) {
        try {
            final int result = serviceProvider.get().deleteAll();
            tokenEventLog.deleteAll(result, null);
            return result;
        } catch (final RuntimeException e) {
            tokenEventLog.deleteAll(0, e);
            throw e;
        }
    }

    @Override
    public final Integer delete(final HttpServletRequest httpServletRequest,
                                final int tokenId) {
        try {
            final int result = serviceProvider.get().delete(tokenId);
            tokenEventLog.delete(tokenId, result, null);
            return result;
        } catch (final RuntimeException e) {
            tokenEventLog.delete(tokenId, 0, e);
            throw e;
        }
    }

    @Override
    public final Integer delete(final HttpServletRequest httpServletRequest,
                                final String data) {
        try {
            final int result = serviceProvider.get().delete(data);
            tokenEventLog.delete(data, result, null);
            return result;
        } catch (final RuntimeException e) {
            tokenEventLog.delete(data, 0, e);
            throw e;
        }
    }

    @Override
    public final Token read(final HttpServletRequest httpServletRequest,
                            final String token) {
        try {
            final Token result = serviceProvider.get().read(token).orElseThrow(NotFoundException::new);
            tokenEventLog.read(token, result, null);
            return result;
        } catch (final RuntimeException e) {
            tokenEventLog.read(token, null, e);
            throw e;
        }
    }

    @Override
    public final Token read(final HttpServletRequest httpServletRequest,
                            final int tokenId) {
        try {
            final Token result = serviceProvider.get().read(tokenId).orElseThrow(NotFoundException::new);
            tokenEventLog.read(tokenId, result, null);
            return result;
        } catch (final RuntimeException e) {
            tokenEventLog.read(tokenId, null, e);
            throw e;
        }
    }

    @Override
    public final Integer toggleEnabled(final HttpServletRequest httpServletRequest,
                                       final int tokenId,
                                       final boolean enabled) {
        try {
            final Integer result = serviceProvider.get().toggleEnabled(tokenId, enabled);
            tokenEventLog.toggleEnabled(tokenId, enabled, null);
            return result;
        } catch (final RuntimeException e) {
            tokenEventLog.toggleEnabled(tokenId, enabled, e);
            throw e;
        }
    }

    @Override
    public final String getPublicKey(final HttpServletRequest httpServletRequest) {
        try {
            final String result = serviceProvider.get().getPublicKey();
            tokenEventLog.getPublicKey(null);
            return result;
        } catch (final RuntimeException e) {
            tokenEventLog.getPublicKey(e);
            throw e;
        }
    }

    @Override
    public TokenConfig fetchTokenConfig() {
        return serviceProvider.get().fetchTokenConfig();
    }
}
