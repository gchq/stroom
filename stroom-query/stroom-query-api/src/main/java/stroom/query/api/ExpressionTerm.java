/*
 * Copyright 2019 Crown Copyright
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

import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.HasDescription;
import stroom.util.shared.NullSafe;
import stroom.util.shared.StringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

@JsonPropertyOrder({
        "field",
        "condition",
        "value",
        "docRef",
        "options"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ExpressionTerm",
        description = "A predicate term in a query expression tree")
public final class ExpressionTerm extends ExpressionItem {

    @Schema(description = "The name of the field that is being evaluated in this predicate term")
    @JsonProperty
    private final String field;

    @Schema(description = "The condition of the predicate term")
    @JsonProperty
    private final Condition condition;

    @Schema(description = "The value that the field value is being evaluated against. Not required if a " +
                          "dictionary is supplied")
    @JsonProperty
    private final String value;

    @Schema(description = "The DocRef that the field value is being evaluated against if the condition is " +
                          "IN_DICTIONARY, IN_FOLDER or IS_DOC_REF")
    @JsonProperty
    private final DocRef docRef;

    @Schema(description = "Field-level query options that tailor how the data source searches the field, " +
                          "keyed by option id. Only options whose value differs " +
                          "from the default are stored. The options a field supports depend on its data source.")
    @JsonProperty
    private final Map<String, String> options;

    /**
     * Convenience method for creating an enabled {@code field = value} {@link ExpressionTerm}
     */
    public static ExpressionTerm equals(final String field, final String value) {
        return new ExpressionTerm(true, field, Condition.EQUALS, value, null);
    }

    /**
     * Convenience method for creating an enabled {@code field = value} {@link ExpressionTerm}
     */
    public static ExpressionTerm equalsCaseSensitive(final String field, final String value) {
        return new ExpressionTerm(true, field, Condition.EQUALS_CASE_SENSITIVE, value, null);
    }

    @Override
    public boolean containsField(final String... fields) {
        if (fields != null) {
            for (final String field : fields) {
                if (Objects.equals(field, this.field)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean containsTerm(final Predicate<ExpressionTerm> predicate) {
        Objects.requireNonNull(predicate);
        return predicate.test(this);
    }

    /**
     * @return True if this term has one of the supplied conditions
     */
    public boolean hasCondition(final Condition... conditions) {
        if (conditions != null && this.condition != null) {
            for (final Condition condition : conditions) {
                if (Objects.equals(this.condition, condition)) {
                    return true;
                }
            }
        }
        return false;
    }

    /// Creates a term with no field-level query options.
    ///
    /// @param enabled   Whether the term is enabled, or null for enabled.
    /// @param field     The name of the field.
    /// @param condition The condition the field value is evaluated with.
    /// @param value     The value the field value is evaluated against.
    /// @param docRef    The document the field value is evaluated against, for conditions such as IN_DICTIONARY.
    public ExpressionTerm(final Boolean enabled,
                          final String field,
                          final Condition condition,
                          final String value,
                          final DocRef docRef) {
        this(enabled, field, condition, value, docRef, null);
    }

    @JsonCreator
    public ExpressionTerm(@JsonProperty("enabled") final Boolean enabled,
                          @JsonProperty("field") final String field,
                          @JsonProperty("condition") final Condition condition,
                          @JsonProperty("value") final String value,
                          @JsonProperty("docRef") final DocRef docRef,
                          @JsonProperty("options") final Map<String, String> options) {
        super(enabled);
        this.field = field;
        this.condition = condition;
        this.value = value;
        this.docRef = docRef;
        // An empty map is stored as null so that terms without options serialise as they did before
        // options existed
        this.options = options == null || options.isEmpty()
                ? null
                : Collections.unmodifiableMap(new LinkedHashMap<>(options));
    }

    public String getField() {
        return field;
    }

    public Condition getCondition() {
        return condition;
    }

    public String getValue() {
        return value;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    /// @return The field-level query options that differ from their defaults, keyed by option id,
    /// or null if there are none.
    public Map<String, String> getOptions() {
        return options;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ExpressionTerm that = (ExpressionTerm) o;
        return Objects.equals(field, that.field) &&
               condition == that.condition &&
               Objects.equals(value, that.value) &&
               Objects.equals(docRef, that.docRef) &&
               Objects.equals(options, that.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), field, condition, value, docRef, options);
    }

    @Override
    void append(final StringBuilder sb, final String pad, final boolean singleLine) {
        if (enabled()) {
            //noinspection SizeReplaceableByIsEmpty // Cos GWT
            if (!singleLine && sb.length() > 0) {
                sb.append("\n");
                sb.append(pad);
            }

            if (field != null) {
                sb.append(field);
            }
            sb.append(" ");
            if (condition != null) {
                sb.append(condition.getDisplayValue());
            }
            if (!Condition.IS_NULL.equals(condition) && !Condition.IS_NOT_NULL.equals(condition)) {
                sb.append(" ");
                if (Condition.IN_DICTIONARY.equals(condition)) {
                    appendDocRef(sb, docRef);
                } else if (Condition.IN_FOLDER.equals(condition)) {
                    appendDocRef(sb, docRef);
                } else if (Condition.IS_DOC_REF.equals(condition)) {
                    appendDocRef(sb, docRef);
                } else if (value != null) {
                    sb.append(StringUtil.trimWhitespace(value));
                }
            }
        }
    }

    private void appendDocRef(final StringBuilder sb, final DocRef docRef) {
        if (docRef != null) {
            if (NullSafe.isNonBlankString(docRef.getName())) {
                sb.append(docRef.getName());
            } else if (NullSafe.isNonBlankString(docRef.getUuid())) {
                sb.append(docRef.getUuid());
            }
        }
    }


    // --------------------------------------------------------------------------------


    public enum Condition implements HasDisplayValue, HasDescription {
        CONTAINS("+",
                "contains",
                "contains (case-insensitive)"),
        EQUALS("=",
                "=",
                "equals (case-insensitive)"),
        STARTS_WITH("^",
                "starts with",
                "starts with (case-insensitive)"),
        ENDS_WITH("$",
                "ends with",
                "ends with (case-insensitive)"),
        NOT_EQUALS("!=",
                "!=",
                "not equals (case-insensitive)"),
        GREATER_THAN(">",
                ">",
                "greater than"),
        GREATER_THAN_OR_EQUAL_TO(">=",
                ">=",
                "greater than or equal to"),
        LESS_THAN("<",
                "<",
                "less than"),
        LESS_THAN_OR_EQUAL_TO("<=",
                "<=",
                "less than or equal to"),
        BETWEEN("between"),
        IN("in"),
        IN_DICTIONARY("in dictionary"),
        IN_FOLDER("in folder"),
        IS_DOC_REF("is"),
        IS_NOT_DOC_REF("is not"),
        IS_USER_REF("is"),
        IS_NOT_USER_REF("is not"),
        IS_NULL("is null"),
        IS_NOT_NULL("is not null"),
        MATCHES_REGEX("/",
                "matches regex",
                "matches regex"),
        WORD_BOUNDARY("?",
                "word boundary",
                "word boundary"),

        CONTAINS_CASE_SENSITIVE("=+",
                "contains (CS)",
                "contains (case sensitive)"),
        EQUALS_CASE_SENSITIVE("==",
                "==",
                "equals (case sensitive)"),
        NOT_EQUALS_CASE_SENSITIVE("!==",
                "!==",
                "not equals (case sensitive)"),
        STARTS_WITH_CASE_SENSITIVE("=^",
                "starts with (CS)",
                "starts with (case sensitive)"),
        ENDS_WITH_CASE_SENSITIVE("=$",
                "ends with (CS)",
                "ends with (case sensitive)"),
        MATCHES_REGEX_CASE_SENSITIVE("=/",
                "matches regex (CS)",
                "matches regex (case sensitive)"),

        // Permission related conditions.
        OF_DOC_REF("of"),
        USER_HAS_PERM("has permissions"),
        USER_HAS_OWNER("has owner permission"),
        USER_HAS_DELETE("has delete permission"),
        USER_HAS_EDIT("has edit permission"),
        USER_HAS_VIEW("has view permission"),
        USER_HAS_USE("has use permission");

        public static final String IN_CONDITION_DELIMITER = ",";

        private final String operator;
        private final String displayValue;
        private final String description;

        Condition(final String displayValue) {
            this.operator = displayValue;
            this.displayValue = displayValue;
            this.description = displayValue;
        }

        Condition(final String operator, final String displayValue, final String description) {
            this.operator = operator;
            this.description = description;
            this.displayValue = displayValue;
        }

        public String getOperator() {
            return operator;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        @Override
        public String getDescription() {
            return description;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    /**
     * Builder for constructing a {@link ExpressionTerm}
     */
    public static final class Builder extends ExpressionItem.Builder<ExpressionTerm, Builder> {

        private String field;
        private Condition condition;
        private String value;
        private DocRef docRef;
        private Map<String, String> options;

        private Builder() {
        }

        private Builder(final ExpressionTerm expressionTerm) {
            super(expressionTerm);
            this.field = expressionTerm.field;
            this.condition = expressionTerm.condition;
            this.value = expressionTerm.value;
            this.docRef = expressionTerm.docRef;
            this.options = expressionTerm.options;
        }

        /**
         * @param value The name of the field that is being evaluated in this predicate term"
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder field(final String value) {
            this.field = value;
            return this;
        }

        public Builder field(final QueryField value) {
            this.field = value.getFldName();
            return this;
        }

        /**
         * Equivalent to passing {@link Condition#EQUALS} to {@link Builder#condition(Condition)}.
         */
        public Builder equals() {
            this.condition = Condition.EQUALS;
            return this;
        }

        /**
         * @param value The condition of the predicate term
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder condition(final Condition value) {
            this.condition = value;
            return this;
        }

        /**
         * @param value The value that the field value is being evaluated against. Not required if a
         *              dictionary is supplied
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder value(final String value) {
            this.value = value;
            return this;
        }

        /**
         * Add a entity term to the builder, e.g fieldX|IS_DOC_REF|docRefToDictionaryY
         * Term is enabled by default. Not all data sources support entity terms and only certain
         * conditions are supported for an entity term.
         *
         * @param value The DocRef for the entity that this predicate is using for its evaluation
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder docRef(final DocRef value) {
            this.docRef = value;
            return this;
        }

        /**
         * A shortcut method for specifying the entity DocRef inline
         *
         * @param type The element type
         * @param uuid The UUID of the dictionary
         * @param name The name of the dictionary
         * @return this builder, with the completed entity added,
         */
        public Builder docRef(final String type, final String uuid, final String name) {
            return this.docRef(
                    DocRef.builder()
                            .type(type)
                            .uuid(uuid)
                            .name(name)
                            .build());
        }

        /// @param options Field-level query options keyed by option id, or null for none.
        /// @return This builder.
        public Builder options(final Map<String, String> options) {
            this.options = options;
            return this;
        }

        @Override
        public ExpressionTerm build() {
            Boolean enabled = this.enabled;
            if (Boolean.TRUE.equals(enabled)) {
                enabled = null;
            }

            return new ExpressionTerm(enabled, field, condition, value, docRef, options);
        }

        @Override
        protected ExpressionTerm.Builder self() {
            return this;
        }
    }
}
