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

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;

// TODO : @66 Add audit logging
public class TokenResourceImpl implements TokenResource {
    private TokenService service;

    @Inject
    public TokenResourceImpl(final TokenService tokenService) {
        this.service = tokenService;
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
        return service.search(searchRequest);
    }

    @Override
    public final Token create(final HttpServletRequest httpServletRequest,
                                 final CreateTokenRequest createTokenRequest) {
        return service.create(createTokenRequest);
    }

    @Override
    public final Integer deleteAll(final HttpServletRequest httpServletRequest) {
        return service.deleteAll();
    }

    @Override
    public final Integer delete(final HttpServletRequest httpServletRequest,
                                 final int tokenId) {
        return service.delete(tokenId);
    }

    @Override
    public final Integer delete(final HttpServletRequest httpServletRequest,
                                 final String token) {
        return service.delete(token);
    }

    @Override
    public final Token read(final HttpServletRequest httpServletRequest,
                               final String token) {
        return service.read(token).orElseThrow(NotFoundException::new);
    }

    @Override
    public final Token read(final HttpServletRequest httpServletRequest,
                               final int tokenId) {
        return service.read(tokenId).orElseThrow(NotFoundException::new);
    }

    @Override
    public final Integer toggleEnabled(final HttpServletRequest httpServletRequest,
                                        final int tokenId,
                                        final boolean enabled) {
        return service.toggleEnabled(tokenId, enabled);
    }

    @Override
    public final String getPublicKey(final HttpServletRequest httpServletRequest) {
        return service.getPublicKey();
    }
}
