package stroom.proxy.app.handler;

import stroom.util.shared.NotInjectableConfig;

@NotInjectableConfig
public interface ForwardConfig {

    boolean isEnabled();

    String getName();
}
