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

import stroom.proxy.app.DownstreamHostConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

public class ForwardHttpPostDestinationFactoryImpl implements ForwardHttpPostDestinationFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            ForwardHttpPostDestinationFactoryImpl.class);

    private final HttpSenderFactory httpSenderFactory;
    private final DownstreamHostConfig downstreamHostConfig;

    @Inject
    public ForwardHttpPostDestinationFactoryImpl(final HttpSenderFactory httpSenderFactory,
                                                 final DownstreamHostConfig downstreamHostConfig) {
        this.httpSenderFactory = httpSenderFactory;
        this.downstreamHostConfig = downstreamHostConfig;
    }

    @Override
    public Destination create(final ForwardHttpPostConfig config) {
        final String fullUrl = config.createForwardUrl(downstreamHostConfig);
        final HttpDestination destination = new HttpDestination(
                config.getName(),
                httpSenderFactory.create(config),
                fullUrl);
        LOGGER.info("Created {} '{}' with url '{}'", destination.getClass().getSimpleName(), config.getName(), fullUrl);
        return destination;
    }
}
