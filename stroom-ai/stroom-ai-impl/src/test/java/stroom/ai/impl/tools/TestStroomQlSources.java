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

import stroom.ai.impl.tools.StroomQlSources.Source;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionTerm;
import stroom.query.common.v2.ExpressionContextFactory;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.SearchRequestFactory;
import stroom.security.api.SecurityContext;
import stroom.util.shared.NullSafe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStroomQlSources {

    private static final DocRef EVENTS = new DocRef("Index", "events-uuid", "Events");
    private static final DocRef HR = new DocRef("DataConnection", "hr-uuid", "HR");
    private static final DocRef ASSETS = new DocRef("DataConnection", "assets-uuid", "Assets");
    private static final Map<String, DocRef> BY_NAME = Map.of(
            EVENTS.getName(), EVENTS,
            HR.getName(), HR,
            ASSETS.getName(), ASSETS);

    private StroomQlSources sources;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        final DataSourceResolver dataSourceResolver = Mockito.mock(DataSourceResolver.class);
        Mockito.when(dataSourceResolver.resolveDataSourceRef(Mockito.anyString())).thenAnswer(invocation -> {
            final DocRef docRef = BY_NAME.get(invocation.<String>getArgument(0));
            if (docRef == null) {
                throw new RuntimeException("Data source \"" + invocation.getArgument(0) + "\" not found");
            }
            return docRef;
        });
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.useAsReadResult(Mockito.any(Supplier.class))).thenAnswer(invocation ->
                invocation.<Supplier<?>>getArgument(0).get());
        final SearchRequestFactory factory = new SearchRequestFactory((keywordGroup, parentTableSettings) -> null,
                dataSourceResolver, () -> criteria -> null, securityContext);
        sources = new StroomQlSources(() -> factory, ExpressionContextFactory::new, () -> securityContext);
    }

    @Test
    void aQueryWithoutJoinsHasItsFromSourceOnly() {
        final List<Source> found = sources.of("from Events where User = 'jbloggs' select User");

        assertThat(found).singleElement().satisfies(source -> {
            assertThat(source.dataSource()).isEqualTo(EVENTS);
            assertThat(source.joined()).isFalse();
            assertThat(fields(source)).containsExactly("User");
        });
    }

    @Test
    void eachJoinedSourceIsGivenWithWhatIsPushedDownToIt() {
        final List<Source> found = sources.of("""
                from Events
                join HR as hr on User = hr.Name
                left join Assets as a on Host = a.Host
                where User = 'jbloggs' and hr.Department = 'IT'
                select User, hr.Department, a.Owner""");

        assertThat(found).extracting(Source::dataSource).containsExactly(EVENTS, HR, ASSETS);
        assertThat(found).extracting(Source::joined).containsExactly(false, true, true);
        assertThat(fields(found.get(0))).containsExactly("User");
        // An inner join takes its own conditions into its search.
        assertThat(fields(found.get(1))).containsExactly("Department");
        assertThat(fields(found.get(2))).isEmpty();
    }

    @Test
    void whereSubqueriesAndBracketedQueriesAreSourcesToo() {
        assertThat(sources.of("from Events where User in (from HR where Department = 'IT' select Name) "
                              + "select User"))
                .extracting(Source::dataSource)
                .containsExactly(EVENTS, HR);
        assertThat(sources.of("from Events as e where not exists (from HR as hr where hr.Name = e.User) "
                              + "select User"))
                .extracting(Source::dataSource)
                .containsExactly(EVENTS, HR);
        final List<Source> bracketed = sources.of("""
                from (from Events where Host = 'h1' group by User select User, count() as N)
                join (from HR where Department = 'IT' select Name) as hr on User = hr.Name
                select User, N""");
        assertThat(bracketed).extracting(Source::dataSource).containsExactly(EVENTS, HR);
        assertThat(fields(bracketed.get(0))).containsExactly("Host");
        assertThat(fields(bracketed.get(1))).containsExactly("Department");
    }

    @Test
    void aQueryThatCantBeReadThrows() {
        assertThatThrownBy(() -> sources.of("from Events join Nowhere as n on User = n.Name select User"))
                .hasMessageContaining("Nowhere");
    }

    // The fields of the conditions pushed down to a source's search.
    private static List<String> fields(final Source source) {
        return NullSafe.list(NullSafe.get(source.expression(), expression -> expression.getChildren())).stream()
                .map(TestStroomQlSources::field)
                .toList();
    }

    private static String field(final ExpressionItem item) {
        return item instanceof final ExpressionTerm term
                ? term.getField()
                : item.toString();
    }
}
