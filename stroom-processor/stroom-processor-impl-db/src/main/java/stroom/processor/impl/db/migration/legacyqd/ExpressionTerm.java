/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.processor.impl.db.migration.legacyqd;

import stroom.docref.HasDisplayValue;
import stroom.util.shared.StringUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import java.util.Objects;
import java.util.function.Predicate;

@JsonPropertyOrder({
        "field",
        "condition",
        "value",
        "docRef"
})
@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlType(name = "ExpressionTerm", propOrder = {
        "field",
        "condition",
        "value",
        "docRef"
})
@XmlAccessorType(XmlAccessType.FIELD)
public final class ExpressionTerm extends ExpressionItem {

    @XmlElement
    @JsonProperty
    private String field; // TODO : XML serialisation still requires no-arg constructor and mutable fields

    @XmlElement
    @JsonProperty
    // TODO : XML serialisation still requires no-arg constructor and mutable fields
    private Condition condition;

    @XmlElement
    @JsonProperty
    // TODO : XML serialisation still requires no-arg constructor and mutable fields
    private String value;

    @XmlElement
    @JsonProperty
    // TODO : XML serialisation still requires no-arg constructor and mutable fields
    private DocRef docRef;

    public ExpressionTerm() {
        // TODO : XML serialisation still requires no-arg constructor and mutable fields
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

    @JsonCreator
    public ExpressionTerm(@JsonProperty("enabled") final Boolean enabled,
                          @JsonProperty("field") final String field,
                          @JsonProperty("condition") final Condition condition,
                          @JsonProperty("value") final String value,
                          @JsonProperty("docRef") final DocRef docRef) {
        super(enabled);
        this.field = field;
        this.condition = condition;
        this.value = value;
        this.docRef = docRef;
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
               Objects.equals(docRef, that.docRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), field, condition, value, docRef);
    }

    @Override
    void append(final StringBuilder sb, final String pad, final boolean singleLine) {
        if (enabled()) {
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
            if (docRef.getName() != null && docRef.getName().trim().length() > 0) {
                sb.append(docRef.getName());
            } else if (docRef.getUuid() != null && docRef.getUuid().trim().length() > 0) {
                sb.append(docRef.getUuid());
            }
        }
    }


    // --------------------------------------------------------------------------------


    public enum Condition implements HasDisplayValue {
        CONTAINS("+",
                "contains",
                "contains"),
        EQUALS("=",
                "=",
                "equals"),
        STARTS_WITH("^",
                "starts with",
                "starts with"),
        ENDS_WITH("$",
                "ends with",
                "ends with"),
        NOT_EQUALS("!=",
                "!=",
                "not equals"),
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
        IS_USER_REF("is"),
        IS_NULL("is null"),
        IS_NOT_NULL("is not null"),
        MATCHES_REGEX("/",
                "matches regex",
                "matches regex"),
        WORD_BOUNDARY("?",
                "word boundary",
                "word boundary"),

        CONTAINS_CASE_SENSITIVE("=+",
                "contains (case sensitive)",
                "contains (case sensitive)"),
        EQUALS_CASE_SENSITIVE("==",
                "==",
                "equals (case sensitive)"),
        STARTS_WITH_CASE_SENSITIVE("=^",
                "starts with (case sensitive)",
                "starts with (case sensitive)"),
        ENDS_WITH_CASE_SENSITIVE("=$",
                "ends with (case sensitive)",
                "ends with (case sensitive)"),
        MATCHES_REGEX_CASE_SENSITIVE("=/",
                "matches regex (case sensitive)",
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

        public String getDescription() {
            return description;
        }
    }
}
