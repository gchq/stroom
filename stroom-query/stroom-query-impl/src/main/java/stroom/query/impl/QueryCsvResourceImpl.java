/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.impl.CsvSearchService.CsvSearchResult;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Objects;

@AutoLogged
class QueryCsvResourceImpl implements QueryCsvResource {

    private final Provider<CsvSearchService> csvSearchServiceProvider;

    @Inject
    QueryCsvResourceImpl(final Provider<CsvSearchService> csvSearchServiceProvider) {
        this.csvSearchServiceProvider = csvSearchServiceProvider;
    }

    /**
     * Always 200, with the CSV found so far, however little that is. The headers, not the status, say
     * whether the search ran incrementally and whether it finished, so a caller can tell a complete
     * empty result from a search that had not found anything yet. See gh-5688.
     */
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    @Override
    public Response csvSearch(final String query,
                              final int offset,
                              final int length,
                              final boolean incremental,
                              final Long timeout) {
        final CsvSearchResult result = csvSearchServiceProvider.get()
                .search(query, offset, length, incremental, timeout);

        return Response
                .ok(Objects.requireNonNullElse(result.csv(), ""), MediaType.TEXT_PLAIN_TYPE)
                .header(QueryCsvResource.CSV_INCREMENTAL_HEADER, result.incremental())
                .header(QueryCsvResource.CSV_COMPLETE_HEADER, result.complete())
                .build();
    }
}
