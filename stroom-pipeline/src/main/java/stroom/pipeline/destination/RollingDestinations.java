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

package stroom.pipeline.destination;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.security.api.SecurityContext;
import stroom.task.api.TaskContextFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class RollingDestinations {

    private static final Logger LOGGER = LoggerFactory.getLogger(RollingDestinations.class);

    private static final int MAX_TRY_COUNT = 1000;

    private static final ConcurrentHashMap<Object, RollingDestination> currentDestinations = new ConcurrentHashMap<>();

    private final Provider<AppenderConfig> appenderConfigProvider;
    private final TaskContextFactory taskContextFactory;
    private final SecurityContext securityContext;

    @Inject
    public RollingDestinations(final Provider<AppenderConfig> appenderConfigProvider,
                               final TaskContextFactory taskContextFactory,
                               final SecurityContext securityContext) {
        this.appenderConfigProvider = appenderConfigProvider;
        this.taskContextFactory = taskContextFactory;
        this.securityContext = securityContext;
    }

    public RollingDestination borrow(final Object key,
                                     final RollingDestinationFactory destinationFactory) throws IOException {
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
        // Try and get an existing destination for the key or create one if necessary.
        final RollingDestination destination = currentDestinations.computeIfAbsent(key, k -> {
            try {
                final int maxActiveDestinations = appenderConfigProvider.get().getMaxActiveDestinations();

                // Try and cope with too many active destinations.
                if (currentDestinations.size() > maxActiveDestinations) {
                    // If the size is still too big then error.
                    throw ProcessException.create("Too many active destinations: " + currentDestinations.size());
                }

                // Create a new destination.
                return destinationFactory.createDestination();

            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Try and roll the destination as there are some cases where a destination needs to be rolled
        // immediately after creation.
        return lockAndRoll(key, destination);
    }

    /**
     * Try and lock this destination so that the current thread has exclusive access. Once locked we will attempt to
     * roll this destination if it needs rolling.
     * <p>
     * If it is rolled then this method will return null as this destination can no longer be used, if the destination
     * does not need rolling yet then it will be returned for use.
     *
     * @param key         The key that this destination is associated with.
     * @param destination The destination to lock and attempt to roll.
     * @return The destination if it didn't need rolling and can be used, null otherwise. The returned
     * destination is locked for exclusive use by the current thread.
     * @throws IOException Could be thrown while attempting to flush or close the destination on roll.
     */
    private RollingDestination lockAndRoll(final Object key, final RollingDestination destination) throws IOException {
        RollingDestination dest = destination;

        // Lock the destination so only the current thread can use it.
        dest.lock();

        boolean rolled = true;
        try {
            // Try and roll the destination.
            rolled = dest.tryFlushAndRoll(false, Instant.now());

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
        // Only remove the destination if it is the same one that is already in the map.
        currentDestinations.compute(key, (k, v) -> {
            if (v == destination) {
                return null;
            }
            return v;
        });
    }

    public void roll() {
        taskContextFactory.current().info(() -> "Rolling all destinations");
        rollAll(false);
    }

    public void forceRoll() {
        rollAll(true);
    }

    private void rollAll(final boolean force) {
        LOGGER.debug("rollAll()");

        final Instant currentTime = Instant.now();
        currentDestinations.forEach(1, (key, destination) -> {
            // We have to do this here as foreach runs multiple threads so each thread needs to run as proc user
            securityContext.asProcessingUser(() -> {
                // Try and lock this destination as we can't flush or roll it if
                // another thread has the lock.
                boolean rolled = false;
                if (destination.tryLock()) {
                    try {
                        try {
                            rolled = destination.tryFlushAndRoll(force, currentTime);
                        } catch (final IOException | RuntimeException e) {
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
                        } catch (final IOException | RuntimeException e) {
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
            });
        });
    }
}
