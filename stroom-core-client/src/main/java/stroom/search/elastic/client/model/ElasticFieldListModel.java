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

import stroom.query.api.datasource.FieldType;
import stroom.search.elastic.client.model.ElasticFieldRow.Kind;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.util.shared.Expander;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/// The client side working copy of the fields and field settings of an Elasticsearch index doc, from which the rows
/// of the Fields tab are built.
///
/// Edits change only this copy. [#getSettingsList()] gives the settings to write back to the doc, without any
/// entries that hold only default values.
public class ElasticFieldListModel {

    /// The Field Type shown for virtual fields, and the type filter value that matches them. It is only used in this
    /// list, not added to the shared field types.
    public static final String VIRTUAL_FILTER_TYPE = "Virtual";
    /// The type label shown for a field whose source indices map it to different types.
    public static final String CONFLICT_TYPE_LABEL = "Conflict";

    static final String STATE_MISSING = "Missing from all source indices";
    static final String STATE_CLASHING = "Hidden by a mapped field of the same name";
    static final String STATE_TYPE_CONFLICT_PREFIX = "Type conflict: ";

    static final String ERROR_EMPTY_NAME = "Enter a name for the virtual field.";
    static final String ERROR_CLASHES_WITH_MAPPED = "Name clashes with a mapped field.";
    static final String ERROR_NAME_IN_USE = "Another field already has this name.";
    // The only native type a virtual field group's expression may use
    private static final String KEYWORD_NATIVE_TYPE = "keyword";

    private final List<ElasticIndexField> fields = new ArrayList<>();
    private final Map<String, ElasticIndexField> fieldsByName = new HashMap<>();
    private final Map<String, ElasticIndexFieldSettings> settingsByName = new LinkedHashMap<>();
    private final Set<String> mandatoryFieldNames = new HashSet<>();

    /// Replaces the fields and settings held by this model.
    ///
    /// @param fields   the fields read from the index mapping
    /// @param settings the field settings of the index doc
    public void read(final List<ElasticIndexField> fields, final List<ElasticIndexFieldSettings> settings) {
        setFields(fields);
        settingsByName.clear();
        NullSafe.list(settings).forEach(entry -> {
            if (entry != null && entry.getFieldName() != null) {
                settingsByName.put(entry.getFieldName(), entry);
            }
        });
    }

    /// Sets the fields Stroom needs to search the index, which can't be disabled.
    ///
    /// @param names the names of the fields, such as the time field, `StreamId` and `EventId`
    public void setMandatoryFieldNames(final Set<String> names) {
        mandatoryFieldNames.clear();
        mandatoryFieldNames.addAll(NullSafe.set(names));
    }

    /// @return the names of the fields that have multi-fields, which can be expanded to show them
    public Set<String> getExpandableNames() {
        final Set<String> names = new HashSet<>();
        for (final ElasticIndexField field : fields) {
            if (field.getMultiFieldParent() != null && fieldsByName.containsKey(field.getMultiFieldParent())) {
                names.add(field.getMultiFieldParent());
            }
        }
        return names;
    }

    /// @param fieldName the full name of a field
    /// @return `true` if Stroom needs the field to search the index, so it can't be disabled
    public boolean isMandatory(final String fieldName) {
        return mandatoryFieldNames.contains(fieldName);
    }

    /// Replaces the mapped fields, keeping the settings as they are.
    ///
    /// @param fields the fields read from the index mapping
    public void setFields(final List<ElasticIndexField> fields) {
        this.fields.clear();
        this.fieldsByName.clear();
        NullSafe.list(fields).forEach(field -> {
            if (field != null && field.getFldName() != null && !field.isVirtual()) {
                this.fields.add(field);
                this.fieldsByName.put(field.getFldName(), field);
            }
        });
    }

    /// @return the settings to store in the index doc, in their original order, without entries that hold only
    /// default values
    public List<ElasticIndexFieldSettings> getSettingsList() {
        return settingsByName.values()
                .stream()
                .filter(settings -> !settings.isDefault())
                .collect(Collectors.toList());
    }

    /// @param fieldName the full name of a field
    /// @return the field's settings, or `null` if it has none
    public ElasticIndexFieldSettings findSettings(final String fieldName) {
        return fieldName == null
                ? null
                : settingsByName.get(fieldName);
    }

    /// @param fieldName the full name of a field
    /// @return the mapped field of that name, or `null` if there is none
    public ElasticIndexField findField(final String fieldName) {
        return fieldName == null
                ? null
                : fieldsByName.get(fieldName);
    }

