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

import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.DoubleField;
import stroom.datasource.api.v2.FloatField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;
import stroom.docref.HasDisplayValue;
import stroom.query.api.v2.ExpressionTerm.Condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlType;

@JsonPropertyOrder({"op", "children"})
@JsonInclude(Include.NON_NULL)
@XmlType(name = "ExpressionOperator", propOrder = {"op", "children"})
@XmlAccessorType(XmlAccessType.FIELD)
@Schema(name = "ExpressionOperator",
        description = "A logical addOperator term in a query expression tree")
public final class ExpressionOperator extends ExpressionItem {

    private static final long serialVersionUID = 6602004424564268512L;

    @XmlElement(name = "op")
    @Schema(description = "The logical addOperator type",
            required = true)
    @JsonProperty
    private Op op; // TODO : XML serialisation still requires no-arg constructor and mutable fields

    @XmlElementWrapper(name = "children")
    @XmlElements({
            @XmlElement(name = "operator", type = ExpressionOperator.class),
            @XmlElement(name = "term", type = ExpressionTerm.class)
    })
    @JsonProperty
    // TODO : XML serialisation still requires no-arg constructor and mutable fields
    private List<ExpressionItem> children;

    public ExpressionOperator() {
        // TODO : XML serialisation still requires no-arg constructor and mutable fields
    }

    @Override
    public boolean containsField(final String... fields) {
        if (children != null) {
            for (final ExpressionItem child : children) {
                if (child.containsField(fields)) {
                    // Found a match so break out
                    return true;
                }
            }
        }
        return false;
    }

    @JsonCreator
    public ExpressionOperator(@JsonProperty("enabled") final Boolean enabled,
                              @JsonProperty("op") final Op op,
                              @JsonProperty("children") final List<ExpressionItem> children) {
        super(enabled);
        this.op = op;
        this.children = children;
    }

    public Op getOp() {
        return op;
    }

    public Op op() {
        if (op == null) {
            return Op.AND;
        }
        return op;
    }

