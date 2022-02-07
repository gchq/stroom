package stroom.proxy.app;

import stroom.collection.mock.MockCollectionModule;
import stroom.db.util.DbModule;
import stroom.dictionary.impl.DictionaryModule;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.impl.DocumentResourceHelperImpl;
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.fs.FSPersistence;
import stroom.legacy.impex_6_1.LegacyImpexModule;
import stroom.proxy.app.guice.ProxyConfigModule;
import stroom.proxy.app.handler.ProxyRequestHandler;
import stroom.proxy.app.handler.RemoteFeedStatusService;
import stroom.proxy.repo.ErrorReceiver;
import stroom.proxy.repo.ErrorReceiverImpl;
import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.MockForwardDestinations;
import stroom.proxy.repo.MockSender;
import stroom.proxy.repo.ProgressLog;
import stroom.proxy.repo.ProgressLogImpl;
import stroom.proxy.repo.ProxyRepoDbModule;
import stroom.proxy.repo.RepoDbDirProvider;
import stroom.proxy.repo.RepoDbDirProviderImpl;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.RepoDirProviderImpl;
import stroom.proxy.repo.Sender;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactory;
import stroom.receive.common.FeedStatusService;
import stroom.receive.common.RemoteFeedModule;
import stroom.receive.common.RequestHandler;
import stroom.receive.rules.impl.DataReceiptPolicyAttributeMapFilterFactoryImpl;
import stroom.receive.rules.impl.ReceiveDataRuleSetService;
import stroom.receive.rules.impl.ReceiveDataRuleSetServiceImpl;
import stroom.security.api.RequestAuthenticator;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.task.impl.TaskContextModule;
import stroom.util.BuildInfoProvider;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.io.PathCreator;
import stroom.util.shared.BuildInfo;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

import java.nio.file.Path;
import javax.ws.rs.client.Client;

public class StoreAndForwardTestModule extends AbstractModule {

    private final Config configuration;
    private final ProxyConfigHolder proxyConfigHolder;

    public StoreAndForwardTestModule(final Config configuration,
                                     final Path configFile) {
        this.configuration = configuration;

        proxyConfigHolder = new ProxyConfigHolder(
                configuration.getProxyConfig(),
                configFile);
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);

        install(new ProxyConfigModule(proxyConfigHolder));
        install(new DbModule());
        install(new ProxyRepoDbModule());
        install(new MockCollectionModule());

        install(new DictionaryModule());
        // Allow discovery of feed status from other proxies.
        install(new RemoteFeedModule());

        install(new TaskContextModule());
        install(new LegacyImpexModule());

        bind(BuildInfo.class).toProvider(BuildInfoProvider.class);
        bind(DataReceiptPolicyAttributeMapFilterFactory.class).to(DataReceiptPolicyAttributeMapFilterFactoryImpl.class);
        bind(DocumentResourceHelper.class).to(DocumentResourceHelperImpl.class);
        bind(ErrorReceiver.class).to(ErrorReceiverImpl.class);
        bind(FeedStatusService.class).to(RemoteFeedStatusService.class);
        bind(ReceiveDataRuleSetService.class).to(ReceiveDataRuleSetServiceImpl.class);
        bind(RequestAuthenticator.class).to(RequestAuthenticatorImpl.class).asEagerSingleton();
        bind(RequestHandler.class).to(ProxyRequestHandler.class);
        bind(SecurityContext.class).to(MockSecurityContext.class);
        bind(Serialiser2Factory.class).to(Serialiser2FactoryImpl.class);
        bind(StoreFactory.class).to(StoreFactoryImpl.class);
        bind(ForwarderDestinations.class).to(MockForwardDestinations.class);
        bind(Sender.class).to(MockSender.class);

        bind(RepoDirProvider.class).to(RepoDirProviderImpl.class);
        bind(RepoDbDirProvider.class).to(RepoDbDirProviderImpl.class);
        bind(ProgressLog.class).to(ProgressLogImpl.class);
    }

    @Provides
    @Singleton
    Persistence providePersistence(final PathCreator pathCreator) {
        final String path = configuration.getProxyConfig().getContentDir();
        return new FSPersistence(pathCreator.toAppPath(path));
    }

    @Provides
    @Singleton
    Client provideJerseyClient() {
        return null;
    }

    @Provides
    EntityEventBus entityEventBus() {
        return event -> {
        };
    }
}
