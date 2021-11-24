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
public class ApiKeyResourceImpl implements ApiKeyResource {

    private final Provider<ApiKeyService> serviceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<TokenConfig> tokenConfigProvider;

    @Inject
    public ApiKeyResourceImpl(final Provider<ApiKeyService> serviceProvider,
                              final Provider<TokenConfig> tokenConfigProvider,
                              final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.serviceProvider = serviceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.tokenConfigProvider = tokenConfigProvider;
    }

    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Timed
    @Override
    public ApiKeyResultPage search(final HttpServletRequest httpServletRequest,
                                   final SearchApiKeyRequest request) {
        return stroomEventLoggingServiceProvider.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "search"))
                .withDescription("List API keys using a quick filter")
                .withDefaultEventAction(SearchEventAction.builder()
                        .withQuery(buildRawQuery(request, null))
                        .build())
                .withComplexLoggedResult(searchEventAction -> {
                    final ApiKeyResultPage result = serviceProvider.get().search(request);

                    final SearchEventAction newSearchEventAction = StroomEventLoggingUtil.createSearchEventAction(
                            result, () ->
                                    buildRawQuery(request, result.getQualifiedFilterInput()));
                    return ComplexLoggedOutcome.success(result, newSearchEventAction);
                })
                .getResultAndLog();
    }

    private Query buildRawQuery(final SearchApiKeyRequest request, final String qualifiedFilterInput) {
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
        return tokenConfigProvider.get().getDefaultApiKeyExpiration().toMillis() / 1000;
    }
}
