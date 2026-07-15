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

package stroom.receive.common;


import stroom.aws.s3.shared.S3EventResource;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Objects;

/**
 * This is an alternative mechanism to {@link S3EventNotificationService} for notifying
 * Stroom of the presence of a file on S3 that needs to be turned into a Stroom Stream.
 * <p>
 * Mainly here to aid testing, but may be useful as an additional method.
 * </p>
 */
public class S3EventResourceImpl implements S3EventResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3EventResourceImpl.class);

    private final Provider<S3EventNotificationService> s3EventNotificationServiceProvider;

    @Inject
    S3EventResourceImpl(final Provider<S3EventNotificationService> s3EventNotificationServiceProvider) {
        this.s3EventNotificationServiceProvider = s3EventNotificationServiceProvider;
    }

    @AutoLogged(OperationType.UNLOGGED) // Not a user operation
    @Override
    public void notify(final S3EventNotificationRequest request) {
        LOGGER.debug("notify() - request: {}", request);
        Objects.requireNonNull(request);
        // TODO Allow calls only from proxy at the moment. May want to open it up for
        //  other people to use.
        s3EventNotificationServiceProvider.get()
                .notify(request.getS3Location(), request.getMetaData());
    }
}
