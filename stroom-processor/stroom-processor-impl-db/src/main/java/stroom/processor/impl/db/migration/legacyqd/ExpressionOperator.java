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
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlType;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@JsonPropertyOrder({"op", "children"})
@JsonInclude(Include.NON_NULL)
@XmlType(name = "ExpressionOperator", propOrder = {"op", "children"})
@XmlAccessorType(XmlAccessType.FIELD)
public final class ExpressionOperator extends ExpressionItem {

    @XmlElement(name = "op")
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

    @Override
    public boolean containsTerm(final Predicate<ExpressionTerm> predicate) {
        if (children != null) {
            for (final ExpressionItem child : children) {
                if (child.containsTerm(predicate)) {
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

    /**
     * @return All children enabled or not
     */
    public List<ExpressionItem> getChildren() {
        return children;
    }

    public boolean hasChildren() {
        return NullSafe.hasItems(children);
    }

    /**
     * @return All enabled children
     */
    @JsonIgnore
    public List<ExpressionItem> getEnabledChildren() {
        return children != null
                ? children.stream()
                .filter(Objects::nonNull)
                .filter(ExpressionItem::enabled)
                .collect(Collectors.toList())
                : Collections.emptyList();
    }

    /**
     * @return True if it has at least one child that is enabled
     */
    public boolean hasEnabledChildren() {
        return children != null
               && children.stream()
                       .anyMatch(expressionItem ->
                               expressionItem != null && expressionItem.enabled());
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
        final ExpressionOperator that = (ExpressionOperator) o;
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
            //noinspection SizeReplaceableByIsEmpty // Cos GWT
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


    // --------------------------------------------------------------------------------


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
}
