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

package stroom.core.db.migration._V07_00_00.query.api.v2;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import stroom.core.db.migration._V07_00_00.docref._V07_00_00_DocRef;
import stroom.core.db.migration._V07_00_00.docref._V07_00_00_HasDisplayValue;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({"field", "condition", "value", "dictionary", "docRef"})
@XmlType(name = "ExpressionTerm", propOrder = {"field", "condition", "value", "dictionary", "docRef"})
@XmlAccessorType(XmlAccessType.FIELD)
@ApiModel(
        value = "ExpressionTerm",
        description = "A predicate term in a query expression tree",
        parent = _V07_00_00_ExpressionItem.class)
public final class _V07_00_00_ExpressionTerm extends _V07_00_00_ExpressionItem {
    private static final long serialVersionUID = 9035311895540457146L;

    @XmlElement
    @ApiModelProperty(
            value = "The name of the field that is being evaluated in this predicate term",
            required = true)
    private String field;

    @XmlElement
    @ApiModelProperty(
            value = "The condition of the predicate term",
            required = true)
    private _V07_00_00_Condition condition;

    @XmlElement
    @ApiModelProperty(
            value = "The value that the field value is being evaluated against. Not required if a dictionary is supplied",
            required = false)
    private String value;

    @XmlElement
    @ApiModelProperty(
            value = "The DocRef for the dictionary that this predicate is using for its evaluation",
            required = true)
    private _V07_00_00_DocRef dictionary;

    @XmlElement
    @ApiModelProperty(
            value = "The DocRef for the dictionary that this predicate is using for its evaluation",
            required = true)
    private _V07_00_00_DocRef docRef;

    private _V07_00_00_ExpressionTerm() {
    }

    public _V07_00_00_ExpressionTerm(final String field, final _V07_00_00_Condition condition, final String value) {
        this(null, field, condition, value, null, null);
    }

    public _V07_00_00_ExpressionTerm(final String field, final _V07_00_00_Condition condition, final _V07_00_00_DocRef dictionary) {
        this(null, field, condition, null, dictionary, null);
    }

    public _V07_00_00_ExpressionTerm(final String field, final _V07_00_00_DocRef entity) {
        this(null, field, _V07_00_00_Condition.EQUALS, null, null, entity);
    }

    public _V07_00_00_ExpressionTerm(final Boolean enabled,
                          final String field,
                          final _V07_00_00_Condition condition,
                          final String value,
                          final _V07_00_00_DocRef dictionary,
                          final _V07_00_00_DocRef docRef) {
        super(enabled);
        this.field = field;
        this.condition = condition;
        this.value = value;
        this.dictionary = dictionary;
        this.docRef = docRef;
    }

    public String getField() {
        return field;
    }

    public _V07_00_00_Condition getCondition() {
        return condition;
    }

    public String getValue() {
        return value;
    }

    public _V07_00_00_DocRef getDictionary() {
        return dictionary;
    }

    public _V07_00_00_DocRef getDocRef() {
        return docRef;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        _V07_00_00_ExpressionTerm that = (_V07_00_00_ExpressionTerm) o;
        return Objects.equals(field, that.field) &&
                condition == that.condition &&
                Objects.equals(value, that.value) &&
                Objects.equals(dictionary, that.dictionary) &&
                Objects.equals(docRef, that.docRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), field, condition, value, dictionary, docRef);
    }

    @Override
    void append(final StringBuilder sb, final String pad, final boolean singleLine) {
        if (getEnabled()) {
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
            sb.append(" ");
            if (_V07_00_00_Condition.IN_DICTIONARY.equals(condition)) {
                appendDocRef(sb, dictionary);
            }
            if (_V07_00_00_Condition.IS_DOC_REF.equals(condition)) {
                appendDocRef(sb, docRef);
            } else if (value != null) {
                sb.append(value);
            }
        }
    }

    private void appendDocRef(final StringBuilder sb, final _V07_00_00_DocRef docRef) {
        if (docRef != null) {
            if (docRef.getName() != null && docRef.getName().trim().length() > 0) {
                sb.append(docRef.getName());
            } else if (docRef.getUuid() != null && docRef.getUuid().trim().length() > 0) {
                sb.append(docRef.getUuid());
            }
        }
    }

    public enum _V07_00_00_Condition implements _V07_00_00_HasDisplayValue {
        CONTAINS("contains"),
        EQUALS("="),
        GREATER_THAN(">"),
        GREATER_THAN_OR_EQUAL_TO(">="),
        LESS_THAN("<"),
        LESS_THAN_OR_EQUAL_TO("<="),
        BETWEEN("between"),
        IN("in"),
        IN_DICTIONARY("in dictionary"),
        IS_DOC_REF("is");

        public static final List<_V07_00_00_Condition> SIMPLE_CONDITIONS = Arrays.asList(
                EQUALS,
                GREATER_THAN,
                GREATER_THAN_OR_EQUAL_TO,
                LESS_THAN,
                LESS_THAN_OR_EQUAL_TO,
                BETWEEN);

        public static final String IN_CONDITION_DELIMITER = ",";

        private final String displayValue;

        _V07_00_00_Condition(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    /**
     * Builder for constructing a {@link _V07_00_00_ExpressionTerm}
     */
    public static class Builder
            extends _V07_00_00_ExpressionItem.Builder<_V07_00_00_ExpressionTerm, Builder> {
        private String field;

        private _V07_00_00_Condition condition;

        private String value;

        private _V07_00_00_DocRef dictionary;

        private _V07_00_00_DocRef docRef;

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
        public Builder condition(final _V07_00_00_Condition value) {
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
         * Add a dictionary term to the builder, e.g fieldX|IN_DICTIONARY|docRefToDictionaryY
         * Term is enabled by default. Not all data sources support dictionary terms and only certain
         * conditions are supported for a dictionary term.
         *
         * @param value The DocRef for the dictionary that this predicate is using for its evaluation
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder dictionary(final _V07_00_00_DocRef value) {
            this.dictionary = value;
            return this;
        }

        /**
         * A shortcut method for specifying the dictionary DocRef inline
         *
         * @param type The element type
         * @param uuid The UUID of the dictionary
         * @param name The name of the dictionary
         * @return this builder, with the completed dictionary added,
         */
        public Builder dictionary(final String type, final String uuid, final String name) {
            return this.dictionary(
                    new _V07_00_00_DocRef.Builder()
                            .type(type)
                            .uuid(uuid)
                            .name(name)
                            .build());
        }

        /**
         * Add a entity term to the builder, e.g fieldX|IS_DOC_REF|docRefToDictionaryY
         * Term is enabled by default. Not all data sources support entity terms and only certain
         * conditions are supported for an entity term.
         *
         * @param value The DocRef for the entity that this predicate is using for its evaluation
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder docRef(final _V07_00_00_DocRef value) {
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
                    new _V07_00_00_DocRef.Builder()
                            .type(type)
                            .uuid(uuid)
                            .name(name)
                            .build());
        }

        @Override
        public _V07_00_00_ExpressionTerm build() {
            return new _V07_00_00_ExpressionTerm(getEnabled(), field, condition, value, dictionary, docRef);
        }

        @Override
        protected _V07_00_00_ExpressionTerm.Builder self() {
            return this;
        }
    }
}