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

package stroom.processor.impl;


import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

import static org.assertj.core.api.Assertions.assertThat;


class TestProcessorFilter {

    @Test
    void testCompare() {
        final ProcessorFilter t1 = new ProcessorFilter();
        t1.setProcessorFilterTracker(new ProcessorFilterTracker());
        t1.getProcessorFilterTracker().setMinMetaId(1L);
        t1.setPriority(1);

        final ProcessorFilter t2 = new ProcessorFilter();
        t2.setProcessorFilterTracker(new ProcessorFilterTracker());
        t2.getProcessorFilterTracker().setMinMetaId(2L);
        t2.setPriority(1);
        t2.setMaxProcessingTasks(10);

        final ProcessorFilter t3 = new ProcessorFilter();
        t3.setProcessorFilterTracker(new ProcessorFilterTracker());
        t3.getProcessorFilterTracker().setMinMetaId(3L);
        t3.setPriority(3);
        t3.setMaxProcessingTasks(20);

        assertThat(t1.isHigherPriority(t2)).isTrue();
        assertThat(t3.isHigherPriority(t2)).isTrue();
        assertThat(t3.isHigherPriority(t1)).isTrue();

        assertThat(t1.isProcessingTaskCountBounded()).isFalse();
        assertThat(t2.isProcessingTaskCountBounded()).isTrue();

        final ArrayList<ProcessorFilter> taskList = new ArrayList<>();
        taskList.add(t1);
        taskList.add(t2);
        taskList.add(t3);

        Collections.sort(taskList, ProcessorFilter.HIGHEST_PRIORITY_FIRST_COMPARATOR);

        assertThat(taskList.get(0) == t3).isTrue();
        assertThat(taskList.get(1) == t1).isTrue();
        assertThat(taskList.get(2) == t2).isTrue();
    }

    @Test
    void testSort() {
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
        filter.setProcessorFilterTracker(new ProcessorFilterTracker());
        filter.getProcessorFilterTracker().setMinMetaId(RandomUtils.nextInt() % 10);
        return filter;
    }

//    @Test
//    void testPercent1() {
//        final long now = System.currentTimeMillis();
//
//        final ProcessorFilterTracker filter = new ProcessorFilterTracker();
//        filter.setMetaCreateMs(1414075939113L);
//        filter.setMinMetaCreateMs(1414074711896L);
//        filter.setMaxMetaCreateMs(1414075927731L);
//
//        assertThat(filter.getTrackerStreamCreatePercentage(now).intValue()).isEqualTo(100);
//    }
//
//    @Test
//    void testPercent2() {
//        // Simple example of 50% done
//
//        final long now = System.currentTimeMillis();
//
//        final ProcessorFilterTracker tracker = new ProcessorFilterTracker();
//        tracker.setMinMetaCreateMs(0L);
//        tracker.setMaxMetaCreateMs(1000L);
//        tracker.setMetaCreateMs(500L);
//
//        assertThat(tracker.getTrackerStreamCreatePercentage(now).intValue()).isEqualTo(50);
//    }
//
//    @Test
//    void testPercentNullHandling() {
//        // Simple example of unknown done
//
//        final long now = System.currentTimeMillis();
//
//        final ProcessorFilterTracker filter = new ProcessorFilterTracker();
//
//        assertThat(filter.getTrackerStreamCreatePercentage(now)).isNull();
//
//        filter.setMinMetaCreateMs(0L);
//        assertThat(filter.getTrackerStreamCreatePercentage(now)).isNull();
//
//        filter.setMetaCreateMs(100L);
//        assertThat(filter.getTrackerStreamCreatePercentage(now)).isNotNull();
//
//        filter.setMaxMetaCreateMs(100L);
//        assertThat(filter.getTrackerStreamCreatePercentage(now)).isNotNull();
//    }
//
//    @Test
//    void testPercent50PercentDone() {
//        // Simple example of unknown done
//
//        final long now = System.currentTimeMillis();
//        final long oneDayMs = 1000 * 60 * 60 * 24;
//
//        final ProcessorFilterTracker filter = new ProcessorFilterTracker();
//
//        assertThat(filter.getTrackerStreamCreatePercentage(now)).isNull();
//
//        filter.setMinMetaCreateMs(now - (10 * oneDayMs));
//        filter.setMetaCreateMs(now - (5 * oneDayMs));
//
//        // Stream Max derived to be today
//        assertThat(filter.getTrackerStreamCreatePercentage(now).intValue()).isEqualTo(50);
//
//        filter.setMaxMetaCreateMs(now);
//        assertThat(filter.getTrackerStreamCreatePercentage(now).intValue()).isEqualTo(50);
//    }
}
