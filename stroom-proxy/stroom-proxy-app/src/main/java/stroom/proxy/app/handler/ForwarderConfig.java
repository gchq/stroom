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
