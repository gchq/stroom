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

import stroom.proxy.app.DownstreamHostConfig;
import stroom.util.io.PathCreator;

public sealed interface ForwarderConfig
        permits ForwardHttpPostConfig, ForwardFileConfig {

    String getName();

    boolean isInstant();

    boolean isEnabled();

    ForwardQueueConfig getForwardQueueConfig();

    String getDestinationDescription(final DownstreamHostConfig downstreamHostConfig,
                                     final PathCreator pathCreator);
}
