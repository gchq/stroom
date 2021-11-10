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

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.identity.config.TokenConfig;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PageRequest;

import com.codahale.metrics.annotation.Timed;
import event.logging.ComplexLoggedOutcome;
import event.logging.Query;
import event.logging.SearchEventAction;

import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotFoundException;

@AutoLogged
public class TokenResourceImpl implements TokenResource {

    private final Provider<TokenService> serviceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    public TokenResourceImpl(final Provider<TokenService> serviceProvider,
                             final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.serviceProvider = serviceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Timed
    @Override
    //todo remove this method
    @AutoLogged(OperationType.VIEW)
    public TokenResultPage list(final HttpServletRequest httpServletRequest) {
        return null;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Timed
    @Override
    public TokenResultPage search(final HttpServletRequest httpServletRequest,
                                  final SearchTokenRequest request) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder(
                StroomEventLoggingUtil.buildTypeId(this, "search"),
                "List API keys using a quick filter",
                SearchEventAction.builder()
                        .withQuery(buildRawQuery(request, null))
                        .build())
                .withComplexLoggedResult(searchEventAction -> {
                    final TokenResultPage result = serviceProvider.get().search(request);

                    final SearchEventAction newSearchEventAction = StroomEventLoggingUtil.createSearchEventAction(
                            result, () -> buildRawQuery(request, result.getQualifiedFilterInput()));
                    return ComplexLoggedOutcome.success(result, newSearchEventAction);
                })
                .getResultAndLog();
    }

    private Query buildRawQuery(final SearchTokenRequest request, final String qualifiedFilterInput) {
        final String filterInput = qualifiedFilterInput != null
                ? qualifiedFilterInput
                : request.getQuickFilter();

        final String rawQuery = LogUtil.message("{\"filter\": \"{}\", "
                        + "\"offset\": \"{}\", "
                        + "\"length\": \"{}\"}",
                Objects.requireNonNullElse(filterInput, ""),
                NullSafe.toStringOrElse(request.getPageRequest(), PageRequest::getOffset, ""),
                NullSafe.toStringOrElse(request.getPageRequest(), PageRequest::getLength, ""));

        return Query.builder()
                .withRaw(rawQuery)
                .build();
    }

    @Timed
    @Override
    public final Token create(final HttpServletRequest httpServletRequest,
                              final CreateTokenRequest createTokenRequest) {
        return serviceProvider.get().create(createTokenRequest);
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

    @Timed
    @Override
    public final Integer deleteByToken(final HttpServletRequest httpServletRequest,
                                       final String data) {
        return serviceProvider.get().delete(data);
    }

    @Timed
    @Override
    public final Token read(final HttpServletRequest httpServletRequest,
                            final String token) {
        return serviceProvider.get().read(token).orElseThrow(NotFoundException::new);
    }

    @Timed
    @Override
    public final Token read(final HttpServletRequest httpServletRequest,
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
    @AutoLogged(OperationType.UNLOGGED)
    public final String getPublicKey(final HttpServletRequest httpServletRequest) {
        return serviceProvider.get().getPublicKey();
    }

    @Timed
    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public TokenConfig fetchTokenConfig() {
        return serviceProvider.get().fetchTokenConfig();
    }

    @Override
    public Token fetch(final Integer id) {
        return read(null, id);
    }
}
