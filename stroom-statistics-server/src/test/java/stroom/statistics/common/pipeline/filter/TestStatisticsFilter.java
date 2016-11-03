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

package stroom.statistics.common.pipeline.filter;

import stroom.entity.server.MockDocumentEntityService;
import stroom.pipeline.server.LocationFactoryProxy;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.FatalErrorReceiver;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.util.ProcessorUtil;
import stroom.statistics.common.FindStatisticsEntityCriteria;
import stroom.statistics.common.StatisticEvent;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.common.Statistics;
import stroom.statistics.common.StatisticsFactory;
import stroom.statistics.shared.StatisticField;
import stroom.statistics.shared.StatisticStore;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.statistics.shared.StatisticsDataSourceData;
import stroom.util.date.DateUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Unit test class for <code>XMLTransformer</code>.
 */
@RunWith(StroomJUnit4ClassRunner.class)
public class TestStatisticsFilter implements Statistics {
    private static final String INPUT_DIR = "TestStatisticsFilter/";
    private static final String ENGINE_NAME = "SQL";
    private static final String STAT_NAME = "myStatName";
    private static final double JUNIT_DOUBLE_TOLLERANCE = 0.001;

    private final ArrayList<StatisticEvent> testEvents = new ArrayList<>();

    @Override
    public boolean putEvents(final List<StatisticEvent> statisticEvents) {
        testEvents.addAll(statisticEvents);
        return true;
    }

    @Override
    public boolean putEvents(final List<StatisticEvent> statisticEvents, final StatisticStore statisticsDataSource) {
        testEvents.addAll(statisticEvents);
        return true;
    }

    @Override
    public boolean putEvent(final StatisticEvent statisticEvent) {
        testEvents.add(statisticEvent);
        return true;
    }

    @Override
    public boolean putEvent(final StatisticEvent statisticEvent, final StatisticStore statisticsDataSource) {
        testEvents.add(statisticEvent);
        return true;
    }

    @Override
    public String getEngineName() {
        return "TEST";
    }

    @Before
    public void beforeTest() {
        testEvents.clear();
    }

    @Test
    public void test2GoodCountStats() throws Exception {
        final String inputPath = INPUT_DIR + "input01_2goodCountEvents.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final MockStatisticsDataSourceService statisticsDataSourceService = new MockStatisticsDataSourceService();
        StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(null, STAT_NAME);
        statisticsDataSource.setEngineName(ENGINE_NAME);
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setPrecision(1000L);
        statisticsDataSource.setEnabled(true);
        statisticsDataSource = statisticsDataSourceService.save(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final MockFactory mockFactory = new MockFactory(this);

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), mockFactory, statisticsDataSourceService);

