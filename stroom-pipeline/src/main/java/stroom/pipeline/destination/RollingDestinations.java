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

package stroom.pipeline.destination;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.jobsystem.server.JobTrackedSchedule;
import stroom.node.server.StroomPropertyService;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.errorhandler.TerminatedException;
import stroom.util.spring.StroomFrequencySchedule;
import stroom.util.spring.StroomShutdown;
import stroom.util.task.TaskMonitor;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Inject;

@Component
public class RollingDestinations {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingDestinations.class);

    private static final int DEFAULT_MAX_ACTIVE_DESTINATIONS = 100;
    private static final int MAX_TRY_COUNT = 1000;

    private static final ConcurrentHashMap<Object, RollingDestination> currentDestinations = new ConcurrentHashMap<>();
    private static final ReentrantLock destCreationLock = new ReentrantLock();

    private final StroomPropertyService stroomPropertyService;

    @Inject
    public RollingDestinations(final StroomPropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    public RollingDestination borrow(final TaskMonitor taskMonitor, final Object key,
                                     final RollingDestinationFactory destinationFactory) throws IOException {
        if (taskMonitor != null && taskMonitor.isTerminated()) {
            throw new TerminatedException();
        }

        // Get a destination for this key. Try and get an existing one or create
        // a new one if required.
        RollingDestination destination = null;

        // Try a number of times to get a destination.
        for (int i = 0; destination == null && i < MAX_TRY_COUNT; i++) {
            destination = getDestination(key, destinationFactory);
        }

        return destination;
    }

    private RollingDestination getDestination(final Object key,
                                              final RollingDestinationFactory destinationFactory) throws IOException {
        RollingDestination destination;

        // Try and get an existing destination for the key.
        destination = currentDestinations.get(key);

        if (destination != null) {
            destination = lockAndRoll(key, destination);
        }

        if (destination == null) {
            destCreationLock.lock();
            try {
                // Try and get an existing destination again under lock.
                destination = currentDestinations.get(key);
                if (destination == null) {
                    final int maxActiveDestinations = getMaxActiveDestinations();

                    // Try and cope with too many active destinations.
                    if (currentDestinations.size() > maxActiveDestinations) {
                        // If the size is still too big then error.
                        if (currentDestinations.size() > maxActiveDestinations) {
                            throw new ProcessException("Too many active destinations: " + currentDestinations.size());
                        }
                    }

                    // Create a new destination.
                    destination = destinationFactory.createDestination();

                    currentDestinations.put(key, destination);
                }

                // Try and roll the destination as there are some cases where a destination needs to be rolled
                // immediately after creation.
                destination = lockAndRoll(key, destination);

            } finally {
                destCreationLock.unlock();
            }
        }

        return destination;
    }

    /**
     * Try and lock this destination so that the current thread has exclusive access. Once locked we will attempt to
     * roll this destination if it needs rolling.
     *
     * If it is rolled then this method will return null as this destination can no longer be used, if the destination
     * does not need rolling yet then it will be returned for use.
     *
     * @param key The key that this destination is associated with.
     * @param destination The destination to lock and attempt to roll.
     * @return The destination if it didn't need rolling and can be used, null otherwise. The returned destination is locked for exclusive use by the current thread.
     * @throws IOException Could be thrown while attempting to flush or close the destination on roll.
     */
    private RollingDestination lockAndRoll(final Object key, final RollingDestination destination) throws IOException {
        RollingDestination dest = destination;

        // Lock the destination so only the current thread can use it.
        dest.lock();

        boolean rolled = true;
        try {
            // Try and roll the destination.
            rolled = dest.tryFlushAndRoll(false, System.currentTimeMillis());

        } finally {
            // If we rolled the destination then remove it and unlock it.
            if (rolled) {
                removeDestination(key, dest);
                dest.unlock();
                dest = null;
            }
        }

        return dest;
    }

    public void returnDestination(final RollingDestination destination) {
        destination.unlock();
    }

    private void removeDestination(final Object key, final RollingDestination destination) {
        destCreationLock.lock();
        try {
            // Only remove the destination if it is the same one that is already in the map.
            if (destination == currentDestinations.get(key)) {
                currentDestinations.remove(key);
            }
        } finally {
            destCreationLock.unlock();
        }
    }

    @StroomFrequencySchedule("1m")
    @JobTrackedSchedule(jobName = "Pipeline Destination Roll", description = "Roll any destinations based on their roll settings")
    public void roll() {
        rollAll(false);
    }

    @StroomShutdown
    public void forceRoll() {
        rollAll(true);
    }

    private void rollAll(final boolean force) {
        LOGGER.debug("rollAll()");

        final long currentTime = System.currentTimeMillis();
        for (final Entry<Object, RollingDestination> entry : currentDestinations.entrySet()) {
            final Object key = entry.getKey();
            final RollingDestination destination = entry.getValue();

            // Try and lock this destination as we can't flush or roll it if
            // another thread has the lock.
            boolean rolled = false;
            if (destination.tryLock()) {
                try {
                    try {
                        rolled = destination.tryFlushAndRoll(force, currentTime);
                    } catch (final Exception e) {
                        rolled = true;
                        LOGGER.error(e.getMessage(), e);
                    }

                    if (rolled) {
                        removeDestination(key, destination);
                    }

                } finally {
                    destination.unlock();
                }
            }

            if (force && !rolled) {
                destination.lock();
                try {
                    try {
                        rolled = destination.tryFlushAndRoll(force, currentTime);
                    } catch (final Exception e) {
                        rolled = true;
                        LOGGER.error(e.getMessage(), e);
                    }

                    if (rolled) {
                        removeDestination(key, destination);
                    }

                } finally {
                    destination.unlock();
                }
            }
        }
    }

    private int getMaxActiveDestinations() {
        int maxActiveDestinations = DEFAULT_MAX_ACTIVE_DESTINATIONS;
        if (stroomPropertyService != null) {
            try {
                final String property = stroomPropertyService.getProperty("stroom.pipeline.appender.maxActiveDestinations");
                if (property != null) {
                    maxActiveDestinations = Integer.parseInt(property);
                }
            } catch (final Exception ex) {
                LOGGER.error(
                        "getMaxActiveDestinations() - Integer.parseInt stroom.pipeline.appender.maxActiveDestinations",
                        ex);
            }
        }
        return maxActiveDestinations;
    }
}
