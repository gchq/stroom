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

package stroom.statistics.server.common;

import org.junit.Assert;
import org.junit.Test;
import stroom.node.server.MockStroomPropertyService;
import stroom.query.api.ExpressionBuilder;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.Query;
import stroom.statistics.common.CommonStatisticConstants;
import stroom.statistics.common.FilterTermsTree;
import stroom.statistics.common.FindEventCriteria;
import stroom.statistics.common.RolledUpStatisticEvent;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.common.StatisticTag;
import stroom.statistics.common.TimeAgnosticStatisticEvent;
import stroom.statistics.common.rollup.RollUpBitMask;
import stroom.statistics.shared.CustomRollUpMask;
import stroom.statistics.shared.StatisticField;
import stroom.statistics.shared.StatisticRollUpType;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.util.date.DateUtil;
import stroom.util.test.StroomUnitTest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class TestAbstractStatisticEventStore extends StroomUnitTest {
    private static final long EVENT_TIME = 1234L;
    private static final String EVENT_NAME = "MyStatistic";
    private static final long EVENT_COUNT = 1;

    private static final String TAG1_NAME = "Tag1";
    private static final String TAG1_VALUE = "Tag1Val";

    private static final String TAG2_NAME = "Tag2";
    private static final String TAG2_VALUE = "Tag2Val";

    private static final String ROLLED_UP_VALUE = "*";

    @Test
    public void testGenerateTagRollUpsTwoTagsAllRollUps() {
        final StatisticEvent event = buildEvent(buildTagList());

        final StatisticStoreEntity statisticsDataSource = buildStatisticDataSource();

        final RolledUpStatisticEvent rolledUpStatisticEvent = AbstractStatistics.generateTagRollUps(event,
                statisticsDataSource);

        assertEquals(4, rolledUpStatisticEvent.getPermutationCount());

        final List<TimeAgnosticStatisticEvent> timeAgnosticStatisticEvents = new ArrayList<TimeAgnosticStatisticEvent>();

        // define all the tag/value perms we expect to get back
        final List<List<StatisticTag>> expectedTagPerms = new ArrayList<List<StatisticTag>>();
        expectedTagPerms
                .add(Arrays.asList(new StatisticTag(TAG1_NAME, TAG1_VALUE), new StatisticTag(TAG2_NAME, TAG2_VALUE)));
        expectedTagPerms.add(
                Arrays.asList(new StatisticTag(TAG1_NAME, TAG1_VALUE), new StatisticTag(TAG2_NAME, ROLLED_UP_VALUE)));
        expectedTagPerms.add(
                Arrays.asList(new StatisticTag(TAG1_NAME, ROLLED_UP_VALUE), new StatisticTag(TAG2_NAME, TAG2_VALUE)));
        expectedTagPerms.add(Arrays.asList(new StatisticTag(TAG1_NAME, ROLLED_UP_VALUE),
                new StatisticTag(TAG2_NAME, ROLLED_UP_VALUE)));

        System.out.println("-------------------------------------------------");
        for (final List<StatisticTag> expectedTagList : expectedTagPerms) {
            System.out.println(expectedTagList);
        }
        System.out.println("-------------------------------------------------");

        for (final TimeAgnosticStatisticEvent eventPerm : rolledUpStatisticEvent) {
            // make sure we don't already have one like this.
            assertFalse(timeAgnosticStatisticEvents.contains(eventPerm));

            assertEquals(event.getName(), eventPerm.getName());
            assertEquals(event.getType(), eventPerm.getType());
            assertEquals(event.getCount(), eventPerm.getCount());
            assertEquals(event.getTagList().size(), eventPerm.getTagList().size());

            System.out.println(eventPerm.getTagList());

            assertTrue(expectedTagPerms.contains(eventPerm.getTagList()));

            for (int i = 0; i < event.getTagList().size(); i++) {
                assertEquals(event.getTagList().get(i).getTag(), eventPerm.getTagList().get(i).getTag());
                Assert.assertTrue(event.getTagList().get(i).getValue().equals(eventPerm.getTagList().get(i).getValue())
                        || RollUpBitMask.ROLL_UP_TAG_VALUE.equals(eventPerm.getTagList().get(i).getValue()));
            }
        }
    }

    @Test
    public void testGenerateTagRollUpsTwoTagsCustomRollUps() {
        final StatisticEvent event = buildEvent(buildTagList());

        final StatisticStoreEntity statisticsDataSource = buildStatisticDataSource(StatisticRollUpType.CUSTOM);

        final RolledUpStatisticEvent rolledUpStatisticEvent = AbstractStatistics.generateTagRollUps(event,
                statisticsDataSource);

        assertEquals(3, rolledUpStatisticEvent.getPermutationCount());

        final List<TimeAgnosticStatisticEvent> timeAgnosticStatisticEvents = new ArrayList<TimeAgnosticStatisticEvent>();

        // define all the tag/value perms we expect to get back
        final List<List<StatisticTag>> expectedTagPerms = new ArrayList<List<StatisticTag>>();

        // nothing rolled up
        expectedTagPerms
                .add(Arrays.asList(new StatisticTag(TAG1_NAME, TAG1_VALUE), new StatisticTag(TAG2_NAME, TAG2_VALUE)));

        // tag 2 rolled up
        expectedTagPerms.add(
                Arrays.asList(new StatisticTag(TAG1_NAME, TAG1_VALUE), new StatisticTag(TAG2_NAME, ROLLED_UP_VALUE)));

        // tags 1 and 2 rolled up
        expectedTagPerms.add(Arrays.asList(new StatisticTag(TAG1_NAME, ROLLED_UP_VALUE),
                new StatisticTag(TAG2_NAME, ROLLED_UP_VALUE)));

        System.out.println("-------------------------------------------------");
        for (final List<StatisticTag> expectedTagList : expectedTagPerms) {
            System.out.println(expectedTagList);
        }
        System.out.println("-------------------------------------------------");

        for (final TimeAgnosticStatisticEvent eventPerm : rolledUpStatisticEvent) {
            // make sure we don't already have one like this.
            assertFalse(timeAgnosticStatisticEvents.contains(eventPerm));

            assertEquals(event.getName(), eventPerm.getName());
            assertEquals(event.getType(), eventPerm.getType());
            assertEquals(event.getCount(), eventPerm.getCount());
            assertEquals(event.getTagList().size(), eventPerm.getTagList().size());

            System.out.println(eventPerm.getTagList());

            assertTrue(expectedTagPerms.contains(eventPerm.getTagList()));

            for (int i = 0; i < event.getTagList().size(); i++) {
                assertEquals(event.getTagList().get(i).getTag(), eventPerm.getTagList().get(i).getTag());
                Assert.assertTrue(event.getTagList().get(i).getValue().equals(eventPerm.getTagList().get(i).getValue())
                        || RollUpBitMask.ROLL_UP_TAG_VALUE.equals(eventPerm.getTagList().get(i).getValue()));
            }
        }
    }

    @Test
    public void testGenerateTagRollUpsNoTags() {
        final StatisticEvent event = buildEvent(new ArrayList<StatisticTag>());

        final StatisticStoreEntity statisticsDataSource = buildStatisticDataSource();

        final RolledUpStatisticEvent rolledUpStatisticEvent = AbstractStatistics.generateTagRollUps(event,
                statisticsDataSource);

        assertEquals(1, rolledUpStatisticEvent.getPermutationCount());

        for (final TimeAgnosticStatisticEvent eventPerm : rolledUpStatisticEvent) {
            assertEquals(event.getName(), eventPerm.getName());
            assertEquals(event.getType(), eventPerm.getType());
            assertEquals(event.getCount(), eventPerm.getCount());
            assertEquals(event.getTagList().size(), eventPerm.getTagList().size());

            for (int i = 0; i < event.getTagList().size(); i++) {
                assertEquals(event.getTagList().get(i).getTag(), eventPerm.getTagList().get(i).getTag());
                Assert.assertTrue(event.getTagList().get(i).getValue().equals(eventPerm.getTagList().get(i).getValue())
                        || RollUpBitMask.ROLL_UP_TAG_VALUE.equals(eventPerm.getTagList().get(i).getValue()));
            }
        }

    }

    @Test
    public void testGenerateTagRollUpsNotEnabled() {
        final StatisticEvent event = buildEvent(buildTagList());

        final StatisticStoreEntity statisticsDataSource = buildStatisticDataSource(StatisticRollUpType.NONE);

        final RolledUpStatisticEvent rolledUpStatisticEvent = AbstractStatistics.generateTagRollUps(event,
                statisticsDataSource);

        assertEquals(1, rolledUpStatisticEvent.getPermutationCount());

        for (final TimeAgnosticStatisticEvent eventPerm : rolledUpStatisticEvent) {
            assertEquals(event.getName(), eventPerm.getName());
            assertEquals(event.getType(), eventPerm.getType());
            assertEquals(event.getCount(), eventPerm.getCount());
            assertEquals(event.getTagList().size(), eventPerm.getTagList().size());

            for (int i = 0; i < event.getTagList().size(); i++) {
                assertEquals(event.getTagList().get(i).getTag(), eventPerm.getTagList().get(i).getTag());
                Assert.assertTrue(event.getTagList().get(i).getValue().equals(eventPerm.getTagList().get(i).getValue())
                        || RollUpBitMask.ROLL_UP_TAG_VALUE.equals(eventPerm.getTagList().get(i).getValue()));
            }
        }
    }

    @Test
    public void testIsDataStoreEnabledSuccess() {
        runIsDataSourceEnabled("sql,hbase", "hbase", true);
    }

    @Test
    public void testIsDataStoreEnabledSuccess2() {
        runIsDataSourceEnabled("sql,hbase,someOther", "hbase", true);
    }

    @Test
    public void testIsDataStoreEnabledFailed() {
        runIsDataSourceEnabled("sql", "hbase", false);
    }

    private void runIsDataSourceEnabled(final String propertyValue, final String engineName,
            final boolean expectedResult) {
        final MockStroomPropertyService propertyService = new MockStroomPropertyService();

        propertyService.setProperty(CommonStatisticConstants.STROOM_STATISTIC_ENGINES_PROPERTY_NAME, propertyValue);

        final boolean result = AbstractStatistics.isDataStoreEnabled(engineName, propertyService);

        Assert.assertEquals(expectedResult, result);

    }

    private StatisticEvent buildEvent(final List<StatisticTag> tagList) {
        return new StatisticEvent(EVENT_TIME, EVENT_NAME, tagList, EVENT_COUNT);
    }

    private List<StatisticTag> buildTagList() {
        final List<StatisticTag> tagList = new ArrayList<StatisticTag>();

        tagList.add(new StatisticTag(TAG1_NAME, TAG1_VALUE));
        tagList.add(new StatisticTag(TAG2_NAME, TAG2_VALUE));

        return tagList;
    }

    private StatisticStoreEntity buildStatisticDataSource() {
        return buildStatisticDataSource(StatisticRollUpType.ALL);
    }

    private StatisticStoreEntity buildStatisticDataSource(final StatisticRollUpType statisticRollUpType) {
        final StatisticStoreEntity statisticsDataSource = new StatisticStoreEntity();

        final StatisticsDataSourceData statisticsDataSourceData = new StatisticsDataSourceData();

        final List<StatisticField> fields = new ArrayList<StatisticField>();

        fields.add(new StatisticField(TAG1_NAME));
        fields.add(new StatisticField(TAG2_NAME));

        statisticsDataSourceData.setStatisticFields(fields);

        // add the custom rollup masks, which only come into play if the type is
        // CUSTOM
        statisticsDataSourceData.addCustomRollUpMask(new CustomRollUpMask(new ArrayList<Integer>())); // no
                                                                                                        // tags
        statisticsDataSourceData.addCustomRollUpMask(new CustomRollUpMask(Arrays.asList(0, 1))); // tags
                                                                                                    // 1&2
        statisticsDataSourceData.addCustomRollUpMask(new CustomRollUpMask(Arrays.asList(1))); // tag
                                                                                                // 2

        statisticsDataSource.setStatisticDataSourceDataObject(statisticsDataSourceData);
        statisticsDataSource.setRollUpType(statisticRollUpType);

        return statisticsDataSource;
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testBuildCriteria_noDate() throws Exception {
        final ExpressionBuilder rootOperator = new ExpressionBuilder(Op.AND);

        final Query query = new Query(null, rootOperator.build());

        final StatisticStoreEntity dataSource = new StatisticStoreEntity();
        dataSource.setName("MyDataSource");

        AbstractStatistics.buildCriteria(query, dataSource);

    }

    @Test(expected = UnsupportedOperationException.class)
    public void testBuildCriteria_invalidDateCondition() throws Exception {
        final ExpressionBuilder rootOperator = new ExpressionBuilder(Op.AND);

        final String dateTerm = "2000-01-01T00:00:00.000Z,2010-01-01T00:00:00.000Z";

        rootOperator.addTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME,
                Condition.IN_DICTIONARY, dateTerm);

        final Query query = new Query(null, rootOperator.build());

        final StatisticStoreEntity dataSource = new StatisticStoreEntity();
        dataSource.setName("MyDataSource");

        AbstractStatistics.buildCriteria(query, dataSource);

    }

    @Test
    public void testBuildCriteria_validDateTerm() throws Exception {
        final ExpressionBuilder rootOperator = new ExpressionBuilder(Op.AND);

        final String fromDateStr = "2000-01-01T00:00:00.000Z";
        final long fromDate = DateUtil.parseNormalDateTimeString(fromDateStr);
        final String toDateStr = "2010-01-01T00:00:00.000Z";
        final long toDate = DateUtil.parseNormalDateTimeString(toDateStr);

        final String dateTerm = fromDateStr + "," + toDateStr;

        rootOperator.addTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, Condition.BETWEEN, dateTerm);

        final Query query = new Query(null, rootOperator.build());

        final StatisticStoreEntity dataSource = new StatisticStoreEntity();
        dataSource.setName("MyDataSource");

        final FindEventCriteria criteria = AbstractStatistics.buildCriteria(query, dataSource);

        Assert.assertNotNull(criteria);
        Assert.assertEquals(fromDate, criteria.getPeriod().getFrom().longValue());
        Assert.assertEquals(toDate + 1, criteria.getPeriod().getTo().longValue());

        // only a date term so the filter tree has noting in it as the date is
        // handled outside of the tree
        Assert.assertEquals(FilterTermsTree.emptyTree(), criteria.getFilterTermsTree());
    }

    @Test(expected = RuntimeException.class)
    public void testBuildCriteria_invalidDateTermOnlyOneDate() throws Exception {
        final ExpressionBuilder rootOperator = new ExpressionBuilder(Op.AND);

        final String fromDateStr = "2000-01-01T00:00:00.000Z";
        final long fromDate = DateUtil.parseNormalDateTimeString(fromDateStr);

        final String dateTerm = fromDateStr;

        rootOperator.addTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, Condition.BETWEEN, dateTerm);

        final Query query = new Query(null, rootOperator.build());

        final StatisticStoreEntity dataSource = new StatisticStoreEntity();
        dataSource.setName("MyDataSource");

        AbstractStatistics.buildCriteria(query, dataSource);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBuildCriteria_validDateTermOtherTermMissingFieldName() throws Exception {
        final ExpressionBuilder rootOperator = new ExpressionBuilder(Op.AND);

        final String fromDateStr = "2000-01-01T00:00:00.000Z";
        final long fromDate = DateUtil.parseNormalDateTimeString(fromDateStr);
        final String toDateStr = "2010-01-01T00:00:00.000Z";
        final long toDate = DateUtil.parseNormalDateTimeString(toDateStr);

        final String dateTerm = fromDateStr + "," + toDateStr;

        rootOperator.addTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, Condition.BETWEEN, dateTerm);
        rootOperator.addTerm(null, Condition.EQUALS, "xxx");

        final Query query = new Query(null, rootOperator.build());

        final StatisticStoreEntity dataSource = new StatisticStoreEntity();
        dataSource.setName("MyDataSource");

        AbstractStatistics.buildCriteria(query, dataSource);
    }

    @Test
    public void testBuildCriteria_validDateTermOtherTermMissingFieldValue() throws Exception {
        final ExpressionBuilder rootOperator = new ExpressionBuilder(Op.AND);

        final String fromDateStr = "2000-01-01T00:00:00.000Z";
        final long fromDate = DateUtil.parseNormalDateTimeString(fromDateStr);
        final String toDateStr = "2010-01-01T00:00:00.000Z";
        final long toDate = DateUtil.parseNormalDateTimeString(toDateStr);

        final String dateTerm = fromDateStr + "," + toDateStr;

        rootOperator.addTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, Condition.BETWEEN, dateTerm);
        rootOperator.addTerm("MyField", Condition.EQUALS, "");

        final Query query = new Query(null, rootOperator.build());

        final StatisticStoreEntity dataSource = new StatisticStoreEntity();
        dataSource.setName("MyDataSource");

        final FindEventCriteria criteria = AbstractStatistics.buildCriteria(query, dataSource);

        Assert.assertNotNull(criteria);
        Assert.assertEquals("[MyField=]", criteria.getFilterTermsTree().toString());
    }

    @Test
    public void testBuildCriteria_validDateTermAndOtherTerm() throws Exception {
        final ExpressionBuilder rootOperator = new ExpressionBuilder(Op.AND);

        final String fromDateStr = "2000-01-01T00:00:00.000Z";
        final long fromDate = DateUtil.parseNormalDateTimeString(fromDateStr);
        final String toDateStr = "2010-01-01T00:00:00.000Z";
        final long toDate = DateUtil.parseNormalDateTimeString(toDateStr);

        final String dateTerm = fromDateStr + "," + toDateStr;

        rootOperator.addTerm(StatisticStoreEntityService.FIELD_NAME_DATE_TIME, Condition.BETWEEN, dateTerm);

        rootOperator.addTerm("MyField", Condition.EQUALS, "xxx");

        final Query query = new Query(null, rootOperator.build());

        final StatisticStoreEntity dataSource = new StatisticStoreEntity();
        dataSource.setName("MyDataSource");

        final FindEventCriteria criteria = AbstractStatistics.buildCriteria(query, dataSource);

        Assert.assertNotNull(criteria);
        Assert.assertEquals(fromDate, criteria.getPeriod().getFrom().longValue());
        Assert.assertEquals(toDate + 1, criteria.getPeriod().getTo().longValue());

        // only a date term so the filter tree has noting in it as the date is
        // handled outside of the tree
        Assert.assertEquals("[MyField=xxx]", criteria.getFilterTermsTree().toString());
    }
}
