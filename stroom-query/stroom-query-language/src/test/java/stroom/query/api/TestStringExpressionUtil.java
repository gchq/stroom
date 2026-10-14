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

package stroom.query.api;

import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.IndexFieldFields;
import stroom.query.api.datasource.QueryField;
import stroom.query.language.filter.FieldProviderImpl;
import stroom.query.language.filter.SimpleStringExpressionParser;
import stroom.query.language.filter.SimpleStringExpressionParser.FieldProvider;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Lives in stroom-query-language rather than stroom-query-api because the point is that the
// generated text is accepted by the quick filter parser.
class TestStringExpressionUtil {

    @Test
    void testEqualToIsAcceptedByTheIndexFieldNameField() {
        // IndexFieldDaoImpl parses the lookup made by IndexFieldServiceImpl.getIndexField against
        // this SQL text field. When the helper produced `==` the parser rejected it, the DAO
        // returned no rows and every field was reported as unknown during indexing.
        final ExpressionTerm term = parseSingleTerm(
                IndexFieldFields.NAME_FIELD,
                StringExpressionUtil.equalTo("StreamId"));

        assertThat(term.getField()).isEqualTo(IndexFieldFields.NAME);
        assertThat(term.getCondition()).isEqualTo(Condition.EQUALS);
        assertThat(term.getValue()).isEqualTo("StreamId");
    }

    @Test
    void testEqualToIsAcceptedByTheGeneralTextNameField() {
        // The in-memory field providers declare their name field as plain text.
        final QueryField nameField = QueryField.createText(IndexFieldFields.NAME);
        final ExpressionTerm term = parseSingleTerm(nameField, StringExpressionUtil.equalTo("Feed"));

        assertThat(term.getCondition()).isEqualTo(Condition.EQUALS);
        assertThat(term.getValue()).isEqualTo("Feed");
    }

    @Test
    void testEqualToKeepsNamesContainingSpacesAsOneValue() {
        // Field names such as "Feed (Keyword)" must not be split into separate terms.
        final ExpressionTerm term = parseSingleTerm(
                IndexFieldFields.NAME_FIELD,
                StringExpressionUtil.equalTo("Feed (Keyword)"));

        assertThat(term.getValue()).isEqualTo("Feed (Keyword)");
    }

    private static ExpressionTerm parseSingleTerm(final QueryField field, final String filter) {
        final FieldProvider fieldProvider = new FieldProviderImpl(List.of(field), List.of(field));
        final Optional<ExpressionOperator> optional = SimpleStringExpressionParser.create(fieldProvider, filter);

        assertThat(optional).isPresent();
        final List<ExpressionItem> children = optional.get().getChildren();
        assertThat(children).hasSize(1);
        assertThat(children.getFirst()).isInstanceOf(ExpressionTerm.class);
        return (ExpressionTerm) children.getFirst();
    }
}
