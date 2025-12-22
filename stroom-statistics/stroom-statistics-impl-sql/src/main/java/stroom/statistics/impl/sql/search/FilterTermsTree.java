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

package stroom.statistics.impl.sql.search;

import stroom.query.api.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.List;

/**
 * Class to hold a tree of filter terms for use in stats retrieval. Due to the
 * way the stats are stored (by UID) it is only possible to do equals or not
 * equals on an object type/value pair
 */
public class FilterTermsTree {

    private static final FilterTermsTree emptyTree;

    static {
        emptyTree = new FilterTermsTree(null);
    }

    PrintableNode root;

    public FilterTermsTree() {
    }

    public FilterTermsTree(final PrintableNode rootNode) {
        this.root = rootNode;
    }

    public static FilterTermsTree emptyTree() {
        return emptyTree;
    }

    public PrintableNode getRootNode() {
        return root;
    }

    public void setRootNode(final PrintableNode rootNode) {
        this.root = rootNode;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("[");
        if (root != null) {
            root.printNode(sb);
        }
        sb.append("]");

        return sb.toString();
    }

    /**
     * This class represents an actual filter term node in the filter tree, i.e.
     * X=Y
     */
    public static class TermNode implements PrintableNode {

        private final String tag;
        private final Condition condition;
        private final String value;

        public TermNode(final String tag, final String value) {
            this(tag, Condition.EQUALS, value);
        }

        public TermNode(final String tag, final Condition condition, final String value) {
            if (tag == null) {
                throw new FilterTermsTreeException("Must have a tag to be added as a filter term");
            }

            this.tag = tag;
            this.condition = condition;
            this.value = value;
        }

        public String getTag() {
            return this.tag;
        }

        public Condition getCondition() {
            return condition;
        }

        public String getValue() {
            return this.value;
        }

        @Override
        public void printNode(final StringBuilder sb) {
            final TermNode termNode = this;
            sb.append(termNode.getTag());
            sb.append(condition.getDisplayValue());
            sb.append(termNode.getValue());
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            printNode(sb);
            return sb.toString();
        }
    }

    /**
     * This class represents an operator node in the filter tree, i.e.
     * AND/OR/NOT
     */
    public static class OperatorNode implements PrintableNode {

        private FilterOperationMode filterOperationMode;
        private List<PrintableNode> children = new ArrayList<>();

        /**
         * Need to supply a list of children as there is no point in creating an
         * operator node without children
         *
         * @param childNodes
         */
        public OperatorNode(final FilterOperationMode filterOperationMode, final List<PrintableNode> childNodes) {
            if (filterOperationMode.equals(FilterOperationMode.NOT) && childNodes.size() < 1) {
                throw new FilterTermsTreeException("Cannot create an operator node with no child nodes");
            }
            // else if (filterOperationMode.equals(FilterOperationMode.OR) &&
            // childNodes.size() < 2) {
            // throw new FilterTermsTreeException(
            // "Cannot create an AND/OR operator node with less than two child
            // nodes");
            // }

            this.filterOperationMode = filterOperationMode;
            this.children.addAll(childNodes);
        }

        public List<PrintableNode> getChildren() {
            return children;
        }

        public void setChildren(final List<PrintableNode> children) {
            this.children = children;
        }

        public FilterOperationMode getFilterOperationMode() {
            return this.filterOperationMode;
        }

        public void setFilterOperationMode(final FilterOperationMode filterOperationMode) {
            this.filterOperationMode = filterOperationMode;
        }

        @Override
        public void printNode(final StringBuilder sb) {
            final OperatorNode operatorNode = this;
            sb.append(" ");
            sb.append(operatorNode.getFilterOperationMode().toString());
            sb.append(" ");
            sb.append("(");

            // print each of the child nodes
            for (final PrintableNode childNode : operatorNode.getChildren()) {
                childNode.printNode(sb);
                sb.append(",");
            }
            // remove the trailing comma
            if (sb.charAt(sb.length() - 1) == ',') {
                sb.deleteCharAt(sb.length() - 1);
            }

            sb.append(")");
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            printNode(sb);
            return sb.toString();
        }
    }

    public static class FilterTermsTreeException extends RuntimeException {

        private static final long serialVersionUID = 8955006804383215661L;

        public FilterTermsTreeException(final String message) {
            super(message);
        }
    }
}
