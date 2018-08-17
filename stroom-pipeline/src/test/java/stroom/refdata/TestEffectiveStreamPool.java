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
import stroom.security.Security;
import stroom.security.impl.mock.MockSecurityContext;
import stroom.data.meta.api.EffectiveMetaDataCriteria;
import stroom.data.meta.api.Data;
import stroom.data.meta.api.DataProperties;
import stroom.data.meta.impl.mock.MockDataMetaService;
import stroom.streamstore.shared.StreamTypeNames;
import stroom.util.cache.CacheManager;
import stroom.util.date.DateUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
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
        final String refFeedName = "TEST_REF";

        final InnerStreamMetaService mockStreamStore = new InnerStreamMetaService() {
            @Override
            public Set<Data> findEffectiveData(final EffectiveMetaDataCriteria criteria) {
                findEffectiveStreamSourceCount++;
                final Set<Data> results = new HashSet<>();
                long workingDate = criteria.getEffectivePeriod().getFrom();
                while (workingDate < criteria.getEffectivePeriod().getTo()) {
                    final Data stream = create(
                            new DataProperties.Builder()
                                    .feedName(refFeedName)
                                    .typeName(StreamTypeNames.RAW_REFERENCE)
                                    .createMs(workingDate)
                                    .build());

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
                    .get(new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            Assert.assertEquals("Database call", 1, findEffectiveStreamSourceCount);

            // Still in window
            time = DateUtil.parseNormalDateTimeString("2010-01-01T13:00:00.000Z");
            fromMs = getFromMs(time);
            toMs = getToMs(fromMs);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            Assert.assertEquals("Database call", 1, findEffectiveStreamSourceCount);

            // After window ...
            time = DateUtil.parseNormalDateTimeString("2010-01-15T13:00:00.000Z");
            fromMs = getFromMs(time);
            toMs = getToMs(fromMs);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            Assert.assertEquals("Database call", 2, findEffectiveStreamSourceCount);

            // Before window ...
            time = DateUtil.parseNormalDateTimeString("2009-12-15T13:00:00.000Z");
            fromMs = getFromMs(time);
            toMs = getToMs(fromMs);
            effectiveStreamPool
                    .get(new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            Assert.assertEquals("Database call", 3, findEffectiveStreamSourceCount);
        } catch (final RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Test
    public void testExpiry() throws InterruptedException {
        final String refFeedName = "TEST_REF";

        final InnerStreamMetaService mockStore = new InnerStreamMetaService();

        try (CacheManager cacheManager = new CacheManager()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(
                    cacheManager, mockStore, new EffectiveStreamInternPool(), new Security(new MockSecurityContext()), 100, TimeUnit.MILLISECONDS);

            Assert.assertEquals("No pooled times yet", 0, effectiveStreamCache.size());
            Assert.assertEquals("No calls to the database yet", 0, mockStore.getCallCount());

            long time = DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z");
            long fromMs = getFromMs(time);
            long toMs = getToMs(fromMs);
            Set<EffectiveStream> streams;

            // Make sure we've got no effective streams.
            streams = effectiveStreamCache.get(
                    new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            Assert.assertEquals("Database call", 1, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 0, streams.size());

            // Add a stream.
            mockStore.addEffectiveStream(refFeedName, time);

            // Make sure we've still got no effective streams as we are getting from cache now.
            streams = effectiveStreamCache.get(
                    new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            Assert.assertEquals("Database call", 1, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 0, streams.size());

            // Expire items in the cache.
            Thread.sleep(100);

            // Make sure we get one now
            streams = effectiveStreamCache.get(
                    new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            Assert.assertEquals("Database call", 2, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 1, streams.size());

            // Add a stream.
            mockStore.addEffectiveStream(
                    refFeedName,
                    DateUtil.parseNormalDateTimeString("2010-01-01T13:00:00.000Z"));

            // Make sure we still get one now
            streams = effectiveStreamCache.get(
                    new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            Assert.assertEquals("Database call", 2, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 1, streams.size());

            // Expire items in the cache.
            Thread.sleep(100);

            // Make sure we get two now
            streams = effectiveStreamCache.get(
                    new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            Assert.assertEquals("Database call", 3, mockStore.getCallCount());
            Assert.assertEquals("Effective streams", 2, streams.size());
        }
    }

    private long getFromMs(final long time) {
        return (time / APPROX_TEN_DAYS) * APPROX_TEN_DAYS;
    }

    private long getToMs(final long fromMs) {
        return fromMs + APPROX_TEN_DAYS;
    }

    private static class InnerStreamMetaService extends MockDataMetaService {
        private long callCount = 0;
        private final List<Data> streams = new ArrayList<>();

        InnerStreamMetaService() {
            super();
        }

        @Override
        public Set<Data> findEffectiveData(final EffectiveMetaDataCriteria criteria) {
            callCount++;

            return streams.stream()
                    .filter(stream ->
                            stream.getEffectiveMs() >= criteria.getEffectivePeriod().getFromMs()
                                    && stream.getEffectiveMs() <= criteria.getEffectivePeriod().getToMs())
                    .collect(Collectors.toSet());
        }

        void addEffectiveStream(final String feedName, long effectiveTimeMs) {
            final Data stream = create(
                    new DataProperties.Builder()
                            .feedName(feedName)
                            .typeName(StreamTypeNames.RAW_REFERENCE)
                            .createMs(effectiveTimeMs)
                            .build());
            streams.add(stream);
        }

        long getCallCount() {
            return callCount;
        }
    }
}
