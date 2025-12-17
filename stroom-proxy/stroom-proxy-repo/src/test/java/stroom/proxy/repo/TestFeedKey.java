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

package stroom.proxy.repo;

import stroom.proxy.repo.FeedKey.FeedKeyInterner;
import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestFeedKey {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestFeedKey.class);

    @Test
    void testIntern_null() {
        final FeedKeyInterner interner = FeedKey.createInterner();

        assertThat(interner.intern(null))
                .isNull();
    }

    @Test
    void testIntern_1() {
        final FeedKeyInterner interner = FeedKey.createInterner();

        final FeedKey feedKey1a = new FeedKey("feed1", "type1");
        final FeedKey feedKey1b = new FeedKey("feed1", "type1");
        final FeedKey feedKey2 = new FeedKey("feed2", "type1");
        final FeedKey feedKey3 = new FeedKey("feed1", "type2");
        final FeedKey feedKey4 = new FeedKey("feed2", "type2");

        assertThat(feedKey1a)
                .isEqualTo(feedKey1b)
                .isNotEqualTo(feedKey2)
                .isNotEqualTo(feedKey3)
                .isNotEqualTo(feedKey4);

        assertThat(feedKey1a.hashCode())
                .isEqualTo(feedKey1a.hashCode())
                .isEqualTo(feedKey1b.hashCode())
                .isNotEqualTo(feedKey2.hashCode())
                .isNotEqualTo(feedKey3.hashCode())
                .isNotEqualTo(feedKey4.hashCode());

        assertThat(feedKey2)
                .isNotEqualTo(feedKey3)
                .isNotEqualTo(feedKey4);

        assertThat(feedKey3)
                .isNotEqualTo(feedKey4);

        assertThat(interner.intern(feedKey1a))
                .isEqualTo(feedKey1a)
                .isSameAs(feedKey1a);
        assertThat(interner.intern(feedKey1b))
                .isEqualTo(feedKey1a)
                .isEqualTo(feedKey1b)
                .isSameAs(feedKey1a);

        assertThat(interner.intern("feed1", "type1"))
                .isEqualTo(feedKey1a)
                .isEqualTo(feedKey1b)
                .isSameAs(feedKey1a);
    }

    @Test
    void testIntern3() {
        final FeedKeyInterner interner = FeedKey.createInterner();
        final FeedKey feedKeyNulla = interner.intern(null, null);
        final FeedKey feedKeyNullb = interner.intern(null, null);
        final FeedKey feedKey11a = interner.intern("feed1", "type1");
        final FeedKey feedKey11b = interner.intern("feed1", "type1");
        final FeedKey feedKey12a = interner.intern("feed1", "type2");
        final FeedKey feedKey12b = interner.intern("feed1", "type2");
        final FeedKey feedKey21a = interner.intern("feed2", "type1");
        final FeedKey feedKey21b = interner.intern("feed2", "type1");
        final FeedKey feedKey22a = interner.intern("feed2", "type2");
        final FeedKey feedKey22b = interner.intern("feed2", "type2");

        assertThat(feedKeyNulla)
                .isSameAs(feedKeyNullb)
                .isNotEqualTo(feedKey11a)
                .isNotEqualTo(feedKey12a)
                .isNotEqualTo(feedKey21a)
                .isNotEqualTo(feedKey22a);
        assertThat(feedKey11a)
                .isSameAs(feedKey11b)
                .isNotEqualTo(feedKey12a)
                .isNotEqualTo(feedKey21a)
                .isNotEqualTo(feedKey22a);
        assertThat(feedKey12a)
                .isSameAs(feedKey12b)
                .isNotEqualTo(feedKey11a)
                .isNotEqualTo(feedKey21a)
                .isNotEqualTo(feedKey22a);
        assertThat(feedKey21a)
                .isSameAs(feedKey21b)
                .isNotEqualTo(feedKey11a)
                .isNotEqualTo(feedKey12a)
                .isNotEqualTo(feedKey22a);
        assertThat(feedKey22a)
                .isSameAs(feedKey22b)
                .isNotEqualTo(feedKey11a)
                .isNotEqualTo(feedKey12a)
                .isNotEqualTo(feedKey21a);
    }

    @Disabled // Manual perf only
    @Test
    void testInternPerf() {

        final FeedKeyInterner feedKeyInterner = FeedKey.createInterner();
        for (int i = 1; i <= 2; i++) {
            for (int j = 1; j <= 2; j++) {
                feedKeyInterner.intern("feed" + i, "type" + j);
            }
        }
        final int iter = 1_000_000;
        final List<FeedKey> feedKeys = new ArrayList<>(iter * 4);

        TestUtil.comparePerformance(
                5,
                iter,
                (rounds, iterations1) -> {
                    feedKeys.clear();
                },
                LOGGER::info,
                TimedCase.of("intern", (round, iterations) -> {
                    for (long k = 0; k < iterations; k++) {
                        for (int i = 1; i <= 2; i++) {
                            for (int j = 1; j <= 2; j++) {
                                feedKeys.add(feedKeyInterner.intern("feed" + i, "type" + j));
                            }
                        }
                    }
                }),
                TimedCase.of("Obj Creation", (round, iterations) -> {
                    for (long k = 0; k < iterations; k++) {
                        for (int i = 1; i <= 2; i++) {
                            for (int j = 1; j <= 2; j++) {
                                feedKeys.add(new FeedKey("feed" + i, "type" + j));
                            }
                        }
                    }
                }));
    }
}
