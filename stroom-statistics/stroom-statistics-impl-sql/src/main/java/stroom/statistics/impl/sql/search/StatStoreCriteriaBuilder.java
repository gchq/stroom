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

import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.statistics.impl.sql.rollup.RollUpBitMask;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.util.Period;
import stroom.util.rest.RestUtil;
import stroom.util.shared.Range;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatStoreCriteriaBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatStoreCriteriaBuilder.class);

    public static FindEventCriteria buildCriteria(final StatisticStoreDoc dataSource,
                                                  final ExpressionOperator expression,
                                                  final DateTimeSettings dateTimeSettings) {

        LOGGER.trace(String.format("buildCriteria called for statistic %s", dataSource.getName()));

        // object looks a bit like this
        // AND
        // Date Time between 2014-10-22T23:00:00.000Z,2014-10-23T23:00:00.000Z

//        ExpressionOperator topLevelExpressionOperator = search.getQuery().getExpression();
//        final Map<String, String> paramMap = ExpressionParamUtil.createParamMap(search.getQuery().getParams());
//        topLevelExpressionOperator = ExpressionUtil.replaceExpressionParameters(topLevelExpressionOperator, paramMap);

        if (expression == null || expression.op() == null) {
            throw RestUtil.badRequest(
                    "The top level operator for the query must be one of [" + Arrays.toString(Op.values()) + "]");
        }

        final List<ExpressionItem> childExpressions = expression.getChildren();

        // Identify the date term in the search criteria. Currently we must have a exactly one BETWEEN operator on the
        // datetime field to be able to search. This is because of the way the search in hbase is done, ie. by
        // start/stop row key.
        // It may be possible to expand the capability to make multiple searches but that is currently not in place
        final List<ExpressionTerm> dateTerms = getDateTerms(childExpressions);

        // ensure the value field is not used in the query terms
        if (contains(expression, StatisticStoreDoc.FIELD_NAME_VALUE)) {
            throw RestUtil.badRequest("Search queries containing the field '"
                    + StatisticStoreDoc.FIELD_NAME_VALUE + "' are not supported.  Please remove it from the query");
        }

        // if we have got here then we have a single BETWEEN date term, so parse
        // it.
        final Range<Long> range = extractRange(dateTerms, dateTimeSettings);

        final List<ExpressionTerm> termNodesInFilter = new ArrayList<>();
        findAllTermNodes(expression, termNodesInFilter);

        final Set<String> rolledUpFieldNames = new HashSet<>();

        for (final ExpressionTerm term : termNodesInFilter) {
            // add any fields that use the roll up marker to the black list. If
            // somebody has said user=* then we do not
            // want that in the filter as it will slow it down. The fact that
            // they have said user=* means it will use
            // the statistic name appropriate for that rollup, meaning the
            // filtering is built into the stat name.
            if (term.getValue().equals(RollUpBitMask.ROLL_UP_TAG_VALUE)) {
                rolledUpFieldNames.add(term.getField());
            }
        }

        if (!rolledUpFieldNames.isEmpty()) {
            if (dataSource.getRollUpType().equals(StatisticRollUpType.NONE)) {
                throw RestUtil.badRequest(
                        "Query contains rolled up terms but the Statistic Data Source does not support any roll-ups");
            } else if (dataSource.getRollUpType().equals(StatisticRollUpType.CUSTOM)) {
                if (!dataSource.isRollUpCombinationSupported(rolledUpFieldNames)) {
                    throw RestUtil.badRequest(String.format("The query contains a combination of rolled up " +
                            "fields %s that is not in the list of custom roll-ups for the statistic data " +
                            "source", rolledUpFieldNames));
                }
            }
        }

        // Date Time is handled separately to the the filter tree so ignore it
        // in the conversion
        final Set<String> blackListedFieldNames = new HashSet<>(rolledUpFieldNames);
        blackListedFieldNames.add(StatisticStoreDoc.FIELD_NAME_DATE_TIME);

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder
                .convertExpresionItemsTree(expression, blackListedFieldNames);

        final FindEventCriteria criteria = FindEventCriteria.instance(new Period(range.getFrom(), range.getTo()),
                dataSource.getName(), filterTermsTree, rolledUpFieldNames);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info(String.format("Searching statistics store with criteria: %s", criteria));
        }

        return criteria;
    }

    private static List<ExpressionTerm> getDateTerms(final List<ExpressionItem> childExpressions) {
        final List<ExpressionTerm> dateTerms = new ArrayList<>();
        if (childExpressions != null) {
            for (final ExpressionItem expressionItem : childExpressions) {
                if (expressionItem.enabled()) {
                    if (expressionItem instanceof final ExpressionTerm expressionTerm) {

                        if (expressionTerm.getField() == null) {
                            throw RestUtil.badRequest("Expression term does not have a field specified");
                        }

                        if (expressionTerm.getField().equals(StatisticStoreDoc.FIELD_NAME_DATE_TIME)) {
                            dateTerms.add(expressionTerm);
                        }
                    } else if (expressionItem instanceof ExpressionOperator) {
                        if (Op.AND.equals(((ExpressionOperator) expressionItem).op())) {
                            dateTerms.addAll(getDateTerms(((ExpressionOperator) expressionItem).getChildren()));
                        }
                    }
                }
            }
        }
        return dateTerms;
    }

    /**
     * Recursive method to populates the passed list with all enabled
     * {@link ExpressionTerm} nodes found in the tree.
     */
    private static void findAllTermNodes(final ExpressionItem node, final List<ExpressionTerm> termsFound) {
        // Don't go any further down this branch if this node is disabled.
        if (node.enabled()) {
            if (node instanceof final ExpressionTerm termNode) {
                termsFound.add(termNode);

            } else if (node instanceof final ExpressionOperator expressionOperator) {
                if (expressionOperator.getChildren() != null) {
                    for (final ExpressionItem childNode : expressionOperator.getChildren()) {
                        findAllTermNodes(childNode, termsFound);
                    }
                }
            }
        }
    }

    private static boolean contains(final ExpressionItem expressionItem, final String fieldToFind) {
        boolean hasBeenFound = false;

        if (expressionItem instanceof ExpressionOperator) {
            if (((ExpressionOperator) expressionItem).getChildren() != null) {
                for (final ExpressionItem item : ((ExpressionOperator) expressionItem).getChildren()) {
                    hasBeenFound = contains(item, fieldToFind);
                    if (hasBeenFound) {
                        break;
                    }
                }
            }
        } else {
            if (((ExpressionTerm) expressionItem).getField() != null) {
                hasBeenFound = ((ExpressionTerm) expressionItem).getField().equals(fieldToFind);
            }
        }

        return hasBeenFound;
    }

    private static Range<Long> extractRange(final List<ExpressionTerm> dateTerms,
                                            final DateTimeSettings dateTimeSettings) {
        long maxRangeFrom = 0;
        long minRangeTo = Long.MAX_VALUE;

        for (final ExpressionTerm term : dateTerms) {
            long rangeFrom = 0;
            long rangeTo = Long.MAX_VALUE;

            if (Condition.BETWEEN.equals(term.getCondition())) {
                final String[] dateArr = term.getValue().split(",");

                if (dateArr.length != 2) {
                    throw RestUtil.badRequest("DateTime term is not a valid format, term: " + term);
                }

                rangeFrom = parseDateTime("from", dateArr[0], dateTimeSettings);
                // add one to make it exclusive
                rangeTo = parseDateTime("to", dateArr[1], dateTimeSettings) + 1;
            } else if (Condition.EQUALS.equals(term.getCondition())) {
                rangeFrom = parseDateTime("from", term.getValue(), dateTimeSettings);
                rangeTo = rangeFrom;
            } else if (Condition.GREATER_THAN.equals(term.getCondition())) {
                rangeFrom = parseDateTime("from", term.getValue(), dateTimeSettings) + 1;
            } else if (Condition.GREATER_THAN_OR_EQUAL_TO.equals(term.getCondition())) {
                rangeFrom = parseDateTime("from", term.getValue(), dateTimeSettings);
            } else if (Condition.LESS_THAN.equals(term.getCondition())) {
                rangeTo = parseDateTime("to", term.getValue(), dateTimeSettings) - 1;
            } else if (Condition.LESS_THAN_OR_EQUAL_TO.equals(term.getCondition())) {
                rangeTo = parseDateTime("to", term.getValue(), dateTimeSettings);
            } else {
                throw RestUtil.badRequest("Unsupported condition for DateTime term: " + term);
            }

            maxRangeFrom = Math.max(maxRangeFrom, rangeFrom);
            minRangeTo = Math.min(minRangeTo, rangeTo);
        }

        return new Range<>(maxRangeFrom, minRangeTo);
    }

    private static long parseDateTime(final String type,
                                      final String value,
                                      final DateTimeSettings dateTimeSettings) {
        try {
            return DateExpressionParser.getMs(type, value, dateTimeSettings);
        } catch (final Exception e) {
            throw RestUtil.badRequest("DateTime term has an invalid '" + type + "' value of '" + value + "'");
        }
    }
}
