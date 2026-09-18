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

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.query.shared.FormatQuickFilterRequest;
import stroom.query.shared.FormatQuickFilterResult;
import stroom.query.shared.ParseQuickFilterRequest;
import stroom.query.shared.ParseQuickFilterResult;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two endpoints the Advanced Query dialog calls: open parses, OK formats, and neither turns a
 * bad input into an HTTP error - the reason rides back on the result.
 */
class TestExpressionResourceImplQuickFilter {

    private static final QueryField NAME = QueryField.createUiText("name");
    private static final QueryField STATUS = QueryField.createUiText("status");
    private static final List<QueryField> DEFAULTS = List.of(NAME);
    private static final List<QueryField> ALL = List.of(NAME, STATUS);

    private final ExpressionResourceImpl resource = new ExpressionResourceImpl();

    @Test
    void testParseThenFormatIsTheIdentityOnCanonicalText() {
        final ParseQuickFilterResult parsed = resource.parseQuickFilter(
                new ParseQuickFilterRequest("abc status:!live", DEFAULTS, ALL));
        assertThat(parsed.isOk()).isTrue();
        assertThat(parsed.getExpression()).isNotNull();

        final FormatQuickFilterResult formatted = resource.formatQuickFilter(
                new FormatQuickFilterRequest(parsed.getExpression(), DEFAULTS, ALL));
        assertThat(formatted.isOk()).isTrue();
        assertThat(formatted.getText()).isEqualTo("abc status:!live");
    }

    @Test
    void testBlankTextParsesToNothing() {
        for (final String blank : new String[]{null, "", "   "}) {
            final ParseQuickFilterResult parsed = resource.parseQuickFilter(
                    new ParseQuickFilterRequest(blank, DEFAULTS, ALL));
            assertThat(parsed.isOk()).isTrue();
            assertThat(parsed.getExpression()).isNull();
        }
        assertThat(resource.formatQuickFilter(new FormatQuickFilterRequest(null, DEFAULTS, ALL)).getText())
                .isEmpty();
    }

    @Test
    void testParseErrorIsPositionalNotAnHttpError() {
        final ParseQuickFilterResult parsed = resource.parseQuickFilter(
                new ParseQuickFilterRequest("abc and", DEFAULTS, ALL));
        assertThat(parsed.isOk()).isFalse();
        assertThat(parsed.getExpression()).isNull();
        assertThat(parsed.getError().getText()).contains("AND");
        assertThat(parsed.getError().getFrom()).isNotNull();
    }

    @Test
    void testFormatErrorNamesTheOffendingTerm() {
        final ExpressionOperator tree = ExpressionOperator.builder().op(Op.AND).addTerm(
                ExpressionTerm.builder().field("status").condition(Condition.IN).value("a,b").build()).build();
        final FormatQuickFilterResult formatted = resource.formatQuickFilter(
                new FormatQuickFilterRequest(tree, DEFAULTS, ALL));
        assertThat(formatted.isOk()).isFalse();
        assertThat(formatted.getText()).isNull();
        assertThat(formatted.getError()).contains("status").contains(Condition.IN.getDisplayValue());
    }
}
