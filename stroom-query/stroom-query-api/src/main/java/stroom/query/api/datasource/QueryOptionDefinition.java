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

package stroom.query.api.datasource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/// Describes one field-level query option that a data source supports for a field: its id, label,
/// type, allowed values and default. Definitions are grouped into a {@link QueryOptionSet} and the
/// options dialog is built from them.
@JsonPropertyOrder({
        "id",
        "displayName",
        "description",
        "type",
        "choices",
        "defaultValue",
        "minValue",
        "maxValue",
        "rules"
})
@JsonInclude(Include.NON_NULL)
public final class QueryOptionDefinition {

    @JsonProperty
    private final String id;
    @JsonProperty
    private final String displayName;
    @JsonProperty
    private final String description;
    @JsonProperty
    private final QueryOptionType type;
    @JsonProperty
    private final List<QueryOptionChoice> choices;
    @JsonProperty
    private final String defaultValue;
    @JsonProperty
    private final Integer minValue;
    @JsonProperty
    private final Integer maxValue;
    @JsonProperty
    private final List<QueryOptionRule> rules;

    /// Creates a definition. Use {@link #builder()} to create one in code, as the builder checks the default
    /// value is valid.
    ///
    /// @param id           The id the option's value is stored under.
    /// @param displayName  The label shown for the option.
    /// @param description  A longer description of what the option does, or null.
    /// @param type         The kind of value the option holds.
    /// @param choices      The allowed values of a {@link QueryOptionType#CHOICE} option, or null.
    /// @param defaultValue The value the option has when it is not set or is disabled.
    /// @param minValue     The lowest allowed value of a {@link QueryOptionType#INTEGER} option, or null.
    /// @param maxValue     The highest allowed value of a {@link QueryOptionType#INTEGER} option, or null.
    /// @param rules        The rules that disable the option, or null.
    @JsonCreator
    public QueryOptionDefinition(@JsonProperty("id") final String id,
                                 @JsonProperty("displayName") final String displayName,
                                 @JsonProperty("description") final String description,
                                 @JsonProperty("type") final QueryOptionType type,
                                 @JsonProperty("choices") final List<QueryOptionChoice> choices,
                                 @JsonProperty("defaultValue") final String defaultValue,
                                 @JsonProperty("minValue") final Integer minValue,
                                 @JsonProperty("maxValue") final Integer maxValue,
                                 @JsonProperty("rules") final List<QueryOptionRule> rules) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.type = type;
        this.choices = choices == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(choices));
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.rules = rules == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(rules));
    }

    /// @return A new builder.
    public static Builder builder() {
        return new Builder();
    }

    /// @return The id the option's value is stored under in an expression term.
    public String getId() {
        return id;
    }

    /// @return The label shown for the option in the options dialog.
    public String getDisplayName() {
        return displayName;
    }

    /// @return A longer description of what the option does, or null.
    public String getDescription() {
        return description;
    }

    /// @return The kind of value the option holds.
    public QueryOptionType getType() {
        return type;
    }

    /// @return The allowed values of a {@link QueryOptionType#CHOICE} option, otherwise an empty list.
    public List<QueryOptionChoice> getChoices() {
        return choices;
    }

    /// @return The value the option has when it is not set or is disabled.
    public String getDefaultValue() {
        return defaultValue;
    }

    /// @return The lowest allowed value of a {@link QueryOptionType#INTEGER} option, or null.
    public Integer getMinValue() {
        return minValue;
    }

    /// @return The highest allowed value of a {@link QueryOptionType#INTEGER} option, or null.
    public Integer getMaxValue() {
        return maxValue;
    }

    /// @return The rules that disable this option depending on other options' values.
    public List<QueryOptionRule> getRules() {
        return rules;
    }

    /// Checks whether a value is valid for this option.
    ///
    /// @param value The value to check.
    /// @return True if the value is valid.
    public boolean isValid(final String value) {
        if (value == null || type == null) {
            return false;
        }
        return switch (type) {
            case BOOLEAN -> "true".equals(value) || "false".equals(value);
            case CHOICE -> choices.stream().anyMatch(choice -> value.equals(choice.getValue()));
            case INTEGER -> isValidInteger(value);
        };
    }

    private boolean isValidInteger(final String value) {
        final int intValue;
        try {
            intValue = Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            return false;
        }
        return (minValue == null || intValue >= minValue)
               && (maxValue == null || intValue <= maxValue);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueryOptionDefinition that = (QueryOptionDefinition) o;
        return Objects.equals(id, that.id)
               && Objects.equals(displayName, that.displayName)
               && Objects.equals(description, that.description)
               && type == that.type
               && Objects.equals(choices, that.choices)
               && Objects.equals(defaultValue, that.defaultValue)
               && Objects.equals(minValue, that.minValue)
               && Objects.equals(maxValue, that.maxValue)
               && Objects.equals(rules, that.rules);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, description, type, choices, defaultValue, minValue, maxValue, rules);
    }

    @Override
    public String toString() {
        return id;
    }


    // --------------------------------------------------------------------------------


    /// Builds a {@link QueryOptionDefinition}.
    public static final class Builder {

        private String id;
        private String displayName;
        private String description;
        private QueryOptionType type;
        private final List<QueryOptionChoice> choices = new ArrayList<>();
        private String defaultValue;
        private Integer minValue;
        private Integer maxValue;
        private final List<QueryOptionRule> rules = new ArrayList<>();

        private Builder() {
        }

        /// @param id The id the option's value is stored under.
        /// @return This builder.
        public Builder id(final String id) {
            this.id = id;
            return this;
        }

        /// @param displayName The label shown for the option.
        /// @return This builder.
        public Builder displayName(final String displayName) {
            this.displayName = displayName;
            return this;
        }

        /// @param description A longer description of what the option does.
        /// @return This builder.
        public Builder description(final String description) {
            this.description = description;
            return this;
        }

        /// Makes this a {@link QueryOptionType#BOOLEAN} option.
        ///
        /// @param defaultValue The default value.
        /// @return This builder.
        public Builder booleanType(final boolean defaultValue) {
            this.type = QueryOptionType.BOOLEAN;
            this.defaultValue = String.valueOf(defaultValue);
            return this;
        }

        /// Makes this a {@link QueryOptionType#CHOICE} option. Add the allowed values with
        /// {@link #addChoice(String, String)}.
        ///
        /// @param defaultValue The default value, which must be one of the choices.
        /// @return This builder.
        public Builder choiceType(final String defaultValue) {
            this.type = QueryOptionType.CHOICE;
            this.defaultValue = defaultValue;
            return this;
        }

        /// Makes this a {@link QueryOptionType#INTEGER} option.
        ///
        /// @param defaultValue The default value.
        /// @param minValue     The lowest allowed value.
        /// @param maxValue     The highest allowed value.
        /// @return This builder.
        public Builder integerType(final int defaultValue, final int minValue, final int maxValue) {
            this.type = QueryOptionType.INTEGER;
            this.defaultValue = String.valueOf(defaultValue);
            this.minValue = minValue;
            this.maxValue = maxValue;
            return this;
        }

        /// @param value        A value allowed for a {@link QueryOptionType#CHOICE} option.
        /// @param displayValue The text shown to the user for the value.
        /// @return This builder.
        public Builder addChoice(final String value, final String displayValue) {
            this.choices.add(new QueryOptionChoice(value, displayValue));
            return this;
        }

        /// @param rule A rule that disables the option depending on another option's value.
        /// @return This builder.
        public Builder addRule(final QueryOptionRule rule) {
            this.rules.add(rule);
            return this;
        }

        /// @return The definition.
        public QueryOptionDefinition build() {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(type, "type");
            final QueryOptionDefinition definition = new QueryOptionDefinition(
                    id,
                    displayName,
                    description,
                    type,
                    choices,
                    defaultValue,
                    minValue,
                    maxValue,
                    rules);
            if (!definition.isValid(defaultValue)) {
                throw new IllegalArgumentException("Invalid default value '" + defaultValue +
                                                   "' for query option " + id);
            }
            return definition;
        }
    }
}
