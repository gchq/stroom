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

import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.entity.shared.DocRef;
import stroom.feed.shared.Feed;
import stroom.streamstore.server.EffectiveMetaDataCriteria;
import stroom.streamstore.server.MockStreamStore;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.cache.CacheManager;
import stroom.util.date.DateUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;
import stroom.util.thread.ThreadUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestEffectiveStreamCache extends StroomUnitTest {
    private long findEffectiveStreamSourceCount = 0;

    @Test
    public void testGrowingWindow() {
        final Feed referenceFeed = new Feed();
        referenceFeed.setReference(true);
        referenceFeed.setName("TEST_REF");
        final Feed eventFeed = new Feed();
        eventFeed.setName("TEST_REF");

        final MockStreamStore mockStreamStore = new MockStreamStore() {
            @Override
            public List<Stream> findEffectiveStream(final EffectiveMetaDataCriteria criteria) {
                findEffectiveStreamSourceCount++;
                final ArrayList<Stream> results = new ArrayList<>();
                long workingDate = criteria.getEffectivePeriod().getFrom();
                while (workingDate < criteria.getEffectivePeriod().getTo()) {
                    final Stream stream = Stream.createStreamForTesting(StreamType.RAW_REFERENCE, referenceFeed,
                            workingDate, workingDate);
                    results.add(stream);
                    workingDate = new DateTime(workingDate).plusDays(1).getMillis();
                }
                return results;
            }
        };

        DocRef feedRef = DocRef.create(referenceFeed);

        try (CacheManager cacheManager = new CacheManager()) {
            final EffectiveStreamCache effectiveStreamPool = new EffectiveStreamCache(cacheManager, mockStreamStore,
                    null, null);

            Assert.assertEquals("No pooled times yet", 0, effectiveStreamPool.size());
            Assert.assertEquals("No calls to the database yet", 0, findEffectiveStreamSourceCount);

            long time = DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z");
            long baseTime = effectiveStreamPool.getBaseTime(time);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), baseTime));
            Assert.assertEquals("Database call", 1, findEffectiveStreamSourceCount);

            // Still in window
            time = DateUtil.parseNormalDateTimeString("2010-01-01T13:00:00.000Z");
            baseTime = effectiveStreamPool.getBaseTime(time);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), baseTime));
            Assert.assertEquals("Database call", 1, findEffectiveStreamSourceCount);

            // After window ...
            time = DateUtil.parseNormalDateTimeString("2010-01-15T13:00:00.000Z");
            baseTime = effectiveStreamPool.getBaseTime(time);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), baseTime));
            Assert.assertEquals("Database call", 2, findEffectiveStreamSourceCount);

            // Before window ...
            time = DateUtil.parseNormalDateTimeString("2009-12-15T13:00:00.000Z");
            baseTime = effectiveStreamPool.getBaseTime(time);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), baseTime));
            Assert.assertEquals("Database call", 3, findEffectiveStreamSourceCount);
        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Test
    public void testExpiry() {
        final Feed referenceFeed = new Feed();
        referenceFeed.setReference(true);
        referenceFeed.setName("TEST_REF");
        final Feed eventFeed = new Feed();
        eventFeed.setName("TEST_REF");

        final MockStore mockStore = new MockStore();

        DocRef feedRef = DocRef.create(referenceFeed);

        try (CacheManager cacheManager = new CacheManager()) {
            final EffectiveStreamCache effectiveStreamPool = new EffectiveStreamCache(cacheManager, mockStore,
                    null, null, 100, TimeUnit.MILLISECONDS);

            Assert.assertEquals("No pooled times yet", 0, effectiveStreamPool.size());
            Assert.assertEquals("No calls to the database yet", 0, mockStore.getCallCount());

            long time = DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z");
            long baseTime = effectiveStreamPool.getBaseTime(time);
            Set<EffectiveStream> streams = null;

            // Make sure we've got no effective streams.
            streams = effectiveStreamPool.get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), baseTime));
            Assert.assertEquals("Database call", 1, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 0, streams.size());

            // Add a stream.
            mockStore.addEffectiveStream(referenceFeed, time);

            // Make sure we've stil got no effective streams as we are getting from cache now.
            streams = effectiveStreamPool.get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), baseTime));
            Assert.assertEquals("Database call", 1, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 0, streams.size());

            // Expire items in the cache.
            ThreadUtil.sleep(100);

            // Make sure we get one now
            streams = effectiveStreamPool.get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), baseTime));
            Assert.assertEquals("Database call", 2, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 1, streams.size());

            // Add a stream.
            mockStore.addEffectiveStream(referenceFeed, DateUtil.parseNormalDateTimeString("2010-01-01T13:00:00.000Z"));

            // Make sure we still get one now
            streams = effectiveStreamPool.get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), baseTime));
            Assert.assertEquals("Database call", 2, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 1, streams.size());

            // Expire items in the cache.
            ThreadUtil.sleep(100);

            // Make sure we get two now
            streams = effectiveStreamPool.get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), baseTime));
            Assert.assertEquals("Database call", 3, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 2, streams.size());

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private static class MockStore extends MockStreamStore {
        private long callCount = 0;
        private final List<Stream> streams = new ArrayList<>();

        @Override
        public List<Stream> findEffectiveStream(final EffectiveMetaDataCriteria criteria) {
            callCount++;

            return streams.stream()
                    .filter(stream -> stream.getEffectiveMs() >= criteria.getEffectivePeriod().getFromMs() && stream.getEffectiveMs() <= criteria.getEffectivePeriod().getToMs())
                    .collect(Collectors.toList());
        }

        void addEffectiveStream(final Feed feed, long effectiveTimeMs) {
            final Stream stream = Stream.createStreamForTesting(StreamType.RAW_REFERENCE, feed,
                    effectiveTimeMs, effectiveTimeMs);
            streams.add(stream);
        }

        long getCallCount() {
            return callCount;
        }
    }
}
