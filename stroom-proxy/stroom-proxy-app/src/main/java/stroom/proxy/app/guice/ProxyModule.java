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
import stroom.dropwizard.common.LogLevelInspector;
import stroom.dropwizard.common.PermissionExceptionMapper;
import stroom.dropwizard.common.TokenExceptionMapper;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.legacy.impex_6_1.LegacyImpexModule;
import stroom.proxy.app.Config;
import stroom.proxy.app.ContentSyncService;
import stroom.proxy.app.ProxyConfigHealthCheck;
import stroom.proxy.app.ProxyConfigHolder;
import stroom.proxy.app.ProxyLifecycle;
import stroom.proxy.app.RequestAuthenticatorImpl;
import stroom.proxy.app.RestClientConfig;
import stroom.proxy.app.RestClientConfigConverter;
import stroom.proxy.app.forwarder.ForwarderDestinationsImpl;
import stroom.proxy.app.handler.ProxyRequestHandler;
import stroom.proxy.app.handler.RemoteFeedStatusService;
import stroom.proxy.app.servlet.ProxySecurityFilter;
import stroom.proxy.app.servlet.ProxyStatusServlet;
import stroom.proxy.app.servlet.ProxyWelcomeServlet;
import stroom.proxy.repo.ErrorReceiver;
import stroom.proxy.repo.ErrorReceiverImpl;
import stroom.proxy.repo.ForwarderDestinations;
import stroom.proxy.repo.ProgressLog;
import stroom.proxy.repo.ProgressLogImpl;
import stroom.proxy.repo.ProxyRepoDbModule;
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
import stroom.util.guice.FilterBinder;
import stroom.util.guice.FilterInfo;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.guice.ServletBinder;
import stroom.util.io.PathCreator;
import stroom.util.shared.BuildInfo;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.client.ssl.TlsConfiguration;
import io.dropwizard.lifecycle.Managed;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.logging.LoggingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;
import javax.inject.Provider;
import javax.ws.rs.client.Client;
import javax.ws.rs.ext.ExceptionMapper;

public class ProxyModule extends AbstractModule {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProxyModule.class);

    // This name is used by dropwizard metrics
    private static final String PROXY_JERSEY_CLIENT_NAME = "stroom-proxy_jersey_client";
    private static final String PROXY_JERSEY_CLIENT_USER_AGENT_PREFIX = "stroom-proxy/";

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
        bind(RequestAuthenticator.class).to(RequestAuthenticatorImpl.class).asEagerSingleton();
        bind(ReceiveDataRuleSetService.class).to(ReceiveDataRuleSetServiceImpl.class);
        bind(RequestHandler.class).to(ProxyRequestHandler.class);
        bind(SecurityContext.class).to(MockSecurityContext.class);
        bind(Serialiser2Factory.class).to(Serialiser2FactoryImpl.class);
        bind(StoreFactory.class).to(StoreFactoryImpl.class);
        bind(ForwarderDestinations.class).to(ForwarderDestinationsImpl.class);
        bind(Sender.class).to(SenderImpl.class);
        bind(ProgressLog.class).to(ProgressLogImpl.class);

        bind(RepoDirProvider.class).to(RepoDirProviderImpl.class);
        bind(RepoDbDirProvider.class).to(RepoDbDirProviderImpl.class);

        HasHealthCheckBinder.create(binder())
                .bind(ContentSyncService.class)
                .bind(FeedStatusResourceImpl.class)
                .bind(ForwarderDestinationsImpl.class)
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

        RestResourcesBinder.create(binder())
                .bind(ReceiveDataRuleSetResourceImpl.class)
                .bind(FeedStatusResourceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(ContentSyncService.class)
                .addBinding(ProxyLifecycle.class);

        GuiceUtil.buildMultiBinder(binder(), ExceptionMapper.class)
                .addBinding(PermissionExceptionMapper.class)
                .addBinding(TokenExceptionMapper.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(ReceiveDataRuleSetService.class)
                .addBinding(DictionaryStore.class);
    }

    @Provides
    @Singleton
    Persistence providePersistence(final PathCreator pathCreator) {
        final String path = configuration.getProxyConfig().getContentDir();
        return new FSPersistence(pathCreator.toAppPath(path));
    }

    @Provides
    @Singleton
    Client provideJerseyClient(final RestClientConfig restClientConfig,
                               final Environment environment,
                               final Provider<BuildInfo> buildInfoProvider,
                               final PathCreator pathCreator,
                               final RestClientConfigConverter restClientConfigConverter) {

        // RestClientConfig is really just a copy of JerseyClientConfiguration
        // so do the conversion
        final JerseyClientConfiguration jerseyClientConfiguration = restClientConfigConverter.convert(
                restClientConfig);

        // If the userAgent has not been explicitly set in the config then set it based
        // on the build version
        if (jerseyClientConfiguration.getUserAgent().isEmpty()) {
            final String userAgent = PROXY_JERSEY_CLIENT_USER_AGENT_PREFIX
                    + buildInfoProvider.get().getBuildVersion();
            LOGGER.info("Setting rest client user agent string to [{}]", userAgent);
            jerseyClientConfiguration.setUserAgent(Optional.of(userAgent));
        }

        // Mutating the TLS config is not ideal but I'm not sure there is another way.
        // We need to allow for relative paths (relative to proxy home), '~', and other system
        // props in the path. Therefore if path creator produces a different path to what was
        // configured then update the config object.
        final TlsConfiguration tlsConfiguration = jerseyClientConfiguration.getTlsConfiguration();
        if (tlsConfiguration != null) {
            if (tlsConfiguration.getKeyStorePath() != null) {
                final File modifiedKeyStorePath = pathCreator.toAppPath(tlsConfiguration.getKeyStorePath().getPath())
                        .toFile();

                if (!modifiedKeyStorePath.getPath().equals(tlsConfiguration.getKeyStorePath().getPath())) {
                    LOGGER.info("Updating rest client key store path from {} to {}",
                            tlsConfiguration.getKeyStorePath(),
                            modifiedKeyStorePath);
                    tlsConfiguration.setKeyStorePath(modifiedKeyStorePath);
                }
            }

            if (tlsConfiguration.getTrustStorePath() != null) {
                final File modifiedTrustStorePath = pathCreator.toAppPath(
                        tlsConfiguration.getTrustStorePath().getPath()).toFile();

                if (!modifiedTrustStorePath.getPath().equals(tlsConfiguration.getTrustStorePath().getPath())) {
                    LOGGER.info("Updating rest client trust store path from {} to {}",
                            tlsConfiguration.getTrustStorePath(),
                            modifiedTrustStorePath);
                    tlsConfiguration.setTrustStorePath(modifiedTrustStorePath);
                }
            }
        }

        LOGGER.info("Creating jersey rest client {}", PROXY_JERSEY_CLIENT_NAME);
        return new JerseyClientBuilder(environment)
                .using(jerseyClientConfiguration)
                .build(PROXY_JERSEY_CLIENT_NAME)
                .register(LoggingFeature.class);
    }

    @Provides
    EntityEventBus entityEventBus() {
        return event -> {
        };
    }
}
