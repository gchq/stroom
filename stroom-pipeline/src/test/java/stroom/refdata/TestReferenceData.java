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

package stroom.refdata;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import stroom.cache.CacheManagerAutoCloseable;
import stroom.entity.shared.DocRefUtil;
import stroom.entity.shared.Range;
import stroom.feed.server.MockFeedService;
import stroom.feed.shared.Feed;
import stroom.pipeline.server.MockPipelineEntityService;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.FatalErrorReceiver;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.refdata.impl.MockReferenceDataLoader;
import stroom.streamstore.shared.StreamType;
import stroom.util.date.DateUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;
import stroom.xml.event.EventList;
import stroom.xml.event.EventListBuilder;
import stroom.xml.event.EventListBuilderFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestReferenceData extends StroomUnitTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestReferenceData.class);

    private final MockFeedService feedService = new MockFeedService();
    private final MockPipelineEntityService pipelineEntityService = new MockPipelineEntityService();

    @Test
    public void testSimple() {
        final Feed feed1 = feedService.create(null, "TEST_FEED_1");
        final Feed feed2 = feedService.create(null, "TEST_FEED_2");
        final PipelineEntity pipeline1 = pipelineEntityService.create(null, "TEST_PIPELINE_1");
        final PipelineEntity pipeline2 = pipelineEntityService.create(null, "TEST_PIPELINE_2");

        final List<PipelineReference> pipelineReferences = new ArrayList<>();
        pipelineReferences.add(new PipelineReference(DocRefUtil.create(pipeline1), DocRefUtil.create(feed1),
                StreamType.REFERENCE.getName()));
        pipelineReferences.add(new PipelineReference(DocRefUtil.create(pipeline2), DocRefUtil.create(feed2),
                StreamType.REFERENCE.getName()));

        final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
        streamSet.add(new EffectiveStream(1, DateUtil.parseNormalDateTimeString("2008-01-01T09:47:00.000Z")));
        streamSet.add(new EffectiveStream(2, DateUtil.parseNormalDateTimeString("2009-01-01T09:47:00.000Z")));
        streamSet.add(new EffectiveStream(3, DateUtil.parseNormalDateTimeString("2010-01-01T09:47:00.000Z")));
        try (CacheManagerAutoCloseable cacheManager = CacheManagerAutoCloseable.create()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager, null, null) {
                @Override
                TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                    return streamSet;
                }
            };
            final ReferenceDataLoader referenceDataLoader = effectiveFeed -> new MapStoreImpl();

            final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, referenceDataLoader, null);
            final ReferenceData referenceData = new ReferenceData();
            referenceData.setEffectiveStreamCache(effectiveStreamCache);
            referenceData.setMapStorePool(mapStoreCache);

            // Add multiple reference data items to prove that looping over maps
            // works.
            addData(referenceData, pipeline1, new String[]{"SID_TO_PF_1", "SID_TO_PF_2"});
            addData(referenceData, pipeline2, new String[]{"SID_TO_PF_3", "SID_TO_PF_4"});
            checkData(referenceData, pipelineReferences, "SID_TO_PF_1");
            checkData(referenceData, pipelineReferences, "SID_TO_PF_2");
            checkData(referenceData, pipelineReferences, "SID_TO_PF_3");
            checkData(referenceData, pipelineReferences, "SID_TO_PF_4");
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void addData(final ReferenceData referenceData, final PipelineEntity pipeline, final String[] mapNames) {
        MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
        for (final String mapName : mapNames) {
            mapStoreBuilder.setEvents(mapName, "user1", getEventsFromString("1111"), false);
            mapStoreBuilder.setEvents(mapName, "user2", getEventsFromString("2222"), false);
        }
        referenceData.put(new MapStoreCacheKey(DocRefUtil.create(pipeline), 1), mapStoreBuilder.getMapStore());

        mapStoreBuilder = new MapStoreBuilderImpl(null);
        for (final String mapName : mapNames) {
            mapStoreBuilder.setEvents(mapName, "user1", getEventsFromString("A1111"), false);
            mapStoreBuilder.setEvents(mapName, "user2", getEventsFromString("A2222"), false);
        }
        referenceData.put(new MapStoreCacheKey(DocRefUtil.create(pipeline), 2), mapStoreBuilder.getMapStore());

        mapStoreBuilder = new MapStoreBuilderImpl(null);
        for (final String mapName : mapNames) {
            mapStoreBuilder.setEvents(mapName, "user1", getEventsFromString("B1111"), false);
            mapStoreBuilder.setEvents(mapName, "user2", getEventsFromString("B2222"), false);
        }
        referenceData.put(new MapStoreCacheKey(DocRefUtil.create(pipeline), 3), mapStoreBuilder.getMapStore());
    }

    private void checkData(final ReferenceData data, final List<PipelineReference> pipelineReferences,
                           final String mapName) {
        final ErrorReceiver errorReceiver = new FatalErrorReceiver();

        Assert.assertEquals("B1111", getStringFromEvents(data.getValue(pipelineReferences, errorReceiver,
                DateUtil.parseNormalDateTimeString("2010-01-01T09:47:00.111Z"), mapName, "user1")));
        Assert.assertEquals("B1111", getStringFromEvents(data.getValue(pipelineReferences, errorReceiver,
                DateUtil.parseNormalDateTimeString("2015-01-01T09:47:00.000Z"), mapName, "user1")));
        Assert.assertEquals("A1111", getStringFromEvents(data.getValue(pipelineReferences, errorReceiver,
                DateUtil.parseNormalDateTimeString("2009-10-01T09:47:00.000Z"), mapName, "user1")));
        Assert.assertEquals("A1111", getStringFromEvents(data.getValue(pipelineReferences, errorReceiver,
                DateUtil.parseNormalDateTimeString("2009-01-01T09:47:00.000Z"), mapName, "user1")));
        Assert.assertEquals("1111", getStringFromEvents(data.getValue(pipelineReferences, errorReceiver,
                DateUtil.parseNormalDateTimeString("2008-01-01T09:47:00.000Z"), mapName, "user1")));

        Assert.assertNull(getStringFromEvents(data.getValue(pipelineReferences, errorReceiver,
                DateUtil.parseNormalDateTimeString("2006-01-01T09:47:00.000Z"), mapName, "user1")));
        Assert.assertNull(getStringFromEvents(data.getValue(pipelineReferences, errorReceiver,
                DateUtil.parseNormalDateTimeString("2009-01-01T09:47:00.000Z"), mapName, "user1_X")));
        Assert.assertNull(getStringFromEvents(data.getValue(pipelineReferences, errorReceiver,
                DateUtil.parseNormalDateTimeString("2009-01-01T09:47:00.000Z"), "SID_TO_PF_X", "user1")));
    }

    @Test
    public void testNestedMaps() {
        Feed feed1 = feedService.create(null, "TEST_FEED_V1");
        feed1.setReference(true);
        feed1 = feedService.save(feed1);

        final PipelineEntity pipelineEntity = new PipelineEntity();
        final List<PipelineReference> pipelineReferences = new ArrayList<>();

        pipelineReferences.add(new PipelineReference(DocRefUtil.create(pipelineEntity),
                DocRefUtil.create(feed1), StreamType.REFERENCE.getName()));

        final ErrorReceiver errorReceiver = new FatalErrorReceiver();

        final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
        streamSet.add(new EffectiveStream(0, 0L));
        try (CacheManagerAutoCloseable cacheManager = CacheManagerAutoCloseable.create()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager, null, null) {
                @Override
                public TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                    return streamSet;
                }
            };
            final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, new MockReferenceDataLoader(), null);
            final ReferenceData referenceData = new ReferenceData();
            referenceData.setEffectiveStreamCache(effectiveStreamCache);
            referenceData.setMapStorePool(mapStoreCache);

            final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
            mapStoreBuilder.setEvents("CARD_NUMBER_TO_PF_NUMBER", "011111", getEventsFromString("091111"), false);
            mapStoreBuilder.setEvents("NUMBER_TO_SID", "091111", getEventsFromString("user1"), false);
            referenceData.put(new MapStoreCacheKey(DocRefUtil.create(pipelineEntity), 0), mapStoreBuilder.getMapStore());

            Assert.assertEquals("091111", getStringFromEvents(referenceData.getValue(pipelineReferences, errorReceiver,
                    0, "CARD_NUMBER_TO_PF_NUMBER", "011111")));
            Assert.assertEquals("user1", getStringFromEvents(
                    referenceData.getValue(pipelineReferences, errorReceiver, 0, "NUMBER_TO_SID", "091111")));

            Assert.assertEquals("user1", getStringFromEvents(referenceData.getValue(pipelineReferences, errorReceiver,
                    0, "CARD_NUMBER_TO_PF_NUMBER/NUMBER_TO_SID", "011111")));
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Test
    public void testRange() {
        Feed feed1 = feedService.create(null, "TEST_FEED_V1");
        feed1.setReference(true);
        feed1 = feedService.save(feed1);

        final PipelineEntity pipelineEntity = new PipelineEntity();
        final List<PipelineReference> pipelineReferences = new ArrayList<>();

        pipelineReferences.add(new PipelineReference(DocRefUtil.create(pipelineEntity),
                DocRefUtil.create(feed1), StreamType.REFERENCE.getName()));

        final ErrorReceiver errorReceiver = new FatalErrorReceiver();

        final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
        streamSet.add(new EffectiveStream(0, 0L));
        try (CacheManagerAutoCloseable cacheManager = CacheManagerAutoCloseable.create()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager, null, null) {
                @Override
                public TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                    return streamSet;
                }
            };
            final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, new MockReferenceDataLoader(), null);
            final ReferenceData referenceData = new ReferenceData();
            referenceData.setEffectiveStreamCache(effectiveStreamCache);
            referenceData.setMapStorePool(mapStoreCache);

            final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
            mapStoreBuilder.setEvents("IP_TO_LOC", new Range<>(2L, 30L), getEventsFromString("here"), false);
            mapStoreBuilder.setEvents("IP_TO_LOC", new Range<>(500L, 2000L), getEventsFromString("there"), false);
            referenceData.put(new MapStoreCacheKey(DocRefUtil.create(pipelineEntity), 0), mapStoreBuilder.getMapStore());

            Assert.assertEquals("here", getStringFromEvents(
                    referenceData.getValue(pipelineReferences, errorReceiver, 0, "IP_TO_LOC", "10")));
            Assert.assertEquals("here", getStringFromEvents(
                    referenceData.getValue(pipelineReferences, errorReceiver, 0, "IP_TO_LOC", "30")));
            Assert.assertEquals("there", getStringFromEvents(
                    referenceData.getValue(pipelineReferences, errorReceiver, 0, "IP_TO_LOC", "500")));
            Assert.assertEquals("there", getStringFromEvents(
                    referenceData.getValue(pipelineReferences, errorReceiver, 0, "IP_TO_LOC", "1000")));
            Assert.assertEquals("there", getStringFromEvents(
                    referenceData.getValue(pipelineReferences, errorReceiver, 0, "IP_TO_LOC", "2000")));
            Assert.assertEquals(null, getStringFromEvents(
                    referenceData.getValue(pipelineReferences, errorReceiver, 0, "IP_TO_LOC", "2001")));
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private EventList getEventsFromString(final String string) {
        final EventListBuilder builder = EventListBuilderFactory.createBuilder();
        final char[] ch = string.toCharArray();
        try {
            builder.characters(ch, 0, ch.length);
        } catch (final SAXException e) {
            LOGGER.error(e.getMessage(), e);
        }
        final EventList eventList = builder.getEventList();
        builder.reset();

        return eventList;
    }

    private String getStringFromEvents(final EventList eventList) {
        if (eventList != null) {
            return eventList.toString();
        }

        return null;
    }
}
