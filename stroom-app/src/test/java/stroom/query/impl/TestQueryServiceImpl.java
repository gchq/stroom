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

package stroom.query.impl;

import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.shared.QueryHelpType;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.TestUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.TypeLiteral;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Set;
import java.util.stream.Stream;

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
}