    /// Builds the row for one field, without tree state.
    ///
    /// @param fieldName the full name of the field
    /// @param kind      what the row represents
    /// @return the row, or `null` if the model holds no such field
    public ElasticFieldRow findRow(final String fieldName, final Kind kind) {
        if (fieldName == null || kind == null) {
            return null;
        }
        final ElasticIndexField field = fieldsByName.get(fieldName);
        final ElasticIndexFieldSettings settings = settingsByName.get(fieldName);
        final boolean exists = switch (kind) {
            case MAPPED -> field != null;
            case VIRTUAL -> field == null && settings != null && settings.isVirtual();
            case CLASHING_VIRTUAL -> field != null && settings != null && settings.isVirtual();
            case MISSING -> field == null && settings != null && !settings.isVirtual();
        };
        return exists
                ? createRow(fieldName, kind, null)
                : null;
    }

    /// Finds the row for a field name, preferring the mapped field when a virtual field has the same name.
    ///
    /// @param fieldName the full name of the field
    /// @return the row, or `null` if the model holds no such field
    public ElasticFieldRow findRow(final String fieldName) {
        for (final Kind kind : Kind.values()) {
            final ElasticFieldRow row = findRow(fieldName, kind);
            if (row != null) {
                return row;
            }
        }
        return null;
    }

    /// A field is enabled unless it, or the field it is a multi-field of, has been disabled.
    ///
    /// @param fieldName the full name of a field
    /// @return `true` if the field is enabled
    public boolean isFieldEnabled(final String fieldName) {
        if (!isOwnEnabled(fieldName)) {
            return false;
        }
        final ElasticIndexField field = fieldsByName.get(fieldName);
        final String parent = NullSafe.get(field, ElasticIndexField::getMultiFieldParent);
        return parent == null || isOwnEnabled(parent);
    }

    /// Sets whether a field is enabled.
    ///
    /// @param fieldName the full name of the field
    /// @param enabled   `true` to enable the field
    public void setEnabled(final String fieldName, final boolean enabled) {
        final ElasticIndexFieldSettings existing = settingsByName.get(fieldName);
        final ElasticIndexFieldSettings.Builder builder = existing == null
                ? ElasticIndexFieldSettings.builder(fieldName)
                : existing.copy();
        putSettings(builder.enabled(enabled).build());
    }

    /// Adds or replaces the settings for a field, keeping the position of any existing entry.
    ///
    /// @param settings the settings, whose field name identifies the entry
    public void putSettings(final ElasticIndexFieldSettings settings) {
        Objects.requireNonNull(settings);
        Objects.requireNonNull(settings.getFieldName());
        settingsByName.put(settings.getFieldName(), settings);
    }

    /// Sets the groups of a virtual field. Nothing changes if the field is not a virtual field, e.g. because it was
    /// renamed or deleted while a group was being edited, so another field is never made a virtual field.
    ///
    /// @param fieldName the name of the virtual field
    /// @param groups    its new groups
    /// @return `true` if the groups were set
    public boolean setVirtualGroups(final String fieldName, final List<ElasticVirtualFieldGroup> groups) {
        final ElasticIndexFieldSettings existing = settingsByName.get(fieldName);
        if (existing == null || !existing.isVirtual()) {
            return false;
        }
        putSettings(existing.copy().virtualGroups(new ArrayList<>(groups)).build());
        return true;
    }

    /// Removes the settings of a field. For a virtual field this deletes the field.
    ///
    /// @param fieldName the full name of the field
    /// @return `true` if the field had settings
    public boolean removeSettings(final String fieldName) {
        return settingsByName.remove(fieldName) != null;
    }

    /// @param fieldName the full name of a field
    /// @return `true` if the field has settings that differ from the defaults
    public boolean hasSettings(final String fieldName) {
        final ElasticIndexFieldSettings settings = settingsByName.get(fieldName);
        return settings != null && !settings.isDefault();
    }

    /// Adds a virtual field with no groups.
    ///
    /// @param name the name of the new field, which is trimmed
    /// @return `true` if the field was added, or `false` if the name fails
    /// [#validateVirtualFieldName(String, String)]
    public boolean addVirtualField(final String name) {
        if (validateVirtualFieldName(null, name) != null) {
            return false;
        }
        putSettings(ElasticIndexFieldSettings.builder(NullSafe.trim(name))
                .virtualGroups(new ArrayList<>())
                .build());
        return true;
    }

