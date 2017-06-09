/*
 * Copyright 2016 Crown Copyright
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

package stroom.query.shared;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import stroom.util.shared.HasDisplayValue;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "operator", propOrder = { "op", "children" })
@XmlRootElement(name = "operator")
public class ExpressionOperator extends ExpressionItem {
    private static final long serialVersionUID = 6602004424564268512L;

    public enum Op implements HasDisplayValue {
        AND("AND"), OR("OR"), NOT("NOT");

        private final String displayValue;

        Op(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }

    @XmlElement(name = "op")
    private Op op = Op.AND;

    @XmlElementWrapper(name = "children")
    @XmlElements({ @XmlElement(name = "operator", type = ExpressionOperator.class),
            @XmlElement(name = "term", type = ExpressionTerm.class) })
    private List<ExpressionItem> children = new ArrayList<>();

    public ExpressionOperator() {
        // Default constructor necessary for GWT serialisation.
    }

    public ExpressionOperator(final Op op) {
        this.op = op;
    }

    public void setType(final Op op) {
        this.op = op;
    }

    public Op getType() {
        return op;
    }

    public void addChild(final ExpressionItem child) {
        children.add(child);
    }

    public void removeChild(final ExpressionItem child) {
        children.remove(child);
    }

    public List<ExpressionItem> getChildren() {
        return children;
    }

    public void setChildren(final List<ExpressionItem> children) {
        this.children = children;
    }

    @Override
    public void append(final StringBuilder sb, final String pad, final boolean singleLine) {
        if (isEnabled()) {
            final String padding = pad + "  ";

            if (!singleLine) {
                sb.append(pad);
            }
            sb.append(op);
            if (!singleLine) {
                sb.append("\n");
            } else {
                sb.append(" {");
            }

            if (children != null) {
                final Iterator<ExpressionItem> iter = children.iterator();

                while (iter.hasNext()) {
                    final ExpressionItem child = iter.next();
                    child.append(sb, padding, singleLine);

                    if (singleLine) {
                        if (iter.hasNext()) {
                            sb.append(", ");
                        }
                    } else {
                        sb.append("\n");
                    }
                }

            }

            if (singleLine) {
                sb.append("} ");
            }
        }
    }

    protected <T extends ExpressionOperator> T copyTo(T dest) {
        dest = super.copyTo(dest);
        ((ExpressionOperator) dest).op = op;

        if (children != null) {
            for (final ExpressionItem item : children) {
                dest.addChild(item.copy());
            }
        }

        return dest;
    }

    @Override
    public ExpressionOperator copy() {
        return copyTo(new ExpressionOperator());
    }

    @Override
    public boolean contains(final String fieldToFind) {
        boolean hasBeenFound = false;

        if (children != null) {
            for (final ExpressionItem item : children) {
                if (item.contains(fieldToFind)) {
                    hasBeenFound = true;
                    break;
                }
            }
        }

        return hasBeenFound;
    }

    @Override
    public boolean internalEquals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final ExpressionOperator that = (ExpressionOperator) o;

        if (op != that.op) return false;

        if (children != null && that.children != null) {
            if (children.size() != that.children.size()) {
                return false;
            }

            for (int i = 0; i < children.size(); i++) {
                final ExpressionItem expressionItem1 = children.get(i);
                final ExpressionItem expressionItem2 = that.children.get(i);
                if (!expressionItem1.internalEquals(expressionItem2)) {
                    return false;
                }
            }
        }

        return children == that.children;
    }

    @Override
    public int internalHashCode() {
        int result = op != null ? op.hashCode() : 0;

        if (children != null) {
            result = 31 * result + children.size();
            for (final ExpressionItem expressionItem : children) {
                result = 31 * result + expressionItem.internalHashCode();
            }
        }

        return result;
    }
}
