/*
 * Copyright 2025 Crown Copyright
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
import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import stroom.util.io.PathCreator;

/**
 * What every forward destination's configuration states, whatever it delivers to: how it retries,
 * where it puts what it gives up on, and how many threads deliver
 * ({@code designs/stages/forward.md} §4.6).
 */
public sealed interface ForwarderConfig
        permits ForwardFileConfig, ForwardHttpPostConfig, ForwardS3Config {

    String getName();

    boolean isInstant();

    boolean isEnabled();

    ForwardRetryConfig getRetry();

    /**
     * @return Where give-up data goes, or null for the default: a {@code 03_failure} directory
     * under {@code 50_forwarding/<name>} in the proxy's data directory.
     */
    FailureDestinationConfig getFailureDestination();

    ConsumerStageThreadsConfig getThreads();

    String getDestinationDescription(final DownstreamHostConfig downstreamHostConfig,
                                     final PathCreator pathCreator);
}
