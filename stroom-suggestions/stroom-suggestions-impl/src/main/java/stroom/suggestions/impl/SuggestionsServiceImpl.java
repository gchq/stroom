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

package stroom.suggestions.impl;

import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;
import stroom.security.api.SecurityContext;
import stroom.suggestions.api.SuggestionsQueryHandler;
import stroom.suggestions.api.SuggestionsService;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@Singleton
@SuppressWarnings("unused")
public class SuggestionsServiceImpl implements SuggestionsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuggestionsServiceImpl.class);

    private final Map<String, Provider<SuggestionsQueryHandler>> providerMap;
    private final SecurityContext securityContext;

    @Inject
    SuggestionsServiceImpl(
            final Map<String, Provider<SuggestionsQueryHandler>> providerMap,
            final SecurityContext securityContext) {
        this.providerMap = providerMap;
        this.securityContext = securityContext;
    }

    @Override
    public Suggestions fetch(final FetchSuggestionsRequest request) throws RuntimeException {
        final String dataSourceType = request.getDataSource().getType();

        return securityContext.secureResult(() -> {
            if (dataSourceType != null) {
                if (providerMap.containsKey(dataSourceType)) {
                    final SuggestionsQueryHandler queryHandler = providerMap.get(dataSourceType).get();
                    return queryHandler.getSuggestions(request);
                } else {
                    LOGGER.debug("Suggestions provider not registered for type: {}", dataSourceType);
                    return Suggestions.EMPTY;
                }
            } else {
                throw new RuntimeException("Data source type not defined");
            }
        });
    }
}
