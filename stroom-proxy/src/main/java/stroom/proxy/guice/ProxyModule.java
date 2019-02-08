package stroom.proxy.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import stroom.receive.AttributeMapFilterFactory;
import stroom.receive.RequestHandler;
import stroom.docstore.Persistence;
import stroom.docstore.impl.fs.FSPersistence;
import stroom.proxy.handler.ForwardStreamHandlerFactory;
import stroom.proxy.handler.ProxyRequestHandler;
import stroom.proxy.handler.StreamHandlerFactory;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryReader;
import stroom.ruleset.AttributeMapFilterFactoryImpl;
import stroom.ruleset.ReceiveDataRuleSetService;
import stroom.ruleset.ReceiveDataRuleSetServiceImpl;
import stroom.security.SecurityContext;
import stroom.util.HasHealthCheck;

import java.nio.file.Paths;

public class ProxyModule extends AbstractModule {
    private final ProxyConfig proxyConfig;

    public ProxyModule(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    protected void configure() {
        install(new ProxyConfigModule(proxyConfig));

        bind(RequestHandler.class).to(ProxyRequestHandler.class);
        bind(StreamHandlerFactory.class).to(ForwardStreamHandlerFactory.class);
        bind(ProxyRepositoryManager.class).asEagerSingleton();
        bind(ProxyRepositoryReader.class).asEagerSingleton();

        bind(AttributeMapFilterFactory.class).to(AttributeMapFilterFactoryImpl.class);
        bind(ReceiveDataRuleSetService.class).to(ReceiveDataRuleSetServiceImpl.class);
        bind(SecurityContext.class).to(NoSecurityContext.class);

        final Multibinder<HasHealthCheck> hasHealthCheckBinder = Multibinder.newSetBinder(binder(), HasHealthCheck.class);
        hasHealthCheckBinder.addBinding().to(ForwardStreamHandlerFactory.class);
    }

    @Provides
    @Singleton
    Persistence providePersistence() {
        return new FSPersistence(Paths.get(proxyConfig.getProxyContentDir()));
    }
}
