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

package stroom.search.server;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import stroom.streamtask.server.InclusiveRanges;
import stroom.streamtask.server.InclusiveRanges.InclusiveRange;
import stroom.util.test.StroomUnitTest;

public class TestEventRanges extends StroomUnitTest {
    @Test
    public void bigTest() {
        for (int i = 0; i < 100; i++) {
            test();
        }
    }

    @Test
    public void test() {
        final InclusiveRanges ref = new InclusiveRanges();

        final List<Long> segs = new ArrayList<Long>();
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
    public void test2() {
        final InclusiveRanges ref = new InclusiveRanges();
        ref.addEvent(1);
        ref.addEvent(2);
        ref.addEvent(3);

        check(ref);
    }

    @Test
    public void test3() {
        final InclusiveRanges ref = new InclusiveRanges();
        ref.addEvent(1);
        ref.addEvent(3);
        ref.addEvent(2);

        check(ref);
    }

    @Test
    public void test4() {
        final InclusiveRanges ref = new InclusiveRanges();
        ref.addEvent(3);
        ref.addEvent(2);
        ref.addEvent(1);

        check(ref);
    }

    @Test
    public void test5() {
        final InclusiveRanges ref = new InclusiveRanges();
        ref.addEvent(3);
        ref.addEvent(2);
        ref.addEvent(3);

        check(ref);
    }

    @Test
    public void test6() {
        final InclusiveRanges ref = new InclusiveRanges();
        ref.addEvent(10);
        ref.addEvent(5);
        ref.addEvent(1);
        ref.addEvent(3);
        ref.addEvent(7);

        Assert.assertEquals(5, ref.getRanges().size());
        check(ref);

        addRange(ref, 8L, 9L);

        Assert.assertEquals(4, ref.getRanges().size());
        check(ref);

        addRange(ref, 2L, 2L);

        Assert.assertEquals(3, ref.getRanges().size());
        check(ref);

        addRange(ref, 4L, 4L);

        Assert.assertEquals(2, ref.getRanges().size());
        check(ref);

        addRange(ref, 20L, 25L);

        Assert.assertEquals(3, ref.getRanges().size());
        check(ref);

        addRange(ref, 18L, 30L);

        Assert.assertEquals(3, ref.getRanges().size());
        check(ref);

        addRange(ref, 20L, 35L);

        Assert.assertEquals(3, ref.getRanges().size());
        check(ref);

        addRange(ref, 15L, 20L);

        Assert.assertEquals(3, ref.getRanges().size());
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
            Assert.assertTrue(range.getMin() <= range.getMax());

            if (lastRange != null) {
                Assert.assertTrue(lastRange.getMin() < range.getMin() - 1);
                Assert.assertTrue(lastRange.getMax() < range.getMin() - 1);
            }

            lastRange = range;
        }

        final String str = ref.rangesToString();
        System.out.println(str);
        Assert.assertTrue(str.length() > 0);
        final List<InclusiveRange> converted = InclusiveRanges.rangesFromString(str);
        Assert.assertEquals(ranges, converted);
    }
}
