package stroom.config.app;

import stroom.activity.impl.db.ActivityConfig;
import stroom.activity.impl.db.ActivityConfig.ActivityDbConfig;
import stroom.annotation.impl.AnnotationConfig;
import stroom.annotation.impl.AnnotationConfig.AnnotationDBConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig.ClusterLockDbConfig;
import stroom.config.app.PropertyServiceConfig.PropertyServiceDbConfig;
import stroom.config.common.CommonDbConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig.DataStoreServiceDbConfig;
import stroom.docstore.impl.db.DocStoreConfig;
import stroom.docstore.impl.db.DocStoreConfig.DocStoreDbConfig;
import stroom.explorer.impl.ExplorerConfig;
import stroom.explorer.impl.ExplorerConfig.ExplorerDbConfig;
import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexConfig.IndexDbConfig;
import stroom.job.impl.JobSystemConfig;
import stroom.job.impl.JobSystemConfig.JobSystemDbConfig;
import stroom.meta.impl.MetaServiceConfig;
import stroom.meta.impl.MetaServiceConfig.MetaServiceDbConfig;
import stroom.node.impl.NodeConfig;
import stroom.node.impl.NodeConfig.NodeDbConfig;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorConfig.ProcessorDbConfig;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.IdentityConfig.IdentityDbConfig;
import stroom.security.impl.AuthorisationConfig;
import stroom.security.impl.AuthorisationConfig.AuthorisationDbConfig;
import stroom.statistics.impl.sql.SQLStatisticsConfig;
import stroom.statistics.impl.sql.SQLStatisticsConfig.SQLStatisticsDbConfig;
import stroom.storedquery.impl.StoredQueryConfig;
import stroom.storedquery.impl.StoredQueryConfig.StoredQueryDbConfig;
import stroom.util.config.ConfigLocation;
import stroom.util.io.PathConfig;
import stroom.util.io.StroomPathConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.google.inject.AbstractModule;

public class AppConfigModule extends AbstractModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AppConfigModule.class);

    private final ConfigHolder configHolder;

    public AppConfigModule(final ConfigHolder configHolder) {
        this.configHolder = configHolder;
    }

    @Override
    protected void configure() {
        LOGGER.debug(() ->
                "Binding appConfig with id " + System.identityHashCode(configHolder.getBootStrapConfig()));

        bind(ConfigHolder.class).toInstance(configHolder);

        // Bind the de-serialised yaml config to a singleton AppConfig object, whose parts
        // can be injected all over the app.
        // ConfigMapper is responsible for mutating it if the yaml file or database props change.
//        bind(AppConfig.class).toProvider(configHolder::getBootStrapConfig);

        // Holder for the location of the yaml config file so the AppConfigMonitor can
        // get hold of it via guice
        bind(ConfigLocation.class)
                .toInstance(new ConfigLocation(configHolder.getConfigFile()));

        // Bind the AbstractDbConfig instances in bootStrapConfig so we can inject the config
        // to connect to the DBs. Once the DB is up we can read the db config props and work
        // out all the effective config.
        bindBootstrapConfigInstances();
    }

    private void bindBootstrapConfigInstances() {
        final AppConfig bootStrapConfig = configHolder.getBootStrapConfig();

        // PathConfig is not settable via the DB so bind here
        bind(PathConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getPathConfig)
                        .orElseGet(StroomPathConfig::new));

        // StroomPathConfig is not settable via the DB so bind here
        bind(StroomPathConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getPathConfig)
                        .orElseGet(StroomPathConfig::new));

        bind(CommonDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getCommonDbConfig)
                        .orElseGet(CommonDbConfig::new));

        bind(ActivityDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getActivityConfig,
                                ActivityConfig::getDbConfig)
                        .orElseGet(ActivityDbConfig::new));

        bind(AnnotationDBConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getAnnotationConfig,
                                AnnotationConfig::getDbConfig)
                        .orElseGet(AnnotationDBConfig::new));

        bind(AuthorisationDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getSecurityConfig,
                                SecurityConfig::getAuthorisationConfig,
                                AuthorisationConfig::getDbConfig)
                        .orElseGet(AuthorisationDbConfig::new));

        bind(ClusterLockDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getClusterLockConfig,
                                ClusterLockConfig::getDbConfig)
                        .orElseGet(ClusterLockDbConfig::new));

        bind(DataStoreServiceDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getDataConfig,
                                DataConfig::getDataStoreServiceConfig,
                                DataStoreServiceConfig::getDbConfig)
                        .orElseGet(DataStoreServiceDbConfig::new));

        bind(DocStoreDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getDocStoreConfig,
                                DocStoreConfig::getDbConfig)
                        .orElseGet(DocStoreDbConfig::new));

        bind(ExplorerDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getExplorerConfig,
                                ExplorerConfig::getDbConfig)
                        .orElseGet(ExplorerDbConfig::new));

        bind(IdentityDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getSecurityConfig,
                                SecurityConfig::getIdentityConfig,
                                IdentityConfig::getDbConfig)
                        .orElseGet(IdentityDbConfig::new));

        bind(IndexDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getIndexConfig,
                                IndexConfig::getDbConfig)
                        .orElseGet(IndexDbConfig::new));

        bind(JobSystemDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getJobSystemConfig,
                                JobSystemConfig::getDbConfig)
                        .orElseGet(JobSystemDbConfig::new));

        bind(MetaServiceDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getDataConfig,
                                DataConfig::getMetaServiceConfig,
                                MetaServiceConfig::getDbConfig)
                        .orElseGet(MetaServiceDbConfig::new));

        bind(NodeDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getNodeConfig,
                                NodeConfig::getDbConfig)
                        .orElseGet(NodeDbConfig::new));

        bind(ProcessorDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getProcessorConfig,
                                ProcessorConfig::getDbConfig)
                        .orElseGet(ProcessorDbConfig::new));

        bind(PropertyServiceDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getPropertyServiceConfig,
                                PropertyServiceConfig::getDbConfig)
                        .orElseGet(PropertyServiceDbConfig::new));

        bind(SQLStatisticsDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getStatisticsConfig,
                                StatisticsConfig::getSqlStatisticsConfig,
                                SQLStatisticsConfig::getDbConfig)
                        .orElseGet(SQLStatisticsDbConfig::new));

        bind(StoredQueryDbConfig.class)
                .toInstance(NullSafe.getAsOptional(
                                bootStrapConfig,
                                AppConfig::getStoredQueryConfig,
                                StoredQueryConfig::getDbConfig)
                        .orElseGet(StoredQueryDbConfig::new));
    }

    protected ConfigHolder getConfigHolder() {
        return configHolder;
    }
}
