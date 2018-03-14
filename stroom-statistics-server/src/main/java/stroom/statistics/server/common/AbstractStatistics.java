/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.statistics.server.common;

import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.query.shared.IndexFields;
import stroom.statistics.common.CommonStatisticConstants;
import stroom.statistics.common.FindEventCriteria;
import stroom.statistics.common.RolledUpStatisticEvent;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticStoreCache;
import stroom.statistics.common.StatisticStoreValidator;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.Statistics;
import stroom.statistics.common.rollup.RollUpBitMask;
import stroom.statistics.shared.CustomRollUpMask;
import stroom.statistics.shared.StatisticRollUpType;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.util.logging.StroomLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public abstract class AbstractStatistics implements Statistics {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(AbstractStatistics.class);

    private final StatisticStoreValidator statisticsDataSourceValidator;

    private final StatisticStoreCache statisticsDataSourceCache;

    private final StroomPropertyService propertyService;

    public AbstractStatistics(final StatisticStoreValidator statisticsDataSourceValidator,
                              final StatisticStoreCache statisticsDataSourceCache, final StroomPropertyService propertyService) {
        this.statisticsDataSourceValidator = statisticsDataSourceValidator;
        this.statisticsDataSourceCache = statisticsDataSourceCache;
        this.propertyService = propertyService;
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
            LOGGER.debug("%s property value: %s", CommonStatisticConstants.STROOM_STATISTIC_ENGINES_PROPERTY_NAME,
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

    public IndexFields getSupportedFields(final IndexFields indexFields) {
        final Set<String> blackList = getIndexFieldBlackList();

        if (blackList.size() == 0) {
            // nothing blacklisted so just return the standard list from the
            // data source
            return indexFields;
        } else {
            // construct an anonymous class instance that will filter out black
            // listed index fields, as supplied by the
            // sub-class
            final IndexFields supportedIndexFields = new IndexFields();
            indexFields.getIndexFields().stream()
                    .filter(indexField -> !blackList.contains(indexField.getFieldName()))
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

//    public abstract StatisticDataSet searchStatisticsData(final Search search, final StatisticStoreEntity dataSource);
}
