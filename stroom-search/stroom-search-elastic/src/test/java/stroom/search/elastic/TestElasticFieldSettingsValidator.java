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

package stroom.search.elastic;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.util.shared.EntityServiceException;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestElasticFieldSettingsValidator {

    private static final List<ElasticIndexField> FIELDS = List.of(
            field("host", FieldType.KEYWORD, "keyword", null),
            field("message", FieldType.TEXT, "text", null),
            field("port", FieldType.KEYWORD, "keyword", Map.of("a", "long", "b", "keyword")));

    @Test
    void testValid() {
        assertValid(virtual("Category", group("web", term("host", "web01"))));
    }

    @Test
    void testValidWithoutMapping() {
        // When the mapping can't be read, a doc can still be saved
        final ElasticIndexDoc doc = doc(virtual("Category", group("web", term("unknown", "x"))));

        assertThatCode(() -> ElasticFieldSettingsValidator.validate(doc, List.of())).doesNotThrowAnyException();
    }

    @Test
    void testDuplicateSettings() {
        assertInvalid("Field 'host' has more than one set of settings",
                ElasticIndexFieldSettings.builder("host").enabled(false).build(),
                ElasticIndexFieldSettings.builder("host").enabled(false).build());
    }

    @Test
    void testVirtualFieldClashesWithMappedField() {
        assertInvalid("Virtual field 'host' has the same name as a field of the index",
                virtual("host", group("web", term("host", "web01"))));
    }

    @Test
    void testDuplicateGroupNames() {
        assertInvalid("Virtual field 'Category' has more than one group named 'web'",
                virtual("Category", group("web", term("host", "a")), group("web", term("host", "b"))));
    }

    @Test
    void testBlankGroupName() {
        assertInvalid("A group of virtual field 'Category' has no name",
                virtual("Category", group(" ", term("host", "a"))));
    }

    @Test
    void testGroupWithNoConditions() {
        assertInvalid("Group 'web' of virtual field 'Category' has no conditions",
                virtual("Category", new ElasticVirtualFieldGroup("web", ExpressionOperator.builder().build())));
    }

    @Test
    void testGroupWithOnlyDisabledConditions() {
        final ExpressionTerm disabled = ExpressionTerm.builder()
                .field("host")
                .condition(Condition.EQUALS)
                .value("a")
                .enabled(false)
                .build();

        assertInvalid("Group 'web' of virtual field 'Category' has no conditions",
                virtual("Category", group("web", disabled)));
    }

    @Test
    void testGroupUsingNonKeywordField() {
        assertInvalid("Group 'web' of virtual field 'Category' uses field 'message', which is not a keyword field " +
                      "of the index",
                virtual("Category", group("web", term("message", "a"))));
    }

    @Test
    void testGroupUsingConflictingField() {
        assertInvalid("Group 'web' of virtual field 'Category' uses field 'port', which is not a keyword field " +
                      "of the index",
                virtual("Category", group("web", term("port", "80"))));
    }

    @Test
    void testGroupUsingVirtualField() {
        assertInvalid("Group 'web' of virtual field 'Category' uses virtual field 'Role'. Groups may only use " +
                      "keyword fields of the index.",
                virtual("Role", group("admin", term("host", "a"))),
                virtual("Category", group("web", term("Role", "admin"))));
    }

    private static void assertValid(final ElasticIndexFieldSettings... settings) {
        assertThatCode(() -> ElasticFieldSettingsValidator.validate(doc(settings), FIELDS))
                .doesNotThrowAnyException();
    }

    private static void assertInvalid(final String message, final ElasticIndexFieldSettings... settings) {
        assertThatThrownBy(() -> ElasticFieldSettingsValidator.validate(doc(settings), FIELDS))
                .isInstanceOf(EntityServiceException.class)
                .hasMessage(message);
    }

    private static ElasticIndexDoc doc(final ElasticIndexFieldSettings... settings) {
        return ElasticIndexDoc.builder().uuid("uuid").fieldSettings(List.of(settings)).build();
    }

    private static ElasticIndexFieldSettings virtual(final String name, final ElasticVirtualFieldGroup... groups) {
        return ElasticIndexFieldSettings.builder(name).virtualGroups(List.of(groups)).build();
    }

    private static ElasticVirtualFieldGroup group(final String name, final ExpressionTerm term) {
        return new ElasticVirtualFieldGroup(name, ExpressionOperator.builder().addTerm(term).build());
    }

    private static ExpressionTerm term(final String field, final String value) {
        return ExpressionTerm.builder().field(field).condition(Condition.EQUALS).value(value).build();
    }

    private static ElasticIndexField field(final String name,
                                           final FieldType type,
                                           final String nativeType,
                                           final Map<String, String> conflictingTypes) {
        return ElasticIndexField.builder()
                .fldName(name)
                .fldType(type)
                .nativeType(nativeType)
                .conflictingTypes(conflictingTypes)
                .build();
    }
}