    /// Checks a new name for a virtual field.
    ///
    /// @param currentName the field's current name, or `null` for a new field
    /// @param newName     the proposed name
    /// @return a message describing why the name can't be used, or `null` if it can
    public String validateVirtualFieldName(final String currentName, final String newName) {
        final String trimmed = NullSafe.trim(newName);
        if (trimmed.isEmpty()) {
            return ERROR_EMPTY_NAME;
        }
        if (fieldsByName.containsKey(trimmed)) {
            return ERROR_CLASHES_WITH_MAPPED;
        }
        if (!trimmed.equals(currentName) && settingsByName.containsKey(trimmed)) {
            return ERROR_NAME_IN_USE;
        }
        return null;
    }

    /// Checks a name entered to rename a virtual field. Keeping the current name is always allowed, as it leaves the
    /// field unchanged, even if that name clashes with a mapped field.
    ///
    /// @param currentName the field's current name
    /// @param newName     the proposed name
    /// @return a message describing why the name can't be used, or `null` if it can
    public String validateVirtualFieldRename(final String currentName, final String newName) {
        if (NullSafe.trim(newName).equals(currentName)) {
            return null;
        }
        return validateVirtualFieldName(currentName, newName);
    }

    /// Renames a virtual field, keeping the position of its settings entry. Names that fail
    /// [#validateVirtualFieldName(String, String)] are not applied, except that a field whose name already clashes
    /// with a mapped field may keep that name.
    ///
    /// @param currentName the field's current name
    /// @param newName     the new name, which is trimmed
    /// @return `true` if the field was renamed
    public boolean renameVirtualField(final String currentName, final String newName) {
        final ElasticIndexFieldSettings existing = settingsByName.get(currentName);
        if (existing == null || !existing.isVirtual()) {
            return false;
        }
        final String trimmed = NullSafe.trim(newName);
        if (trimmed.equals(currentName) || validateVirtualFieldName(currentName, trimmed) != null) {
            return false;
        }

        final List<ElasticIndexFieldSettings> entries = new ArrayList<>(settingsByName.values());
        settingsByName.clear();
        for (final ElasticIndexFieldSettings entry : entries) {
            if (entry.getFieldName().equals(currentName)) {
                settingsByName.put(trimmed, entry.copy().fieldName(trimmed).build());
            } else {
                settingsByName.put(entry.getFieldName(), entry);
            }
        }
        return true;
    }

