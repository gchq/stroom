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

package stroom.sqlstore.impl;

import stroom.db.util.ExpressionMapper;
import stroom.db.util.ExpressionMapperFactory;
import stroom.db.util.TermHandlerFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionUtil;
import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.test.common.util.test.StroomUnitTest;

import org.jooq.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the StroomQL to JOOQ SQL conversion of Updatable Temporal Store queries.
 * This test class verifies that various ExpressionCriteria trees (containing AND, OR, NOT operations
 * and different field types) are translated correctly into JOOQ Conditions mapping to the underlying
 * `updatable_temporal_store` MySQL table columns.
 */
class TestUpdatableSqlTemporalStore extends StroomUnitTest {

    private ExpressionMapper expressionMapper;

    @BeforeEach
    void setUp() {
        // Setup the expression mapper in the same way as the DAO implementation
        final TermHandlerFactory termHandlerFactory = new TermHandlerFactory(null, null, null);
        final ExpressionMapperFactory expressionMapperFactory = new ExpressionMapperFactory(termHandlerFactory);
        this.expressionMapper = expressionMapperFactory.create();

        expressionMapper.map(UpdatableTemporalStore.MAP_FIELD,
                stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore.UPDATABLE_TEMPORAL_STORE.MAP_NAME,
                String::valueOf);
        expressionMapper.map(UpdatableTemporalStore.KEY_FIELD,
                stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore.UPDATABLE_TEMPORAL_STORE.MAP_KEY,
                String::valueOf);
        expressionMapper.map(UpdatableTemporalStore.TIME_FIELD,
                stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore.UPDATABLE_TEMPORAL_STORE.EFFECTIVE_TIME,
                Long::valueOf);
        expressionMapper.map(UpdatableTemporalStore.VALUE_FIELD,
                stroom.sqlstore.impl.db.jooq.tables.UpdatableTemporalStore.UPDATABLE_TEMPORAL_STORE.MAP_VALUE,
                String::valueOf);
    }

    /**
     * Test that a simple equality term translates into a direct SQL equality condition.
     */
    @Test
    void testSimpleEqualityTranslation() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "my-map")
                .build();

        final Condition condition = expressionMapper.apply(expression);

        assertThat(condition).isNotNull();
        assertThat(condition.toString())
                .contains("map_name\" = 'my-map'");
    }

    /**
     * Test that a composite AND expression translates to a correct SQL AND block.
     */
    @Test
    void testCompositeAndTranslation() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(ExpressionOperator.Op.AND)
                .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "my-map")
                .addTerm(UpdatableTemporalStore.KEY_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "my-key")
                .build();

        final Condition condition = expressionMapper.apply(expression);

        assertThat(condition).isNotNull();
        assertThat(condition.toString())
                .contains("map_name\" = 'my-map'")
                .contains("map_key\" = 'my-key'");
    }

    /**
     * Test that numeric fields (EffectiveTime) are parsed and compared correctly.
     */
    @Test
    void testNumericTimeFieldTranslation() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(UpdatableTemporalStore.TIME_FIELD.getFldName(), ExpressionTerm.Condition.GREATER_THAN, "1000")
                .build();

        final Condition condition = expressionMapper.apply(expression);

        assertThat(condition).isNotNull();
        assertThat(condition.toString())
                .contains("effective_time\" > 1000");
    }

    /**
     * Test nested operators (AND with nested OR) translate into the appropriate SQL structure.
     */
    @Test
    void testNestedOperatorsTranslation() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(ExpressionOperator.Op.AND)
                .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "my-map")
                .addOperator(ExpressionOperator.builder()
                        .op(ExpressionOperator.Op.OR)
                        .addTerm(UpdatableTemporalStore.KEY_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "key1")
                        .addTerm(UpdatableTemporalStore.KEY_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "key2")
                        .build())
                .build();

        final Condition condition = expressionMapper.apply(expression);

        assertThat(condition).isNotNull();
        assertThat(condition.toString())
                .contains("map_name\" = 'my-map'")
                .contains("map_key\" = 'key1'")
                .contains("map_key\" = 'key2'");
    }

    /**
     * Test that we can extract the query timestamp and filter out the time field.
     */
    @Test
    void testTimeExtractionAndFiltering() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .op(ExpressionOperator.Op.AND)
                .addTerm(UpdatableTemporalStore.MAP_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "my-map")
                .addTerm(UpdatableTemporalStore.TIME_FIELD.getFldName(), ExpressionTerm.Condition.EQUALS, "12345")
                .build();

        // 1. Extract query time
        final List<ExpressionTerm> timeTerms = ExpressionUtil.terms(
                expression,
                List.of(UpdatableTemporalStore.TIME_FIELD.getFldName()));

        Long queryTime = null;
        for (final ExpressionTerm term : timeTerms) {
            if (UpdatableTemporalStore.TIME_FIELD.getFldName().equals(term.getField()) &&
                    (term.getCondition() == ExpressionTerm.Condition.EQUALS ||
                    term.getCondition() == ExpressionTerm.Condition.LESS_THAN_OR_EQUAL_TO)) {
                queryTime = Long.valueOf(term.getValue());
                break;
            }
        }
        assertThat(queryTime).isEqualTo(12345L);

        // 2. Filter out time field
        final ExpressionOperator filtered = ExpressionUtil.copyOperator(
                expression,
                item -> {
                    if (item instanceof ExpressionTerm) {
                        return !UpdatableTemporalStore.TIME_FIELD.getFldName()
                                .equals(((ExpressionTerm) item).getField());
                    }
                    return true;
                });

        assertThat(filtered).isNotNull();
        final Condition condition = expressionMapper.apply(filtered);
        assertThat(condition.toString())
                .contains("map_name\" = 'my-map'")
                .doesNotContain("effective_time");
    }
}
