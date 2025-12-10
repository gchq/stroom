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

import stroom.meta.api.EffectiveMeta;
import stroom.meta.api.EffectiveMetaSet;
import stroom.meta.api.EffectiveMetaSet.Builder;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestEffectiveStreamInternPool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestEffectiveStreamInternPool.class);
    private static final String FEED_1 = "FEED1";
    private static final String REFERENCE_TYPE = "Reference";
    private static final String FEED_2 = "FEED2";

    @Test
    void name() {
        final EffectiveStreamInternPool internPool = new EffectiveStreamInternPool();

        final int iterations = 100;
        final List<EffectiveMetaSet> setList = new ArrayList<>(6);
        final List<EffectiveMetaSet> internedSetList = new ArrayList<>(6);

        for (int i = 1; i <= 6; i++) {
            final Builder setBuilder = EffectiveMetaSet.builder("DUMMY_FEED", "DummyType");
            final int id = i % 2 == 0
                    ? 2
                    : 1;
            for (int timeMs = 1; timeMs <= iterations; timeMs++) {
                setBuilder.add(id, timeMs);
            }
            final EffectiveMetaSet set = setBuilder.build();
            setList.add(set);
            final EffectiveMetaSet internedSet = internPool.intern(set);
            assertThat(internedSet)
                    .isEqualTo(set);
            internedSetList.add(internedSet);
        }

        // 6 added, but 3 for id=1, 3 for id=2, so only 2 interned
        assertThat(internPool.size())
                .isEqualTo(2);
        assertThat(setList.size())
                .isEqualTo(6);
        assertThat(internedSetList.size())
                .isEqualTo(6);
        // Verify the instances interned
        assertThat(System.identityHashCode(setList.get(0)))
                .isEqualTo(System.identityHashCode(internedSetList.get(0)))
                .isEqualTo(System.identityHashCode(internedSetList.get(2)))
                .isEqualTo(System.identityHashCode(internedSetList.get(4)));
        assertThat(System.identityHashCode(setList.get(1)))
                .isEqualTo(System.identityHashCode(internedSetList.get(1)))
                .isEqualTo(System.identityHashCode(internedSetList.get(3)))
                .isEqualTo(System.identityHashCode(internedSetList.get(5)));
    }

    @Test
    void test() {
        final EffectiveMetaSet set1 = EffectiveMetaSet.builder("SET1 FEED-IDENTITY-V1", "Reference")
                .add(56826237, DateUtil.parseNormalDateTimeString("2024-08-06T13:52:10.000Z"))
                .add(56890075, DateUtil.parseNormalDateTimeString("2024-08-07T13:52:16.000Z"))
                .build();

        LOGGER.info("set1:\n{}", set1);

        final EffectiveMetaSet set2 = EffectiveMetaSet.builder("SET2 FEED-COMBINED-V2.0", "Reference")
                .add(56826239, DateUtil.parseNormalDateTimeString("2024-08-06T13:52:10.000Z"))
                .add(56890073, DateUtil.parseNormalDateTimeString("2024-08-07T13:52:16.000Z"))
                .build();

        LOGGER.info("set2:\n{}", set2);
        LOGGER.info("--------------------------------------------------------------------------------");

        final EffectiveStreamInternPool internPool = new EffectiveStreamInternPool();

        final EffectiveMetaSet interned1 = internPool.intern(set1);

        LOGGER.info("interned1:\n{}", interned1);
        compareSets(set1, interned1);
        assertThat(interned1)
                .isSameAs(set1);


        final EffectiveMetaSet interned2 = internPool.intern(set2);
        LOGGER.info("interned2:\n{}", interned2);
        compareSets(set2, interned2);
        assertThat(interned2)
                .isSameAs(set2);
    }

    @Test
    void test2() {
        final EffectiveStreamInternPool internPool = new EffectiveStreamInternPool();

        final EffectiveMetaSet set1a = EffectiveMetaSet.builder(FEED_1, REFERENCE_TYPE)
                .add(101, 100)
                .add(102, 200)
                .build();

        // Same as set1
        final EffectiveMetaSet set1b = EffectiveMetaSet.builder(FEED_1, REFERENCE_TYPE)
                .add(101, 100)
                .add(102, 200)
                .build();

        // One less item than set 1a
        final EffectiveMetaSet set1c = EffectiveMetaSet.builder(FEED_2, REFERENCE_TYPE)
                .add(101, 100)
                .build();

        // One more item than set 1a
        final EffectiveMetaSet set1d = EffectiveMetaSet.builder(FEED_2, REFERENCE_TYPE)
                .add(101, 100)
                .add(102, 200)
                .add(103, 300)
                .build();

        // Same as 1a after de-duping
        final EffectiveMetaSet set1e = EffectiveMetaSet.builder(FEED_2, REFERENCE_TYPE)
                .add(11, 100)
                .add(101, 100)
                .add(12, 200)
                .add(102, 200)
                .build();

        final EffectiveMetaSet set2 = EffectiveMetaSet.builder(FEED_2, REFERENCE_TYPE)
                .add(201, 100)
                .add(201, 200)
                .build();

        final EffectiveMetaSet set3a = EffectiveMetaSet.empty();
        final EffectiveMetaSet set3b = EffectiveMetaSet.builder(FEED_2, REFERENCE_TYPE)
                .build();

        assertThat(internPool.intern(set1a))
                .isSameAs(set1a);
        assertThat(internPool.intern(set1a))
                .isSameAs(set1a);

        // Same as set1a
        assertThat(internPool.intern(set1b))
                .isSameAs(set1a);
        assertThat(internPool.intern(set1b))
                .isSameAs(set1a);

        // Different to all other sets, so gets itself back
        assertThat(internPool.intern(set1c))
                .isSameAs(set1c);
        assertThat(internPool.intern(set1c))
                .isSameAs(set1c);

        // Different to all other sets, so gets itself back
        assertThat(internPool.intern(set1d))
                .isSameAs(set1d);
        assertThat(internPool.intern(set1d))
                .isSameAs(set1d);

        // Same as 1a after de-duping
        assertThat(internPool.intern(set1e))
                .isSameAs(set1a);
        assertThat(internPool.intern(set1e))
                .isSameAs(set1a);

        // Different to all other sets, so gets itself back
        assertThat(internPool.intern(set2))
                .isSameAs(set2);
        assertThat(internPool.intern(set2))
                .isSameAs(set2);


        // Different to all other sets, so gets itself back
        assertThat(internPool.intern(set3a))
                .isSameAs(set3a);
        assertThat(internPool.intern(set3a))
                .isSameAs(set3a);

        // Same as 3a
        assertThat(internPool.intern(set3b))
                .isSameAs(set3a);
        assertThat(internPool.intern(set3b))
                .isSameAs(set3a);
    }

    private void compareSets(final EffectiveMetaSet set1,
                             final EffectiveMetaSet set2) {

        assertThat(set1)
                .isEqualTo(set2);
        set1.stream()
                .forEach(effectiveMeta1 -> {
                    assertThat(set2.contains(effectiveMeta1))
                            .isTrue();
                    final EffectiveMeta effectiveMeta2 = set2.stream()
                            .filter(effMeta -> effMeta.equals(effectiveMeta1))
                            .findAny()
                            .orElseThrow();

                    assertThat(effectiveMeta1.getId())
                            .isEqualTo(effectiveMeta2.getId());
                    assertThat(effectiveMeta1.getFeedName())
                            .isEqualTo(effectiveMeta2.getFeedName());
                    assertThat(effectiveMeta1.getTypeName())
                            .isEqualTo(effectiveMeta2.getTypeName());
                    assertThat(effectiveMeta1.getEffectiveMs())
                            .isEqualTo(effectiveMeta2.getEffectiveMs());
                });
    }
}
