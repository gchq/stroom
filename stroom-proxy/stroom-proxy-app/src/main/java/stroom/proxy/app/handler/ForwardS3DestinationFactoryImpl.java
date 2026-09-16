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

package stroom.proxy.app.handler;

import stroom.aws.s3.client.S3ClientPool;
import stroom.aws.s3.client.S3MetaKeysMapper;
import stroom.cache.api.TemplateCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.util.Objects;

public class ForwardS3DestinationFactoryImpl implements ForwardS3DestinationFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardS3DestinationFactoryImpl.class);

    private final S3ClientPool s3ClientPool;
    private final TemplateCache templateCache;
    private final S3MetaKeysMapper s3MetaKeysMapper;
    private final RemoteS3EventClient remoteS3EventClient;

    @Inject
    public ForwardS3DestinationFactoryImpl(final S3ClientPool s3ClientPool,
                                           final TemplateCache templateCache,
                                           final S3MetaKeysMapper s3MetaKeysMapper,
                                           final RemoteS3EventClient remoteS3EventClient) {
        this.s3ClientPool = s3ClientPool;
        this.templateCache = templateCache;
        this.s3MetaKeysMapper = s3MetaKeysMapper;
        this.remoteS3EventClient = remoteS3EventClient;
    }

    @Override
    public Destination create(final ForwardS3Config config) {
        Objects.requireNonNull(config);
        final S3Destination destination = new S3Destination(
                config.getName(),
                config,
                s3ClientPool,
                templateCache,
                s3MetaKeysMapper,
                remoteS3EventClient);
        LOGGER.info("Created {} '{}' with destination '{}'",
                destination.getClass().getSimpleName(), config.getName(), destination.getDescription());
        return destination;
    }
}