        statisticsFilter.setStatisticsDataSource(statisticsDataSource);

        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());

        Assert.assertEquals(statisticsDataSource.getEngineName(), mockFactory.getEngine());
        Assert.assertEquals("Expecting 2 events", 2, testEvents.size());

        Assert.assertEquals(DateUtil.parseNormalDateTimeString("2000-01-01T00:00:00.000Z"),
                testEvents.get(0).getTimeMs());
        Assert.assertEquals(STAT_NAME, testEvents.get(0).getName());
        Assert.assertEquals(2, testEvents.get(0).getTagList().size());
        Assert.assertEquals("tag1name", testEvents.get(0).getTagList().get(0).getTag());
        Assert.assertEquals("1tag1value", testEvents.get(0).getTagList().get(0).getValue());
        Assert.assertEquals("tag2name", testEvents.get(0).getTagList().get(1).getTag());
        Assert.assertEquals("1tag2value", testEvents.get(0).getTagList().get(1).getValue());
        Assert.assertEquals(1L, testEvents.get(0).getCount().longValue());
        Assert.assertNull(testEvents.get(0).getValue());

        Assert.assertEquals(DateUtil.parseNormalDateTimeString("2000-01-02T00:00:00.000Z"),
                testEvents.get(1).getTimeMs());
        Assert.assertEquals(STAT_NAME, testEvents.get(1).getName());
        Assert.assertEquals(2, testEvents.get(1).getTagList().size());
        Assert.assertEquals("tag1name", testEvents.get(1).getTagList().get(0).getTag());
        Assert.assertEquals("2tag1value", testEvents.get(1).getTagList().get(0).getValue());
        Assert.assertEquals("tag2name", testEvents.get(1).getTagList().get(1).getTag());
        Assert.assertEquals("2tag2value", testEvents.get(1).getTagList().get(1).getValue());
        Assert.assertEquals(1L, testEvents.get(1).getCount().longValue());
        Assert.assertNull(testEvents.get(1).getValue());
        // Assert.assertNull(testEvents.get(1).getCount());
        // Assert.assertEquals(1L, testEvents.get(1).getValue().longValue());

        // <statistic time="2000-01-03T00:00:00.000Z" name="3oldstyle"
        // increment="1" precision="3600"/>
        // Assert.assertEquals(DateUtil.parseNormalDateTimeString("2000-01-03T00:00:00.000Z"),
        // testEvents.get(2)
        // .getTimeMs());
        // Assert.assertEquals("3oldstyle", testEvents.get(2).getName());
        // Assert.assertEquals(3600L, testEvents.get(2).getPrecisionMs());
        // Assert.assertEquals(0, testEvents.get(2).getTagList().size());
        // Assert.assertEquals(2L, testEvents.get(2).getCount().longValue());
        // Assert.assertNull(testEvents.get(2).getValue());

        // <statistic time="2000-01-04T00:00:00.000Z" name="3oldstyle"
        // precision="14400000"/>
        // Assert.assertEquals(DateUtil.parseNormalDateTimeString("2000-01-04T00:00:00.000Z"),
        // testEvents.get(3)
        // .getTimeMs());
        // Assert.assertEquals("4oldstyle", testEvents.get(3).getName());
        // Assert.assertEquals(14400000L, testEvents.get(3).getPrecisionMs());
        // Assert.assertEquals(0, testEvents.get(3).getTagList().size());
        // Assert.assertEquals(1L, testEvents.get(3).getCount().longValue());
        // Assert.assertNull(testEvents.get(3).getValue());

    }

    @Test
    public void test2GoodValueStats() throws Exception {
        final String inputPath = INPUT_DIR + "input02_2goodValueEvents.xml";
        final long precision = 60 * 1000L;

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final MockStatisticsDataSourceService statisticsDataSourceService = new MockStatisticsDataSourceService();
        StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(null, STAT_NAME);
        statisticsDataSource.setEngineName(ENGINE_NAME);
        statisticsDataSource.setStatisticType(StatisticType.VALUE);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setPrecision(precision);
        statisticsDataSource.setEnabled(true);
        statisticsDataSource = statisticsDataSourceService.save(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final MockFactory mockFactory = new MockFactory(this);

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), mockFactory, statisticsDataSourceService);

        statisticsFilter.setStatisticsDataSource(statisticsDataSource);

        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());

        Assert.assertEquals(statisticsDataSource.getEngineName(), mockFactory.getEngine());
        Assert.assertEquals("Expecting 2 events", 2, testEvents.size());

        Assert.assertEquals(DateUtil.parseNormalDateTimeString("2000-01-01T00:00:00.000Z"),
                testEvents.get(0).getTimeMs());
        Assert.assertEquals(STAT_NAME, testEvents.get(0).getName());
        Assert.assertEquals(2, testEvents.get(0).getTagList().size());
        Assert.assertEquals("tag1name", testEvents.get(0).getTagList().get(0).getTag());
        Assert.assertEquals("1tag1value", testEvents.get(0).getTagList().get(0).getValue());
        Assert.assertEquals("tag2name", testEvents.get(0).getTagList().get(1).getTag());
        Assert.assertEquals("1tag2value", testEvents.get(0).getTagList().get(1).getValue());
        Assert.assertEquals(1.5, testEvents.get(0).getValue().doubleValue(), JUNIT_DOUBLE_TOLLERANCE);
        Assert.assertNull(testEvents.get(0).getCount());

        Assert.assertEquals(DateUtil.parseNormalDateTimeString("2000-01-02T00:00:00.000Z"),
                testEvents.get(1).getTimeMs());
        Assert.assertEquals(STAT_NAME, testEvents.get(1).getName());
        Assert.assertEquals(2, testEvents.get(1).getTagList().size());
        Assert.assertEquals("tag1name", testEvents.get(1).getTagList().get(0).getTag());
        Assert.assertEquals("2tag1value", testEvents.get(1).getTagList().get(0).getValue());
        Assert.assertEquals("tag2name", testEvents.get(1).getTagList().get(1).getTag());
        Assert.assertEquals("2tag2value", testEvents.get(1).getTagList().get(1).getValue());
        Assert.assertEquals(3.9, testEvents.get(1).getValue().doubleValue(), JUNIT_DOUBLE_TOLLERANCE);
        Assert.assertNull(testEvents.get(1).getCount());

    }

    @Test(expected = RuntimeException.class)
    public void testBadStatType() throws Exception {
        final String inputPath = INPUT_DIR + "input03_badType.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final MockStatisticsDataSourceService statisticsDataSourceService = new MockStatisticsDataSourceService();
        StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(null, STAT_NAME);
        statisticsDataSource.setEngineName(ENGINE_NAME);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(true);
        statisticsDataSource = statisticsDataSourceService.save(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final MockFactory mockFactory = new MockFactory(this);

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), mockFactory, statisticsDataSourceService);

        statisticsFilter.setStatisticsDataSource(statisticsDataSource);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());
    }

    @Test(expected = RuntimeException.class)
    public void testInvalidTag() throws Exception {
        final String inputPath = INPUT_DIR + "input03_badType.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final MockStatisticsDataSourceService statisticsDataSourceService = new MockStatisticsDataSourceService();
        StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(null, STAT_NAME);
        statisticsDataSource.setEngineName(ENGINE_NAME);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.VALUE);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(true);
        statisticsDataSource = statisticsDataSourceService.save(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final MockFactory mockFactory = new MockFactory(this);

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), mockFactory, statisticsDataSourceService);

        statisticsFilter.setStatisticsDataSource(statisticsDataSource);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());
    }

    @Test
    public void testNoCountElement() throws Exception {
        final String inputPath = INPUT_DIR + "input05_noCountOrValue.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final MockStatisticsDataSourceService statisticsDataSourceService = new MockStatisticsDataSourceService();
        StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(null, STAT_NAME);
        statisticsDataSource.setEngineName(ENGINE_NAME);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(true);
        statisticsDataSource = statisticsDataSourceService.save(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final MockFactory mockFactory = new MockFactory(this);

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), mockFactory, statisticsDataSourceService);

        statisticsFilter.setStatisticsDataSource(statisticsDataSource);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());

        Assert.assertEquals("Expecting 1 event", 1, testEvents.size());

        Assert.assertEquals(1L, testEvents.get(0).getCount().longValue());
    }

    @Test(expected = ProcessException.class)
    public void testNoValueElement() throws Exception {
        final String inputPath = INPUT_DIR + "input05_noCountOrValue.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final MockStatisticsDataSourceService statisticsDataSourceService = new MockStatisticsDataSourceService();
        StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(null, STAT_NAME);
        statisticsDataSource.setEngineName(ENGINE_NAME);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.VALUE);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(true);
        statisticsDataSource = statisticsDataSourceService.save(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final MockFactory mockFactory = new MockFactory(this);

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), mockFactory, statisticsDataSourceService);

        statisticsFilter.setStatisticsDataSource(statisticsDataSource);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());
    }

    @Test(expected = ProcessException.class)
    public void testDisabledStatDataSource() throws Exception {
        final String inputPath = INPUT_DIR + "input01_2goodCountEvents.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final MockStatisticsDataSourceService statisticsDataSourceService = new MockStatisticsDataSourceService();
        StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(null, STAT_NAME);
        statisticsDataSource.setEngineName(ENGINE_NAME);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(false);
        statisticsDataSource = statisticsDataSourceService.save(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final MockFactory mockFactory = new MockFactory(this);

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), mockFactory, statisticsDataSourceService);

        statisticsFilter.setStatisticsDataSource(statisticsDataSource);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());
    }

    @Test(expected = ProcessException.class)
    public void testInvalidElement() throws Exception {
        final String inputPath = INPUT_DIR + "input06_invalidElement.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final MockStatisticsDataSourceService statisticsDataSourceService = new MockStatisticsDataSourceService();
        StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(null, STAT_NAME);
        statisticsDataSource.setEngineName(ENGINE_NAME);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(true);
        statisticsDataSource = statisticsDataSourceService.save(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final MockFactory mockFactory = new MockFactory(this);

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), mockFactory, statisticsDataSourceService);

        statisticsFilter.setStatisticsDataSource(statisticsDataSource);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());
    }

    @Test
    public void testEmptyCountElement() throws Exception {
        final String inputPath = INPUT_DIR + "input07_emptyCountElement.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final MockStatisticsDataSourceService statisticsDataSourceService = new MockStatisticsDataSourceService();
        StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(null, STAT_NAME);
        statisticsDataSource.setEngineName(ENGINE_NAME);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(true);
        statisticsDataSource = statisticsDataSourceService.save(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final MockFactory mockFactory = new MockFactory(this);

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), mockFactory, statisticsDataSourceService);

        statisticsFilter.setStatisticsDataSource(statisticsDataSource);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());

        Assert.assertEquals("Expecting 1 event", 1, testEvents.size());

        Assert.assertEquals(1L, testEvents.get(0).getCount().longValue());
    }

    @Test
    public void testNoStatisticElements() {
        final String inputPath = INPUT_DIR + "input08_noStatisticElements.xml";

        final ByteArrayInputStream input = new ByteArrayInputStream(getString(inputPath).getBytes());

        final MockStatisticsDataSourceService statisticsDataSourceService = new MockStatisticsDataSourceService();
        StatisticStoreEntity statisticsDataSource = statisticsDataSourceService.create(null, STAT_NAME);
        statisticsDataSource.setEngineName(ENGINE_NAME);
        // xml has a value element so set this to count
        statisticsDataSource.setStatisticType(StatisticType.COUNT);
        statisticsDataSource.setStatisticDataSourceDataObject(new StatisticsDataSourceData(
                Arrays.asList(new StatisticField("tag1name"), new StatisticField("tag2name"))));
        statisticsDataSource.setEnabled(true);
        statisticsDataSource = statisticsDataSourceService.save(statisticsDataSource);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final MockFactory mockFactory = new MockFactory(this);

        final StatisticsFilter statisticsFilter = new StatisticsFilter(errorReceiverProxy,
                new LocationFactoryProxy(), mockFactory, statisticsDataSourceService);

        statisticsFilter.setStatisticsDataSource(statisticsDataSource);

        // will throw an error as the type in the xml doesn't match the type in
        // the SDS
        ProcessorUtil.processXml(input, errorReceiverProxy, statisticsFilter, new LocationFactoryProxy());

        Assert.assertEquals("Expecting 0 event", 0, testEvents.size());
    }

    private String getString(final String resourceName) {
        return StreamUtil.streamToString(StroomStatisticsServerTestFileUtil.getInputStream(resourceName));
    }

    @Override
    public List<String> getValuesByTag(final String tagName) {
        throw new UnsupportedOperationException("Not used in this test class");
    }

    @Override
    public List<String> getValuesByTagAndPartialValue(final String tagName, final String partialValue) {
        throw new UnsupportedOperationException("Not used in this test class");
    }

    // @Override
    // public StatisticDataSet searchStatisticsData(final Search search, final
    // StatisticStore dataSource) {
    // throw new UnsupportedOperationException("Not used in this test class");
    // }

    // @Override
    // public BaseResultList<StatisticsDataSource>
    // getDataSources(FindStatStoreCriteria criteria) {
    // throw new UnsupportedOperationException("Not used in this test class");
    // }

    @Override
    public void flushAllEvents() {
        throw new UnsupportedOperationException("Not used in this test class");

    }

    private static class MockStatisticsDataSourceService extends MockDocumentEntityService<StatisticStoreEntity, FindStatisticsEntityCriteria> implements StatisticStoreEntityService {
//        @Override
//        public BaseResultList<StatisticStoreEntity> find(final FindStatisticsEntityCriteria criteria)
//                throws RuntimeException {
//            throw new UnsupportedOperationException("Method not implmented");
//        }
//
//        @Override
//        public StatisticStoreEntity create(final StatisticStoreEntity entity) throws RuntimeException {
//            throw new UnsupportedOperationException("Method not implmented");
//        }
//
//        @Override
//        public StatisticStoreEntity load(final StatisticStoreEntity entity) throws RuntimeException {
//            return entity;
//        }
//
//        @Override
//        public StatisticStoreEntity load(final StatisticStoreEntity entity, final Set<String> fetchSet)
//                throws RuntimeException {
//            throw new UnsupportedOperationException("Method not implmented");
//        }
//
//        @Override
//        public StatisticStoreEntity loadById(final long id) throws RuntimeException {
//            throw new UnsupportedOperationException("Method not implmented");
//        }
//
//        @Override
//        public StatisticStoreEntity loadById(final long id, final Set<String> fetchSet) throws RuntimeException {
//            throw new UnsupportedOperationException("Method not implmented");
//        }
//
//        @Override
//        public StatisticStoreEntity save(final StatisticStoreEntity entity) throws RuntimeException {
//            throw new UnsupportedOperationException("Method not implmented");
//        }
//
//        @Override
//        public Boolean delete(final StatisticStoreEntity entity) throws RuntimeException {
//            throw new UnsupportedOperationException("Method not implmented");
//        }
//
//        @Override
//        public FindStatisticsEntityCriteria createCriteria() {
//            throw new UnsupportedOperationException("Method not implmented");
//        }

        // @Override
        // public StatisticsDataSource loadByUUID(final String uuid) {
        // throw new UnsupportedOperationException("Method not implmented");
        // }
        //
        // @Override
        // public StatisticsDataSource loadByUUID(final String uuid, final
        // Set<String> fetchSet) {
        // throw new UnsupportedOperationException("Method not implmented");
        // }
        @Override
        public Class<StatisticStoreEntity> getEntityClass() {
            return StatisticStoreEntity.class;
        }
    }

    private static class MockFactory implements StatisticsFactory {
        private final Statistics statisticEventStore;
        private String engine;

        public MockFactory(final Statistics statisticEventStore) {
            this.statisticEventStore = statisticEventStore;
        }

        public String getEngine() {
            return engine;
        }

        @Override
        public void initStatisticEventStoreBeanNames() {
            throw new RuntimeException("Method not implmented");

        }

        @Override
        public Statistics instance() {
            throw new RuntimeException("Method not implmented");
        }

        @Override
        public Statistics instance(final String engineName) {
            this.engine = engineName;
            return statisticEventStore;
        }

        @Override
        public Statistics instance(final List<String> engineNames) {
            throw new RuntimeException("Method not implmented");
        }

        @Override
        public Set<String> getAllEngineNames() {
            throw new RuntimeException("Method not implmented");
        }

    }

    // @Override
    // public IndexFields getSupportedFields(final DataSource
    // statisticsDataSource) {
    // throw new UnsupportedOperationException("Not used in this test class");
    // }
}
