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

package stroom.processor.api;


import stroom.processor.api.InclusiveRanges.InclusiveRange;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestEventRanges {

    @Test
    void bigTest() {
        for (int i = 0; i < 100; i++) {
            test();
        }
    }

    @Test
    void test() {
        final InclusiveRanges ref = new InclusiveRanges();

        final List<Long> segs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            segs.add(Long.valueOf(i));
        }

        for (int j = 0; j < 20; j++) {
            final int index = (int) (Math.random() * segs.size());
            final Long seg = segs.remove(index);
            ref.addEvent(seg);
        }

        check(ref);
    }

    @Test
    void test2() {
        final InclusiveRanges ref = new InclusiveRanges();
        ref.addEvent(1);
        ref.addEvent(2);
        ref.addEvent(3);

        check(ref);
    }

    @Test
    void test3() {
        final InclusiveRanges ref = new InclusiveRanges();
        ref.addEvent(1);
        ref.addEvent(3);
        ref.addEvent(2);

        check(ref);
    }

    @Test
    void test4() {
        final InclusiveRanges ref = new InclusiveRanges();
        ref.addEvent(3);
        ref.addEvent(2);
        ref.addEvent(1);

        check(ref);
    }

    @Test
    void test5() {
        final InclusiveRanges ref = new InclusiveRanges();
        ref.addEvent(3);
        ref.addEvent(2);
        ref.addEvent(3);

        check(ref);
    }

    @Test
    void test6() {
        final InclusiveRanges ref = new InclusiveRanges();
        ref.addEvent(10);
        ref.addEvent(5);
        ref.addEvent(1);
        ref.addEvent(3);
        ref.addEvent(7);

        assertThat(ref.getRanges().size()).isEqualTo(5);
        check(ref);

        addRange(ref, 8L, 9L);

        assertThat(ref.getRanges().size()).isEqualTo(4);
        check(ref);

        addRange(ref, 2L, 2L);

        assertThat(ref.getRanges().size()).isEqualTo(3);
        check(ref);

        addRange(ref, 4L, 4L);

        assertThat(ref.getRanges().size()).isEqualTo(2);
        check(ref);

        addRange(ref, 20L, 25L);

        assertThat(ref.getRanges().size()).isEqualTo(3);
        check(ref);

        addRange(ref, 18L, 30L);

        assertThat(ref.getRanges().size()).isEqualTo(3);
        check(ref);

        addRange(ref, 20L, 35L);

        assertThat(ref.getRanges().size()).isEqualTo(3);
        check(ref);

        addRange(ref, 15L, 20L);

        assertThat(ref.getRanges().size()).isEqualTo(3);
        check(ref);
    }

    private void addRange(final InclusiveRanges ref, final long min, final long max) {
        for (long l = min; l <= max; l++) {
            ref.addEvent(l);
        }
    }

    private void check(final InclusiveRanges ref) {
        final List<InclusiveRange> ranges = ref.getRanges();
        InclusiveRange lastRange = null;
        for (final InclusiveRange range : ranges) {
            System.out.println(range);
            assertThat(range.getMin() <= range.getMax()).isTrue();

            if (lastRange != null) {
                assertThat(lastRange.getMin() < range.getMin() - 1).isTrue();
                assertThat(lastRange.getMax() < range.getMin() - 1).isTrue();
            }

            lastRange = range;
        }

        final String str = ref.rangesToString();
        System.out.println(str);
        assertThat(str.length() > 0).isTrue();
        final List<InclusiveRange> converted = InclusiveRanges.rangesFromString(str);
        assertThat(converted).isEqualTo(ranges);
    }
}
