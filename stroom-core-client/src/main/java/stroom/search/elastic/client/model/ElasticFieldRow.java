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

import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldSettings;
import stroom.util.shared.Expander;
import stroom.util.shared.TreeRow;

import java.util.Objects;

/// One row of the Elasticsearch index Fields tab.
///
/// A row is a field from the index mapping, a virtual field defined in the index doc's field settings, or a settings
/// entry whose field is no longer in any source index. Rows are identified by their name and [Kind], so a virtual
/// field that has the same name as a mapped field is a separate row.
public final class ElasticFieldRow implements TreeRow {

    private final String name;
    private final Kind kind;
    private final ElasticIndexField field;
    private final ElasticIndexFieldSettings settings;
    private final String typeLabel;
    private final String filterType;
    private final String stateNote;
    private final boolean enabled;
    private final boolean enabledEditable;
    private final Expander expander;

    /// @param name            the full name of the field
    /// @param kind            what the row represents
    /// @param field           the mapped field, or `null` if the row is not for a mapped field
    /// @param settings        the field's settings, or `null` if it has none
    /// @param typeLabel       the text shown in the Field Type column
    /// @param filterType      the value the type filter matches the row on, or `null` if it has none
    /// @param stateNote       why the field can't be used as normal (missing, type conflict or hidden by a mapped
    ///                        field), or `null` if it can
    /// @param enabled         whether the field is enabled, taking a disabled parent field into account
    /// @param enabledEditable whether the user may change the enabled state from the list
    /// @param expander        the tree expander state for the row
    public ElasticFieldRow(final String name,
                           final Kind kind,
                           final ElasticIndexField field,
                           final ElasticIndexFieldSettings settings,
                           final String typeLabel,
                           final String filterType,
                           final String stateNote,
                           final boolean enabled,
                           final boolean enabledEditable,
                           final Expander expander) {
        this.name = name;
        this.kind = kind;
        this.field = field;
        this.settings = settings;
        this.typeLabel = typeLabel;
        this.filterType = filterType;
        this.stateNote = stateNote;
        this.enabled = enabled;
        this.enabledEditable = enabledEditable;
        this.expander = expander;
    }

    /// @return the full name of the field
    public String getName() {
        return name;
    }

    /// @return what the row represents
    public Kind getKind() {
        return kind;
    }

    /// @return the mapped field, or `null` if the row is not for a mapped field
    public ElasticIndexField getField() {
        return field;
    }

    /// @return the field's settings, or `null` if it has none
    public ElasticIndexFieldSettings getSettings() {
        return settings;
    }

    /// @return the text shown in the Field Type column
    public String getTypeLabel() {
        return typeLabel;
    }

    /// @return the value the type filter matches the row on, or `null` if it has none
    public String getFilterType() {
        return filterType;
    }

    /// @return why the field can't be used as normal, i.e. it is missing from all source indices, has a type
    /// conflict or is a virtual field hidden by a mapped field, or `null` if it can
    public String getStateNote() {
        return stateNote;
    }

    /// @return `true` if the field is enabled, taking a disabled parent field into account
    public boolean isEnabled() {
        return enabled;
    }

    /// @return `true` if the user may change the enabled state from the list
    public boolean isEnabledEditable() {
        return enabledEditable;
    }

    /// @return `true` if the row is a virtual field, whether or not its name clashes with a mapped field
    public boolean isVirtual() {
        return kind == Kind.VIRTUAL || kind == Kind.CLASHING_VIRTUAL;
    }

    /// @return `true` if the row is a mapped field whose source indices map it to different types
    public boolean hasTypeConflict() {
        return field != null && field.hasTypeConflict();
    }

    @Override
    public Expander getExpander() {
        return expander;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ElasticFieldRow that = (ElasticFieldRow) o;
        return Objects.equals(name, that.name) && kind == that.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, kind);
    }

    @Override
    public String toString() {
        return name + " (" + kind + ")";
    }

    /// What an [ElasticFieldRow] represents.
    public enum Kind {
        /// A field in the index mapping.
        MAPPED,
        /// A virtual field defined in the field settings.
        VIRTUAL,
        /// A virtual field whose name is the same as a mapped field, so is hidden by it.
        CLASHING_VIRTUAL,
        /// A settings entry for a field that is not in any source index.
        MISSING
    }
}
