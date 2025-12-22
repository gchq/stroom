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

package stroom.statistics.impl.sql.filter;

import stroom.docref.DocRef;
import stroom.docrefinfo.mock.MockDocRefInfoService;
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.memory.MemoryPersistence;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.util.ProcessorUtil;
import stroom.query.api.datasource.QueryField;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.statistics.impl.sql.StatisticEvent;
import stroom.statistics.impl.sql.Statistics;
import stroom.statistics.impl.sql.entity.StatisticStoreSerialiser;
import stroom.statistics.impl.sql.entity.StatisticStoreStore;
import stroom.statistics.impl.sql.entity.StatisticStoreStoreImpl;
import stroom.statistics.impl.sql.shared.StatisticField;
import stroom.statistics.impl.sql.shared.StatisticStore;
import stroom.statistics.impl.sql.shared.StatisticStoreDoc;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.statistics.impl.sql.shared.StatisticsDataSourceData;
import stroom.util.date.DateUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit test class for <code>XMLTransformer</code>.
 */
class TestStatisticsFilter implements Statistics {

    private static final String INPUT_DIR = "TestStatisticsFilter/";
    private static final String STAT_NAME = "myStatName";
    private static final double JUNIT_DOUBLE_TOLERANCE = 0.001;

    private final ArrayList<StatisticEvent> testEvents = new ArrayList<>();

    @Override
    public void putEvents(final List<StatisticEvent> statisticEvents) {
        testEvents.addAll(statisticEvents);
    }

    @Override
    public void putEvents(final List<StatisticEvent> statisticEvents, final StatisticStore statisticStore) {
        testEvents.addAll(statisticEvents);
    }

    @Override
    public void putEvent(final StatisticEvent statisticEvent) {
        testEvents.add(statisticEvent);
    }

    @Override
    public void putEvent(final StatisticEvent statisticEvent, final StatisticStore statisticsDataSource) {
        testEvents.add(statisticEvent);
    }

    @BeforeEach
    void beforeTest() {
        testEvents.clear();
    }

    @Test
    void test2GoodCountStats() {
        final String inputPath = INPUT_DIR + "input01_2goodCountEvents.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final StatisticStoreStore statisticStoreStore = getStore();
        final DocRef docRef = statisticStoreStore.createDocument(STAT_NAME);
        final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setConfig(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setPrecision(1000L);
        statisticsDataSource.setEnabled(true);
        statisticStoreStore.writeDocument(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), this, statisticStoreStore);

        statisticsFilter.setStatisticsDataSource(docRef);

        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());

        assertThat(testEvents.size())
                .as("Expecting 2 events")
                .isEqualTo(2);

        assertThat(testEvents.get(0).getTimeMs())
                .isEqualTo(DateUtil.parseNormalDateTimeString("2000-01-01T00:00:00.000Z"));
        assertThat(testEvents.get(0).getName())
                .isEqualTo(STAT_NAME);
        assertThat(testEvents.get(0).getTagList().size())
                .isEqualTo(2);
        assertThat(testEvents.get(0).getTagList().get(0).getTag())
                .isEqualTo("tag1name");
        assertThat(testEvents.get(0).getTagList().get(0).getValue())
                .isEqualTo("1tag1value");
        assertThat(testEvents.get(0).getTagList().get(1).getTag())
                .isEqualTo("tag2name");
        assertThat(testEvents.get(0).getTagList().get(1).getValue())
                .isEqualTo("1tag2value");
        assertThat(testEvents.get(0).getCount())
                .isEqualTo(1L);
        assertThatThrownBy(() -> testEvents.get(0)
                .getValue()).isInstanceOf(RuntimeException.class);

