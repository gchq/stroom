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

package stroom.pipeline.refdata;


import stroom.cache.api.CacheManager;
import stroom.cache.impl.CacheManagerImpl;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.EffectiveMeta;
import stroom.meta.api.EffectiveMetaDataCriteria;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.mock.MockMetaService;
import stroom.meta.shared.Meta;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.NullSafe;
import stroom.util.cache.CacheConfig;
import stroom.util.date.DateUtil;
import stroom.util.time.StroomDuration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestEffectiveStreamCache extends StroomUnitTest {

    private static final String FEED_NAME = "MY_FEED";
    private static final String TYPE_NAME = StreamTypeNames.REFERENCE;

    // Actually 11.5 days but this is fine for the purposes of reference data.
//    private static final long APPROX_TEN_DAYS = 1000000000;

    private long findEffectiveStreamSourceCount = 0;

    @Test
    void testGrowingWindow() {
        final String refFeedName = "TEST_REF";

        final InnerStreamMetaService metaService = new InnerStreamMetaService() {

            @Override
            public Set<EffectiveMeta> findEffectiveData(final EffectiveMetaDataCriteria criteria) {
                findEffectiveStreamSourceCount++;
                final Set<EffectiveMeta> results = new HashSet<>();
                long workingDate = criteria.getEffectivePeriod().getFrom();
                while (workingDate < criteria.getEffectivePeriod().getTo()) {
                    final Meta meta = create(
                            MetaProperties.builder()
                                    .feedName(refFeedName)
                                    .typeName(StreamTypeNames.REFERENCE)
                                    .effectiveMs(workingDate)
                                    .build());

                    final EffectiveMeta effectiveMeta = new EffectiveMeta(meta);

                    results.add(effectiveMeta);
                    workingDate = Instant.ofEpochMilli(workingDate)
                            .atZone(ZoneOffset.UTC)
                            .plusDays(1)
                            .toInstant()
                            .toEpochMilli();
                }
                return results;
            }
        };

        try (CacheManager cacheManager = new CacheManagerImpl()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager,
                    metaService,
                    new EffectiveStreamInternPool(),
                    new MockSecurityContext(),
                    ReferenceDataConfig::new);

            assertThat(effectiveStreamCache.size()).as("No pooled times yet").isEqualTo(0);
            assertThat(findEffectiveStreamSourceCount).as("No calls to the database yet").isEqualTo(0);

            long time = DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z");
            long fromMs = getFromMs(time);
            long toMs = getToMs(fromMs);
            effectiveStreamCache
                    .get(new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            assertThat(findEffectiveStreamSourceCount).as("Database call").isEqualTo(1);

            // Still in window
            time = DateUtil.parseNormalDateTimeString("2010-01-01T13:00:00.000Z");
            fromMs = getFromMs(time);
            toMs = getToMs(fromMs);
            effectiveStreamCache
                    .get(new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            assertThat(findEffectiveStreamSourceCount).as("Database call").isEqualTo(1);

            // After window ...
            time = DateUtil.parseNormalDateTimeString("2010-01-15T13:00:00.000Z");
            fromMs = getFromMs(time);
            toMs = getToMs(fromMs);
            effectiveStreamCache
                    .get(new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            assertThat(findEffectiveStreamSourceCount).as("Database call").isEqualTo(2);

            // Before window ...
            time = DateUtil.parseNormalDateTimeString("2009-12-15T13:00:00.000Z");
            fromMs = getFromMs(time);
            toMs = getToMs(fromMs);
            effectiveStreamCache
                    .get(new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            assertThat(findEffectiveStreamSourceCount).as("Database call").isEqualTo(3);
        } catch (final RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Test
    void testExpiry() throws InterruptedException {
        final String refFeedName = "TEST_REF";

        final InnerStreamMetaService mockStore = new InnerStreamMetaService();

        try (CacheManager cacheManager = new CacheManagerImpl()) {
            final ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig()
                    .withEffectiveStreamCache(CacheConfig.builder()
                            .maximumSize(1000L)
                            .expireAfterWrite(StroomDuration.ofMillis(100))
                            .build());

            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(
                    cacheManager,
                    mockStore,
                    new EffectiveStreamInternPool(),
                    new MockSecurityContext(),
                    () -> referenceDataConfig);

            assertThat(effectiveStreamCache.size()).as("No pooled times yet").isEqualTo(0);
            assertThat(mockStore.getCallCount()).as("No calls to the database yet").isEqualTo(0);

            long time = DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z");
            long fromMs = getFromMs(time);
            long toMs = getToMs(fromMs);
            Set<EffectiveStream> streams;

            // Make sure we've got no effective streams.
            streams = effectiveStreamCache.get(
                    new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            assertThat(mockStore.getCallCount()).as("Database call").isEqualTo(1);
            assertThat(streams.size()).as("Effective streams").isEqualTo(0);

            // Add a stream.
            mockStore.addEffectiveStream(refFeedName, time);

            // Make sure we've still got no effective streams as we are getting from cache now.
            streams = effectiveStreamCache.get(
                    new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            assertThat(mockStore.getCallCount()).as("Database call").isEqualTo(1);
            assertThat(streams.size()).as("Effective streams").isEqualTo(0);

            // Expire items in the cache.
            Thread.sleep(100);

            // Make sure we get one now
            streams = effectiveStreamCache.get(
                    new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            assertThat(mockStore.getCallCount()).as("Database call").isEqualTo(2);
            assertThat(streams.size()).as("Effective streams").isEqualTo(1);

            // Add a stream.
            mockStore.addEffectiveStream(
                    refFeedName,
                    DateUtil.parseNormalDateTimeString("2010-01-01T13:00:00.000Z"));

            // Make sure we still get one now
            streams = effectiveStreamCache.get(
                    new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            assertThat(mockStore.getCallCount()).as("Database call").isEqualTo(2);
            assertThat(streams.size()).as("Effective streams").isEqualTo(1);

            // Expire items in the cache.
            Thread.sleep(100);

            // Make sure we get two now
            streams = effectiveStreamCache.get(
                    new EffectiveStreamKey(refFeedName, StreamTypeNames.REFERENCE, fromMs, toMs));
            assertThat(mockStore.getCallCount()).as("Database call").isEqualTo(3);
            assertThat(streams.size()).as("Effective streams").isEqualTo(2);
        }
    }

    @Test
    void testDuplicateStreams() {

        final List<EffectiveMeta> effectiveMetaList = List.of(
                createEffectiveMeta(1L, 1000L),
                createEffectiveMeta(11L, 1000L), // dup
                createEffectiveMeta(2L, 1100L),
                createEffectiveMeta(22L, 1100L), // dup
                createEffectiveMeta(222L, 1100L), // dup
                createEffectiveMeta(3L, 1200L),
                createEffectiveMeta(4L, 1300L),
                createEffectiveMeta(5L, 1400L)
        );

        final MetaService mockMetaService = Mockito.mock(MetaService.class);
        Mockito.when(mockMetaService.findEffectiveData(Mockito.any()))
                .thenReturn(new HashSet<>(effectiveMetaList));

        try (CacheManager cacheManager = new CacheManagerImpl()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(
                    cacheManager,
                    mockMetaService,
                    new EffectiveStreamInternPool(),
                    new MockSecurityContext(),
                    ReferenceDataConfig::new);

            final NavigableSet<EffectiveStream> effectiveStreams = effectiveStreamCache.get(new EffectiveStreamKey(
                    FEED_NAME,
                    TYPE_NAME,
                    0,
                    1_000_000));

            Assertions.assertThat(effectiveStreams)
                    .hasSize(5); // 5 non-dups

            final List<Long> streamIds = effectiveStreams.stream()
                    .map(EffectiveStream::getStreamId)
                    .collect(Collectors.toList());

            // The latest stream Id from each dup set is used
            Assertions.assertThat(streamIds)
                    .containsExactly(11L, 222L, 3L, 4L, 5L);
        }
    }

    private long getFromMs(final long time) {
        return (time / ReferenceData.APPROX_TEN_DAYS) * ReferenceData.APPROX_TEN_DAYS;
    }

    private long getToMs(final long fromMs) {
        return fromMs + ReferenceData.APPROX_TEN_DAYS;
    }

    private EffectiveMeta createEffectiveMeta(final long streamId,
                                              final long effectiveTimeMs) {
        return new EffectiveMeta(streamId, FEED_NAME, TYPE_NAME, effectiveTimeMs);
    }

    private static class InnerStreamMetaService extends MockMetaService {

        private final List<Meta> streams = new ArrayList<>();
        private long callCount = 0;

        InnerStreamMetaService() {
            super();
        }

        @Override
        public Set<EffectiveMeta> findEffectiveData(final EffectiveMetaDataCriteria criteria) {
            callCount++;

            return streams.stream()
                    .filter(stream ->
                                    NullSafe.test(
                                            stream.getEffectiveMs(),
                                            effMs ->
                                                    effMs >= criteria.getEffectivePeriod().getFromMs()
                                            && effMs <= criteria.getEffectivePeriod().getToMs()))
                    .map(EffectiveMeta::new)
                    .collect(Collectors.toSet());
        }

        void addEffectiveStream(final String feedName, long effectiveTimeMs) {
            final Meta meta = create(
                    MetaProperties.builder()
                            .feedName(feedName)
                            .typeName(StreamTypeNames.REFERENCE)
                            .createMs(effectiveTimeMs)
                            .build());
            streams.add(meta);
        }

        long getCallCount() {
            return callCount;
        }
    }
}
