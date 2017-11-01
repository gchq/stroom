package stroom.proxy.handler;

import stroom.proxy.repo.ProxyRepositoryStreamHandlerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class MasterStreamHandlerFactory implements StreamHandlerFactory {
    private final ProxyRepositoryStreamHandlerFactory proxyRepositoryStreamHandlerFactory;
    private final ForwardStreamHandlerFactory forwardStreamHandlerFactory;

    @Inject
    MasterStreamHandlerFactory(final ProxyRepositoryStreamHandlerFactory proxyRepositoryStreamHandlerFactory,
                               final ForwardStreamHandlerFactory forwardStreamHandlerFactory) {
        this.proxyRepositoryStreamHandlerFactory = proxyRepositoryStreamHandlerFactory;
        this.forwardStreamHandlerFactory = forwardStreamHandlerFactory;
    }

    @Override
    public List<StreamHandler> addReceiveHandlers(final List<StreamHandler> handlers) {
        proxyRepositoryStreamHandlerFactory.addReceiveHandlers(handlers);
        forwardStreamHandlerFactory.addReceiveHandlers(handlers);
        return handlers;
    }

    @Override
    public List<StreamHandler> addSendHandlers(final List<StreamHandler> handlers) {
        proxyRepositoryStreamHandlerFactory.addSendHandlers(handlers);
        forwardStreamHandlerFactory.addSendHandlers(handlers);
        return handlers;
    }
}
