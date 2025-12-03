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

package stroom.pipeline.refdata;


import stroom.cache.api.CacheManager;
import stroom.cache.impl.CacheManagerImpl;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.api.EffectiveMeta;
import stroom.meta.api.EffectiveMetaDataCriteria;
import stroom.meta.api.EffectiveMetaSet;
import stroom.meta.api.EffectiveMetaSet.Builder;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.MetaService;
import stroom.meta.mock.MockMetaService;
import stroom.meta.shared.Meta;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.cache.CacheConfig;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class TestEffectiveStreamCache extends StroomUnitTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestEffectiveStreamCache.class);

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
            public EffectiveMetaSet findEffectiveData(final EffectiveMetaDataCriteria criteria) {
                findEffectiveStreamSourceCount++;
                final String type = StreamTypeNames.REFERENCE;
                final Builder builder = EffectiveMetaSet.builder(refFeedName, type);

                long workingDate = criteria.getEffectivePeriod().getFrom();
                while (workingDate < criteria.getEffectivePeriod().getTo()) {
                    final Meta meta = create(
                            MetaProperties.builder()
                                    .feedName(refFeedName)
                                    .typeName(type)
                                    .effectiveMs(workingDate)
                                    .build());

                    builder.add(meta.getId(), meta.getEffectiveMs());

                    workingDate = Instant.ofEpochMilli(workingDate)
                            .atZone(ZoneOffset.UTC)
                            .plusDays(1)
                            .toInstant()
                            .toEpochMilli();
                }
                return builder.build();
            }
        };

        try (final CacheManager cacheManager = new CacheManagerImpl()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager,
                    metaService,
                    new EffectiveStreamInternPool(),
                    new MockSecurityContext(),
                    ReferenceDataConfig::new);

            assertThat(effectiveStreamCache.size()).as("No pooled times yet").isEqualTo(0);
            assertThat(findEffectiveStreamSourceCount).as("No calls to the database yet").isEqualTo(0);

            long time = DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z");
            effectiveStreamCache
                    .get(EffectiveStreamKey.forLookupTime(refFeedName, StreamTypeNames.REFERENCE, time));
            assertThat(findEffectiveStreamSourceCount).as("Database call").isEqualTo(1);

            // Still in window
            time = DateUtil.parseNormalDateTimeString("2010-01-01T13:00:00.000Z");
            effectiveStreamCache
                    .get(EffectiveStreamKey.forLookupTime(refFeedName, StreamTypeNames.REFERENCE, time));
            assertThat(findEffectiveStreamSourceCount).as("Database call").isEqualTo(1);

            // After window ...
            time = DateUtil.parseNormalDateTimeString("2010-01-15T13:00:00.000Z");
            effectiveStreamCache
                    .get(EffectiveStreamKey.forLookupTime(refFeedName, StreamTypeNames.REFERENCE, time));
            assertThat(findEffectiveStreamSourceCount).as("Database call").isEqualTo(2);

            // Before window ...
            time = DateUtil.parseNormalDateTimeString("2009-12-15T13:00:00.000Z");
            effectiveStreamCache
                    .get(EffectiveStreamKey.forLookupTime(refFeedName, StreamTypeNames.REFERENCE, time));
            assertThat(findEffectiveStreamSourceCount).as("Database call").isEqualTo(3);
        } catch (final RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Test
    void testExpiry() throws InterruptedException {
        final String refFeedName = "TEST_REF";

        final InnerStreamMetaService mockStore = new InnerStreamMetaService();

        try (final CacheManager cacheManager = new CacheManagerImpl()) {
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

            final long time = DateUtil.parseNormalDateTimeString("2010-01-01T12:00:00.000Z");
            final long fromMs = getFromMs(time);
            final long toMs = getToMs(fromMs);
            EffectiveMetaSet streams;

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

        final EffectiveMetaSet effectiveMetaSet = EffectiveMetaSet.builder(FEED_NAME, TYPE_NAME)
                .add(1L, 1000L)
                .add(11L, 1000L) // dup
                .add(2L, 1100L)
                .add(22L, 1100L) // dup
                .add(222L, 1100L) // dup
                .add(3L, 1200L)
                .add(4L, 1300L)
                .add(5L, 1400L)
                .build();

        final MetaService mockMetaService = Mockito.mock(MetaService.class);
        Mockito.when(mockMetaService.findEffectiveData(Mockito.any()))
                .thenReturn(effectiveMetaSet);

        try (final CacheManager cacheManager = new CacheManagerImpl()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(
                    cacheManager,
                    mockMetaService,
                    new EffectiveStreamInternPool(),
                    new MockSecurityContext(),
                    ReferenceDataConfig::new);

            final EffectiveMetaSet effectiveStreams = effectiveStreamCache.get(new EffectiveStreamKey(
                    FEED_NAME,
                    TYPE_NAME,
                    0,
                    1_000_000));

            assertThat(effectiveStreams.size())
                    .isEqualTo(5); // 5 non-dups

            final List<Long> streamIds = effectiveStreams.stream()
                    .map(EffectiveMeta::getId)
                    .collect(Collectors.toList());

            // The latest stream Id from each dup set is used
            assertThat(streamIds)
                    .containsExactly(11L, 222L, 3L, 4L, 5L);
        }
    }

    @Test
    void testSystemInfo() throws JsonProcessingException {
        final MetaService mockMetaService = Mockito.mock(MetaService.class);

        try (final CacheManager cacheManager = new CacheManagerImpl()) {
            final EffectiveStreamCache effectiveStreamCache = new EffectiveStreamCache(cacheManager,
                    mockMetaService,
                    new EffectiveStreamInternPool(),
                    new MockSecurityContext(),
                    ReferenceDataConfig::new);

            for (int i = 1; i <= 3; i++) {
                final String feedName = FEED_NAME + i;
                int id = i * 10;
                Mockito.when(mockMetaService.findEffectiveData(Mockito.any()))
                        .thenReturn(EffectiveMetaSet.builder(feedName, TYPE_NAME)
                                .add(id++, i * 1000 + 1L)
                                .add(id++, i * 1000 + 2L)
                                .add(id++, i * 1000 + 3L)
                                .add(id++, i * 1000 + 4L)
                                .add(id++, i * 1000 + 5L)
                                .add(id++, i * 1000 + 5L) // 3 with same time
                                .add(id++, i * 1000 + 5L)
                                .build());
                effectiveStreamCache.get(
                        new EffectiveStreamKey(feedName, TYPE_NAME, 0, 10_000));
            }

            assertThat(effectiveStreamCache.size())
                    .isEqualTo(3);

            final SystemInfoResult systemInfo = effectiveStreamCache.getSystemInfo();
            final ObjectMapper objectMapper = new ObjectMapper();
            final String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(systemInfo);
            LOGGER.debug("systemInfo:\n{}", json);
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

    private EffectiveMeta createEffectiveMeta(final long streamId,
                                              final String feedName,
                                              final long effectiveTimeMs) {
        return new EffectiveMeta(streamId, feedName, TYPE_NAME, effectiveTimeMs);
    }


    // --------------------------------------------------------------------------------


    private static class InnerStreamMetaService extends MockMetaService {

        private final List<Meta> streams = new ArrayList<>();
        private long callCount = 0;

        InnerStreamMetaService() {
            super();
        }

        @Override
        public EffectiveMetaSet findEffectiveData(final EffectiveMetaDataCriteria criteria) {
            callCount++;

            return streams.stream()
                    .filter(stream ->
                            NullSafe.test(
                                    stream.getEffectiveMs(),
                                    effMs ->
                                            effMs >= criteria.getEffectivePeriod().getFromMs()
                                            && effMs <= criteria.getEffectivePeriod().getToMs()))
                    .map(EffectiveMeta::new)
                    .collect(EffectiveMetaSet.collector(criteria.getFeed(), criteria.getType()));
        }

        void addEffectiveStream(final String feedName, final long effectiveTimeMs) {
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