        assertThat(testEvents.get(1).getTimeMs())
                .isEqualTo(DateUtil.parseNormalDateTimeString("2000-01-02T00:00:00.000Z"));
        assertThat(testEvents.get(1).getName())
                .isEqualTo(STAT_NAME);
        assertThat(testEvents.get(1).getTagList().size())
                .isEqualTo(2);
        assertThat(testEvents.get(1).getTagList().get(0).getTag())
                .isEqualTo("tag1name");
        assertThat(testEvents.get(1).getTagList().get(0).getValue())
                .isEqualTo("2tag1value");
        assertThat(testEvents.get(1).getTagList().get(1).getTag())
                .isEqualTo("tag2name");
        assertThat(testEvents.get(1).getTagList().get(1).getValue())
                .isEqualTo("2tag2value");
        assertThat(testEvents.get(1).getCount())
                .isEqualTo(1L);
        assertThatThrownBy(() -> testEvents.get(1)
                .getValue()).isInstanceOf(RuntimeException.class);
        // assertThat(testEvents.get(1).getCount()).isNull();
        // assertThat(testEvents.get(1).getValue().longValue())
        // .isEqualTo(1L);

        // <statistic time="2000-01-03T00:00:00.000Z" name="3oldstyle"
        // increment="1" precision="3600"/>
        // assertThat(// testEvents.get(2)
        // .getTimeMs())
        // .isEqualTo(DateUtil.parseNormalDateTimeString("2000-01-03T00:00:00.000Z"));
        // assertThat(testEvents.get(2).getName())
        // .isEqualTo("3oldstyle");
        // assertThat(testEvents.get(2).getPrecisionMs())
        // .isEqualTo(3600L);
        // assertThat(testEvents.get(2).getTagList().size())
        // .isEqualTo(0);
        // assertThat(testEvents.get(2).getCount().longValue())
        // .isEqualTo(2L);
        // assertThat(testEvents.get(2).getValue()).isNull();

