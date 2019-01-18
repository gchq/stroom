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

package stroom.lifecycle;

import stroom.util.lifecycle.LifecycleAware;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;

class LifecycleTasks {
    private final Deque<LifecycleAware> startPendingBeans;
    private final Deque<LifecycleAware> stopPendingBeans;

    @Inject
    LifecycleTasks(final Set<LifecycleAware> lifecycleAwareSet) {
        final List<LifecycleAware> list = new ArrayList<>(lifecycleAwareSet);
        list.sort(Comparator
                .comparing(LifecycleAware::priority)
                .reversed()
                .thenComparing(lifecycleAware -> lifecycleAware.getClass().getName()));

        startPendingBeans = new ConcurrentLinkedDeque<>(list);
        stopPendingBeans = new ConcurrentLinkedDeque<>(list);
    }

    /**
     * @return things that need running at start up of null if they have all
     * started
     */
    LifecycleTask getStart() {
        LifecycleTask lifecycleTask = null;

        final LifecycleAware lifecycleAware = startPendingBeans.pollFirst();
        if (lifecycleAware != null) {
            lifecycleTask = new LifecycleTask(lifecycleAware.getClass().getSimpleName() + " - start",
                    task -> lifecycleAware.start(),
                    new AtomicBoolean());
        }

        return lifecycleTask;
    }

    /**
     * @return things that need running at start up of null if they have all
     * started
     */
    LifecycleTask getStop() {
        LifecycleTask lifecycleTask = null;

        final LifecycleAware lifecycleAware = stopPendingBeans.pollLast();
        if (lifecycleAware != null) {
            lifecycleTask = new LifecycleTask(lifecycleAware.getClass().getSimpleName() + " - stop",
                    task -> lifecycleAware.stop(),
                    new AtomicBoolean());
        }

        return lifecycleTask;
    }
}
