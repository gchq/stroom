package stroom.proxy.app.handler;

public sealed interface ForwarderConfig
        permits ForwardHttpPostConfig, ForwardFileConfig {

    String getName();

    boolean isInstant();

    boolean isEnabled();

    ForwardQueueConfig getForwardQueueConfig();
}