        // <statistic time="2000-01-04T00:00:00.000Z" name="3oldstyle"
        // precision="14400000"/>
        // assertThat(// testEvents.get(3)
        // .getTimeMs())
        // .isEqualTo(DateUtil.parseNormalDateTimeString("2000-01-04T00:00:00.000Z"));
        // assertThat(testEvents.get(3).getName())
        // .isEqualTo("4oldstyle");
        // assertThat(testEvents.get(3).getPrecisionMs())
        // .isEqualTo(14400000L);
        // assertThat(testEvents.get(3).getTagList().size())
        // .isEqualTo(0);
        // assertThat(testEvents.get(3).getCount().longValue())
        // .isEqualTo(1L);
        // assertThat(testEvents.get(3).getValue()).isNull();

    }


    @Test
    void test2GoodValueStats() {
        final String inputPath = INPUT_DIR + "input02_2goodValueEvents.xml";
        final long precision = 60 * 1000L;

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final StatisticStoreStore statisticStoreStore = getStore();
        final DocRef docRef = statisticStoreStore.createDocument(STAT_NAME);
        final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
        statisticsDataSource.setStatisticType(StatisticType.VALUE);
        statisticsDataSource.setConfig(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setPrecision(precision);
        statisticsDataSource.setEnabled(true);
        statisticStoreStore.writeDocument(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());


        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), this, statisticStoreStore);

        statisticsFilter.setStatisticsDataSource(docRef);

        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());

        assertThat(testEvents.size())
                .as("Expecting 2 events")
                .isEqualTo(2);

        assertThat(testEvents.get(0).getTimeMs())
                .isEqualTo(DateUtil.parseNormalDateTimeString("2000-01-01T00:00:00.000Z"));
        assertThat(testEvents.get(0).getName())
                .isEqualTo(STAT_NAME);
        assertThat(testEvents.get(0).getTagList().size())
                .isEqualTo(2);
        assertThat(testEvents.get(0).getTagList().get(0).getTag())
                .isEqualTo("tag1name");
        assertThat(testEvents.get(0).getTagList().get(0).getValue())
                .isEqualTo("1tag1value");
        assertThat(testEvents.get(0).getTagList().get(1).getTag())
                .isEqualTo("tag2name");
        assertThat(testEvents.get(0).getTagList().get(1).getValue())
                .isEqualTo("1tag2value");
        assertThat(testEvents.get(0).getValue()).isCloseTo(1.5, within(JUNIT_DOUBLE_TOLERANCE));
        assertThatThrownBy(() -> testEvents.get(0)
                .getCount()).isInstanceOf(RuntimeException.class);

        assertThat(testEvents.get(1).getTimeMs())
                .isEqualTo(DateUtil.parseNormalDateTimeString("2000-01-02T00:00:00.000Z"));
        assertThat(testEvents.get(1).getName())
                .isEqualTo(STAT_NAME);
        assertThat(testEvents.get(1).getTagList().size())
                .isEqualTo(2);
        assertThat(testEvents.get(1).getTagList().get(0).getTag())
                .isEqualTo("tag1name");
        assertThat(testEvents.get(1).getTagList().get(0).getValue())
                .isEqualTo("2tag1value");
        assertThat(testEvents.get(1).getTagList().get(1).getTag())
                .isEqualTo("tag2name");
        assertThat(testEvents.get(1).getTagList().get(1).getValue())
                .isEqualTo("2tag2value");
        assertThat(testEvents.get(1).getValue()).isCloseTo(3.9, within(JUNIT_DOUBLE_TOLERANCE));
        assertThatThrownBy(() -> testEvents.get(0)
                .getCount()).isInstanceOf(RuntimeException.class);

    }

    @Test
    void testBadStatType() {
        assertThatThrownBy(() -> {
            final String inputPath = INPUT_DIR + "input03_badType.xml";

            final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

            final StatisticStoreStore statisticStoreStore = getStore();
            final DocRef docRef = statisticStoreStore.createDocument(STAT_NAME);
            final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
            // xml has a value element so set this to count
            statisticsDataSource.setStatisticType(StatisticType.COUNT);
            statisticsDataSource.setConfig(new StatisticsDataSourceData(
                    Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
            statisticsDataSource.setEnabled(true);
            statisticStoreStore.writeDocument(statisticsDataSource);

            final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

            final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                    new LocationFactoryProxy(), this, statisticStoreStore);

            statisticsFilter.setStatisticsDataSource(docRef);

            // will throw an error as the type in the xml doesn't match the type in
            // the SDS
            ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());
        }).isInstanceOf(RuntimeException.class);
    }

    @Test
    void testNoCountElement() {
        final String inputPath = INPUT_DIR + "input05_noCountOrValue.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final StatisticStoreStore statisticStoreStore = getStore();
        final DocRef docRef = statisticStoreStore.createDocument(STAT_NAME);
        final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setConfig(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(true);
        statisticStoreStore.writeDocument(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), this, statisticStoreStore);

        statisticsFilter.setStatisticsDataSource(docRef);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());

        assertThat(testEvents.size())
                .as("Expecting 1 event")
                .isEqualTo(1);

        assertThat(testEvents.get(0).getCount())
                .isEqualTo(1L);
    }

    @Test
    void testNoValueElement() {
        assertThatThrownBy(() -> {
            final String inputPath = INPUT_DIR + "input05_noCountOrValue.xml";

            final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

            final StatisticStoreStore statisticStoreStore = getStore();
            final DocRef docRef = statisticStoreStore.createDocument(STAT_NAME);
            final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
            // xml has a value element so set this to count
            statisticsDataSource.setStatisticType(StatisticType.VALUE);
            statisticsDataSource.setConfig(new StatisticsDataSourceData(
                    Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
            statisticsDataSource.setEnabled(true);
            statisticStoreStore.writeDocument(statisticsDataSource);

            final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());


            final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                    new LocationFactoryProxy(), this, statisticStoreStore);

            statisticsFilter.setStatisticsDataSource(docRef);

            // will throw an error as the type in the xml doesn't match the type in
            // the SDS
            ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());
        }).isInstanceOf(ProcessException.class);
    }

    @Test
    void testDisabledStatDataSource() {
        assertThatThrownBy(() -> {
            final String inputPath = INPUT_DIR + "input01_2goodCountEvents.xml";

            final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

            final StatisticStoreStore statisticStoreStore = getStore();
            final DocRef docRef = statisticStoreStore.createDocument(STAT_NAME);
            final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
            // xml has a value element so set this to count
            statisticsDataSource.setStatisticType(StatisticType.COUNT);
            statisticsDataSource.setConfig(new StatisticsDataSourceData(
                    Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
            statisticsDataSource.setEnabled(false);
            statisticStoreStore.writeDocument(statisticsDataSource);

            final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

            final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                    new LocationFactoryProxy(), this, statisticStoreStore);

            statisticsFilter.setStatisticsDataSource(docRef);

            // will throw an error as the type in the xml doesn't match the type in
            // the SDS
            ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());
        }).isInstanceOf(ProcessException.class);
    }

    @Test
    void testInvalidElement() {
        assertThatThrownBy(() -> {
            final String inputPath = INPUT_DIR + "input06_invalidElement.xml";

            final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

            final StatisticStoreStore statisticStoreStore = getStore();
            final DocRef docRef = statisticStoreStore.createDocument(STAT_NAME);
            final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
            // xml has a value element so set this to count
            statisticsDataSource.setStatisticType(StatisticType.COUNT);
            statisticsDataSource.setConfig(new StatisticsDataSourceData(
                    Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
            statisticsDataSource.setEnabled(true);
            statisticStoreStore.writeDocument(statisticsDataSource);

            final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

            final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                    new LocationFactoryProxy(), this, statisticStoreStore);

            statisticsFilter.setStatisticsDataSource(docRef);

            // will throw an error as the type in the xml doesn't match the type in
            // the SDS
            ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());
        }).isInstanceOf(ProcessException.class);
    }

    @Test
    void testEmptyCountElement() {
        final String inputPath = INPUT_DIR + "input07_emptyCountElement.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final StatisticStoreStore statisticStoreStore = getStore();
        final DocRef docRef = statisticStoreStore.createDocument(STAT_NAME);
        final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setConfig(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(true);
        statisticStoreStore.writeDocument(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), this, statisticStoreStore);

        statisticsFilter.setStatisticsDataSource(docRef);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());

        assertThat(testEvents.size())
                .as("Expecting 1 event")
                .isEqualTo(1);

        assertThat(testEvents.get(0).getCount())
                .isEqualTo(1L);
    }

    @Test
    void testNoStatisticElements() {
        final String inputPath = INPUT_DIR + "input08_noStatisticElements.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final StatisticStoreStore statisticStoreStore = getStore();
        final DocRef docRef = statisticStoreStore.createDocument(STAT_NAME);
        final StatisticStoreDoc statisticsDataSource = statisticStoreStore.readDocument(docRef);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setConfig(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(true);
        statisticStoreStore.writeDocument(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), this, statisticStoreStore);

        statisticsFilter.setStatisticsDataSource(docRef);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());

        assertThat(testEvents.size())
                .as("Expecting 0 event")
                .isEqualTo(0);
    }

    private String getString(final String resourceName) {
        return StroomStatisticsServerTestFileUtil.getString(resourceName);
    }

    @Override
    public List<String> getValuesByTag(final String tagName) {
        throw new UnsupportedOperationException("Not used in this test class");
    }

    @Override
    public List<String> getValuesByTagAndPartialValue(final String tagName, final String partialValue) {
        throw new UnsupportedOperationException("Not used in this test class");
    }

    @Override
    public List<QueryField> getSupportedFields(final List<QueryField> indexFields) {
        throw new UnsupportedOperationException("Not used in this test class");
    }

    @Override
    public void flushAllEvents() {
        throw new UnsupportedOperationException("Not used in this test class");
    }

    private StatisticStoreStore getStore() {
        final Persistence persistence = new MemoryPersistence();
        final SecurityContext securityContext = new MockSecurityContext();

        return new StatisticStoreStoreImpl(
                new StoreFactoryImpl(
                        persistence,
                        null,
                        securityContext,
                        MockDocRefInfoService::new),
                new StatisticStoreSerialiser(new Serialiser2FactoryImpl()));
    }
}
