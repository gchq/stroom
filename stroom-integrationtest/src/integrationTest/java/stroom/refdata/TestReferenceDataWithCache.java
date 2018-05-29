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
import stroom.docref.DocRef;
import stroom.feed.FeedStore;
import stroom.guice.PipelineScopeRunnable;
import stroom.guice.StroomBeanStore;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineReference;
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
    private final EventListBuilder builder = EventListBuilderFactory.createBuilder();

    @Inject
    private FeedStore feedStore;
    @Inject
    private PipelineStore pipelineStore;
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
            final DocRef feed1 = feedStore.createDocument("TEST_FEED_1");
//            feed1.setReference(true);
//            feed1 = feedService.save(feed1);

            final DocRef feed2 = feedStore.createDocument("TEST_FEED_2");
//            feed2.setReference(true);
//            feed2 = feedService.save(feed2);

            final DocRef pipeline1Ref = pipelineStore.createDocument("TEST_PIPELINE_1");
            final DocRef pipeline2Ref = pipelineStore.createDocument("TEST_PIPELINE_2");

            final PipelineReference pipelineReference1 = new PipelineReference(pipeline1Ref, feed1, StreamType.REFERENCE.getName());
            final PipelineReference pipelineReference2 = new PipelineReference(pipeline2Ref, feed2, StreamType.REFERENCE.getName());

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
                final ReferenceDataLoader referenceDataLoader = effectiveFeed -> new MapStoreImpl();
                final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, referenceDataLoader, null, null);
                referenceData.setEffectiveStreamCache(effectiveStreamCache);
                referenceData.setMapStorePool(mapStoreCache);

                // Add multiple reference data items to prove that looping over maps
                // works.
                addData(referenceData, pipeline1Ref, new String[]{"SID_TO_PF_1", "SID_TO_PF_2"});
                addData(referenceData, pipeline2Ref, new String[]{"SID_TO_PF_3", "SID_TO_PF_4"});
                checkData(referenceData, pipelineReferences, "SID_TO_PF_1");
                checkData(referenceData, pipelineReferences, "SID_TO_PF_2");
                checkData(referenceData, pipelineReferences, "SID_TO_PF_3");
                checkData(referenceData, pipelineReferences, "SID_TO_PF_4");
            } catch (final RuntimeException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }

    private void addData(final ReferenceData referenceData, final DocRef pipelineRef, final String[] mapNames) {
        MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
        for (final String mapName : mapNames) {
            mapStoreBuilder.setEvents(mapName, "user1", getEventsFromString("1111"), false);
            mapStoreBuilder.setEvents(mapName, "user2", getEventsFromString("2222"), false);
        }
        referenceData.put(new MapStoreCacheKey(pipelineRef, 1), mapStoreBuilder.getMapStore());

        mapStoreBuilder = new MapStoreBuilderImpl(null);
        for (final String mapName : mapNames) {
            mapStoreBuilder.setEvents(mapName, "user1", getEventsFromString("A1111"), false);
            mapStoreBuilder.setEvents(mapName, "user2", getEventsFromString("A2222"), false);
        }
        referenceData.put(new MapStoreCacheKey(pipelineRef, 2), mapStoreBuilder.getMapStore());

        mapStoreBuilder = new MapStoreBuilderImpl(null);
        for (final String mapName : mapNames) {
            mapStoreBuilder.setEvents(mapName, "user1", getEventsFromString("B1111"), false);
            mapStoreBuilder.setEvents(mapName, "user2", getEventsFromString("B2222"), false);
        }
        referenceData.put(new MapStoreCacheKey(pipelineRef, 3), mapStoreBuilder.getMapStore());
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
            final DocRef feed = feedStore.createDocument("TEST_FEED_V3");
//            feed.setReference(true);
//            feed = feedService.save(feed);

            final DocRef pipelineRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, "1234");
            final PipelineReference pipelineReference = new PipelineReference(pipelineRef, feed, StreamType.REFERENCE.getName());
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
                final ReferenceDataLoader referenceDataLoader = effectiveFeed -> new MapStoreImpl();
                final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, referenceDataLoader, null, null);
                data.setEffectiveStreamCache(effectiveStreamCache);
                data.setMapStorePool(mapStoreCache);

                final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
                mapStoreBuilder.setEvents("CARD_NUMBER_TO_PF_NUMBER", "011111", getEventsFromString("091111"), false);
                mapStoreBuilder.setEvents("NUMBER_TO_SID", "091111", getEventsFromString("user1"), false);
                data.put(new MapStoreCacheKey(pipelineRef, 0), mapStoreBuilder.getMapStore());

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
        data.getValue(pipelineReferences, time, mapName, key, result);
        if (result.getEventList() != null) {
            return result.getEventList().toString();
        }

        return null;
    }

    private ReferenceData createReferenceData() {
        return beanStore.getInstance(ReferenceData.class);
    }
}
