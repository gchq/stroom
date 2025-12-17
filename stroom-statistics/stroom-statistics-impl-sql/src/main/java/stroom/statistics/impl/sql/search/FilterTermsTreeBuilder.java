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

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.statistics.impl.sql.search.FilterTermsTree.OperatorNode;
import stroom.statistics.impl.sql.search.FilterTermsTree.TermNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FilterTermsTreeBuilder {

    private FilterTermsTreeBuilder() {
        // Utility class of static methods so should never be initialised
    }

    public static FilterTermsTree convertExpresionItemsTree(final ExpressionOperator rootItem) {
        return convertExpresionItemsTree(rootItem, Collections.emptySet());
    }

    /**
     * Converts a tree of {@link ExpressionItem} objects into a
     * {@link FilterTermsTree}. Conversion is not exact as some features of the
     * {@link ExpressionItem} tree are not supported so it may throw a
     * {@link RuntimeException}.
     *
     * @param rootItem The {@link ExpressionItem} object that is the root of the tree
     * @return A {@link FilterTermsTree} object containing a tree of
     * {@link PrintableNode} objects
     */
    public static FilterTermsTree convertExpresionItemsTree(final ExpressionOperator rootItem,
                                                            final Set<String> blackListedFieldNames) {
        final PrintableNode newRootNode = convertNode(rootItem, blackListedFieldNames);

        // we may have black listed all our terms and been left with a null root
        // node so handle that
        return newRootNode != null
                ? new FilterTermsTree(newRootNode)
                : FilterTermsTree.emptyTree();
    }

    private static PrintableNode convertNode(final ExpressionItem oldNode, final Set<String> fieldBlackList) {
        PrintableNode newNode = null;

        if (oldNode.enabled()) {
            if (oldNode instanceof ExpressionTerm) {
                final ExpressionTerm termNode = (ExpressionTerm) oldNode;
                if (termNode.getValue() != null && termNode.getValue().length() > 0) {
                    newNode = convertTermNode(termNode, fieldBlackList);
                }
            } else if (oldNode instanceof ExpressionOperator) {
                newNode = convertOperatorNode((ExpressionOperator) oldNode, fieldBlackList);
            } else {
                throw new RuntimeException("Node is of a type that we don't expect: " + oldNode.getClass().getName());
            }
        }

        // the node is disabled so just return null rather than including it
        // in the new tree
        return newNode;
    }

    private static PrintableNode convertTermNode(final ExpressionTerm oldNode, final Set<String> fieldBlackList) {
        final PrintableNode newNode;

        if (fieldBlackList != null && fieldBlackList.contains(oldNode.getField())) {
            // this term is black listed so ignore it
            newNode = null;
        } else {
            // TODO we could convert a CONTAINS ExpressionTerm to multiple
            // TermNodes contained within an OR
            // OperatorNode. e.g. if the ExpressionTerm is 'CONTAINS "apache"'
            // then we go to the UID cache to find
            // all
            // tag values that contain apache and included them all as OR
            // TermNodes. We cannot propagate partial
            // matches
            // any further down the stack as we can only filter on distinct UIDs

            if (oldNode.getCondition().equals(Condition.EQUALS)) {
                newNode = new TermNode(oldNode.getField(), Condition.EQUALS, oldNode.getValue());
            } else if (oldNode.getCondition().equals(Condition.NOT_EQUALS)) {
                newNode = new TermNode(oldNode.getField(), Condition.NOT_EQUALS, oldNode.getValue());
            } else if (oldNode.getCondition().equals(Condition.IN)) {
                if (oldNode.getValue() == null) {
                    newNode = new TermNode(oldNode.getField(), Condition.EQUALS, null);
                } else {
                    final String[] values = oldNode.getValue().split(Condition.IN_CONDITION_DELIMITER);

                    if (values.length == 1) {
                        // only one value so just convert it like it is EQUALS
                        newNode = new TermNode(oldNode.getField(), Condition.EQUALS, oldNode.getValue());
                    } else {
                        // multiple values in the IN list so convert it into a
                        // set of EQUALS terms under and OR node
                        final List<PrintableNode> orTermNodes = new ArrayList<>();

                        for (final String value : values) {
                            orTermNodes.add(convertTermNode(
                                    ExpressionTerm
                                            .builder()
                                            .field(oldNode.getField())
                                            .condition(Condition.EQUALS)
                                            .value(value)
                                            .build(),
                                    fieldBlackList));
                        }
                        newNode = new OperatorNode(FilterOperationMode.OR, orTermNodes);
                    }
                }
            } else {
                throw new UnsupportedOperationException("Only EQUALS, NOT_EQUALS and IN are currently supported");
            }
        }
        return newNode;
    }

    /**
     * @return The converted node, null if the old node has no children
     */
    private static PrintableNode convertOperatorNode(final ExpressionOperator oldNode,
                                                     final Set<String> fieldBlackList) {
        // ExpressionOperator can be created with no child nodes so if that is
        // the case just return null and handle for
        // the null in the calling method

        if (oldNode.getChildren() == null || oldNode.getChildren().size() == 0) {
            return null;
        } else {
            final FilterOperationMode operationMode = FilterOperationMode.valueOf(oldNode.op().toString());

            final List<PrintableNode> children = new ArrayList<>();

            for (final ExpressionItem oldChild : oldNode.getChildren()) {
                final PrintableNode newChild = convertNode(oldChild, fieldBlackList);

                // if the newChild is null it means it was probably an
                // ExpressionOperator with no children
                if (newChild != null) {
                    children.add(newChild);
                }
            }

            PrintableNode newNode = null;
            // term nodes may have been returned as null if they were expression
            // terms that this tree does not support
            if (children.isEmpty()) {
                // newNode is already null so do nothing
            } else if (children.size() == 1 && !operationMode.equals(FilterOperationMode.NOT)) {
                // only have one child for an AND or OR so no point in keeping
                // the operator node, just had the one child
                // to the tree instead
                newNode = children.get(0);
            } else {
                newNode = new OperatorNode(operationMode, children);
            }

            return newNode;
        }
    }
}