    public List<ExpressionItem> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return children != null && children.size() > 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ExpressionOperator that = (ExpressionOperator) o;
        return op == that.op &&
                Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), op, children);
    }

    @Override
    void append(final StringBuilder sb, final String pad, final boolean singleLine) {
        if (enabled()) {
            if (!singleLine && sb.length() > 0) {
                sb.append("\n");
                sb.append(pad);
            }

            sb.append(op());
            if (singleLine) {
                sb.append(" {");
            }

            if (children != null) {
                final String padding = pad + "  ";
                boolean firstItem = true;
                for (final ExpressionItem expressionItem : children) {
                    if (expressionItem.enabled()) {
                        if (singleLine && !firstItem) {
                            sb.append(", ");
                        }

                        expressionItem.append(sb, padding, singleLine);
                        firstItem = false;
                    }
                }
            }

            if (singleLine) {
                sb.append("}");
            }
        }
    }

    public enum Op implements HasDisplayValue {
        AND("AND"),
        OR("OR"),
        NOT("NOT");

        private final String displayValue;

        Op(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    /**
     * Builder for constructing a {@link ExpressionOperator}
     */
    public static final class Builder extends ExpressionItem.Builder<ExpressionOperator, Builder> {

        private Op op;
        private final List<ExpressionItem> children = new ArrayList<>();

        private Builder() {
        }

        private Builder(final ExpressionOperator expressionOperator) {
            super(expressionOperator);
            this.op = expressionOperator.op;
        }

        /**
         * Changes the operator of this builder
         *
         * @param op The operator to set for this builder
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder op(final Op op) {
            this.op = op;
            return this;
        }

        public Builder children(final List<ExpressionItem> children) {
            this.children.clear();
            if (children != null) {
                this.children.addAll(children);
            }
            return this;
        }

        /**
         * Adds an {@link ExpressionOperator} to this builder
         *
         * @param item The expression item to add as children
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addOperator(ExpressionOperator item) {
            return addOperators(item);
        }

        /**
         * Adds an {@link ExpressionOperator} to this builder
         *
         * @param items The expression items to add as children
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addOperators(ExpressionOperator... items) {
            return addOperators(Arrays.asList(items));
        }

        /**
         * Adds an {@link ExpressionOperator} to this builder
         *
         * @param items The expression items to add as children
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addOperators(Collection<ExpressionOperator> items) {
            this.children.addAll(items);
            return this;
        }

        /**
         * Adds an {@link ExpressionTerm} to this builder
         *
         * @param item The expression item to add as children
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addTerm(ExpressionTerm item) {
            return addTerms(item);
        }

        /**
         * Adds an {@link ExpressionTerm} to this builder
         *
         * @param items The expression items to add as children
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addTerms(ExpressionTerm... items) {
            return addTerms(Arrays.asList(items));
        }

        /**
         * Adds an {@link ExpressionTerm} to this builder
         *
         * @param items The expression items to add as children
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder addTerms(Collection<ExpressionTerm> items) {
            this.children.addAll(items);
            return this;
        }

        /**
         * A convenience function for adding terms in one go, the parameters should read fairly clearly
         *
         * @param field     The field name
         * @param condition The condition to apply to the value
         * @param value     The value
         * @return this builder, with the completed term added.
         */
        public Builder addTerm(final String field,
                               final ExpressionTerm.Condition condition,
                               final String value) {
            return addTerm(ExpressionTerm.builder()
                    .field(field)
                    .condition(condition)
                    .value(value)
                    .build());
        }

        /**
         * A convenience function for adding terms in one go, the parameters should read fairly clearly
         *
         * @param field     The field name
         * @param condition The condition to apply to the value
         * @param value     The value
         * @return this builder, with the completed term added.
         */
        public Builder addTerm(final BooleanField field,
                               final ExpressionTerm.Condition condition,
                               final boolean value) {
            return addTerm(ExpressionTerm.builder()
                    .field(field.getName())
                    .condition(condition)
                    .value(String.valueOf(value))
                    .build());
        }

        public Builder addTerm(final DateField field,
                               final ExpressionTerm.Condition condition,
                               final String value) {
            return addTerm(ExpressionTerm.builder()
                    .field(field.getName())
                    .condition(condition)
                    .value(value)
                    .build());
        }

        public Builder addTerm(final DocRefField field,
                               final ExpressionTerm.Condition condition,
                               final String value) {
            return addTerm(ExpressionTerm.builder()
                    .field(field.getName())
                    .condition(condition)
                    .value(value)
                    .build());
        }

        public Builder addTerm(final DocRefField field,
                               final ExpressionTerm.Condition condition,
                               final DocRef docRef) {
            if (!Condition.IS_DOC_REF.equals(condition) &&
                    !Condition.IN_FOLDER.equals(condition)) {
                throw new RuntimeException("Unexpected condition used for doc ref :" + condition);
            }

            return addTerm(ExpressionTerm.builder()
                    .field(field.getName())
                    .condition(condition)
                    .docRef(docRef)
                    .build());
        }

        public Builder addTerm(final IdField field,
                               final ExpressionTerm.Condition condition,
                               final long value) {
            return addTerm(ExpressionTerm.builder()
                    .field(field.getName())
                    .condition(condition)
                    .value(String.valueOf(value))
                    .build());
        }

        public Builder addTerm(final IntegerField field,
                               final ExpressionTerm.Condition condition,
                               final int value) {
            return addTerm(ExpressionTerm.builder()
                    .field(field.getName())
                    .condition(condition)
                    .value(String.valueOf(value))
                    .build());
        }

        public Builder addTerm(final LongField field,
                               final ExpressionTerm.Condition condition,
                               final long value) {
            return addTerm(ExpressionTerm.builder()
                    .field(field.getName())
                    .condition(condition)
                    .value(String.valueOf(value))
                    .build());
        }

        public Builder addTerm(final FloatField field,
                               final ExpressionTerm.Condition condition,
                               final float value) {
            return addTerm(ExpressionTerm.builder()
                    .field(field.getName())
                    .condition(condition)
                    .value(String.valueOf(value))
                    .build());
        }

        public Builder addTerm(final DoubleField field,
                               final ExpressionTerm.Condition condition,
                               final double value) {
            return addTerm(ExpressionTerm.builder()
                    .field(field.getName())
                    .condition(condition)
                    .value(String.valueOf(value))
                    .build());
        }

        public Builder addTerm(final TextField field,
                               final ExpressionTerm.Condition condition,
                               final String value) {
            return addTerm(ExpressionTerm.builder()
                    .field(field.getName())
                    .condition(condition)
                    .value(value)
                    .build());
        }

        public Builder addTerm(final String field,
                               final ExpressionTerm.Condition condition,
                               final DocRef docRef) {
            return addTerm(ExpressionTerm.builder()
                    .field(field)
                    .condition(condition)
                    .docRef(docRef)
                    .build());
        }

        @Override
        ExpressionOperator.Builder self() {
            return this;
        }

        @Override
        public ExpressionOperator build() {
            Boolean enabled = this.enabled;
            if (Boolean.TRUE.equals(enabled)) {
                enabled = null;
            }

            Op op = this.op;
            if (Op.AND.equals(op)) {
                op = null;
            }

            List<ExpressionItem> children = this.children;
            if (children.size() == 0) {
                children = null;
            } else {
                children = Collections.unmodifiableList(children);
            }

            return new ExpressionOperator(enabled, op, children);
        }
    }
}
