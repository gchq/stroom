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

package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Forwards each sourceDir to more than one destination.
 */
final class MultiForwardDestination implements ForwardDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(MultiForwardDestination.class);

    private final List<ForwardDestination> destinations;
    private final NumberedDirProvider copiesDirProvider;
    private final int destinationCount;
    private final CleanupDirQueue cleanupDirQueue;

    MultiForwardDestination(
            final List<ForwardDestination> destinations,
            final NumberedDirProvider copiesDirProvider,
            final CleanupDirQueue cleanupDirQueue) {

        if (NullSafe.size(destinations) < 2) {
            throw new IllegalArgumentException(LogUtil.message(
                    "{} is intended for use with two or more destinations, destinationCount {}",
                    this.getClass().getSimpleName(), NullSafe.size(destinations)));
        }
        this.destinations = destinations;
        this.copiesDirProvider = copiesDirProvider;
        this.destinationCount = destinations.size();
        this.cleanupDirQueue = cleanupDirQueue;
    }

    @Override
    public void add(final Path sourceDir) {
        final List<DestinationCopy> destinationCopies = new ArrayList<>(destinationCount);
        try {
            for (final ForwardDestination destination : destinations) {
                final Path copy = copiesDirProvider.get();
                copyContents(sourceDir, copy);
                destinationCopies.add(new DestinationCopy(destination, copy));
            }

            final List<Exception> exceptions = new ArrayList<>(destinationCount);
            for (int i = 0; i < destinationCopies.size(); i++) {
                final DestinationCopy destinationCopy = destinationCopies.get(i);
                // This will move to the copy to the dest
                final ForwardDestination destination = destinationCopy.destination;
                final Path copyDir = destinationCopy.copyDir;
                try {
                    destination.add(copyDir);
                    // Null the destinationCopy as it has been moved and doesn't need to be cleaned up
                    destinationCopies.set(i, null);
                } catch (final Exception e) {
                    LOGGER.debug("Error adding '{}' to destination {}: {}",
                            copyDir, destination.asString(), LogUtil.exceptionMessage(e), e);
                    exceptions.add(new RuntimeException(LogUtil.message(
                            "Error adding {} to destination {}",
                            copyDir, destination.asString())));
                }
            }
            if (exceptions.isEmpty()) {
                // No problems with any of the copies so bin the source.
                // There is the risk that we send something twice but that is preferable to dropping data.
                cleanupDirQueue.add(sourceDir);
            } else {
                // Leave the source to try again after reboot
                // Wrap all the exceptions encountered
                throw new RuntimeException(LogUtil.message(
                        "Error adding to {} destinations:\n{}",
                        exceptions.size(),
                        exceptions.stream()
                                .map(Exception::getMessage)
                                .collect(Collectors.joining("\n"))),
                        exceptions.getFirst());
            }
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        } finally {
            // Get rid of any copies that haven't already been moved
            destinationCopies.stream()
                    .filter(Objects::nonNull)
                    .map(DestinationCopy::copyDir)
                    .forEach(cleanupDirQueue::add);
        }
    }

    @Override
    public String getName() {
        return "Multi forward destination";
    }

    @Override
    public DestinationType getDestinationType() {
        return DestinationType.MULTI;
    }

    @Override
    public String getDestinationDescription() {
        return "Facade for " + destinations.size() + " destinations";
    }

    private void copyContents(final Path source, final Path target) {
        LOGGER.debug("Copying contents of {} to {}", source, target);
        try (final Stream<Path> stream = Files.list(source)) {
            stream.forEach(path -> {
                try {
                    Files.copy(path, target.resolve(path.getFileName()));
                } catch (final IOException e) {
                    LOGGER.error(e::getMessage, e);
                    throw new UncheckedIOException(e);
                }
            });
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }


    // --------------------------------------------------------------------------------


    private record DestinationCopy(ForwardDestination destination,
                                   Path copyDir) {

    }
}
