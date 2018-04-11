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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.guice.StroomBeanStore;
import stroom.util.lifecycle.MethodReference;
import stroom.util.lifecycle.StroomShutdown;
import stroom.util.lifecycle.StroomStartup;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StroomBeanLifeCycle {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomBeanLifeCycle.class);

    private final StroomBeanStore stroomBeanStore;

    private volatile boolean shuttingDown;
    private volatile boolean initialised = false;

    private volatile List<MethodReference> startPendingBeans = null;
    private volatile List<MethodReference> stopPendingBeans = null;

    @Inject
    StroomBeanLifeCycle(final StroomBeanStore stroomBeanStore) {
        this.stroomBeanStore = stroomBeanStore;
    }

    /**
     * @return things that need running at start up of null if they have all
     * started
     */
    public StroomBeanMethodExecutable getStartExecutable() {
        init();
        // Keep looping until we have returned everything
        while (true) {
            // Don't try and start anymore
            if (shuttingDown) {
                return null;
            }

            // everything running !
            if (startPendingBeans.size() == 0) {
                return null;
            }

            for (final MethodReference methodReference : startPendingBeans) {
                addBeanStarting(methodReference);
                return new StroomBeanMethodExecutable(methodReference, stroomBeanStore, "Starting");
            }
        }
    }

    /**
     * @return things that need running at start up of null if they have all
     * started
     */
    public StroomBeanMethodExecutable getStopExecutable() {
        init();
        // Keep looping until we have returned everything
        while (true) {
            // everything running !
            if (stopPendingBeans.size() == 0) {
                return null;
            }

            for (final MethodReference methodReference : stopPendingBeans) {
                addBeanStopping(methodReference);
                return new StroomBeanMethodExecutable(methodReference, stroomBeanStore, "Stopping");
            }
        }
    }

    private synchronized void addBeanStarting(final MethodReference methodReference) {
        startPendingBeans.remove(methodReference);
    }

    private synchronized void addBeanStopping(final MethodReference methodReference) {
        stopPendingBeans.remove(methodReference);
    }

    private synchronized void init() {
        if (initialised) {
            return;
        }

        startPendingBeans = new ArrayList<>(stroomBeanStore.getAnnotatedMethods(StroomStartup.class));
        Collections.sort(startPendingBeans, new Comparator<MethodReference>() {
            @Override
            public int compare(final MethodReference o1, final MethodReference o2) {
                final StroomStartup stroomStartup1 = o1.getMethod().getAnnotation(StroomStartup.class);
                final StroomStartup stroomStartup2 = o2.getMethod().getAnnotation(StroomStartup.class);

                int compare = Integer.compare(stroomStartup2.priority(), stroomStartup1.priority());
                if (compare != 0) {
                    // We want to reverse sort so that highest priority
                    // startup methods come first.
                    return compare;
                }

                compare = o1.getClazz().getName().compareTo(o2.getClazz().getName());
                if (compare != 0) {
                    return compare;
                }

                return o1.getMethod().getName().compareTo(o2.getMethod().getName());
            }
        });

        stopPendingBeans = new ArrayList<>(stroomBeanStore.getAnnotatedMethods(StroomShutdown.class));
        Collections.sort(stopPendingBeans, new Comparator<MethodReference>() {
            @Override
            public int compare(final MethodReference o1, final MethodReference o2) {
                final StroomShutdown stroomShutdown1 = o1.getMethod().getAnnotation(StroomShutdown.class);
                final StroomShutdown stroomShutdown2 = o2.getMethod().getAnnotation(StroomShutdown.class);

                int compare = Integer.compare(stroomShutdown2.priority(), stroomShutdown1.priority());
                if (compare != 0) {
                    // We want to reverse sort so that highest priority
                    // shutdown methods come first.
                    return compare;
                }

                compare = o1.getClazz().getName().compareTo(o2.getClazz().getName());
                if (compare != 0) {
                    return compare;
                }

                return o1.getMethod().getName().compareTo(o2.getMethod().getName());
            }
        });

        initialised = true;
    }
}
