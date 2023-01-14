package stroom.proxy.app.guice;

import stroom.collection.mock.MockCollectionModule;
import stroom.db.util.DbModule;
import stroom.dictionary.impl.DictionaryModule;
import stroom.dictionary.impl.DictionaryStore;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.impl.DocumentResourceHelperImpl;
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.Serialiser2FactoryImpl;
import stroom.docstore.impl.StoreFactoryImpl;
import stroom.docstore.impl.fs.FSPersistence;
import stroom.dropwizard.common.FilteredHealthCheckServlet;
import stroom.dropwizard.common.LogLevelInspector;
import stroom.dropwizard.common.PermissionExceptionMapper;
import stroom.dropwizard.common.TokenExceptionMapper;
import stroom.importexport.api.ImportConverter;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.proxy.app.Config;
import stroom.proxy.app.ContentSyncService;
import stroom.proxy.app.ProxyConfigHealthCheck;
import stroom.proxy.app.ProxyConfigHolder;
import stroom.proxy.app.ProxyLifecycle;
import stroom.proxy.app.RequestAuthenticatorImpl;
import stroom.proxy.app.event.EventResourceImpl;
import stroom.proxy.app.forwarder.FailureDestinationsImpl;
import stroom.proxy.app.forwarder.ForwarderDestinationsImpl;
import stroom.proxy.app.handler.ProxyId;
import stroom.proxy.app.handler.ProxyRequestHandler;
import stroom.proxy.app.handler.RemoteFeedStatusService;
import stroom.proxy.app.servlet.ProxySecurityFilter;
import stroom.proxy.app.servlet.ProxyStatusServlet;
import stroom.proxy.app.servlet.ProxyWelcomeServlet;
import stroom.proxy.repo.ErrorReceiver;
import stroom.proxy.repo.ErrorReceiverImpl;
import stroom.proxy.repo.FailureDestinations;
import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.ProgressLog;
import stroom.proxy.repo.ProgressLogImpl;
import stroom.proxy.repo.ProxyDbModule;
import stroom.proxy.repo.RepoDbDirProvider;
import stroom.proxy.repo.RepoDbDirProviderImpl;
import stroom.proxy.repo.RepoDirProvider;
import stroom.proxy.repo.RepoDirProviderImpl;
import stroom.proxy.repo.Sender;
import stroom.proxy.repo.SenderImpl;
import stroom.receive.common.DataReceiptPolicyAttributeMapFilterFactory;
import stroom.receive.common.DebugServlet;
import stroom.receive.common.FeedStatusResourceImpl;
import stroom.receive.common.FeedStatusService;
import stroom.receive.common.ReceiveDataServlet;
import stroom.receive.common.RemoteFeedModule;
import stroom.receive.common.RequestHandler;
import stroom.receive.rules.impl.DataReceiptPolicyAttributeMapFilterFactoryImpl;
import stroom.receive.rules.impl.ReceiveDataRuleSetResourceImpl;
import stroom.receive.rules.impl.ReceiveDataRuleSetService;
import stroom.receive.rules.impl.ReceiveDataRuleSetServiceImpl;
import stroom.security.api.RequestAuthenticator;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.task.impl.TaskContextModule;
import stroom.util.BuildInfoProvider;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.guice.AdminServletBinder;
import stroom.util.guice.FilterBinder;
import stroom.util.guice.FilterInfo;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.guice.ServletBinder;
import stroom.util.io.PathCreator;
import stroom.util.shared.BuildInfo;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import javax.ws.rs.client.Client;
import javax.ws.rs.ext.ExceptionMapper;

public class ProxyModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyModule.class);

    private final Config configuration;
    private final Environment environment;
    private final ProxyConfigHolder proxyConfigHolder;

    public ProxyModule(final Config configuration,
                       final Environment environment,
                       final Path configFile) {
        this.configuration = configuration;
        this.environment = environment;

        proxyConfigHolder = new ProxyConfigHolder(
                configuration.getProxyConfig(),
                configFile);
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);
        bind(Environment.class).toInstance(environment);
        bind(HealthCheckRegistry.class).toInstance(environment.healthChecks());
        bind(ProxyId.class).asEagerSingleton();

        install(new ProxyConfigModule(proxyConfigHolder));
        install(new DbModule());
        install(new ProxyDbModule());
        install(new MockCollectionModule());

        install(new DictionaryModule());
        // Allow discovery of feed status from other proxies.
        install(new RemoteFeedModule());

        install(new TaskContextModule());

        bind(BuildInfo.class).toProvider(BuildInfoProvider.class);
//        bind(BufferFactory.class).to(BufferFactoryImpl.class);
        bind(DataReceiptPolicyAttributeMapFilterFactory.class).to(DataReceiptPolicyAttributeMapFilterFactoryImpl.class);
        bind(DocumentResourceHelper.class).to(DocumentResourceHelperImpl.class);
        bind(ErrorReceiver.class).to(ErrorReceiverImpl.class);
        bind(FeedStatusService.class).to(RemoteFeedStatusService.class);
        bind(RequestAuthenticator.class).to(RequestAuthenticatorImpl.class).asEagerSingleton();
        bind(ReceiveDataRuleSetService.class).to(ReceiveDataRuleSetServiceImpl.class);
        bind(RequestHandler.class).to(ProxyRequestHandler.class);
        bind(SecurityContext.class).to(MockSecurityContext.class);
        bind(Serialiser2Factory.class).to(Serialiser2FactoryImpl.class);
        bind(StoreFactory.class).to(StoreFactoryImpl.class);
        bind(ForwarderDestinations.class).to(ForwarderDestinationsImpl.class);
        bind(FailureDestinations.class).to(FailureDestinationsImpl.class);
        bind(Sender.class).to(SenderImpl.class);
        bind(ProgressLog.class).to(ProgressLogImpl.class);

        bind(RepoDirProvider.class).to(RepoDirProviderImpl.class);
        bind(RepoDbDirProvider.class).to(RepoDbDirProviderImpl.class);

        // Proxy doesn't do import so bind a dummy ImportConverter for the StoreImpl(s) to use
        bind(ImportConverter.class).to(NoOpImportConverter.class);

        bind(Client.class).toProvider(ProxyJerseyClientProvider.class);

        HasHealthCheckBinder.create(binder())
                .bind(ContentSyncService.class)
                .bind(FeedStatusResourceImpl.class)
                .bind(LogLevelInspector.class)
                .bind(ProxyConfigHealthCheck.class)
                .bind(RemoteFeedStatusService.class);

        FilterBinder.create(binder())
                .bind(new FilterInfo(ProxySecurityFilter.class.getSimpleName(), "/*"),
                        ProxySecurityFilter.class);

        ServletBinder.create(binder())
                .bind(DebugServlet.class)
                .bind(ProxyStatusServlet.class)
                .bind(ProxyWelcomeServlet.class)
                .bind(ReceiveDataServlet.class);

        AdminServletBinder.create(binder())
                .bind(FilteredHealthCheckServlet.class);

        RestResourcesBinder.create(binder())
                .bind(ReceiveDataRuleSetResourceImpl.class)
                .bind(FeedStatusResourceImpl.class)
                .bind(EventResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(ContentSyncService.class)
                .addBinding(ProxyLifecycle.class)
                .addBinding(RemoteFeedStatusService.class);

        GuiceUtil.buildMultiBinder(binder(), ExceptionMapper.class)
                .addBinding(PermissionExceptionMapper.class)
                .addBinding(TokenExceptionMapper.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(ReceiveDataRuleSetService.class)
                .addBinding(DictionaryStore.class);
    }

    @SuppressWarnings("unused")
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
