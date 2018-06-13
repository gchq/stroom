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
 *
 */

package stroom.refdata;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import stroom.entity.shared.DocRefUtil;
import stroom.feed.FeedService;
import stroom.feed.shared.Feed;
import stroom.guice.PipelineScopeRunnable;
import stroom.guice.StroomBeanStore;
import stroom.pipeline.PipelineService;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.query.api.v2.DocRef;
import stroom.streamstore.shared.StreamType;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.util.cache.CacheManager;
import stroom.util.date.DateUtil;
import stroom.xml.event.EventList;
import stroom.xml.event.EventListBuilder;
import stroom.xml.event.EventListBuilderFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public class TestReferenceDataWithCache extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestReferenceDataWithCache.class);
    private static volatile DocRef folder;
    private final EventListBuilder builder = EventListBuilderFactory.createBuilder();

    @Inject
    private FeedService feedService;
    @Inject
    private PipelineService pipelineService;
    @Inject
    private StroomBeanStore beanStore;
    @Inject
    private PipelineScopeRunnable pipelineScopeRunnable;

    /**
     * Test.
     */
    @Test
    public void testSimple() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            Feed feed1 = feedService.create("TEST_FEED_1");
            feed1.setReference(true);
            feed1 = feedService.save(feed1);

            Feed feed2 = feedService.create("TEST_FEED_2");
            feed2.setReference(true);
            feed2 = feedService.save(feed2);

            final PipelineEntity pipeline1 = pipelineService.create("TEST_PIPELINE_1");
            final PipelineEntity pipeline2 = pipelineService.create("TEST_PIPELINE_2");

            final PipelineReference pipelineReference1 = new PipelineReference(DocRefUtil.create(pipeline1),
                    DocRefUtil.create(feed1), StreamType.REFERENCE.getName());
            final PipelineReference pipelineReference2 = new PipelineReference(DocRefUtil.create(pipeline2),
                    DocRefUtil.create(feed2), StreamType.REFERENCE.getName());

            final List<PipelineReference> pipelineReferences = new ArrayList<>();
            pipelineReferences.add(pipelineReference1);
            pipelineReferences.add(pipelineReference2);

            final ReferenceData referenceData = createReferenceData();

            final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
            streamSet.add(new EffectiveStream(1, DateUtil.parseNormalDateTimeString("2008-01-01T09:47:00.000Z")));
            streamSet.add(new EffectiveStream(2, DateUtil.parseNormalDateTimeString("2009-01-01T09:47:00.000Z")));
            streamSet.add(new EffectiveStream(3, DateUtil.parseNormalDateTimeString("2010-01-01T09:47:00.000Z")));

            try (final CacheManager cacheManager = new CacheManager()) {
                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager, null, null, null) {
                    @Override
                    public TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                        return streamSet;
                    }
                };
//                final ReferenceDataLoader referenceDataLoader = effectiveFeed -> new MapStoreImpl();
//                final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, referenceDataLoader, null, null);
                referenceData.setEffectiveStreamCache(effectiveStreamCache);
