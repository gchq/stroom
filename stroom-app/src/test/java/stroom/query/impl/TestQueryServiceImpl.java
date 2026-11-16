/*
 * Copyright 2024 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.query.api.token.TokenType;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.SearchRequestFactory;
import stroom.query.shared.QueryHelpType;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.Mockito;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestQueryServiceImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestQueryServiceImpl.class);

    private static final Set<QueryHelpType> FIELDS_AND_FUNCS = Set.of(QueryHelpType.FIELD, QueryHelpType.FUNCTION);

    @TestFactory
    Stream<DynamicTest> testGetQueryHelpContext() {
        final QueryServiceImpl queryService = new QueryServiceImpl(
                null,
                null,
                null,
                new MockSecurityContext(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                new ExpressionPredicateFactory(),
                null,
                null);

        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<Set<QueryHelpType>>() {
                })
                .withTestFunction(testCase -> {
                    final String partialQuery = testCase.getInput();
                    final ContextualQueryHelp contextualQueryHelp = queryService.getQueryHelpContext(partialQuery);
                    final Set<QueryHelpType> types = contextualQueryHelp.queryHelpTypes();

                    LOGGER.debug("Types: {}, Query:\n{}",
                            types, partialQuery);
                    LOGGER.debug("helpTypes: {}", contextualQueryHelp.queryHelpTypes());
                    LOGGER.debug("structureItems: {}", contextualQueryHelp.applicableStructureItems());
                    return types;
                })
                .withSimpleEqualityAssertion()
                .addCase("", Set.of(QueryHelpType.STRUCTURE))
                .addCase("from", Set.of())
                .addCase("from ", Set.of(QueryHelpType.DATA_SOURCE))
                .addCase("from Dual1", Set.of(QueryHelpType.DATA_SOURCE))
                .addCase("""
                        from Dual2
                        """, Set.of(QueryHelpType.STRUCTURE))
                .addCase("""
                        from Dual
                        select Dummy1""", FIELDS_AND_FUNCS)
                .addCase("""
                        from Dual
                        select Dummy2
                        """, Set.of(QueryHelpType.FIELD, QueryHelpType.FUNCTION, QueryHelpType.STRUCTURE))
                .addCase("""
                        from Dual
                        select Dummy2,""", FIELDS_AND_FUNCS)
                .addCase("""
                        from Dual
                        select Dummy2,\s""", FIELDS_AND_FUNCS)
                .addCase("""
                        from Dual
                        eval""", Set.of(QueryHelpType.STRUCTURE))
                .addCase("""
                        from Dual
                        eval\s""", Set.of())
                .addCase("""
                        from Dual
                        eval =""", Set.of())
                .addCase("""
                        from Dual
                        eval =\s""", FIELDS_AND_FUNCS)
                .build();
    }

    // A query service whose data sources named sql_... run SQL, as a Data Connection does.
    @SuppressWarnings("unchecked")
    private static QueryServiceImpl queryServiceWithSqlSources() {
        final SearchRequestFactory searchRequestFactory = Mockito.mock(SearchRequestFactory.class);
        Mockito.doAnswer(invocation -> {
            final String query = invocation.getArgument(0);
            final String name = query.contains("sql_hr")
                    ? "sql_hr"
                    : "Dual";
            ((Consumer<DocRef>) invocation.getArgument(1)).accept(new DocRef("Type", name, name));
            return null;
        }).when(searchRequestFactory).extractDataSourceOnly(Mockito.anyString(), Mockito.any());
        final DataSourceProviderRegistry registry = Mockito.mock(DataSourceProviderRegistry.class);
        Mockito.when(registry.takesNativeQuery(Mockito.any()))
                .thenAnswer(invocation -> ((DocRef) invocation.getArgument(0)).getName().startsWith("sql_"));
        return new QueryServiceImpl(null, null, null, new MockSecurityContext(), null, null, null, registry, null,
                null, searchRequestFactory, null, null, new ExpressionPredicateFactory(), null, null);
    }

    @Test
    void sqlClauseIsOnlyOfferedForADataSourceThatRunsSql() {
        final QueryServiceImpl queryService = queryServiceWithSqlSources();
        final String sql = Structures.name(TokenType.SQL);

        assertThat(queryService.getQueryHelpContext("from sql_hr\n").applicableStructureItems()).contains(sql);
        assertThat(queryService.getQueryHelpContext("from Dual\n").applicableStructureItems())
                .doesNotContain(sql)
                .contains(Structures.name(TokenType.WHERE));
    }

    @Test
    void noHelpIsOfferedWhileTheSqlIsTyped() {
        // The SQL's own words are not StroomQL, so nothing StroomQL offers applies.
        final ContextualQueryHelp help = queryServiceWithSqlSources()
                .getQueryHelpContext("from sql_hr\nsql \"\"\"\nselect a from ");

        assertThat(help.queryHelpTypes()).isEmpty();
        assertThat(help.applicableStructureItems()).isEmpty();
    }

    @Test
    void helpResumesAfterTheSql() {
        final ContextualQueryHelp help = queryServiceWithSqlSources()
                .getQueryHelpContext("from sql_hr\nsql \"\"\"select a from t\"\"\"\n");

        assertThat(help.applicableStructureItems())
                .contains(Structures.name(TokenType.SELECT))
                .doesNotContain(Structures.name(TokenType.SQL));
    }
}
