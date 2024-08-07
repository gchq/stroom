package stroom.proxy.app;

import stroom.collection.mock.MockCollectionModule;
import stroom.db.util.DbModule;
import stroom.dictionary.impl.DictionaryModule;
import stroom.docrefinfo.api.DocRefDecorator;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.impl.DocumentResourceHelperImpl;
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.fs.FSPersistence;
import stroom.importexport.api.ImportConverter;
import stroom.proxy.app.guice.NoDecorationDocRefDecorator;
import stroom.proxy.app.guice.NoOpImportConverter;
import stroom.proxy.app.guice.ProxyConfigModule;
import stroom.proxy.app.handler.ProxyRequestHandler;
import stroom.proxy.app.handler.RemoteFeedStatusService;
import stroom.proxy.app.jersey.ProxyJerseyModule;
import stroom.proxy.app.security.ProxySecurityModule;
import stroom.proxy.repo.ErrorReceiver;
import stroom.proxy.repo.ErrorReceiverImpl;
import stroom.proxy.repo.ProgressLog;
import stroom.proxy.repo.ProgressLogImpl;
import stroom.proxy.repo.ProxyDbModule;
import stroom.proxy.repo.RepoDbDirProvider;
import stroom.proxy.repo.RepoDbDirProviderImpl;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.RepoDirProviderImpl;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactory;
import stroom.receive.common.FeedStatusService;
import stroom.receive.common.RemoteFeedModule;
import stroom.receive.common.RequestHandler;
import stroom.receive.rules.impl.DataReceiptPolicyAttributeMapFilterFactoryImpl;
import stroom.receive.rules.impl.ReceiveDataRuleSetService;
import stroom.receive.rules.impl.ReceiveDataRuleSetServiceImpl;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.task.impl.TaskContextModule;
import stroom.util.BuildInfoProvider;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.io.PathCreator;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.shared.BuildInfo;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.core.setup.Environment;
import jakarta.ws.rs.client.Client;
import org.mockito.Mockito;

import java.nio.file.Path;

public abstract class AbstractStoreAndForwardTestModule extends AbstractModule {

    private final Config configuration;
    private final ProxyConfigHolder proxyConfigHolder;

    public AbstractStoreAndForwardTestModule(final Config configuration,
                                             final Path configFile) {
        this.configuration = configuration;

        proxyConfigHolder = new ProxyConfigHolder(
                configuration.getProxyConfig(),
                configFile);
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);
        bind(Environment.class).toInstance(new Environment("TestEnvironment"));

        install(new ProxyConfigModule(proxyConfigHolder));
        install(new DbModule());
        install(new ProxyDbModule());
        install(new MockCollectionModule());
        install(new ProxySecurityModule());

        bind(Client.class).toInstance(Mockito.mock(Client.class));
        bind(WebTargetFactory.class).toInstance(Mockito.mock(WebTargetFactory.class));

        install(new DictionaryModule());
        // Allow discovery of feed status from other proxies.
        install(new RemoteFeedModule());

        install(new TaskContextModule());
        install(new ProxyJerseyModule());

        bind(BuildInfo.class).toProvider(BuildInfoProvider.class);
        bind(DataReceiptPolicyAttributeMapFilterFactory.class).to(DataReceiptPolicyAttributeMapFilterFactoryImpl.class);
        bind(DocumentResourceHelper.class).to(DocumentResourceHelperImpl.class);
        bind(ErrorReceiver.class).to(ErrorReceiverImpl.class);
        bind(FeedStatusService.class).to(RemoteFeedStatusService.class);
        bind(ReceiveDataRuleSetService.class).to(ReceiveDataRuleSetServiceImpl.class);
//        bind(RequestAuthenticator.class).to(RequestAuthenticatorImpl.class).asEagerSingleton();
        bind(RequestHandler.class).to(ProxyRequestHandler.class);
        bind(SecurityContext.class).to(MockSecurityContext.class);
        bind(Serialiser2Factory.class).to(Serialiser2FactoryImpl.class);
        bind(StoreFactory.class).to(StoreFactoryImpl.class);
        bind(DocRefDecorator.class).to(NoDecorationDocRefDecorator.class);

        bind(RepoDirProvider.class).to(RepoDirProviderImpl.class);
        bind(RepoDbDirProvider.class).to(RepoDbDirProviderImpl.class);
        bind(ProgressLog.class).to(ProgressLogImpl.class);

        // Proxy doesn't do import so bind a dummy ImportConverter for the StoreImpl(s) to use
        bind(ImportConverter.class).to(NoOpImportConverter.class);
    }

    @Provides
    @Singleton
    Persistence providePersistence(final PathCreator pathCreator) {
        final String path = configuration.getProxyConfig().getContentDir();
        return new FSPersistence(pathCreator.toAppPath(path));
    }

    @Provides
    EntityEventBus entityEventBus() {
        return event -> {
        };
    }
}
