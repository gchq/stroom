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

import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.app.ProxyConfig;
import stroom.proxy.repo.ProxyServices;
import stroom.proxy.repo.store.FileStores;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

public class ForwardHttpPostDestinationFactoryImpl implements ForwardHttpPostDestinationFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            ForwardHttpPostDestinationFactoryImpl.class);

    private final CleanupDirQueue cleanupDirQueue;
    private final ProxyServices proxyServices;
    private final DirQueueFactory dirQueueFactory;
    private final Provider<ProxyConfig> proxyConfigProvider;
    private final DataDirProvider dataDirProvider;
    private final SimplePathCreator simplePathCreator;
    private final HttpSenderFactory httpSenderFactory;
    private final FileStores fileStores;
    private final DownstreamHostConfig downstreamHostConfig;

    @Inject
    public ForwardHttpPostDestinationFactoryImpl(final CleanupDirQueue cleanupDirQueue,
                                                 final ProxyServices proxyServices,
                                                 final DirQueueFactory dirQueueFactory,
                                                 final Provider<ProxyConfig> proxyConfigProvider,
                                                 final DataDirProvider dataDirProvider,
                                                 final SimplePathCreator simplePathCreator,
                                                 final HttpSenderFactory httpSenderFactory,
                                                 final FileStores fileStores,
                                                 final DownstreamHostConfig downstreamHostConfig) {
        this.cleanupDirQueue = cleanupDirQueue;
        this.proxyServices = proxyServices;
        this.dirQueueFactory = dirQueueFactory;
        this.proxyConfigProvider = proxyConfigProvider;
        this.dataDirProvider = dataDirProvider;
        this.simplePathCreator = simplePathCreator;
        this.httpSenderFactory = httpSenderFactory;
        this.fileStores = fileStores;
        this.downstreamHostConfig = downstreamHostConfig;
    }

    @Override
    public ForwardDestination create(final ForwardHttpPostConfig forwardHttpPostConfig) {
        final StreamDestination streamDestination = httpSenderFactory.create(forwardHttpPostConfig);
        final String name = forwardHttpPostConfig.getName();
        final ForwardHttpPostDestination forwardHttpDestination = new ForwardHttpPostDestination(
                name,
                streamDestination,
                cleanupDirQueue,
                forwardHttpPostConfig,
                downstreamHostConfig);

        final ForwardDestination destination = getWrappedForwardDestination(
                forwardHttpPostConfig, forwardHttpDestination);

        final String fullUrl = forwardHttpPostConfig.createForwardUrl(downstreamHostConfig);

        LOGGER.info("Created {} '{}' with url '{}'",
                destination.getClass().getSimpleName(),
                name,
                fullUrl);

        return destination;
    }

    private ForwardDestination getWrappedForwardDestination(
            final ForwardHttpPostConfig config,
            final ForwardHttpPostDestination forwardHttpPostDestination) {

        final ForwardQueueConfig forwardQueueConfig = config.getForwardQueueConfig();
        Objects.requireNonNull(forwardQueueConfig, () -> LogUtil.message(
                "No forwardQueueConfig set for destination '{}'", config.getName()));
        // We have queue config so wrap out ultimate destination with some queue/retry logic
        return new RetryingForwardDestination(
                forwardQueueConfig,
                forwardHttpPostDestination,
                dataDirProvider,
                simplePathCreator,
                dirQueueFactory,
                proxyServices,
                fileStores);
    }
}
