package stroom.proxy.app.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import stroom.dictionary.api.DictionaryStore;
import stroom.dictionary.impl.DictionaryResource;
import stroom.dictionary.impl.DictionaryResource2;
import stroom.docstore.impl.Persistence;
import stroom.docstore.impl.fs.FSPersistence;
import stroom.dropwizard.common.LogLevelInspector;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.proxy.app.Config;
import stroom.proxy.app.ContentSyncService;
import stroom.proxy.app.NoSecurityContext;
import stroom.proxy.app.ProxyConfigHealthCheck;
import stroom.proxy.app.handler.ForwardStreamHandlerFactory;
import stroom.proxy.app.handler.ProxyRequestHandler;
import stroom.proxy.app.handler.RemoteFeedStatusService;
import stroom.proxy.app.servlet.ConfigServlet;
import stroom.proxy.app.servlet.ProxySecurityFilter;
import stroom.proxy.app.servlet.ProxyStatusServlet;
import stroom.proxy.app.servlet.ProxyWelcomeServlet;
import stroom.proxy.repo.ProxyLifecycle;
import stroom.proxy.repo.ProxyRepositoryManager;
import stroom.proxy.repo.ProxyRepositoryReader;
import stroom.proxy.repo.StreamHandlerFactory;
import stroom.receive.common.DebugServlet;
import stroom.receive.common.FeedStatusService;
import stroom.receive.common.ReceiveDataServlet;
import stroom.receive.common.RemoteFeedModule;
import stroom.receive.common.RequestHandler;
import stroom.receive.rules.impl.ReceiveDataRuleSetResource;
import stroom.receive.rules.impl.ReceiveDataRuleSetResource2;
import stroom.receive.rules.impl.ReceiveDataRuleSetService;
import stroom.receive.rules.impl.ReceiveDataRuleSetServiceImpl;
import stroom.security.api.SecurityContext;
import stroom.util.RestResource;
import stroom.util.guice.FilterBinder;
import stroom.util.guice.FilterInfo;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HealthCheckBinder;
import stroom.util.guice.ResourcePaths;
import stroom.util.guice.ServletBinder;

import javax.ws.rs.client.Client;
import java.nio.file.Paths;

public class ProxyModule extends AbstractModule {
    private final Config configuration;
    private final Environment environment;
    private final Client jerseyClient;

    public ProxyModule(final Config configuration, final Environment environment, final Client jerseyClient) {
        this.configuration = configuration;
        this.environment = environment;
        this.jerseyClient = jerseyClient;
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);
        bind(Environment.class).toInstance(environment);
        // Bind the dropwizard managed jersey client
        bind(Client.class).toInstance(this.jerseyClient);

        install(new ProxyConfigModule(configuration.getProxyConfig()));

        // Allow discovery of feed status from other proxies.
        install(new RemoteFeedModule());

        bind(RequestHandler.class).to(ProxyRequestHandler.class);
        bind(StreamHandlerFactory.class).to(ForwardStreamHandlerFactory.class);
        bind(ProxyRepositoryManager.class).asEagerSingleton();
        bind(ProxyRepositoryReader.class).asEagerSingleton();
        bind(ReceiveDataRuleSetService.class).to(ReceiveDataRuleSetServiceImpl.class);
        bind(SecurityContext.class).to(NoSecurityContext.class);
        bind(FeedStatusService.class).to(RemoteFeedStatusService.class);

        HealthCheckBinder.create(binder())
                .bind(ContentSyncService.class)
                .bind(ForwardStreamHandlerFactory.class)
                .bind(LogLevelInspector.class)
                .bind(ProxyConfigHealthCheck.class)
        .bind(DictionaryResource.class)
       .bind(RuleSetResource.class)
       .bind(FeedStatusResource.class)
        .bind(RemoteFeedStatusService.class);

        FilterBinder.create(binder())
                .bind(new FilterInfo(ProxySecurityFilter.class.getSimpleName(), "/*"), ProxySecurityFilter.class);

        ServletBinder.create(binder())
                .bind(ResourcePaths.ROOT_PATH + "/config", ConfigServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/datafeed", ReceiveDataServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/datafeed/*", ReceiveDataServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/debug", DebugServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/status", ProxyStatusServlet.class)
                .bind(ResourcePaths.ROOT_PATH + "/ui", ProxyWelcomeServlet.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(DictionaryResource.class)
                .addBinding(DictionaryResource2.class)
                .addBinding(ReceiveDataRuleSetResource.class)
                .addBinding(ReceiveDataRuleSetResource2.class)
                .addBinding(FeedStatusResource.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(ContentSyncService.class)
                .addBinding(ProxyLifecycle.class);

        final Multibinder<ImportExportActionHandler> importExportActionHandlerBinder = Multibinder.newSetBinder(binder(), ImportExportActionHandler.class);
            importExportActionHandlerBinder.addBinding().to(ReceiveDataRuleSetService.class);
            importExportActionHandlerBinder.addBinding().to(DictionaryStore.class);
    }

    @Provides
    @Singleton
    Persistence providePersistence() {
        return new FSPersistence(Paths.get(configuration.getProxyConfig().getProxyContentDir()));
    }
}