    /// @return the names of the mapped fields with native type `keyword` that are not virtual and have no type
    /// conflict, which are the fields a virtual field group's expression may use. Other types Stroom treats as
    /// keywords, such as `wildcard` and `version`, are left out, as the server rejects them in group expressions.
    public List<String> getKeywordFieldNames() {
        return fields.stream()
                .filter(field -> field.getFldType() == FieldType.KEYWORD)
                .filter(field -> KEYWORD_NATIVE_TYPE.equals(field.getNativeType()))
                .filter(field -> !field.hasTypeConflict())
                .map(ElasticIndexField::getFldName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /// @return the names of the mapped `text` and `keyword` fields that have no type conflict, which are the fields
    /// whose values may be sent to a rerank model
    public List<String> getRerankTextFieldNames() {
        return fields.stream()
                .filter(field -> field.getFldType() == FieldType.TEXT || field.getFldType() == FieldType.KEYWORD)
                .filter(field -> !field.hasTypeConflict())
                .map(ElasticIndexField::getFldName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    /// @return the values the type filter can match, which are the type labels of the rows present plus
    /// [#VIRTUAL_FILTER_TYPE] if there are virtual fields, in alphabetical order
    public List<String> getTypeFilterOptions() {
        final Set<String> options = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (final ElasticFieldRow row : createAllRows()) {
            if (NullSafe.isNonBlankString(row.getFilterType())) {
                options.add(row.getFilterType());
            }
        }
        return new ArrayList<>(options);
    }

    /// Builds the rows to show, as a flat list in display order. Multi-fields follow the field they belong to and
    /// are only included when that field is expanded. When a filter is set, only matching rows are included, along
    /// with the parent of any matching multi-field, which is then shown expanded.
    ///
    /// @param nameFilter    text the field name must contain, ignoring case, or `null` for no name filter
    /// @param typeFilter    the [ElasticFieldRow#getFilterType()] values to include, or empty for all types
    /// @param sortColumn    the column to sort on
    /// @param ascending     `true` to sort in ascending order
    /// @param expandedNames the names of the fields whose multi-fields are shown
    /// @return the rows to show
    public List<ElasticFieldRow> buildRows(final String nameFilter,
                                           final Collection<String> typeFilter,
                                           final SortColumn sortColumn,
                                           final boolean ascending,
                                           final Set<String> expandedNames) {
        final String normalisedNameFilter = NullSafe.trim(nameFilter).toLowerCase();
        final Set<String> types = typeFilter == null
                ? Set.of()
                : Set.copyOf(typeFilter);
        final boolean filtering = !normalisedNameFilter.isEmpty() || !types.isEmpty();
        final Comparator<ElasticFieldRow> comparator = createComparator(sortColumn, ascending);

        final Map<String, List<ElasticIndexField>> childrenByParent = getChildrenByParent();
        final List<ElasticFieldRow> topLevelRows = createTopLevelRows();
        topLevelRows.sort(comparator);

        final List<ElasticFieldRow> rows = new ArrayList<>();
        for (final ElasticFieldRow topLevelRow : topLevelRows) {
            final List<ElasticFieldRow> children = new ArrayList<>();
            if (topLevelRow.getKind() == Kind.MAPPED) {
                for (final ElasticIndexField child : NullSafe.list(childrenByParent.get(topLevelRow.getName()))) {
                    final ElasticFieldRow childRow = createRow(child.getFldName(),
                            Kind.MAPPED,
                            new Expander(1, false, true));
                    if (!filtering || matches(childRow, normalisedNameFilter, types)) {
                        children.add(childRow);
                    }
                }
            }
            children.sort(comparator);

            final boolean parentMatches = !filtering || matches(topLevelRow, normalisedNameFilter, types);
            if (parentMatches || !children.isEmpty()) {
                final boolean leaf = children.isEmpty();
                final boolean expanded = !leaf
                                         && (expandedNames.contains(topLevelRow.getName())
                                             || (filtering && !children.isEmpty()));
                rows.add(withExpander(topLevelRow, new Expander(0, expanded, leaf)));
                if (expanded) {
                    rows.addAll(children);
                }
            }
        }
        return rows;
    }

    private boolean isOwnEnabled(final String fieldName) {
        final ElasticIndexFieldSettings settings = settingsByName.get(fieldName);
        return settings == null || settings.isEnabled();
    }

    private Map<String, List<ElasticIndexField>> getChildrenByParent() {
        final Map<String, List<ElasticIndexField>> childrenByParent = new HashMap<>();
        for (final ElasticIndexField field : fields) {
            final String parent = field.getMultiFieldParent();
            if (parent != null && fieldsByName.containsKey(parent)) {
                childrenByParent.computeIfAbsent(parent, k -> new ArrayList<>()).add(field);
            }
        }
        return childrenByParent;
    }

    private boolean isMultiFieldWithParent(final ElasticIndexField field) {
        final String parent = field.getMultiFieldParent();
        return parent != null && fieldsByName.containsKey(parent);
    }

    private List<ElasticFieldRow> createTopLevelRows() {
        final List<ElasticFieldRow> rows = new ArrayList<>();
        for (final ElasticIndexField field : fields) {
            if (!isMultiFieldWithParent(field)) {
                rows.add(createRow(field.getFldName(), Kind.MAPPED, null));
            }
        }
        for (final ElasticIndexFieldSettings settings : settingsByName.values()) {
            final String name = settings.getFieldName();
            final boolean mapped = fieldsByName.containsKey(name);
            if (settings.isVirtual()) {
                rows.add(createRow(name, mapped
                        ? Kind.CLASHING_VIRTUAL
                        : Kind.VIRTUAL, null));
            } else if (!mapped) {
                rows.add(createRow(name, Kind.MISSING, null));
            }
        }
        return rows;
    }

    private List<ElasticFieldRow> createAllRows() {
        final List<ElasticFieldRow> rows = createTopLevelRows();
        for (final ElasticIndexField field : fields) {
            if (isMultiFieldWithParent(field)) {
                rows.add(createRow(field.getFldName(), Kind.MAPPED, null));
            }
        }
        return rows;
    }

    private ElasticFieldRow createRow(final String name, final Kind kind, final Expander expander) {
        final ElasticIndexField field = kind == Kind.MAPPED
                ? fieldsByName.get(name)
                : null;
        final ElasticIndexFieldSettings settings = settingsByName.get(name);
        // A virtual entry with the name of a mapped field belongs to the clashing virtual row, not the mapped one
        final ElasticIndexFieldSettings rowSettings = kind == Kind.MAPPED && settings != null && settings.isVirtual()
                ? null
                : settings;

        final String typeLabel;
        final String filterType;
        final String stateNote;
        final boolean enabledEditable;
        switch (kind) {
            case MAPPED -> {
                if (field.hasTypeConflict()) {
                    typeLabel = CONFLICT_TYPE_LABEL;
                    stateNote = STATE_TYPE_CONFLICT_PREFIX + String.join(", ",
                            new TreeSet<>(field.getConflictingTypes().values()));
                } else {
                    typeLabel = field.getFldType().getDisplayValue();
                    stateNote = null;
                }
                filterType = typeLabel;
                final String parent = field.getMultiFieldParent();
                final boolean parentEnabled = parent == null || isOwnEnabled(parent);
                final boolean nameHeldByVirtual = settings != null && settings.isVirtual();
                enabledEditable = !field.hasTypeConflict()
                                  && parentEnabled
                                  && !nameHeldByVirtual
                                  && !mandatoryFieldNames.contains(name);
            }
            case VIRTUAL -> {
                // Shown only in this list; virtual fields are still queried as keyword fields
                typeLabel = VIRTUAL_FILTER_TYPE;
                filterType = VIRTUAL_FILTER_TYPE;
                stateNote = null;
                enabledEditable = true;
            }
            case CLASHING_VIRTUAL -> {
                typeLabel = VIRTUAL_FILTER_TYPE;
                filterType = VIRTUAL_FILTER_TYPE;
                stateNote = STATE_CLASHING;
                enabledEditable = false;
            }
            default -> {
                typeLabel = "";
                filterType = null;
                stateNote = STATE_MISSING;
                enabledEditable = false;
            }
        }

        final boolean enabled = kind == Kind.MAPPED
                ? !field.hasTypeConflict() && isMappedFieldEnabled(field, rowSettings)
                : settings == null || settings.isEnabled();

        return new ElasticFieldRow(name,
                kind,
                field,
                rowSettings,
                typeLabel,
                filterType,
                stateNote,
                enabled,
                enabledEditable,
                expander);
    }

    private boolean isMappedFieldEnabled(final ElasticIndexField field,
                                         final ElasticIndexFieldSettings ownSettings) {
        if (ownSettings != null && !ownSettings.isEnabled()) {
            return false;
        }
        final String parent = field.getMultiFieldParent();
        return parent == null || isOwnEnabled(parent);
    }

    private static ElasticFieldRow withExpander(final ElasticFieldRow row, final Expander expander) {
        return new ElasticFieldRow(row.getName(),
                row.getKind(),
                row.getField(),
                row.getSettings(),
                row.getTypeLabel(),
                row.getFilterType(),
                row.getStateNote(),
                row.isEnabled(),
                row.isEnabledEditable(),
                expander);
    }

    private static boolean matches(final ElasticFieldRow row,
                                   final String normalisedNameFilter,
                                   final Set<String> types) {
        if (!normalisedNameFilter.isEmpty() && !row.getName().toLowerCase().contains(normalisedNameFilter)) {
            return false;
        }
        return types.isEmpty() || (row.getFilterType() != null && types.contains(row.getFilterType()));
    }

    private static Comparator<ElasticFieldRow> createComparator(final SortColumn sortColumn,
                                                                final boolean ascending) {
        final Comparator<ElasticFieldRow> byName = Comparator
                .comparing(ElasticFieldRow::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ElasticFieldRow::getName)
                .thenComparing(ElasticFieldRow::getKind);
        final Comparator<ElasticFieldRow> primary = switch (Objects.requireNonNullElse(sortColumn,
                SortColumn.NAME)) {
            case TYPE -> Comparator.comparing(ElasticFieldRow::getTypeLabel, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(byName);
            case ENABLED -> Comparator.comparing(ElasticFieldRow::isEnabled).thenComparing(byName);
            case STATUS -> Comparator.comparing(
                            (ElasticFieldRow row) -> NullSafe.string(row.getStateNote()),
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(byName);
            default -> byName;
        };
        return ascending
                ? primary
                : primary.reversed();
    }

    /// The columns the field list can be sorted on.
    public enum SortColumn {
        /// Sort on the field name.
        NAME,
        /// Sort on the field type label.
        TYPE,
        /// Sort on whether the field is enabled.
        ENABLED,
        /// Sort on the field's problem, if it has one.
        STATUS
    }
}
