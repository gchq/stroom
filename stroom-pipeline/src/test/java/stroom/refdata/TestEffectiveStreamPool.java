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

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestEffectiveStreamPool extends StroomUnitTest {
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
                    workingDate = Instant.ofEpochMilli(workingDate).atZone(ZoneOffset.UTC).plusDays(1).toInstant().toEpochMilli();
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
        } catch (final RuntimeException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
