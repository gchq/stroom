/*
 * Copyright 2017 Crown Copyright
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

package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"field", "condition", "value", "docRef"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@XmlType(name = "ExpressionTerm", propOrder = {"field", "condition", "value", "docRef"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(
        value = "ExpressionTerm",
        description = "A predicate term in a query expression tree",
        parent = ExpressionItem.class)
public final class ExpressionTerm extends ExpressionItem {
    private static final long serialVersionUID = 9035311895540457146L;

    @XmlElement
    @ApiModelProperty(
            value = "The name of the field that is being evaluated in this predicate term",
            required = true)
    @JsonProperty
    private String field;

    @XmlElement
    @ApiModelProperty(
            value = "The condition of the predicate term",
            required = true)
    @JsonProperty
    private Condition condition;

    @XmlElement
    @ApiModelProperty(
            value = "The value that the field value is being evaluated against. Not required if a dictionary is supplied")
    @JsonProperty
    private String value;

    @XmlElement
    @ApiModelProperty(
            value = "The DocRef that the field value is being evaluated against if the condition is IN_DICTIONARY, IN_FOLDER or IS_DOC_REF")
    @JsonProperty
    private DocRef docRef;

    public ExpressionTerm() {
        // Required for GWT JSON serialisation.
    }

    public ExpressionTerm(final String field, final Condition condition, final String value) {
        this(true, field, condition, value, null);
    }

    public ExpressionTerm(final String field, final Condition condition, final DocRef docRef) {
        this(true, field, condition, null, docRef);
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

    public void setField(final String field) {
        this.field = field;
    }

    public Condition getCondition() {
        return condition;
    }

    public void setCondition(final Condition condition) {
        this.condition = condition;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public void setDocRef(final DocRef docRef) {
        this.docRef = docRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ExpressionTerm that = (ExpressionTerm) o;
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
                    sb.append(value);
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

    public enum Condition implements HasDisplayValue {
        CONTAINS("contains"),
        EQUALS("="),
        GREATER_THAN(">"),
        GREATER_THAN_OR_EQUAL_TO(">="),
        LESS_THAN("<"),
        LESS_THAN_OR_EQUAL_TO("<="),
        BETWEEN("between"),
        IN("in"),
        IN_DICTIONARY("in dictionary"),
        IN_FOLDER("in folder"),
        IS_DOC_REF("is"),
        IS_NULL("is null"),
        IS_NOT_NULL("is not null");

        public static final List<Condition> SIMPLE_CONDITIONS = Arrays.asList(
                EQUALS,
                GREATER_THAN,
                GREATER_THAN_OR_EQUAL_TO,
                LESS_THAN,
                LESS_THAN_OR_EQUAL_TO,
                BETWEEN);

        public static final String IN_CONDITION_DELIMITER = ",";

        private final String displayValue;

        Condition(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    /**
     * Builder for constructing a {@link ExpressionTerm}
     */
    public static class Builder
            extends ExpressionItem.Builder<ExpressionTerm, Builder> {
        private String field;

        private Condition condition;

        private String value;

        private DocRef docRef;

        public Builder() {
        }

        public Builder(final Boolean enabled) {
            super(enabled);
        }

        /**
         * @param value The name of the field that is being evaluated in this predicate term"
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder field(final String value) {
            this.field = value;
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
         * @param value The value that the field value is being evaluated against. Not required if a dictionary is supplied
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
                    new DocRef.Builder()
                            .type(type)
                            .uuid(uuid)
                            .name(name)
                            .build());
        }

        @Override
        public ExpressionTerm build() {
            return new ExpressionTerm(getEnabled(), field, condition, value, docRef);
        }

        @Override
        protected ExpressionTerm.Builder self() {
            return this;
        }
    }
}