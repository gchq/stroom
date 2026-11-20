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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.query.impl.QueryService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Checks the user can use every data source a StroomQL query names before a tool checks or offers it
/// (validate_stroomql, propose_query, propose_query_change, propose_dashboard): the `from` source, and each one it
/// joins (join lines, and the `exists` and `in (from …)` subqueries of `where`, which Stroom runs as joins). A data
/// source the user can't use is treated as one that doesn't exist.
class StroomQlUseCheck {

    /// The refusal for a query whose `from` names no data source the user can use.
    static final String NO_DATA_SOURCE = "The query doesn't name a data source you can use: check its 'from' "
                                         + "clause against list_data_sources.";
    /// The refusal for a query that joins a data source the user can't use.
    static final String NO_JOINED_DATA_SOURCE = "The query joins a data source you can't use: check its join lines "
                                                + "and where subqueries (exists, in (from ...)) against "
                                                + "list_data_sources.";

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomQlUseCheck.class);

    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final Provider<StroomQlSources> stroomQlSourcesProvider;

    @Inject
    StroomQlUseCheck(final DataSourceAccess dataSourceAccess,
                     final Provider<QueryService> queryServiceProvider,
                     final Provider<StroomQlSources> stroomQlSourcesProvider) {
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.stroomQlSourcesProvider = stroomQlSourcesProvider;
    }

    /// @param query A StroomQL query.
    /// @return The data sources it names, and the refusal if the user can't use one of them.
    Checked check(final String query) {
        final DocRef first = usableDataSource(query).orElse(null);
        if (first == null) {
            return new Checked(null, List.of(), NO_DATA_SOURCE);
        }
        final List<DocRef> joined = joinedDataSources(query);
        if (!joined.stream().allMatch(dataSourceAccess::canUse)) {
            // The joined ones aren't named: the user can't use one of them.
            return new Checked(first, List.of(), NO_JOINED_DATA_SOURCE);
        }
        return new Checked(first, joined, null);
    }

    // The data source the query's from names, if the user can use it.
    private Optional<DocRef> usableDataSource(final String query) {
        try {
            return queryServiceProvider.get().getReferencedDataSource(query)
                    .filter(dataSourceAccess::canUse);
        } catch (final RuntimeException e) {
            return Optional.empty();
        }
    }

    // The data sources the query joins, each once; none if it joins none, or can't be read, as checking it then
    // gives the parser's own error.
    private List<DocRef> joinedDataSources(final String query) {
        try {
            return stroomQlSourcesProvider.get().of(query).stream()
                    .filter(StroomQlSources.Source::joined)
                    .map(StroomQlSources.Source::dataSource)
                    .distinct()
                    .toList();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to read the query's joined data sources: {}",
                    e.getMessage()), e);
            return List.of();
        }
    }


    // --------------------------------------------------------------------------------


    /// What [#check(String)] found.
    ///
    /// @param first   The data source the query's `from` names, or null if the user can't use it.
    /// @param joined  The data sources it joins, each once; none if it joins none, or if it was refused.
    /// @param refusal Why the query is refused, or null if the user can use every data source it names.
    record Checked(DocRef first, List<DocRef> joined, String refusal) {

        /// @return True if the user can use every data source the query names.
        boolean usable() {
            return refusal == null;
        }

        /// @return The data sources to name in the audit log: the first, then each joined one; only those known to
        /// be usable when refused.
        List<DocRef> audited() {
            final List<DocRef> docRefs = new ArrayList<>();
            if (first != null) {
                docRefs.add(first);
            }
            docRefs.addAll(joined);
            return docRefs;
        }
    }
}
