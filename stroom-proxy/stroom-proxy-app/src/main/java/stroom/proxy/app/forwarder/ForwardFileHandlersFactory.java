package stroom.proxy.app.forwarder;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ForwardFileHandlersFactory {

    @Inject
    public ForwardFileHandlersFactory() {
    }

    public ForwardFileHandlers create(final ForwardFileConfig config) {
        return new ForwardFileHandlers(config);
    }
}
