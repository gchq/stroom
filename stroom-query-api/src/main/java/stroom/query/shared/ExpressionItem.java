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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import java.io.Serializable;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "item", propOrder = { "enabled" })
@XmlSeeAlso({ ExpressionOperator.class, ExpressionTerm.class })
public abstract class ExpressionItem implements Serializable {
    private static final long serialVersionUID = -8483817637655853635L;

    @XmlElement(name = "enabled", defaultValue = "true")
    private boolean enabled = true;

    public ExpressionItem() {
        // Default constructor necessary for GWT serialisation.
    }

    /**
     * Recursive method to populates the passed list with all enabled
     * {@link ExpressionTerm} nodes found in the tree.
     */
    public static void findAllTermNodes(final ExpressionItem node, final List<ExpressionTerm> termsFound) {
        // Don't go any further down this branch if this node is disabled.
        if (node.enabled) {
            if (node instanceof ExpressionTerm) {
                final ExpressionTerm termNode = (ExpressionTerm) node;

                termsFound.add(termNode);

            } else if (node instanceof ExpressionOperator) {
                for (final ExpressionItem childNode : ((ExpressionOperator) node).getChildren()) {
                    findAllTermNodes(childNode, termsFound);
                }
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public abstract void append(final StringBuilder sb, final String pad, final boolean singleLine);

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb, "", false);
        return sb.toString();
    }

    public abstract ExpressionItem copy();

    protected <T extends ExpressionItem> T copyTo(final T dest) {
        ((ExpressionItem) dest).enabled = enabled;
        return dest;
    }

    /**
     * Recursive method to establish if an {@link ExpressionItem} (or any of its
     * children) equal/contain the passed field name
     *
     * @param fieldToFind
     *            The field to look for
     * @return True if it is found
     */
    public abstract boolean contains(String fieldToFind);

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        ExpressionItem that = (ExpressionItem) o;

        return new EqualsBuilder()
                .append(enabled, that.enabled)
                .isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder hashCodeBuilder = new HashCodeBuilder();
        hashCodeBuilder.append(enabled);
        return hashCodeBuilder.toHashCode();
    }
}
