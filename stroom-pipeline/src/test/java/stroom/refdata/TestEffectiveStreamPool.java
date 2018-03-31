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

package stroom.refdata;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.entity.shared.DocRefUtil;
import stroom.feed.shared.Feed;
import stroom.query.api.v2.DocRef;
import stroom.security.MockSecurityContext;
import stroom.security.Security;
import stroom.streamstore.EffectiveMetaDataCriteria;
import stroom.streamstore.MockStreamStore;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.cache.CacheManager;
import stroom.util.date.DateUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;
import stroom.util.thread.ThreadUtil;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestEffectiveStreamPool extends StroomUnitTest {
    // Actually 11.5 days but this is fine for the purposes of reference data.
    private static final long APPROX_TEN_DAYS = 1000000000;

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
                    final Stream stream = Stream.createStreamForTesting(
                            StreamType.RAW_REFERENCE, referenceFeed, workingDate, workingDate);
                    results.add(stream);
                    workingDate = Instant.ofEpochMilli(workingDate)
                            .atZone(ZoneOffset.UTC)
                            .plusDays(1)
                            .toInstant()
                            .toEpochMilli();
                }
                return results;
            }
        };

        DocRef feedRef = DocRefUtil.create(referenceFeed);

        try (CacheManager cacheManager = new CacheManager()) {
            final EffectiveStreamCache effectiveStreamPool = new EffectiveStreamCache(cacheManager,
                    mockStreamStore,
                    new EffectiveStreamInternPool(),
                    new Security(new MockSecurityContext()));

            Assert.assertEquals("No pooled times yet", 0, effectiveStreamPool.size());
            Assert.assertEquals("No calls to the database yet", 0, findEffectiveStreamSourceCount);

            long time = DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z");
            long fromMs = getFromMs(time);
            long toMs = getToMs(fromMs);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), fromMs, toMs));
            Assert.assertEquals("Database call", 1, findEffectiveStreamSourceCount);

            // Still in window
            time = DateUtil.parseNormalDateTimeString("2010-01-01T13:00:00.000Z");
            fromMs = getFromMs(time);
            toMs = getToMs(fromMs);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), fromMs, toMs));
            Assert.assertEquals("Database call", 1, findEffectiveStreamSourceCount);

            // After window ...
            time = DateUtil.parseNormalDateTimeString("2010-01-15T13:00:00.000Z");
            fromMs = getFromMs(time);
            toMs = getToMs(fromMs);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), fromMs, toMs));
            Assert.assertEquals("Database call", 2, findEffectiveStreamSourceCount);

            // Before window ...
            time = DateUtil.parseNormalDateTimeString("2009-12-15T13:00:00.000Z");
            fromMs = getFromMs(time);
            toMs = getToMs(fromMs);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), fromMs, toMs));
            Assert.assertEquals("Database call", 3, findEffectiveStreamSourceCount);
        } catch (final RuntimeException e) {
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

        DocRef feedRef = DocRefUtil.create(referenceFeed);

        try (CacheManager cacheManager = new CacheManager()) {
            final EffectiveStreamCache effectiveStreamPool = new EffectiveStreamCache(
                    cacheManager, mockStore, null, null, 100, TimeUnit.MILLISECONDS);

            Assert.assertEquals("No pooled times yet", 0, effectiveStreamPool.size());
            Assert.assertEquals("No calls to the database yet", 0, mockStore.getCallCount());

            long time = DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z");
            long fromMs = getFromMs(time);
            long toMs = getToMs(fromMs);
            Set<EffectiveStream> streams;

            // Make sure we've got no effective streams.
            streams = effectiveStreamPool.get(
                    new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), fromMs, toMs));
            Assert.assertEquals("Database call", 1, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 0, streams.size());

            // Add a stream.
            mockStore.addEffectiveStream(referenceFeed, time);

            // Make sure we've stil got no effective streams as we are getting from cache now.
            streams = effectiveStreamPool.get(
                    new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), fromMs, toMs));
            Assert.assertEquals("Database call", 1, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 0, streams.size());

            // Expire items in the cache.
            ThreadUtil.sleep(100);

            // Make sure we get one now
            streams = effectiveStreamPool.get(
                    new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), fromMs, toMs));
            Assert.assertEquals("Database call", 2, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 1, streams.size());

            // Add a stream.
            mockStore.addEffectiveStream(
                    referenceFeed,
                    DateUtil.parseNormalDateTimeString("2010-01-01T13:00:00.000Z"));

            // Make sure we still get one now
            streams = effectiveStreamPool.get(
                    new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), fromMs, toMs));
            Assert.assertEquals("Database call", 2, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 1, streams.size());

            // Expire items in the cache.
            ThreadUtil.sleep(100);

            // Make sure we get two now
            streams = effectiveStreamPool.get(
                    new EffectiveStreamKey(feedRef, StreamType.REFERENCE.getName(), fromMs, toMs));
            Assert.assertEquals("Database call", 3, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 2, streams.size());

        } catch (final Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private long getFromMs(final long time) {
        return (time / APPROX_TEN_DAYS) * APPROX_TEN_DAYS;
    }

    private long getToMs(final long fromMs) {
        return fromMs + APPROX_TEN_DAYS;
    }

    private static class MockStore extends MockStreamStore {
        private long callCount = 0;
        private final List<Stream> streams = new ArrayList<>();

        @Override
        public List<Stream> findEffectiveStream(final EffectiveMetaDataCriteria criteria) {
            callCount++;

            return streams.stream()
                    .filter(stream ->
                            stream.getEffectiveMs() >= criteria.getEffectivePeriod().getFromMs()
                                    && stream.getEffectiveMs() <= criteria.getEffectivePeriod().getToMs())
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