//                referenceData.setMapStorePool(mapStoreCache);

                final ErrorReceiver errorReceiver = new FatalErrorReceiver();

                // Add multiple reference data items to prove that looping over maps
                // works.
                addData(referenceData, pipeline1, new String[]{"SID_TO_PF_1", "SID_TO_PF_2"});
                addData(referenceData, pipeline2, new String[]{"SID_TO_PF_3", "SID_TO_PF_4"});
                checkData(referenceData, pipelineReferences, "SID_TO_PF_1");
                checkData(referenceData, pipelineReferences, "SID_TO_PF_2");
                checkData(referenceData, pipelineReferences, "SID_TO_PF_3");
                checkData(referenceData, pipelineReferences, "SID_TO_PF_4");
            } catch (final RuntimeException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private void addData(final ReferenceData referenceData, final PipelineEntity pipeline, final String[] mapNames) {
        MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
        for (final String mapName : mapNames) {
//            mapStoreBuilder.setEvents(mapName, "user1", getEventsFromString("1111"), false);
//            mapStoreBuilder.setEvents(mapName, "user2", getEventsFromString("2222"), false);
        }
//        referenceData.put(new MapStoreCacheKey(DocRefUtil.create(pipeline), 1), mapStoreBuilder.getMapStore());

        mapStoreBuilder = new MapStoreBuilderImpl(null);
        for (final String mapName : mapNames) {
//            mapStoreBuilder.setEvents(mapName, "user1", getEventsFromString("A1111"), false);
//            mapStoreBuilder.setEvents(mapName, "user2", getEventsFromString("A2222"), false);
        }
//        referenceData.put(new MapStoreCacheKey(DocRefUtil.create(pipeline), 2), mapStoreBuilder.getMapStore());

        mapStoreBuilder = new MapStoreBuilderImpl(null);
        for (final String mapName : mapNames) {
//            mapStoreBuilder.setEvents(mapName, "user1", getEventsFromString("B1111"), false);
//            mapStoreBuilder.setEvents(mapName, "user2", getEventsFromString("B2222"), false);
        }
//        referenceData.put(new MapStoreCacheKey(DocRefUtil.create(pipeline), 3), mapStoreBuilder.getMapStore());
    }

    private void checkData(final ReferenceData data, final List<PipelineReference> pipelineReferences, final String mapName) {
        Assert.assertEquals("B1111", lookup(data, pipelineReferences, "2010-01-01T09:47:00.111Z", mapName, "user1"));
        Assert.assertEquals("B1111", lookup(data, pipelineReferences, "2015-01-01T09:47:00.000Z", mapName, "user1"));
        Assert.assertEquals("A1111", lookup(data, pipelineReferences, "2009-10-01T09:47:00.000Z", mapName, "user1"));
        Assert.assertEquals("A1111", lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", mapName, "user1"));
        Assert.assertEquals("1111", lookup(data, pipelineReferences, "2008-01-01T09:47:00.000Z", mapName, "user1"));

        Assert.assertNull(lookup(data, pipelineReferences, "2006-01-01T09:47:00.000Z", mapName, "user1"));
        Assert.assertNull(lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", mapName, "user1_X"));
        Assert.assertNull(lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", "SID_TO_PF_X", "user1"));
    }

    /**
     * Test.
     */
    @Test
    public void testNestedMaps() {
        pipelineScopeRunnable.scopeRunnable(() -> {
            Feed feed = feedService.create("TEST_FEED_V3");
            feed.setReference(true);
            feed = feedService.save(feed);

            final PipelineEntity pipelineEntity = new PipelineEntity();
            final PipelineReference pipelineReference = new PipelineReference(DocRefUtil.create(pipelineEntity),
                    DocRefUtil.create(feed), StreamType.REFERENCE.getName());
            final List<PipelineReference> pipelineReferences = new ArrayList<>();
            pipelineReferences.add(pipelineReference);

            final ReferenceData data = createReferenceData();

            final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
            streamSet.add(new EffectiveStream(0, 0L));

            try (final CacheManager cacheManager = new CacheManager()) {
                final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager, null, null, null) {
                    @Override
                    public TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                        return streamSet;
                    }
                };
//                final ReferenceDataLoader referenceDataLoader = effectiveFeed -> new MapStoreImpl();
//                final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, referenceDataLoader, null, null);
                data.setEffectiveStreamCache(effectiveStreamCache);
//                data.setMapStorePool(mapStoreCache);

                final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
//                mapStoreBuilder.setEvents("CARD_NUMBER_TO_PF_NUMBER", "011111", getEventsFromString("091111"), false);
//                mapStoreBuilder.setEvents("NUMBER_TO_SID", "091111", getEventsFromString("user1"), false);
//                data.put(new MapStoreCacheKey(DocRefUtil.create(pipelineEntity), 0), mapStoreBuilder.getMapStore());

                Assert.assertEquals("091111", lookup(data, pipelineReferences, 0, "CARD_NUMBER_TO_PF_NUMBER", "011111"));
                Assert.assertEquals("user1", lookup(data, pipelineReferences, 0, "NUMBER_TO_SID", "091111"));
                Assert.assertEquals("user1", lookup(data, pipelineReferences, 0, "CARD_NUMBER_TO_PF_NUMBER/NUMBER_TO_SID", "011111"));
            }
        });
    }

    private EventList getEventsFromString(final String string) {
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

    private String lookup(final ReferenceData data,
                          final List<PipelineReference> pipelineReferences,
                          final String time,
                          final String mapName,
                          final String key) {
        return lookup(data, pipelineReferences, DateUtil.parseNormalDateTimeString(time), mapName, key);
    }

    private String lookup(final ReferenceData data,
                          final List<PipelineReference> pipelineReferences,
                          final long time,
                          final String mapName,
                          final String key) {
        final ReferenceDataResult result = new ReferenceDataResult();
        data.getValue(pipelineReferences, LookupIdentifier.of(mapName, key, time), result);
//        if (result.getEventListProxy() != null) {
//            return result.getEventListProxy().toString();
//        }

        return null;
    }

    private ReferenceData createReferenceData() {
        return beanStore.getInstance(ReferenceData.class);
    }
}
