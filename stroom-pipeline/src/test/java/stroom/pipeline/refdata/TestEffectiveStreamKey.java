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

import stroom.data.shared.StreamTypeNames;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

class TestEffectiveStreamKey {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestEffectiveStreamKey.class);

    private static final String FEED_NAME = "MY_FEED";
    private static final String STREAM_TYPE = StreamTypeNames.REFERENCE;
    private static final Instant TIME = LocalDateTime.of(2020, 6, 1, 11, 0)
            .toInstant(ZoneOffset.UTC);

    @Test
    void testForLookupTime() {
        final EffectiveStreamKey key1 = buildKey(TIME);
    }

    @Test
    void testEquals_true() {

        final Instant time1 = TIME;
        // Should be in the same bucket
        final Instant time2 = TIME.plus(1, ChronoUnit.HOURS);

        final EffectiveStreamKey key1 = buildKey(time1);
        final EffectiveStreamKey key2 = buildKey(time2);

        Assertions.assertThat(key1)
                .isEqualTo(key2);

        Assertions.assertThat(key1.hashCode())
                .isEqualTo(key2.hashCode());
    }

    @Test
    void testEquals_false() {

        final Instant time1 = TIME;
        // Should be in a different bucket
        final Instant time2 = TIME.plus(100, ChronoUnit.DAYS);

        final EffectiveStreamKey key1 = buildKey(time1);
        final EffectiveStreamKey key2 = buildKey(time2);

        Assertions.assertThat(key1)
                .isNotEqualTo(key2);
    }

    @Test
    void testNextKey() {

        final Instant time1 = TIME;
        final EffectiveStreamKey key1 = buildKey(time1);
        final EffectiveStreamKey key2 = key1.nextKey();

        Assertions.assertThat(key2.getFromMs())
                .isEqualTo(key1.getToMs());
    }

    @Test
    void testPreviousKey() {

        final Instant time2 = TIME;
        final EffectiveStreamKey key2 = buildKey(time2);
        final EffectiveStreamKey key1 = key2.previousKey();

        Assertions.assertThat(key2.getFromMs())
                .isEqualTo(key1.getToMs());
    }

    @Test
    void testOnUpperBound() {

        final Instant time1 = TIME;
        final EffectiveStreamKey key1 = buildKey(time1);

        // Upper bound is exclusive so this time should fall into next bucket
        final EffectiveStreamKey key2a = buildKey(Instant.ofEpochMilli(key1.getToMs()));

        Assertions.assertThat(key1)
                .isNotEqualTo(key2a);

        final EffectiveStreamKey key2b = key1.nextKey();
        Assertions.assertThat(key2a)
                .isEqualTo(key2b);
    }

    @Test
    void testOnLowerBound() {

        final Instant time1 = TIME;
        final EffectiveStreamKey key1 = buildKey(time1);

        // Lower bound is inclusive so this time should fall into the same
        final EffectiveStreamKey key2 = buildKey(Instant.ofEpochMilli(key1.getFromMs()));

        Assertions.assertThat(key1)
                .isEqualTo(key2);
    }

    @Test
    void testBelowLowerBound() {

        final Instant time1 = TIME;
        final EffectiveStreamKey key1 = buildKey(time1);

        // Lower bound is inclusive so this time should fall into previous bucket
        final EffectiveStreamKey key2a = buildKey(Instant.ofEpochMilli(key1.getFromMs() - 1));

        Assertions.assertThat(key1)
                .isNotEqualTo(key2a);

        final EffectiveStreamKey key2b = key1.previousKey();
        Assertions.assertThat(key2a)
                .isEqualTo(key2b);
    }

    private void checkBounds(final Instant time, final EffectiveStreamKey key) {
        LOGGER.debug("time: {}, from(inc): {}, to(exc): {}",
                time, Instant.ofEpochMilli(key.getFromMs()), Instant.ofEpochMilli(key.getToMs()));

        Assertions.assertThat(time)
                .isAfterOrEqualTo(Instant.ofEpochMilli(key.getFromMs()));
        Assertions.assertThat(time)
                .isBefore(Instant.ofEpochMilli(key.getToMs()));
    }

    private EffectiveStreamKey buildKey(final Instant time) {
        final EffectiveStreamKey key = EffectiveStreamKey.forLookupTime(FEED_NAME, STREAM_TYPE, time.toEpochMilli());
        checkBounds(time, key);
        return key;
    }
}
