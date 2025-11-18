/*
 * Copyright 2017 Crown Copyright
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

package stroom.statistics.impl.sql;


import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.statistics.impl.sql.rollup.RollUpBitMask;
import stroom.statistics.impl.sql.rollup.RolledUpStatisticEvent;
import stroom.statistics.impl.sql.search.FilterTermsTree;
import stroom.statistics.impl.sql.search.FindEventCriteria;
import stroom.statistics.impl.sql.search.StatStoreCriteriaBuilder;
import stroom.statistics.impl.sql.shared.CustomRollUpMask;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.date.DateUtil;

import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSQLStatisticEventStore2 extends StroomUnitTest {

    private static final long EVENT_TIME = 1234L;
    private static final String EVENT_NAME = "MyStatistic";
    private static final long EVENT_COUNT = 1;

    private static final String TAG1_NAME = "Tag1";
    private static final String TAG1_VALUE = "Tag1Val";

    private static final String TAG2_NAME = "Tag2";
    private static final String TAG2_VALUE = "Tag2Val";

    private static final String ROLLED_UP_VALUE = "*";

    @Test
    void testGenerateTagRollUpsTwoTagsAllRollUps() {
        final StatisticEvent event = buildEvent(buildTagList());

        final StatisticStoreDoc statisticsDataSource = buildStatisticDataSource();

        final RolledUpStatisticEvent rolledUpStatisticEvent = SQLStatisticEventStore.generateTagRollUps(event,
                statisticsDataSource);

        assertThat(rolledUpStatisticEvent.getPermutationCount()).isEqualTo(4);

        final List<TimeAgnosticStatisticEvent> timeAgnosticStatisticEvents = new ArrayList<>();

        // define all the tag/value perms we expect to get back
        final List<List<StatisticTag>> expectedTagPerms = new ArrayList<>();
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
            assertThat(timeAgnosticStatisticEvents.contains(eventPerm)).isFalse();

            assertThat(eventPerm.getName()).isEqualTo(event.getName());
            assertThat(eventPerm.getType()).isEqualTo(event.getType());
            assertThat(eventPerm.getCount()).isEqualTo(event.getCount());
            assertThat(eventPerm.getTagList()).hasSize(event.getTagList().size());

            System.out.println(eventPerm.getTagList());

            assertThat(expectedTagPerms.contains(eventPerm.getTagList())).isTrue();

            for (int i = 0; i < event.getTagList().size(); i++) {
                assertThat(eventPerm.getTagList().get(i).getTag()).isEqualTo(event.getTagList().get(i).getTag());
                assertThat(event.getTagList().get(i).getValue().equals(eventPerm.getTagList().get(i).getValue())
                           || RollUpBitMask.ROLL_UP_TAG_VALUE
                                   .equals(eventPerm.getTagList().get(i).getValue())).isTrue();
            }
        }
    }

    @Test
    void testGenerateTagRollUpsTwoTagsCustomRollUps() {
        final StatisticEvent event = buildEvent(buildTagList());

        final StatisticStoreDoc statisticsDataSource = buildStatisticDataSource(StatisticRollUpType.CUSTOM);

        final RolledUpStatisticEvent rolledUpStatisticEvent = SQLStatisticEventStore.generateTagRollUps(event,
                statisticsDataSource);

        assertThat(rolledUpStatisticEvent.getPermutationCount()).isEqualTo(3);

        final List<TimeAgnosticStatisticEvent> timeAgnosticStatisticEvents = new ArrayList<>();

        // define all the tag/value perms we expect to get back
        final List<List<StatisticTag>> expectedTagPerms = new ArrayList<>();

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
            assertThat(timeAgnosticStatisticEvents.contains(eventPerm)).isFalse();

            assertThat(eventPerm.getName()).isEqualTo(event.getName());
            assertThat(eventPerm.getType()).isEqualTo(event.getType());
            assertThat(eventPerm.getCount()).isEqualTo(event.getCount());
            assertThat(eventPerm.getTagList()).hasSize(event.getTagList().size());

            System.out.println(eventPerm.getTagList());

            assertThat(expectedTagPerms.contains(eventPerm.getTagList())).isTrue();

            for (int i = 0; i < event.getTagList().size(); i++) {
                assertThat(eventPerm.getTagList().get(i).getTag()).isEqualTo(event.getTagList().get(i).getTag());
                assertThat(event.getTagList().get(i).getValue().equals(eventPerm.getTagList().get(i).getValue())
                           || RollUpBitMask.ROLL_UP_TAG_VALUE
                                   .equals(eventPerm.getTagList().get(i).getValue())).isTrue();
            }
        }
    }

    @Test
    void testGenerateTagRollUpsNoTags() {
        final StatisticEvent event = buildEvent(new ArrayList<>());

        final StatisticStoreDoc statisticsDataSource = buildStatisticDataSource();

        final RolledUpStatisticEvent rolledUpStatisticEvent = SQLStatisticEventStore.generateTagRollUps(event,
                statisticsDataSource);

        assertThat(rolledUpStatisticEvent.getPermutationCount()).isEqualTo(1);

        for (final TimeAgnosticStatisticEvent eventPerm : rolledUpStatisticEvent) {
            assertThat(eventPerm.getName()).isEqualTo(event.getName());
            assertThat(eventPerm.getType()).isEqualTo(event.getType());
            assertThat(eventPerm.getCount()).isEqualTo(event.getCount());
            assertThat(eventPerm.getTagList()).hasSize(event.getTagList().size());

            for (int i = 0; i < event.getTagList().size(); i++) {
                assertThat(eventPerm.getTagList().get(i).getTag()).isEqualTo(event.getTagList().get(i).getTag());
                assertThat(event.getTagList().get(i).getValue().equals(eventPerm.getTagList().get(i).getValue())
                           || RollUpBitMask.ROLL_UP_TAG_VALUE
                                   .equals(eventPerm.getTagList().get(i).getValue())).isTrue();
            }
        }

    }

    @Test
    void testGenerateTagRollUpsNotEnabled() {
        final StatisticEvent event = buildEvent(buildTagList());

        final StatisticStoreDoc statisticsDataSource = buildStatisticDataSource(StatisticRollUpType.NONE);

        final RolledUpStatisticEvent rolledUpStatisticEvent = SQLStatisticEventStore.generateTagRollUps(event,
                statisticsDataSource);

        assertThat(rolledUpStatisticEvent.getPermutationCount()).isEqualTo(1);

        for (final TimeAgnosticStatisticEvent eventPerm : rolledUpStatisticEvent) {
            assertThat(eventPerm.getName()).isEqualTo(event.getName());
            assertThat(eventPerm.getType()).isEqualTo(event.getType());
            assertThat(eventPerm.getCount()).isEqualTo(event.getCount());
            assertThat(eventPerm.getTagList()).hasSize(event.getTagList().size());

            for (int i = 0; i < event.getTagList().size(); i++) {
                assertThat(eventPerm.getTagList().get(i).getTag()).isEqualTo(event.getTagList().get(i).getTag());
                assertThat(event.getTagList().get(i).getValue().equals(eventPerm.getTagList().get(i).getValue())
                           || RollUpBitMask.ROLL_UP_TAG_VALUE
                                   .equals(eventPerm.getTagList().get(i).getValue())).isTrue();
            }
        }
    }

    private StatisticEvent buildEvent(final List<StatisticTag> tagList) {
        return StatisticEvent.createCount(EVENT_TIME, EVENT_NAME, tagList, EVENT_COUNT);
    }

    private List<StatisticTag> buildTagList() {
        final List<StatisticTag> tagList = new ArrayList<>();

        tagList.add(new StatisticTag(TAG1_NAME, TAG1_VALUE));
        tagList.add(new StatisticTag(TAG2_NAME, TAG2_VALUE));

        return tagList;
    }

    private StatisticStoreDoc buildStatisticDataSource() {
        return buildStatisticDataSource(StatisticRollUpType.ALL);
    }

    private StatisticStoreDoc buildStatisticDataSource(final StatisticRollUpType statisticRollUpType) {
        final StatisticStoreDoc statisticsDataSource = StatisticStoreDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .build();

        final StatisticsDataSourceData statisticsDataSourceData = new StatisticsDataSourceData();

        final List<StatisticField> fields = new ArrayList<>();

        fields.add(new StatisticField(TAG1_NAME));
        fields.add(new StatisticField(TAG2_NAME));

        statisticsDataSourceData.setFields(fields);

        // add the custom rollup masks, which only come into play if the type is
        // CUSTOM
        statisticsDataSourceData.addCustomRollUpMask(new CustomRollUpMask(new ArrayList<>())); // no
        // tags
        statisticsDataSourceData.addCustomRollUpMask(new CustomRollUpMask(Arrays.asList(0, 1))); // tags
        // 1&2
        statisticsDataSourceData.addCustomRollUpMask(new CustomRollUpMask(Arrays.asList(1))); // tag
        // 2

        statisticsDataSource.setConfig(statisticsDataSourceData);
        statisticsDataSource.setRollUpType(statisticRollUpType);

        return statisticsDataSource;
    }

    @Test
    void testBuildCriteria_invalidDateCondition() {
        assertThatThrownBy(() -> {
            final ExpressionOperator.Builder rootOperator = ExpressionOperator.builder();

            final String dateTerm = "2000-01-01T00:00:00.000Z,2010-01-01T00:00:00.000Z";

            rootOperator.addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME,
                    Condition.IN_DICTIONARY, dateTerm);

            final StatisticStoreDoc dataSource = getDoc();

            StatStoreCriteriaBuilder.buildCriteria(dataSource, rootOperator.build(), null);
        }).isInstanceOf(BadRequestException.class);
    }

    @Test
    void testBuildCriteria_validDateTerm() {
        final ExpressionOperator.Builder rootOperator = ExpressionOperator.builder();

        final String fromDateStr = "2000-01-01T00:00:00.000Z";
        final long fromDate = DateUtil.parseNormalDateTimeString(fromDateStr);
        final String toDateStr = "2010-01-01T00:00:00.000Z";
        final long toDate = DateUtil.parseNormalDateTimeString(toDateStr);

        final String dateTerm = fromDateStr + "," + toDateStr;

        rootOperator.addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME, Condition.BETWEEN, dateTerm);

        final StatisticStoreDoc dataSource = getDoc();

        final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(dataSource,
                rootOperator.build(),
                DateTimeSettings.builder().build());

        assertThat(criteria).isNotNull();
        assertThat(criteria.getPeriod().getFrom().longValue()).isEqualTo(fromDate);
        assertThat(criteria.getPeriod().getTo().longValue()).isEqualTo(toDate + 1);

        // only a date term so the filter tree has noting in it as the date is
        // handled outside of the tree
        assertThat(criteria.getFilterTermsTree()).isEqualTo(FilterTermsTree.emptyTree());
    }

    @Test
    void testBuildCriteria_invalidDateTermOnlyOneDate() {
        assertThatThrownBy(() -> {
            final ExpressionOperator.Builder rootOperator = ExpressionOperator.builder();

            final String fromDateStr = "2000-01-01T00:00:00.000Z";
            final long fromDate = DateUtil.parseNormalDateTimeString(fromDateStr);

            final String dateTerm = fromDateStr;

            rootOperator.addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME, Condition.BETWEEN, dateTerm);

            final StatisticStoreDoc dataSource = getDoc();

            StatStoreCriteriaBuilder.buildCriteria(dataSource, rootOperator.build(), null);
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testBuildCriteria_validDateTermOtherTermMissingFieldName() {
        assertThatThrownBy(() -> {
            final ExpressionOperator.Builder rootOperator = ExpressionOperator.builder();

            final String fromDateStr = "2000-01-01T00:00:00.000Z";
            final long fromDate = DateUtil.parseNormalDateTimeString(fromDateStr);
            final String toDateStr = "2010-01-01T00:00:00.000Z";
            final long toDate = DateUtil.parseNormalDateTimeString(toDateStr);

            final String dateTerm = fromDateStr + "," + toDateStr;

            rootOperator.addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME, Condition.BETWEEN, dateTerm);
            rootOperator.addTextTerm(QueryField.createText(null), Condition.EQUALS, "xxx");

            final StatisticStoreDoc dataSource = getDoc();

            StatStoreCriteriaBuilder.buildCriteria(dataSource, rootOperator.build(), null);
        }).isInstanceOf(BadRequestException.class);
    }

    private StatisticStoreDoc getDoc() {
        return StatisticStoreDoc
                .builder()
                .uuid(UUID.randomUUID().toString())
                .name("MyDataSource")
                .build();
    }

    @Test
    void testBuildCriteria_validDateTermOtherTermMissingFieldValue() {
        final ExpressionOperator.Builder rootOperator = ExpressionOperator.builder();

        final String fromDateStr = "2000-01-01T00:00:00.000Z";
        final long fromDate = DateUtil.parseNormalDateTimeString(fromDateStr);
        final String toDateStr = "2010-01-01T00:00:00.000Z";
        final long toDate = DateUtil.parseNormalDateTimeString(toDateStr);

        final String dateTerm = fromDateStr + "," + toDateStr;

        rootOperator.addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME, Condition.BETWEEN, dateTerm);
        rootOperator.addTerm("MyField", Condition.EQUALS, "");

        final StatisticStoreDoc dataSource = getDoc();

        final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(dataSource,
                rootOperator.build(),
                DateTimeSettings.builder().build());

        assertThat(criteria).isNotNull();
        assertThat(criteria.getFilterTermsTree().toString()).isEqualTo("[]");
    }

    @Test
    void testBuildCriteria_validDateTermAndOtherTerm() {
        final ExpressionOperator.Builder rootOperator = ExpressionOperator.builder();

        final String fromDateStr = "2000-01-01T00:00:00.000Z";
        final long fromDate = DateUtil.parseNormalDateTimeString(fromDateStr);
        final String toDateStr = "2010-01-01T00:00:00.000Z";
        final long toDate = DateUtil.parseNormalDateTimeString(toDateStr);

        final String dateTerm = fromDateStr + "," + toDateStr;

        rootOperator.addTerm(StatisticStoreDoc.FIELD_NAME_DATE_TIME, Condition.BETWEEN, dateTerm);

        rootOperator.addTerm("MyField", Condition.EQUALS, "xxx");

        final StatisticStoreDoc dataSource = getDoc();

        final FindEventCriteria criteria = StatStoreCriteriaBuilder.buildCriteria(dataSource,
                rootOperator.build(),
                DateTimeSettings.builder().build());

        assertThat(criteria).isNotNull();
        assertThat(criteria.getPeriod().getFrom().longValue()).isEqualTo(fromDate);
        assertThat(criteria.getPeriod().getTo().longValue()).isEqualTo(toDate + 1);

        // only a date term so the filter tree has noting in it as the date is
        // handled outside of the tree
        assertThat(criteria.getFilterTermsTree().toString()).isEqualTo("[MyField=xxx]");
    }
}
