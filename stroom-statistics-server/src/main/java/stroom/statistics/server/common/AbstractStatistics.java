/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.statistics.server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.datasource.api.DataSourceField;
import stroom.entity.shared.Period;
import stroom.entity.shared.Range;
import stroom.node.server.StroomPropertyService;
import stroom.query.DateExpressionParser;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Query;
import stroom.statistics.common.CommonStatisticConstants;
import stroom.statistics.common.FilterTermsTree;
import stroom.statistics.common.FilterTermsTreeBuilder;
import stroom.statistics.common.FindEventCriteria;
import stroom.statistics.common.RolledUpStatisticEvent;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticStoreCache;
import stroom.statistics.common.StatisticStoreValidator;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.Statistics;
import stroom.statistics.common.rollup.RollUpBitMask;
import stroom.statistics.shared.common.CustomRollUpMask;
import stroom.statistics.shared.common.StatisticRollUpType;
import stroom.statistics.shared.StatisticStoreEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public abstract class AbstractStatistics implements Statistics {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractStatistics.class);

    private static final List<Condition> SUPPORTED_DATE_CONDITIONS = Arrays.asList(Condition.BETWEEN);

    private final StatisticStoreValidator statisticsDataSourceValidator;

    private final StatisticStoreCache statisticsDataSourceCache;

    private final StroomPropertyService propertyService;

    public AbstractStatistics(final StatisticStoreValidator statisticsDataSourceValidator,
                              final StatisticStoreCache statisticsDataSourceCache, final StroomPropertyService propertyService) {
        this.statisticsDataSourceValidator = statisticsDataSourceValidator;
        this.statisticsDataSourceCache = statisticsDataSourceCache;
        this.propertyService = propertyService;
    }

    protected static FindEventCriteria buildCriteria(final Query query, final StatisticStoreEntity dataSource) {
        LOGGER.trace("buildCriteria called for statistic {}", dataSource.getName());

        // Get the current time in millis since epoch.
        final long nowEpochMilli = System.currentTimeMillis();

        // object looks a bit like this
        // AND
        // Date Time between 2014-10-22T23:00:00.000Z,2014-10-23T23:00:00.000Z

        final ExpressionOperator topLevelExpressionOperator = query.getExpression();

        if (topLevelExpressionOperator == null || topLevelExpressionOperator.getOp() == null) {
            throw new IllegalArgumentException(
                    "The top level operator for the query must be one of [" + ExpressionOperator.Op.values() + "]");
        }

        final List<ExpressionItem> childExpressions = topLevelExpressionOperator.getChildren();
        int validDateTermsFound = 0;
        int dateTermsFound = 0;

        // Identify the date term in the search criteria. Currently we must have
        // a exactly one BETWEEN operator on the
        // datetime
        // field to be able to search. This is because of the way the search in
        // hbase is done, ie. by start/stop row
        // key.
        // It may be possible to expand the capability to make multiple searches
        // but that is currently not in place
        ExpressionTerm dateTerm = null;
        if (childExpressions != null) {
            for (final ExpressionItem expressionItem : childExpressions) {
                if (expressionItem instanceof ExpressionTerm) {
                    final ExpressionTerm expressionTerm = (ExpressionTerm) expressionItem;

                    if (expressionTerm.getField() == null) {
                        throw new IllegalArgumentException("Expression term does not have a field specified");
                    }

                    if (expressionTerm.getField().equals(StatisticStoreEntity.FIELD_NAME_DATE_TIME)) {
                        dateTermsFound++;

                        if (SUPPORTED_DATE_CONDITIONS.contains(expressionTerm.getCondition())) {
                            dateTerm = expressionTerm;
                            validDateTermsFound++;
                        }
                    }
                } else if (expressionItem instanceof ExpressionOperator) {
                    if (((ExpressionOperator) expressionItem).getOp() == null) {
                        throw new IllegalArgumentException(
                                "An operator in the query is missing a type, it should be one of " + Op.values());
                    }
                }
            }
        }

        // ensure we have a date term
        if (dateTermsFound != 1 || validDateTermsFound != 1) {
            throw new UnsupportedOperationException(
                    "Search queries on the statistic store must contain one term using the '"
                            + StatisticStoreEntity.FIELD_NAME_DATE_TIME
                            + "' field with one of the following condtitions [" + SUPPORTED_DATE_CONDITIONS.toString()
                            + "].  Please amend the query");
        }

        // ensure the value field is not used in the query terms
        if (contains(query.getExpression(), StatisticStoreEntity.FIELD_NAME_VALUE)) {
            throw new UnsupportedOperationException("Search queries containing the field '"
                    + StatisticStoreEntity.FIELD_NAME_VALUE + "' are not supported.  Please remove it from the query");
        }

        // if we have got here then we have a single BETWEEN date term, so parse
        // it.
        final Range<Long> range = extractRange(dateTerm, nowEpochMilli);

        final List<ExpressionTerm> termNodesInFilter = new ArrayList<>();
        findAllTermNodes(topLevelExpressionOperator, termNodesInFilter);

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
                throw new UnsupportedOperationException(
                        "Query contains rolled up terms but the Statistic Data Source does not support any roll-ups");
            } else if (dataSource.getRollUpType().equals(StatisticRollUpType.CUSTOM)) {
                if (!dataSource.isRollUpCombinationSupported(rolledUpFieldNames)) {
                    throw new UnsupportedOperationException(String.format(
                            "The query contains a combination of rolled up fields %s that is not in the list of custom roll-ups for the statistic data source",
                            rolledUpFieldNames));
                }
            }
        }

        // Date Time is handled spearately to the the filter tree so ignore it
        // in the conversion
        final Set<String> blackListedFieldNames = new HashSet<>();
        blackListedFieldNames.addAll(rolledUpFieldNames);
        blackListedFieldNames.add(StatisticStoreEntity.FIELD_NAME_DATE_TIME);

        final FilterTermsTree filterTermsTree = FilterTermsTreeBuilder
                .convertExpresionItemsTree(topLevelExpressionOperator, blackListedFieldNames);

        final FindEventCriteria criteria = FindEventCriteria.instance(new Period(range.getFrom(), range.getTo()),
                dataSource.getName(), filterTermsTree, rolledUpFieldNames);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Searching statistics store with criteria: {}", criteria.toString());
        }

        return criteria;
    }

    /**
     * Recursive method to populates the passed list with all enabled
     * {@link ExpressionTerm} nodes found in the tree.
     */
    public static void findAllTermNodes(final ExpressionItem node, final List<ExpressionTerm> termsFound) {
        // Don't go any further down this branch if this node is disabled.
        if (node.enabled()) {
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

    public static boolean contains(final ExpressionItem expressionItem, final String fieldToFind) {
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

    // TODO could go futher up the chain so is store agnostic
    public static RolledUpStatisticEvent generateTagRollUps(final StatisticEvent event,
                                                            final StatisticStoreEntity statisticsDataSource) {
        RolledUpStatisticEvent rolledUpStatisticEvent = null;

        final int eventTagListSize = event.getTagList().size();

        final StatisticRollUpType rollUpType = statisticsDataSource.getRollUpType();

        if (eventTagListSize == 0 || StatisticRollUpType.NONE.equals(rollUpType)) {
            rolledUpStatisticEvent = new RolledUpStatisticEvent(event);
        } else if (StatisticRollUpType.ALL.equals(rollUpType)) {
            final List<List<StatisticTag>> tagListPerms = generateStatisticTagPerms(event.getTagList(),
                    RollUpBitMask.getRollUpPermutationsAsBooleans(eventTagListSize));

            // wrap the original event along with the perms list
            rolledUpStatisticEvent = new RolledUpStatisticEvent(event, tagListPerms);

        } else if (StatisticRollUpType.CUSTOM.equals(rollUpType)) {
            final Set<List<Boolean>> perms = new HashSet<>();
            for (final CustomRollUpMask mask : statisticsDataSource.getStatisticDataSourceDataObject()
                    .getCustomRollUpMasks()) {
                final RollUpBitMask rollUpBitMask = RollUpBitMask.fromTagPositions(mask.getRolledUpTagPositions());

                perms.add(rollUpBitMask.getBooleanMask(eventTagListSize));
            }
            final List<List<StatisticTag>> tagListPerms = generateStatisticTagPerms(event.getTagList(), perms);

            rolledUpStatisticEvent = new RolledUpStatisticEvent(event, tagListPerms);
        }

        return rolledUpStatisticEvent;
    }

    private static Range<Long> extractRange(final ExpressionTerm dateTerm, final long nowEpochMilli) {

        final String[] dateArr = dateTerm.getValue().split(",");

        if (dateArr.length != 2) {
            throw new RuntimeException("DateTime term is not a valid format, term: " + dateTerm.toString());
        }

        long rangeFrom = DateExpressionParser.parse(dateArr[0], nowEpochMilli)
                .map(time -> time.toInstant().toEpochMilli())
                .orElse(0L);
        // add one to make it exclusive
        long rangeTo = DateExpressionParser.parse(dateArr[1], nowEpochMilli)
                .map(time -> time.toInstant().toEpochMilli() + 1)
                .orElse(Long.MAX_VALUE);

        final Range<Long> range = new Range<>(rangeFrom, rangeTo);

        return range;
    }

    private static List<List<StatisticTag>> generateStatisticTagPerms(final List<StatisticTag> eventTags,
                                                                      final Set<List<Boolean>> perms) {
        final List<List<StatisticTag>> tagListPerms = new ArrayList<>();
        final int eventTagListSize = eventTags.size();

        for (final List<Boolean> perm : perms) {
            final List<StatisticTag> tags = new ArrayList<>();
            for (int i = 0; i < eventTagListSize; i++) {
                if (perm.get(i).booleanValue() == true) {
                    // true means a rolled up tag so create a new tag with the
                    // rolled up marker
                    tags.add(new StatisticTag(eventTags.get(i).getTag(), RollUpBitMask.ROLL_UP_TAG_VALUE));
                } else {
                    // false means not rolled up so use the existing tag's value
                    tags.add(eventTags.get(i));
                }
            }
            tagListPerms.add(tags);
        }
        return tagListPerms;
    }

    /**
     * TODO: This is a bit simplistic as a user could create a filter that said
     * user=user1 AND user='*' which makes no sense. At the moment we would
     * assume that the user tag is being rolled up so user=user1 would never be
     * found in the data and thus would return no data.
     */
    public static RollUpBitMask buildRollUpBitMaskFromCriteria(final FindEventCriteria criteria,
                                                               final StatisticStoreEntity statisticsDataSource) {
        final Set<String> rolledUpTagsFound = criteria.getRolledUpFieldNames();

        final RollUpBitMask result;

        if (rolledUpTagsFound.size() > 0) {
            final List<Integer> rollUpTagPositionList = new ArrayList<>();

            for (final String tag : rolledUpTagsFound) {
                final Integer position = statisticsDataSource.getPositionInFieldList(tag);
                if (position == null) {
                    throw new RuntimeException(String.format("No field position found for tag %s", tag));
                }
                rollUpTagPositionList.add(position);
            }
            result = RollUpBitMask.fromTagPositions(rollUpTagPositionList);

        } else {
            result = RollUpBitMask.ZERO_MASK;
        }
        return result;
    }

    public static boolean isDataStoreEnabled(final String engineName, final StroomPropertyService propertyService) {
        final String enabledEngines = propertyService
                .getProperty(CommonStatisticConstants.STROOM_STATISTIC_ENGINES_PROPERTY_NAME);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("{} property value: {}", CommonStatisticConstants.STROOM_STATISTIC_ENGINES_PROPERTY_NAME,
                    enabledEngines);
        }

        boolean result = false;

        if (enabledEngines != null) {
            for (final String engine : enabledEngines.split(",")) {
                if (engine.equals(engineName)) {
                    result = true;
                }
            }
        }
        return result;
    }

    @Override
    public boolean putEvent(final StatisticEvent statisticEvent) {
        final StatisticStoreEntity statisticsDataSource = getStatisticsDataSource(statisticEvent.getName(),
                getEngineName());
        return putEvent(statisticEvent, statisticsDataSource);
    }

    @Override
    public boolean putEvents(final List<StatisticEvent> statisticEvents) {
        // sort the list of events by name so we can send ones with the same
        // stat name off together
        Collections.sort(statisticEvents, new Comparator<StatisticEvent>() {
            @Override
            public int compare(final StatisticEvent event1, final StatisticEvent event2) {
                return event1.getName().compareTo(event2.getName());
            }
        });

        final List<StatisticEvent> eventsBatch = new ArrayList<>();
        String statNameLastSeen = null;

        boolean outcome = true;

        for (final StatisticEvent event : statisticEvents) {
            // we can only put a batch of events if they share the same stat
            // name
            if (statNameLastSeen != null && !event.getName().equals(statNameLastSeen)) {
                outcome = outcome && putBatch(eventsBatch);
            }

            eventsBatch.add(event);

            statNameLastSeen = event.getName();
        }

        // sweep up any stragglers
        outcome = outcome && putBatch(eventsBatch);

        return outcome;
    }

    private boolean putBatch(final List<StatisticEvent> eventsBatch) {
        boolean outcome = true;
        if (eventsBatch.size() > 0) {
            final StatisticEvent firstEventInBatch = eventsBatch.get(0);
            final StatisticStoreEntity statisticsDataSource = getStatisticsDataSource(firstEventInBatch.getName(),
                    getEngineName());
            outcome = putEvents(eventsBatch, statisticsDataSource);
            eventsBatch.clear();
        }
        return outcome;
    }

    protected boolean validateStatisticDataSource(final StatisticEvent statisticEvent,
                                                  final StatisticStoreEntity statisticsDataSource) {
        if (statisticsDataSourceValidator != null) {
            return statisticsDataSourceValidator.validateStatisticDataSource(statisticEvent.getName(), getEngineName(),
                    statisticEvent.getType(), statisticsDataSource);
        } else {
            // no validator has been supplied so return true
            return true;
        }
    }

    protected StatisticStoreEntity getStatisticsDataSource(final String statisticName, final String engineName) {
        return statisticsDataSourceCache.getStatisticsDataSource(statisticName, engineName);
    }

    public List<DataSourceField> getSupportedFields(final List<DataSourceField> indexFields) {
        final Set<String> blackList = getIndexFieldBlackList();

        if (blackList.size() == 0) {
            // nothing blacklisted so just return the standard list from the
            // data source
            return indexFields;
        } else {
            // construct an anonymous class instance that will filter out black
            // listed index fields, as supplied by the
            // sub-class
            final List<DataSourceField> supportedIndexFields = new ArrayList<>();
            indexFields.stream()
                    .filter(indexField -> !blackList.contains(indexField.getName()))
                    .forEach(supportedIndexFields::add);

            return supportedIndexFields;
        }
    }

    /**
     * Template method, should be overridden by a sub-class if it needs to black
     * list certain index fields
     *
     * @return
     */
    protected Set<String> getIndexFieldBlackList() {
        return Collections.emptySet();
    }

    public boolean isDataStoreEnabled() {
        return isDataStoreEnabled(getEngineName(), propertyService);
    }

    public List<Set<Integer>> getFieldPositionsForBitMasks(final List<Short> maskValues) {
        if (maskValues != null) {
            final List<Set<Integer>> tagPosPermsList = new ArrayList<>();

            for (final Short maskValue : maskValues) {
                tagPosPermsList.add(RollUpBitMask.fromShort(maskValue).getTagPositions());
            }
            return tagPosPermsList;
        } else {
            return Collections.emptyList();
        }
    }
}
