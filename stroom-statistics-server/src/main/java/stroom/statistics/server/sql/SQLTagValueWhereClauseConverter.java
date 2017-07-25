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

package stroom.statistics.server.sql;

import stroom.statistics.server.sql.search.FilterOperationMode;
import stroom.statistics.server.sql.search.FilterTermsTree;
import stroom.statistics.server.sql.search.PrintableNode;
import stroom.util.sql.SQLSafe;

import java.util.EnumMap;
import java.util.List;

public class SQLTagValueWhereClauseConverter {
    // map to provide a lookup from the FilterOperationMode enum to the SQL
    // reserved word
    private static final EnumMap<FilterOperationMode, String> OPERATOR_TO_SQL_TERM_MAP;

    static {
        OPERATOR_TO_SQL_TERM_MAP = new EnumMap<>(FilterOperationMode.class);
        OPERATOR_TO_SQL_TERM_MAP.put(FilterOperationMode.AND, "AND");
        OPERATOR_TO_SQL_TERM_MAP.put(FilterOperationMode.OR, "OR");
        OPERATOR_TO_SQL_TERM_MAP.put(FilterOperationMode.NOT, "NOT");
    }

    public static String buildTagValueWhereClause(final FilterTermsTree filterTermsTree,
                                                  final List<String> bindVariables) {
        final StringBuilder whereClause = new StringBuilder("");

        if (filterTermsTree != null && filterTermsTree.getRootNode() != null) {
            convertNode(filterTermsTree.getRootNode(), whereClause, bindVariables);
        }

        return whereClause.toString();
    }

    /**
     * Recursive method to build up a where clause string (and associated list
     * of bind variables) from a tree of {@link PrintableNode} objects
     */
    private static void convertNode(final PrintableNode oldNode, final StringBuilder whereClause,
                                    final List<String> bindVariables) {
        if (oldNode instanceof FilterTermsTree.TermNode) {
            convertTermNode((FilterTermsTree.TermNode) oldNode, whereClause, bindVariables);
        } else if (oldNode instanceof FilterTermsTree.OperatorNode) {
            convertOperatorNode((FilterTermsTree.OperatorNode) oldNode, whereClause, bindVariables);

        } else {
            throw new RuntimeException(
                    "Node is of a type that we don't expect: " + oldNode.getClass().getCanonicalName());
        }
    }

    private static void convertTermNode(final FilterTermsTree.TermNode oldNode, final StringBuilder whereClause,
                                        final List<String> bindVariables) {
        final String valueString = oldNode.getValue();

        final String cleanedValue;

        if (valueString == null || valueString.isEmpty()) {
            cleanedValue = SQLStatisticConstants.NULL_VALUE_STRING;
        } else {
            cleanedValue = SQLSafe.cleanRegexpTerm(valueString);
        }
        // construct something like:
        // sql: ' name REGEXP ?'
        // bind: '¬Tag1¬Val1(¬|$)'
        // which equates to: ' name REGEXP '¬Tag1¬Val1(¬|$)' '

        whereClause.append(" " + SQLStatisticNames.NAME + " REGEXP ? ");

        final String regexTerm = SQLStatisticConstants.NAME_SEPARATOR + oldNode.getTag()
                + SQLStatisticConstants.NAME_SEPARATOR + cleanedValue + "($|" + SQLStatisticConstants.NAME_SEPARATOR
                + ")";

        bindVariables.add(regexTerm);
    }

    private static void convertOperatorNode(final FilterTermsTree.OperatorNode oldNode, final StringBuilder whereClause,
                                            final List<String> bindVariables) {
        if (oldNode.getChildren().size() < 1) {
            throw new RuntimeException("Operator node cannot have no children");
        }

        if (oldNode.getFilterOperationMode().equals(FilterOperationMode.NOT)) {
            if (oldNode.getChildren().size() > 1) {
                throw new RuntimeException("NOT node has more than one children");
            }
            // should get something like ' NOT (.......) '
            whereClause.append(" " + OPERATOR_TO_SQL_TERM_MAP.get(oldNode.getFilterOperationMode()) + " (");
            convertNode(oldNode.getChildren().get(0), whereClause, bindVariables);
            whereClause.append(" ) ");

        } else {
            // OR or AND
            // should get something like ' (... AND ... AND ...) '
            // where ... could be a term or another operator set
            whereClause.append(" ( ");
            int i = 1;
            for (final PrintableNode oldChild : oldNode.getChildren()) {
                convertNode(oldChild, whereClause, bindVariables);

                if (i != oldNode.getChildren().size()) {
                    // not the last child so add the operator in between
                    whereClause.append(" " + OPERATOR_TO_SQL_TERM_MAP.get(oldNode.getFilterOperationMode()) + " ");
                }
                i++;
            }
            whereClause.append(" ) ");
        }
    }
}
