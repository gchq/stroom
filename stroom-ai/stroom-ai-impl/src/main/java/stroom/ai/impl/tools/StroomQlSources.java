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
import stroom.query.api.ExpressionOperator;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.api.SearchRequest;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.language.SearchRequestFactory;
import stroom.security.api.SecurityContext;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.List;

/// Every data source a StroomQL query searches, each with the conditions pushed down to its own search, read with
/// Stroom's own parser: the `from` source (or the source of a bracketed `from` query), then each joined source in
/// the order of its join line, including the `exists` and `in (from …)` subqueries of `where`, which Stroom runs
/// as joins.
public class StroomQlSources {

    private final Provider<SearchRequestFactory> searchRequestFactoryProvider;
    private final Provider<ExpressionContextFactory> expressionContextFactoryProvider;
    private final Provider<SecurityContext> securityContextProvider;

    @Inject
    public StroomQlSources(final Provider<SearchRequestFactory> searchRequestFactoryProvider,
                           final Provider<ExpressionContextFactory> expressionContextFactoryProvider,
                           final Provider<SecurityContext> securityContextProvider) {
        this.searchRequestFactoryProvider = searchRequestFactoryProvider;
        this.expressionContextFactoryProvider = expressionContextFactoryProvider;
        this.securityContextProvider = securityContextProvider;
    }

    /// Reads the data sources a query searches, as the user (with Use counting as Read, as the query service
    /// parses a query).
    ///
    /// @param query A StroomQL query.
    /// @return Its data sources, the first source first, each with its pushed-down conditions.
    /// @throws RuntimeException with the parser's reason, if the query can't be read.
    public List<Source> of(final String query) {
        final SearchRequest sample = new SearchRequest(null, null, Query.builder().build(), null, null, false, null);
        final SearchRequest mapped = securityContextProvider.get().useAsReadResult(() ->
                searchRequestFactoryProvider.get().create(query, sample,
                        expressionContextFactoryProvider.get().createContext(sample)));
        return of(mapped.getQuery());
    }

    /// @param query A query as Stroom's parser gives it.
    /// @return The data sources it searches, the first source first, each with its pushed-down conditions; none
    /// if it has no data source.
    static List<Source> of(final Query query) {
        final List<Source> sources = new ArrayList<>();
        if (query == null || query.getDataSource() == null) {
            return sources;
        }
        sources.add(new Source(query.getDataSource(), query.getExpression(), false));
        for (final QueryJoin join : NullSafe.list(query.getJoins())) {
            final Query joined = join.getQuery();
            if (joined != null && joined.getDataSource() != null) {
                sources.add(new Source(joined.getDataSource(), joined.getExpression(), true));
            }
        }
        return sources;
    }


    // --------------------------------------------------------------------------------


    /// A data source a query searches.
    ///
    /// @param dataSource The data source.
    /// @param expression The conditions pushed down to its search, or null for none.
    /// @param joined     True for a joined source (a join line, or a `where` subquery), false for the first.
    public record Source(DocRef dataSource, ExpressionOperator expression, boolean joined) {

    }
}
