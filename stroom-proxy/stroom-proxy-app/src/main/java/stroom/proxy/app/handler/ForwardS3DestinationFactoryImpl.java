/*
 * Copyright 2016-2026 Crown Copyright
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


import stroom.aws.s3.client.S3ClientPool;
import stroom.aws.s3.client.S3MetaFieldsMapper;
import stroom.cache.api.TemplateCache;
import stroom.proxy.app.DataDirProvider;
import stroom.proxy.app.DownstreamHostConfig;
import stroom.proxy.repo.ProxyServices;
import stroom.proxy.repo.store.FileStores;
import stroom.util.io.SimplePathCreator;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

public class ForwardS3DestinationFactoryImpl implements ForwardS3DestinationFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardS3DestinationFactoryImpl.class);

    private final Provider<DownstreamHostConfig> downstreamHostConfigProvider;
    private final DirQueueFactory dirQueueFactory;
    private final DataDirProvider dataDirProvider;
    private final SimplePathCreator simplePathCreator;
    private final ProxyServices proxyServices;
    private final FileStores fileStores;
    private final S3ClientPool s3ClientPool;
    private final TemplateCache templateCache;
    private final S3MetaFieldsMapper s3MetaFieldsMapper;
    private final CleanupDirQueue cleanupDirQueue;
    private final JerseyClientFactory jerseyClientFactory;
    private final RemoteS3EventClient remoteS3EventClient;

    @Inject
    public ForwardS3DestinationFactoryImpl(final Provider<DownstreamHostConfig> downstreamHostConfigProvider,
                                           final DirQueueFactory dirQueueFactory,
                                           final DataDirProvider dataDirProvider,
                                           final SimplePathCreator simplePathCreator,
                                           final ProxyServices proxyServices,
                                           final FileStores fileStores,
                                           final S3ClientPool s3ClientPool,
                                           final TemplateCache templateCache,
                                           final S3MetaFieldsMapper s3MetaFieldsMapper,
                                           final CleanupDirQueue cleanupDirQueue,
                                           final JerseyClientFactory jerseyClientFactory,
                                           final RemoteS3EventClient remoteS3EventClient) {
        this.downstreamHostConfigProvider = downstreamHostConfigProvider;
        this.dirQueueFactory = dirQueueFactory;
        this.dataDirProvider = dataDirProvider;
        this.simplePathCreator = simplePathCreator;
        this.proxyServices = proxyServices;
        this.fileStores = fileStores;
        this.s3ClientPool = s3ClientPool;
        this.templateCache = templateCache;
        this.s3MetaFieldsMapper = s3MetaFieldsMapper;
        this.cleanupDirQueue = cleanupDirQueue;
        this.jerseyClientFactory = jerseyClientFactory;
        this.remoteS3EventClient = remoteS3EventClient;
    }

    @Override
    public ForwardDestination create(final ForwardS3Config forwardS3Config) {
        Objects.requireNonNull(forwardS3Config);
        final String name = forwardS3Config.getName();
        final ForwardS3DestinationImpl forwardS3Destination = new ForwardS3DestinationImpl(
                name,
                forwardS3Config,
                downstreamHostConfigProvider.get(),
                s3ClientPool,
                templateCache,
                s3MetaFieldsMapper,
                cleanupDirQueue,
                remoteS3EventClient);

        final ForwardDestination destination = getWrappedForwardDestination(
                forwardS3Config, forwardS3Destination);

        LOGGER.info("Created {} '{}' with destination details '{}'",
                destination.getClass().getSimpleName(),
                name,
                destination.getDestinationDescription());

        return forwardS3Destination;
    }

    private ForwardDestination getWrappedForwardDestination(
            final ForwardS3Config config,
            final ForwardS3DestinationImpl forwardS3Destination) {

        final ForwardQueueConfig forwardQueueConfig = config.getForwardQueueConfig();
        Objects.requireNonNull(forwardQueueConfig, () -> LogUtil.message(
                "No forwardQueueConfig set for destination '{}'", config.getName()));
        // We have queue config so wrap out ultimate destination with some queue/retry logic
        return new RetryingForwardDestination(
                forwardQueueConfig,
                forwardS3Destination,
                dataDirProvider,
                simplePathCreator,
                dirQueueFactory,
                proxyServices,
                fileStores);
    }
}
