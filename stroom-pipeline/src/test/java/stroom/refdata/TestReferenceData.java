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
 *
 */

package stroom.refdata;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import stroom.docref.DocRef;
import stroom.docstore.Persistence;
import stroom.docstore.Store;
import stroom.docstore.memory.MemoryPersistence;
import stroom.entity.shared.Range;
import stroom.feed.FeedStore;
import stroom.feed.FeedStoreImpl;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.PipelineStoreImpl;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.refdata.impl.MockReferenceDataLoader;
import stroom.security.MockSecurityContext;
import stroom.security.SecurityContext;
import stroom.streamstore.shared.StreamTypeEntity;
import stroom.util.cache.CacheManager;
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

    private final SecurityContext securityContext = new MockSecurityContext();
    private final Persistence persistence = new MemoryPersistence();
    private final FeedStore feedStore = new FeedStoreImpl(new Store<>(persistence, securityContext), securityContext, persistence);
    private final PipelineStore pipelineStore = new PipelineStoreImpl(new Store<>(persistence, securityContext), securityContext, persistence);

    @Test
    public void testSimple() {
        final DocRef feed1Ref = feedStore.createDocument("TEST_FEED_1");
        final DocRef feed2Ref = feedStore.createDocument("TEST_FEED_2");
        final DocRef pipeline1Ref = pipelineStore.createDocument("TEST_PIPELINE_1");
        final DocRef pipeline2Ref = pipelineStore.createDocument("TEST_PIPELINE_2");

        final List<PipelineReference> pipelineReferences = new ArrayList<>();
        pipelineReferences.add(new PipelineReference(pipeline1Ref, feed1Ref, StreamTypeEntity.REFERENCE.getName()));
        pipelineReferences.add(new PipelineReference(pipeline2Ref, feed2Ref, StreamTypeEntity.REFERENCE.getName()));

        final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
        streamSet.add(new EffectiveStream(1, DateUtil.parseNormalDateTimeString("2008-01-01T09:47:00.000Z")));
        streamSet.add(new EffectiveStream(2, DateUtil.parseNormalDateTimeString("2009-01-01T09:47:00.000Z")));
        streamSet.add(new EffectiveStream(3, DateUtil.parseNormalDateTimeString("2010-01-01T09:47:00.000Z")));
        try (CacheManager cacheManager = new CacheManager()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager, null, null, null) {
                @Override
                protected TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                    return streamSet;
                }
            };
            final ReferenceDataLoader referenceDataLoader = effectiveFeed -> new MapStoreImpl();

            final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, referenceDataLoader, null, null);
            final ReferenceData referenceData = new ReferenceData(effectiveStreamCache, mapStoreCache, null, null, null, null);

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

    private void checkData(final ReferenceData data, final List<PipelineReference> pipelineReferences,
                           final String mapName) {
        final ReferenceDataResult result = new ReferenceDataResult();
        data.getValue(pipelineReferences, DateUtil.parseNormalDateTimeString("2010-01-01T09:47:00.111Z"), mapName, "user1", result);

        Assert.assertEquals("B1111", lookup(data, pipelineReferences, "2010-01-01T09:47:00.111Z", mapName, "user1"));
        Assert.assertEquals("B1111", lookup(data, pipelineReferences, "2015-01-01T09:47:00.000Z", mapName, "user1"));
        Assert.assertEquals("A1111", lookup(data, pipelineReferences, "2009-10-01T09:47:00.000Z", mapName, "user1"));
        Assert.assertEquals("A1111", lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", mapName, "user1"));
        Assert.assertEquals("1111", lookup(data, pipelineReferences, "2008-01-01T09:47:00.000Z", mapName, "user1"));

        Assert.assertNull(lookup(data, pipelineReferences, "2006-01-01T09:47:00.000Z", mapName, "user1"));
        Assert.assertNull(lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", mapName, "user1_X"));
        Assert.assertNull(lookup(data, pipelineReferences, "2009-01-01T09:47:00.000Z", "SID_TO_PF_X", "user1"));
    }

    @Test
    public void testNestedMaps() {
        final DocRef feed1Ref = feedStore.createDocument("TEST_FEED_V1");
        final FeedDoc feedDoc = feedStore.readDocument(feed1Ref);
        feedDoc.setReference(true);
        feedStore.writeDocument(feedDoc);

        final DocRef pipelineRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, "12345");
        final List<PipelineReference> pipelineReferences = new ArrayList<>();

        pipelineReferences.add(new PipelineReference(pipelineRef, feed1Ref, StreamTypeEntity.REFERENCE.getName()));

        final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
        streamSet.add(new EffectiveStream(0, 0L));
        try (CacheManager cacheManager = new CacheManager()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager, null, null, null) {
                @Override
                protected TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                    return streamSet;
                }
            };
            final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, new MockReferenceDataLoader(), null, null);
            final ReferenceData referenceData = new ReferenceData(effectiveStreamCache, mapStoreCache, null, null, null, null);

            final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
            mapStoreBuilder.setEvents("CARD_NUMBER_TO_PF_NUMBER", "011111", getEventsFromString("091111"), false);
            mapStoreBuilder.setEvents("NUMBER_TO_SID", "091111", getEventsFromString("user1"), false);
            referenceData.put(new MapStoreCacheKey(pipelineRef, 0), mapStoreBuilder.getMapStore());

            Assert.assertEquals("091111", lookup(referenceData, pipelineReferences, 0, "CARD_NUMBER_TO_PF_NUMBER", "011111"));
            Assert.assertEquals("user1", lookup(referenceData, pipelineReferences, 0, "NUMBER_TO_SID", "091111"));

            Assert.assertEquals("user1", lookup(referenceData, pipelineReferences, 0, "CARD_NUMBER_TO_PF_NUMBER/NUMBER_TO_SID", "011111"));
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Test
    public void testRange() {
        final DocRef feed1Ref = feedStore.createDocument("TEST_FEED_V1");
        final FeedDoc feedDoc = feedStore.readDocument(feed1Ref);
        feedDoc.setReference(true);
        feedStore.writeDocument(feedDoc);

        final DocRef pipelineRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, "12345");
        final List<PipelineReference> pipelineReferences = new ArrayList<>();

        pipelineReferences.add(new PipelineReference(pipelineRef, feed1Ref, StreamTypeEntity.REFERENCE.getName()));

        final TreeSet<EffectiveStream> streamSet = new TreeSet<>();
        streamSet.add(new EffectiveStream(0, 0L));
        try (CacheManager cacheManager = new CacheManager()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager, null, null, null) {
                @Override
                protected TreeSet<EffectiveStream> create(final EffectiveStreamKey key) {
                    return streamSet;
                }
            };
            final MapStoreCache mapStoreCache = new MapStoreCache(cacheManager, new MockReferenceDataLoader(), null, null);
            final ReferenceData referenceData = new ReferenceData(effectiveStreamCache, mapStoreCache, null, null, null, null);

            final MapStoreBuilder mapStoreBuilder = new MapStoreBuilderImpl(null);
            mapStoreBuilder.setEvents("IP_TO_LOC", new Range<>(2L, 30L), getEventsFromString("here"), false);
            mapStoreBuilder.setEvents("IP_TO_LOC", new Range<>(500L, 2000L), getEventsFromString("there"), false);
            referenceData.put(new MapStoreCacheKey(pipelineRef, 0), mapStoreBuilder.getMapStore());

            Assert.assertEquals("here", lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC", "10"));
            Assert.assertEquals("here", lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC", "30"));
            Assert.assertEquals("there", lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC", "500"));
            Assert.assertEquals("there", lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC", "1000"));
            Assert.assertEquals("there", lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC", "2000"));
            Assert.assertNull(lookup(referenceData, pipelineReferences, 0, "IP_TO_LOC", "2001"));
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
}
