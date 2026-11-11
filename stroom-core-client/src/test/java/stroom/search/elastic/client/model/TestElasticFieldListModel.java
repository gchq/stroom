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

package stroom.search.elastic.client.model;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.FieldType;
import stroom.search.elastic.client.model.ElasticFieldListModel.SortColumn;
import stroom.search.elastic.client.model.ElasticFieldRow.Kind;
import stroom.search.elastic.shared.ElasticDenseVectorSettings;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticFieldListModel {

    private ElasticFieldListModel model;

    @BeforeEach
    void setUp() {
        model = new ElasticFieldListModel();
        model.read(List.of(
                        field("title", FieldType.TEXT, null),
                        field("title.keyword", FieldType.KEYWORD, "title"),
                        field("host", FieldType.KEYWORD, null),
                        field("bytes", FieldType.LONG, null),
                        field("embedding", FieldType.DENSE_VECTOR, null),
                        ElasticIndexField.builder()
                                .fldName("status")
                                .fldType(FieldType.KEYWORD)
                                .conflictingTypes(Map.of("index-a", "long", "index-b", "keyword"))
                                .build()),
                List.of(
                        ElasticIndexFieldSettings.builder("gone").enabled(false).build(),
                        ElasticIndexFieldSettings.builder("category").virtualGroups(List.of()).build(),
                        ElasticIndexFieldSettings.builder("host").virtualGroups(List.of()).build()));
    }

    @Test
    void testMultiFieldFoldsUnderCollapsedParent() {
        final List<ElasticFieldRow> rows = buildRows(Set.of());

        assertThat(names(rows))
                .containsExactly("bytes", "category", "embedding", "gone", "host", "host", "status", "title");
        final ElasticFieldRow title = rows.get(rows.size() - 1);
        assertThat(title.getExpander().isLeaf()).isFalse();
        assertThat(title.getExpander().isExpanded()).isFalse();
        assertThat(rows.get(0).getExpander().isLeaf()).isTrue();
    }

    @Test
    void testExpandedParentShowsMultiFieldAfterIt() {
        final List<ElasticFieldRow> rows = buildRows(Set.of("title"));

        assertThat(names(rows)).endsWith("title", "title.keyword");
        final ElasticFieldRow child = rows.get(rows.size() - 1);
        assertThat(child.getExpander().getDepth()).isEqualTo(1);
        assertThat(rows.get(rows.size() - 2).getExpander().isExpanded()).isTrue();
    }

    @Test
    void testRowKindsAndStateNotes() {
        final List<ElasticFieldRow> rows = buildRows(Set.of());

        final ElasticFieldRow missing = find(rows, "gone", Kind.MISSING);
        assertThat(missing.getStateNote()).isEqualTo(ElasticFieldListModel.STATE_MISSING);
        assertThat(missing.isEnabled()).isFalse();
        assertThat(missing.isEnabledEditable()).isFalse();

        final ElasticFieldRow virtual = find(rows, "category", Kind.VIRTUAL);
        assertThat(virtual.getStateNote()).isNull();
        assertThat(virtual.getTypeLabel()).isEqualTo(ElasticFieldListModel.VIRTUAL_FILTER_TYPE);
        assertThat(virtual.getFilterType()).isEqualTo(ElasticFieldListModel.VIRTUAL_FILTER_TYPE);
        assertThat(virtual.isEnabledEditable()).isTrue();

        final ElasticFieldRow clashing = find(rows, "host", Kind.CLASHING_VIRTUAL);
        assertThat(clashing.getStateNote()).isEqualTo(ElasticFieldListModel.STATE_CLASHING);
        assertThat(clashing.isEnabledEditable()).isFalse();

        // The mapped field wins over the virtual field of the same name and doesn't take its settings
        final ElasticFieldRow mappedHost = find(rows, "host", Kind.MAPPED);
        assertThat(mappedHost.getSettings()).isNull();
        assertThat(mappedHost.getStateNote()).isNull();
        assertThat(mappedHost.isEnabled()).isTrue();

        final ElasticFieldRow conflict = find(rows, "status", Kind.MAPPED);
        assertThat(conflict.getTypeLabel()).isEqualTo(ElasticFieldListModel.CONFLICT_TYPE_LABEL);
        assertThat(conflict.getStateNote()).isEqualTo("Type conflict: keyword, long");
        assertThat(conflict.isEnabled()).isFalse();
        assertThat(conflict.isEnabledEditable()).isFalse();
    }

    @Test
    void testSetVirtualGroups() {
        final List<ElasticVirtualFieldGroup> groups = List.of(
                new ElasticVirtualFieldGroup("web", ExpressionOperator.builder().build()));

        assertThat(model.setVirtualGroups("category", groups)).isTrue();
        assertThat(model.findSettings("category").getVirtualGroups()).isEqualTo(groups);
    }

    @Test
    void testSetVirtualGroupsNeverMakesAnotherFieldVirtual() {
        // A group edited for a virtual field must not be applied to another field, e.g. one selected while the
        // group dialog was open
        final List<ElasticVirtualFieldGroup> groups = List.of(
                new ElasticVirtualFieldGroup("web", ExpressionOperator.builder().build()));

        assertThat(model.setVirtualGroups("bytes", groups)).isFalse();
        assertThat(model.setVirtualGroups("gone", groups)).isFalse();
        assertThat(model.setVirtualGroups("unknown", groups)).isFalse();
        assertThat(model.findSettings("bytes")).isNull();
        assertThat(model.findSettings("gone").isVirtual()).isFalse();
        assertThat(model.findSettings("unknown")).isNull();
    }

    @Test
    void testExpandableNames() {
        // Only fields with multi-fields can be expanded
        assertThat(model.getExpandableNames()).containsExactly("title");
    }

    @Test
    void testMandatoryFieldEnabledNotEditable() {
        model.setMandatoryFieldNames(Set.of("bytes"));

        final ElasticFieldRow bytes = find(buildRows(Set.of()), "bytes", Kind.MAPPED);

        assertThat(bytes.isEnabled()).isTrue();
        assertThat(bytes.isEnabledEditable()).isFalse();
    }

    @Test
    void testVectorSettingsHaveNoStateNote() {
        model.putSettings(ElasticIndexFieldSettings.builder("embedding")
                .denseVector(new ElasticDenseVectorSettings(null, "title", "score"))
                .enabled(false)
                .build());

        final ElasticFieldRow embedding = find(buildRows(Set.of()), "embedding", Kind.MAPPED);

        assertThat(embedding.getSettings().getDenseVector()).isNotNull();
        assertThat(embedding.getStateNote()).isNull();
    }

    @Test
    void testDisablingParentCascadesToMultiField() {
        model.setEnabled("title", false);

        final List<ElasticFieldRow> rows = buildRows(Set.of("title"));
        final ElasticFieldRow title = find(rows, "title", Kind.MAPPED);
        final ElasticFieldRow keyword = find(rows, "title.keyword", Kind.MAPPED);

        assertThat(title.isEnabled()).isFalse();
        assertThat(title.isEnabledEditable()).isTrue();
        // Being disabled is shown by the Enabled column, not as a state note
        assertThat(title.getStateNote()).isNull();
        assertThat(keyword.isEnabled()).isFalse();
        assertThat(keyword.isEnabledEditable()).isFalse();
        assertThat(model.isFieldEnabled("title.keyword")).isFalse();
    }

    @Test
    void testSortDescendingKeepsChildrenUnderParent() {
        final List<ElasticFieldRow> rows = model.buildRows(null,
                List.of(),
                SortColumn.NAME,
                false,
                Set.of("title"));

        assertThat(names(rows)).startsWith("title", "title.keyword", "status");
        assertThat(names(rows)).endsWith("bytes");
    }

    @Test
    void testSortByType() {
        final List<ElasticFieldRow> rows = model.buildRows(null,
                List.of(),
                SortColumn.TYPE,
                true,
                Set.of());

        // Missing fields have no type, so sort first, and "Virtual" sorts after the field types
        assertThat(rows.get(0).getName()).isEqualTo("gone");
        assertThat(rows.get(rows.size() - 1).getTypeLabel()).isEqualTo(ElasticFieldListModel.VIRTUAL_FILTER_TYPE);
    }

    @Test
    void testSortByStatus() {
        final List<ElasticFieldRow> rows = model.buildRows(null,
                List.of(),
                SortColumn.STATUS,
                false,
                Set.of());

        // Fields with a problem sort before normal fields, which have no status, including virtual fields
        assertThat(rows.get(0).getStateNote()).isNotNull();
        assertThat(rows.get(rows.size() - 1).getStateNote()).isNull();
        assertThat(find(rows, "category", Kind.VIRTUAL).getStateNote()).isNull();
    }

    @Test
    void testSortByEnabled() {
        final List<ElasticFieldRow> rows = model.buildRows(null,
                List.of(),
                SortColumn.ENABLED,
                true,
                Set.of());

        assertThat(rows.get(0).isEnabled()).isFalse();
        assertThat(rows.get(rows.size() - 1).isEnabled()).isTrue();
    }

    @Test
    void testNameFilterOnMultiFieldKeepsParentExpanded() {
        final List<ElasticFieldRow> rows = model.buildRows("KEYWORD",
                List.of(),
                SortColumn.NAME,
                true,
                Set.of());

        assertThat(names(rows)).containsExactly("title", "title.keyword");
        assertThat(rows.get(0).getExpander().isExpanded()).isTrue();
    }

    @Test
    void testTypeFilter() {
        final List<ElasticFieldRow> virtualRows = model.buildRows(null,
                List.of(ElasticFieldListModel.VIRTUAL_FILTER_TYPE),
                SortColumn.NAME,
                true,
                Set.of());
        assertThat(names(virtualRows)).containsExactly("category", "host");

        // A parent that doesn't match is shown without its non-matching multi-fields
        final List<ElasticFieldRow> textRows = model.buildRows(null,
                List.of(FieldType.TEXT.getDisplayValue()),
                SortColumn.NAME,
                true,
                Set.of("title"));
        assertThat(names(textRows)).containsExactly("title");
        assertThat(textRows.get(0).getExpander().isLeaf()).isTrue();
    }

    @Test
    void testTypeFilterOptions() {
        assertThat(model.getTypeFilterOptions()).containsExactly(
                ElasticFieldListModel.CONFLICT_TYPE_LABEL,
                FieldType.DENSE_VECTOR.getDisplayValue(),
                FieldType.KEYWORD.getDisplayValue(),
                FieldType.LONG.getDisplayValue(),
                FieldType.TEXT.getDisplayValue(),
                ElasticFieldListModel.VIRTUAL_FILTER_TYPE);
    }

    @Test
    void testSettingsListDropsDefaultsAndKeepsOrder() {
        final List<ElasticIndexFieldSettings> original = model.getSettingsList();

        model.setEnabled("bytes", false);
        assertThat(model.getSettingsList()).extracting(ElasticIndexFieldSettings::getFieldName)
                .containsExactly("gone", "category", "host", "bytes");

        model.setEnabled("bytes", true);
        model.setEnabled("gone", true);
        model.setEnabled("gone", false);
        assertThat(model.getSettingsList()).isEqualTo(original);
    }

    @Test
    void testRemoveSettings() {
        assertThat(model.removeSettings("category")).isTrue();
        assertThat(model.removeSettings("category")).isFalse();
        assertThat(model.findRow("category")).isNull();
        assertThat(model.hasSettings("gone")).isTrue();
        assertThat(model.hasSettings("bytes")).isFalse();
    }

    @Test
    void testAddVirtualField() {
        assertThat(model.addVirtualField(" sources ")).isTrue();
        assertThat(model.addVirtualField("targets")).isTrue();

        final ElasticFieldRow row = model.findRow("sources", Kind.VIRTUAL);
        assertThat(row).isNotNull();
        assertThat(row.getSettings().getVirtualGroups()).isEmpty();
        assertThat(model.getSettingsList()).extracting(ElasticIndexFieldSettings::getFieldName)
                .endsWith("sources", "targets");
    }

    @Test
    void testAddVirtualFieldRejectsInvalidNames() {
        final List<ElasticIndexFieldSettings> before = model.getSettingsList();

        assertThat(model.addVirtualField(" ")).isFalse();
        assertThat(model.addVirtualField(null)).isFalse();
        // Clashes with a mapped field
        assertThat(model.addVirtualField("bytes")).isFalse();
        // Already used by a virtual field or by settings for a missing field
        assertThat(model.addVirtualField("category")).isFalse();
        assertThat(model.addVirtualField("gone")).isFalse();
        assertThat(model.getSettingsList()).isEqualTo(before);
    }

    @Test
    void testValidateNewVirtualFieldName() {
        assertThat(model.validateVirtualFieldName(null, ""))
                .isEqualTo(ElasticFieldListModel.ERROR_EMPTY_NAME);
        assertThat(model.validateVirtualFieldName(null, "bytes"))
                .isEqualTo(ElasticFieldListModel.ERROR_CLASHES_WITH_MAPPED);
        assertThat(model.validateVirtualFieldName(null, "category"))
                .isEqualTo(ElasticFieldListModel.ERROR_NAME_IN_USE);
        assertThat(model.validateVirtualFieldName(null, "sources")).isNull();
    }

    @Test
    void testValidateVirtualFieldRename() {
        // Keeping the name is allowed, even for a virtual field whose name clashes with a mapped field
        assertThat(model.validateVirtualFieldRename("category", " category ")).isNull();
        assertThat(model.validateVirtualFieldRename("host", "host")).isNull();
        assertThat(model.validateVirtualFieldRename("host", "bytes"))
                .isEqualTo(ElasticFieldListModel.ERROR_CLASHES_WITH_MAPPED);
        assertThat(model.validateVirtualFieldRename("category", "gone"))
                .isEqualTo(ElasticFieldListModel.ERROR_NAME_IN_USE);
        assertThat(model.validateVirtualFieldRename("category", " "))
                .isEqualTo(ElasticFieldListModel.ERROR_EMPTY_NAME);
        assertThat(model.validateVirtualFieldRename("host", "sources")).isNull();
    }

    @Test
    void testValidateVirtualFieldName() {
        assertThat(model.validateVirtualFieldName("category", " "))
                .isEqualTo(ElasticFieldListModel.ERROR_EMPTY_NAME);
        assertThat(model.validateVirtualFieldName("category", null))
                .isEqualTo(ElasticFieldListModel.ERROR_EMPTY_NAME);
        assertThat(model.validateVirtualFieldName("category", "bytes"))
                .isEqualTo(ElasticFieldListModel.ERROR_CLASHES_WITH_MAPPED);
        assertThat(model.validateVirtualFieldName("category", "gone"))
                .isEqualTo(ElasticFieldListModel.ERROR_NAME_IN_USE);
        assertThat(model.validateVirtualFieldName("category", "category")).isNull();
        assertThat(model.validateVirtualFieldName("category", " sources ")).isNull();
    }

    @Test
    void testRenameVirtualFieldKeepsPosition() {
        assertThat(model.renameVirtualField("category", " sources ")).isTrue();

        assertThat(model.getSettingsList()).extracting(ElasticIndexFieldSettings::getFieldName)
                .containsExactly("gone", "sources", "host");
        assertThat(model.findRow("sources", Kind.VIRTUAL)).isNotNull();
        assertThat(model.findRow("category")).isNull();
    }

    @Test
    void testRenameRejectsInvalidNames() {
        assertThat(model.renameVirtualField("category", "bytes")).isFalse();
        assertThat(model.renameVirtualField("category", "")).isFalse();
        assertThat(model.renameVirtualField("gone", "other")).isFalse();
        assertThat(model.renameVirtualField("unknown", "other")).isFalse();
        assertThat(model.findRow("category", Kind.VIRTUAL)).isNotNull();
    }

    @Test
    void testRenameClashingVirtualFieldResolvesClash() {
        assertThat(model.renameVirtualField("host", "hostGroup")).isTrue();

        assertThat(model.findRow("hostGroup", Kind.VIRTUAL)).isNotNull();
        assertThat(model.findRow("host", Kind.CLASHING_VIRTUAL)).isNull();
        assertThat(model.findRow("host", Kind.MAPPED)).isNotNull();
    }

    @Test
    void testKeywordAndRerankFieldNames() {
        assertThat(model.getKeywordFieldNames()).containsExactly("host", "title.keyword");
        assertThat(model.getRerankTextFieldNames()).containsExactly("host", "title", "title.keyword");
    }

    @Test
    void testKeywordFieldNamesOnlyNativeKeyword() {
        final ElasticFieldListModel wildcardModel = new ElasticFieldListModel();
        wildcardModel.read(List.of(
                field("host", FieldType.KEYWORD, null),
                ElasticIndexField.builder()
                        .fldName("pattern").fldType(FieldType.KEYWORD).nativeType("wildcard").build(),
                ElasticIndexField.builder()
                        .fldName("release").fldType(FieldType.KEYWORD).nativeType("version").build()),
                List.of());

        // The server only accepts native keyword fields in a group's expression
        assertThat(wildcardModel.getKeywordFieldNames()).containsExactly("host");
    }

    @Test
    void testFindRowPrefersMapped() {
        assertThat(model.findRow("host").getKind()).isEqualTo(Kind.MAPPED);
        assertThat(model.findRow("gone").getKind()).isEqualTo(Kind.MISSING);
        assertThat(model.findRow("nothing")).isNull();
        assertThat(model.findRow(null)).isNull();
        assertThat(model.findRow("gone", Kind.MAPPED)).isNull();
    }

    @Test
    void testRefreshedFieldsKeepSettings() {
        model.setEnabled("bytes", false);
        final List<ElasticIndexField> fields = new ArrayList<>();
        fields.add(field("gone", FieldType.LONG, null));
        model.setFields(fields);

        // The settings for "gone" now belong to a mapped field, and "bytes" has become missing
        assertThat(model.findRow("gone").getKind()).isEqualTo(Kind.MAPPED);
        assertThat(model.findRow("gone").isEnabled()).isFalse();
        assertThat(model.findRow("bytes").getKind()).isEqualTo(Kind.MISSING);
    }

    @Test
    void testServerVirtualFieldsInMappingAreIgnored() {
        model.setFields(List.of(ElasticIndexField.builder()
                .fldName("category")
                .fldType(FieldType.KEYWORD)
                .virtual(true)
                .build()));

        assertThat(model.findRow("category").getKind()).isEqualTo(Kind.VIRTUAL);
    }

    private List<ElasticFieldRow> buildRows(final Set<String> expanded) {
        return model.buildRows("", null, SortColumn.NAME, true, new HashSet<>(expanded));
    }

    private static ElasticFieldRow find(final List<ElasticFieldRow> rows, final String name, final Kind kind) {
        return rows.stream()
                .filter(row -> row.getName().equals(name) && row.getKind() == kind)
                .findFirst()
                .orElseThrow();
    }

    private static List<String> names(final List<ElasticFieldRow> rows) {
        return rows.stream().map(ElasticFieldRow::getName).toList();
    }

    private static ElasticIndexField field(final String name, final FieldType type, final String parent) {
        return ElasticIndexField.builder()
                .fldName(name)
                .fldType(type)
                .nativeType(type.getDisplayValue().toLowerCase())
                .multiFieldParent(parent)
                .build();
    }
}
