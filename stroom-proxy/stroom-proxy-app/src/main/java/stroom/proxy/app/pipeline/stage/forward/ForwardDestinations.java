/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.handler.Destination;
import stroom.proxy.app.handler.DirNames;
import stroom.proxy.app.handler.DirUtil;
import stroom.proxy.app.handler.FailureDestinationFactory;
import stroom.proxy.app.handler.FileDestination;
import stroom.proxy.app.handler.ForwardFileConfig;
import stroom.proxy.app.handler.ForwardFileDestinationFactory;
import stroom.proxy.app.handler.ForwardHttpPostConfig;
import stroom.proxy.app.handler.ForwardHttpPostDestinationFactory;
import stroom.proxy.app.handler.ForwardS3Config;
import stroom.proxy.app.handler.ForwardS3DestinationFactory;
import stroom.proxy.app.handler.ForwarderConfig;
import stroom.proxy.app.pipeline.config.ForwardDestinationFacts;
import stroom.proxy.repo.store.FileStores;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The enabled forward destinations, each built from its configuration with its give-up destination
 * and its retry bounds, ready for the assembler to give a loop.
 */
@Singleton
public class ForwardDestinations {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardDestinations.class);

    private final List<Wiring> wirings;

    @Inject
    public ForwardDestinations(final Provider<ProxyConfig> proxyConfigProvider,
                               final DataDirProvider dataDirProvider,
                               final PathCreator pathCreator,
                               final ForwardFileDestinationFactory fileFactory,
                               final ForwardHttpPostDestinationFactory httpFactory,
                               final ForwardS3DestinationFactory s3Factory,
                               final FailureDestinationFactory failureDestinationFactory,
                               final FileStores fileStores) {
        final ProxyConfig proxyConfig = proxyConfigProvider.get();
        // An instant forwarder relays during receipt and has no loop; it is receipt's.
        final List<ForwarderConfig> configs = proxyConfig.streamAllEnabledForwarders()
                .filter(config -> !config.isInstant())
                .toList();
        checkDestinationNamesDoNotCollide(configs);

        final Path forwardingRoot = dataDirProvider.get().resolve(DirNames.FORWARDING);
        final List<Wiring> built = new ArrayList<>(configs.size());
        int order = 0;
        for (final ForwarderConfig config : configs) {
            final Destination destination = switch (config) {
                case final ForwardHttpPostConfig http -> httpFactory.create(http);
                case final ForwardFileConfig file -> fileFactory.create(file);
                case final ForwardS3Config s3 -> s3Factory.create(s3);
            };
            final Destination giveUpDestination = failureDestinationFactory.create(
                    config.getName(),
                    forwardingRoot.resolve(DirUtil.makeSafeName(config.getName())),
                    config.getFailureDestination(),
                    fileStores,
                    ++order,
                    pathCreator);
            built.add(new Wiring(
                    config.getName(),
                    destination,
                    new GiveUp(giveUpDestination),
                    config.getRetry().toBounds(),
                    config.getRetry().getLivenessCheckInterval().getDuration(),
                    config.getThreads().getConsumerThreads()));
            LOGGER.info("Forward destination '{}' delivers to {}", config.getName(), destination);
        }
        this.wirings = List.copyOf(built);
    }

    /**
     * Refuse to start when two enabled forward destinations have names that sanitise to one directory
     * name. Each destination's default give-up directory is named by the sanitised name, and the
     * sanitisation is lossy: "stroom.main" and "stroom_main" are one directory, and two destinations
     * sharing one would interleave their give-ups.
     */
    static void checkDestinationNamesDoNotCollide(final List<ForwarderConfig> configs) {
        final Map<String, List<String>> namesBySafeName = NullSafe.stream(configs)
                .map(ForwarderConfig::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(DirUtil::makeSafeName, TreeMap::new, Collectors.toList()));

        final String collisions = namesBySafeName.entrySet()
                .stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> LogUtil.message("'{}' <- {}", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("; "));

        if (!collisions.isEmpty()) {
            throw new RuntimeException(LogUtil.message(
                    "Two or more enabled forward destinations have names that resolve to the same "
                    + "directory, so they would share one give-up directory: {}. "
                    + "Destination names must differ by more than the characters that are replaced "
                    + "when making a directory name ([a-zA-Z0-9_-] are kept, everything else becomes "
                    + "'_').", collisions));
        }
    }

    public List<Wiring> getWirings() {
        return wirings;
    }


    // --------------------------------------------------------------------------------


    /**
     * One enabled destination, ready to be given a loop.
     *
     * @param livenessCheckInterval How often to run the destination's liveness check, where it has one.
     * @param consumerThreads       How many threads deliver to it.
     */
    public record Wiring(String name,
                         Destination destination,
                         GiveUp giveUp,
                         ForwardBounds bounds,
                         Duration livenessCheckInterval,
                         int consumerThreads) {

        public Wiring {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(destination, "destination");
            Objects.requireNonNull(giveUp, "giveUp");
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(livenessCheckInterval, "livenessCheckInterval");
            if (consumerThreads < 1) {
                throw new IllegalArgumentException("Destination '" + name + "' needs at least one thread");
            }
        }

        public ForwardDestinationFacts toFacts() {
            final Path giveUpDirectory = giveUp.getDestination() instanceof final FileDestination file
                    ? file.getStoreDir()
                    : null;
            return new ForwardDestinationFacts(
                    name, bounds.maxRetryAge(), bounds.retryDelay(), bounds.longestDelay(), giveUpDirectory);
        }
    }
}
