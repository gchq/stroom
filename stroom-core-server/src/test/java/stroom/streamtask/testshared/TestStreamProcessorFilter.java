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

package stroom.streamtask.testshared;

import org.apache.commons.lang.math.RandomUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.streamtask.shared.ProcessorFilter;
import stroom.streamtask.shared.ProcessorFilterTracker;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStreamProcessorFilter extends StroomUnitTest {
    @Test
    public void testCompare() {
        final ProcessorFilter t1 = new ProcessorFilter();
        t1.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
        t1.getStreamProcessorFilterTracker().setMinStreamId(1L);
        t1.setPriority(1);

        final ProcessorFilter t2 = new ProcessorFilter();
        t2.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
        t2.getStreamProcessorFilterTracker().setMinStreamId(2L);
        t2.setPriority(1);

        final ProcessorFilter t3 = new ProcessorFilter();
        t3.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
        t3.getStreamProcessorFilterTracker().setMinStreamId(3L);
        t3.setPriority(3);

        Assert.assertTrue(t1.isHigherPriority(t2));
        Assert.assertTrue(t3.isHigherPriority(t2));
        Assert.assertTrue(t3.isHigherPriority(t1));

        final ArrayList<ProcessorFilter> taskList = new ArrayList<>();
        taskList.add(t1);
        taskList.add(t2);
        taskList.add(t3);

        Collections.sort(taskList, ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR);

        Assert.assertTrue(taskList.get(0) == t3);
        Assert.assertTrue(taskList.get(1) == t1);
        Assert.assertTrue(taskList.get(2) == t2);
    }

    @Test
    public void testSort() {
        final LinkedList<ProcessorFilter> newStreamTaskList = new LinkedList<>();
        for (int i = 0; i < 10000; i++) {
            newStreamTaskList.add(createFilter());
        }

        // Sort the new list
        Collections.sort(newStreamTaskList, ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR);
    }

    private ProcessorFilter createFilter() {
        final ProcessorFilter filter = new ProcessorFilter();
        filter.setPriority(RandomUtils.nextInt() % 10);
        filter.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
        filter.getStreamProcessorFilterTracker().setMinStreamId(RandomUtils.nextInt() % 10);
        return filter;
    }

    @Test
    public void testPercent1() {
        final long now = System.currentTimeMillis();

        final ProcessorFilterTracker filter = new ProcessorFilterTracker();
        filter.setStreamCreateMs(1414075939113L);
        filter.setMinStreamCreateMs(1414074711896L);
        filter.setMaxStreamCreateMs(1414075927731L);

        Assert.assertEquals(100, filter.getTrackerStreamCreatePercentage(now).intValue());
    }

    @Test
    public void testPercent2() {
        // Simple example of 50% done

        final long now = System.currentTimeMillis();

        final ProcessorFilterTracker filter = new ProcessorFilterTracker();
        filter.setMinStreamCreateMs(0L);
        filter.setMaxStreamCreateMs(1000L);
        filter.setStreamCreateMs(500L);

        Assert.assertEquals(50, filter.getTrackerStreamCreatePercentage(now).intValue());
    }

    @Test
    public void testPercentNullHandling() {
        // Simple example of unknown done

        final long now = System.currentTimeMillis();

        final ProcessorFilterTracker filter = new ProcessorFilterTracker();

        Assert.assertNull(filter.getTrackerStreamCreatePercentage(now));

        filter.setMinStreamCreateMs(0L);
        Assert.assertNull(filter.getTrackerStreamCreatePercentage(now));

        filter.setStreamCreateMs(100L);
        Assert.assertNotNull(filter.getTrackerStreamCreatePercentage(now));

        filter.setMaxStreamCreateMs(100L);
        Assert.assertNotNull(filter.getTrackerStreamCreatePercentage(now));
    }

    @Test
    public void testPercent50PercentDone() {
        // Simple example of unknown done

        final long now = System.currentTimeMillis();
        final long oneDayMs = 1000 * 60 * 60 * 24;

        final ProcessorFilterTracker filter = new ProcessorFilterTracker();

        Assert.assertNull(filter.getTrackerStreamCreatePercentage(now));

        filter.setMinStreamCreateMs(now - (10 * oneDayMs));
        filter.setStreamCreateMs(now - (5 * oneDayMs));

        // Stream Max derived to be today
        Assert.assertEquals(50, filter.getTrackerStreamCreatePercentage(now).intValue());

        filter.setMaxStreamCreateMs(now);
        Assert.assertEquals(50, filter.getTrackerStreamCreatePercentage(now).intValue());

    }
}
