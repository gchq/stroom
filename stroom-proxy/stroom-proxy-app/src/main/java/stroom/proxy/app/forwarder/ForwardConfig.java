package stroom.proxy.app.forwarder;

import stroom.util.shared.NotInjectableConfig;

@NotInjectableConfig
public interface ForwardConfig {

    boolean isEnabled();

    String getName();
}
