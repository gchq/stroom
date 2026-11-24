/*
 * Copyright 2022 Crown Copyright
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

package stroom.dashboard.shared;

import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@JsonPropertyOrder({
        "key",
        "value",
        "values",
        "dictionary",
        "useDictionary",
        "allowTextEntry",
        "multiSelect",
        "selectedValues"
})
@JsonInclude(Include.NON_NULL)
public final class ListInputComponentSettings implements ComponentSettings {

    private static final String SELECTED_VALUES_DELIMITER = ",";

    @JsonProperty
    private final String key;
    @JsonProperty
    private final String value;
    @JsonProperty
    private final List<String> values;
    @JsonProperty
    private final DocRef dictionary;
    @JsonProperty
    private final boolean useDictionary;
    @JsonProperty
    private final boolean allowTextEntry;
    @JsonProperty
    private final boolean multiSelect;
    @JsonProperty
    private final List<String> selectedValues;

    @JsonCreator
    public ListInputComponentSettings(@JsonProperty("key") final String key,
                                      @JsonProperty("value") final String value,
                                      @JsonProperty("values") final List<String> values,
                                      @JsonProperty("dictionary") final DocRef dictionary,
                                      @JsonProperty("useDictionary") final Boolean useDictionary,
                                      @JsonProperty("allowTextEntry") final Boolean allowTextEntry,
                                      @JsonProperty("multiSelect") final Boolean multiSelect,
                                      @JsonProperty("selectedValues") final List<String> selectedValues) {
        this.key = key;
        this.value = value;
        this.values = values;
        this.dictionary = dictionary;
        this.useDictionary = Objects.requireNonNullElse(useDictionary, false);
        this.allowTextEntry = Objects.requireNonNullElse(allowTextEntry, false);
        this.multiSelect = Objects.requireNonNullElse(multiSelect, false);
        this.selectedValues = selectedValues;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public List<String> getValues() {
        return values;
    }

    public DocRef getDictionary() {
        return dictionary;
    }

    public boolean isUseDictionary() {
        return useDictionary;
    }

    public boolean isAllowTextEntry() {
        return allowTextEntry;
    }

    /// @return `true` if the user can select more than one value. The selected values are
    /// then held in [#getSelectedValues()] rather than [#getValue()].
    public boolean isMultiSelect() {
        return multiSelect;
    }

    /// @return The values the user has selected when [#isMultiSelect()] is `true`.
    /// May be `null`.
    public List<String> getSelectedValues() {
        return selectedValues;
    }

    /// Joins multi-select values into the comma separated form used when the list input's
    /// key is interpolated, e.g. `${key}`.
    ///
    /// @param values The selected values. Values are trimmed, and blank values and duplicates
    ///               are ignored.
    /// @return The joined values, or an empty string if there are none.
    public static String joinSelectedValues(final List<String> values) {
        return NullSafe.stream(values)
                .map(NullSafe::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.joining(SELECTED_VALUES_DELIMITER));
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ListInputComponentSettings that = (ListInputComponentSettings) o;
        return useDictionary == that.useDictionary &&
                Objects.equals(key, that.key) &&
                Objects.equals(value, that.value) &&
                Objects.equals(values, that.values) &&
                Objects.equals(dictionary, that.dictionary) &&
                Objects.equals(allowTextEntry, that.allowTextEntry) &&
                multiSelect == that.multiSelect &&
                Objects.equals(selectedValues, that.selectedValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key,
                value,
                values,
                dictionary,
                useDictionary,
                allowTextEntry,
                multiSelect,
                selectedValues);
    }

    @Override
    public String toString() {
        return "DropDownInputComponentSettings{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", values=" + values +
                ", dictionary=" + dictionary +
                ", useDictionary=" + useDictionary +
                ", allowTextEntry=" + allowTextEntry +
                ", multiSelect=" + multiSelect +
                ", selectedValues=" + selectedValues +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder extends ComponentSettings
            .AbstractBuilder<ListInputComponentSettings, ListInputComponentSettings.Builder> {

        private String key;
        private String value;
        private List<String> values;
        private DocRef dictionary;
        private boolean useDictionary;
        private boolean allowTextEntry;
        private boolean multiSelect;
        private List<String> selectedValues;

        private Builder() {
        }

        private Builder(final ListInputComponentSettings listInputComponentSettings) {
            this.key = listInputComponentSettings.key;
            this.value = listInputComponentSettings.value;
            this.values = listInputComponentSettings.values == null
                    ? null
                    : new ArrayList<>(listInputComponentSettings.values);
            this.dictionary = listInputComponentSettings.dictionary;
            this.useDictionary = listInputComponentSettings.useDictionary;
            this.allowTextEntry = listInputComponentSettings.allowTextEntry;
            this.multiSelect = listInputComponentSettings.multiSelect;
            this.selectedValues = listInputComponentSettings.selectedValues == null
                    ? null
                    : new ArrayList<>(listInputComponentSettings.selectedValues);
        }

        public Builder key(final String key) {
            this.key = key;
            return self();
        }

        public Builder value(final String value) {
            this.value = value;
            return self();
        }

        public Builder values(final List<String> values) {
            this.values = values;
            return self();
        }

        public Builder dictionary(final DocRef dictionary) {
            this.dictionary = dictionary;
            return self();
        }

        public Builder useDictionary(final boolean useDictionary) {
            this.useDictionary = useDictionary;
            return self();
        }

        public Builder allowTextEntry(final boolean allowTextEntry) {
            this.allowTextEntry = allowTextEntry;
            return self();
        }

        /// @param multiSelect `true` to let the user select more than one value.
        /// @return This builder.
        public Builder multiSelect(final boolean multiSelect) {
            this.multiSelect = multiSelect;
            return self();
        }

        /// @param selectedValues The values the user has selected when multi-select is enabled.
        /// @return This builder.
        public Builder selectedValues(final List<String> selectedValues) {
            this.selectedValues = selectedValues;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ListInputComponentSettings build() {
            return new ListInputComponentSettings(
                    key,
                    value,
                    values,
                    dictionary,
                    useDictionary,
                    allowTextEntry,
                    multiSelect,
                    selectedValues
            );
        }
    }
}
