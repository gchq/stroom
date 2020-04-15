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
import javax.ws.rs.core.Response;

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
    public final Response search(final HttpServletRequest httpServletRequest,
                                 final SearchRequest searchRequest) {
        var results = service.search(searchRequest);
        return Response.status(Response.Status.OK).entity(results).build();
    }

    @Override
    public final Response create(final HttpServletRequest httpServletRequest,
                                 final CreateTokenRequest createTokenRequest) {
        var token = service.create(createTokenRequest);
        return Response.status(Response.Status.OK).entity(token).build();
    }

    @Override
    public final Response deleteAll(final HttpServletRequest httpServletRequest) {
        service.deleteAll();
        return Response.status(Response.Status.OK).entity("All tokens deleted").build();
    }

    @Override
    public final Response delete(final HttpServletRequest httpServletRequest,
                                 final int tokenId) {
        service.delete(tokenId);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @Override
    public final Response delete(final HttpServletRequest httpServletRequest,
                                 final String token) {
        service.delete(token);
        return Response.status(Response.Status.OK).entity("Deleted token").build();
    }

    @Override
    public final Response read(final HttpServletRequest httpServletRequest,
                               final String token) {
        return service.read(token)
                .map(tokenResult -> Response.status(Response.Status.OK).entity(tokenResult).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @Override
    public final Response read(final HttpServletRequest httpServletRequest,
                               final int tokenId) {
        return service.read(tokenId)
                .map(token -> Response.status(Response.Status.OK).entity(token).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @Override
    public final Response toggleEnabled(final HttpServletRequest httpServletRequest,
                                        final int tokenId,
                                        final boolean enabled) {
        service.toggleEnabled(tokenId, enabled);
        return Response.status(Response.Status.NO_CONTENT).build();
    }

    @Override
    public final Response getPublicKey(final HttpServletRequest httpServletRequest) {
        String jwkAsJson = service.getPublicKey();
        return Response.status(Response.Status.OK).entity(jwkAsJson).build();
    }
}
