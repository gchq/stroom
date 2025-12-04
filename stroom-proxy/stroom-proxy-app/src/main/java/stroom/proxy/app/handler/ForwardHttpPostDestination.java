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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ForwardHttpPostDestination implements ForwardDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardHttpPostDestination.class);

    private final StreamDestination destination;
    private final CleanupDirQueue cleanupDirQueue;
    private final ForwardHttpPostConfig forwardHttpPostConfig;
    private final String destinationName;
    private final DownstreamHostConfig downstreamHostConfig;

    public ForwardHttpPostDestination(final String destinationName,
                                      final StreamDestination destination,
                                      final CleanupDirQueue cleanupDirQueue,
                                      final ForwardHttpPostConfig forwardHttpPostConfig,
                                      final DownstreamHostConfig downstreamHostConfig) {
        this.destination = destination;
        this.cleanupDirQueue = cleanupDirQueue;
        this.destinationName = destinationName;
        this.forwardHttpPostConfig = forwardHttpPostConfig;
        this.downstreamHostConfig = downstreamHostConfig;
    }

    @Override
    public void add(final Path sourceDir) {
        LOGGER.debug("'{}' - add(), dir: {}", destinationName, sourceDir);
        try {
            final FileGroup fileGroup = new FileGroup(sourceDir);
            final AttributeMap attributeMap = new AttributeMap();
            AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);
            // Make sure we tell the destination we are sending zip data.
            attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);

            // Send the data.
            try (final InputStream inputStream =
                    new BufferedInputStream(Files.newInputStream(fileGroup.getZip()))) {
                destination.send(attributeMap, inputStream);
            }

            // We have completed sending so can delete the data.
            cleanupDirQueue.add(sourceDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean hasLivenessCheck() {
        return destination.hasLivenessCheck();
    }

    @Override
    public boolean performLivenessCheck() throws Exception {
        return !hasLivenessCheck()
               || destination.performLivenessCheck();
    }

    @Override
    public String getName() {
        return forwardHttpPostConfig.getName();
    }

    @Override
    public DestinationType getDestinationType() {
        return DestinationType.HTTP;
    }

    @Override
    public String getDestinationDescription() {
        return getForwardUrl() + " (instant=" + forwardHttpPostConfig.isInstant() + ")";
    }

    private String getForwardUrl() {
        return forwardHttpPostConfig.createForwardUrl(downstreamHostConfig);
    }

    @Override
    public String toString() {
        return asString();
    }
}
