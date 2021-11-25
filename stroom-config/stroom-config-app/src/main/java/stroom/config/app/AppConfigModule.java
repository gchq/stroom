package stroom.config.app;

import stroom.util.config.ConfigLocation;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;

import com.google.inject.AbstractModule;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class AppConfigModule extends AbstractModule {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AppConfigModule.class);

    private final ConfigHolder configHolder;

    public AppConfigModule(final ConfigHolder configHolder) {
        this.configHolder = configHolder;
    }

    @Override
    protected void configure() {
        LOGGER.debug(() ->
                "Binding appConfig with id " + System.identityHashCode(configHolder.getAppConfig()));

        bind(ConfigHolder.class).toInstance(configHolder);

        // Bind the de-serialised yaml config to a singleton AppConfig object, whose parts
        // can be injected all over the app.
        // ConfigMapper is responsible for mutating it if the yaml file or database props change.
        bind(AppConfig.class).toProvider(configHolder::getAppConfig);

        // Holder for the location of the yaml config file so the AppConfigMonitor can
        // get hold of it via guice
        bind(ConfigLocation.class)
                .toInstance(new ConfigLocation(configHolder.getConfigFile()));

        // AppConfig will instantiate all of its child config objects so
        // bind each of these instances so we can inject these objects on their own.
        // This allows gradle modules to know nothing about the other modules.
        // Our bind method has the arguments in the reverse way to guice so we can
        // more easily see the tree structure. Each config pojo must extend AbstractConfig.
        // Some config objects are shared and thus should not be bound here and should be
        // annotated with NotInjectableConfig

        // WARNING If you don't bind the class here and then inject that config class then
        // you will get a vanilla instance that is not linked to the config yml or db props!
        // It is safer to bind everything to be on the safe side

//        bindConfig(AppConfig::getActivityConfig,
//                AppConfig::setActivityConfig,
//                stroom.activity.impl.db.ActivityConfig.class);
//        bindConfig(AppConfig::getAnnotationConfig,
//                AppConfig::setAnnotationConfig,
//                stroom.annotation.impl.AnnotationConfig.class);
//        bindConfig(AppConfig::getByteBufferPoolConfig, AppConfig::setByteBufferPoolConfig, ByteBufferPoolConfig.class);
//        bindConfig(AppConfig::getClusterConfig, AppConfig::setClusterConfig, ClusterConfig.class);
//        bindConfig(AppConfig::getClusterLockConfig, AppConfig::setClusterLockConfig, ClusterLockConfig.class);
//        bindConfig(AppConfig::getCommonDbConfig, AppConfig::setCommonDbConfig, CommonDbConfig.class);
//        bindConfig(AppConfig::getContentPackImportConfig,
//                AppConfig::setContentPackImportConfig,
//                ContentPackImportConfig.class);
//        bindConfig(AppConfig::getLegacyDbConfig, AppConfig::setLegacyDbConfig, LegacyDbConfig.class);
//        bindConfig(AppConfig::getDashboardConfig, AppConfig::setDashboardConfig, DashboardConfig.class);
//        bindConfig(AppConfig::getDataConfig, AppConfig::setDataConfig, DataConfig.class, dataConfig -> {
//            bindConfig(dataConfig,
//                    DataConfig::getDataRetentionConfig,
//                    DataConfig::setDataRetentionConfig,
//                    DataRetentionConfig.class);
//            bindConfig(dataConfig,
//                    DataConfig::getDataStoreServiceConfig,
//                    DataConfig::setDataStoreServiceConfig,
//                    DataStoreServiceConfig.class);
//            bindConfig(dataConfig, DataConfig::getFsVolumeConfig, DataConfig::setFsVolumeConfig, FsVolumeConfig.class);
//            bindConfig(dataConfig,
//                    DataConfig::getMetaServiceConfig,
//                    DataConfig::setMetaServiceConfig,
//                    MetaServiceConfig.class,
//                    metaServiceConfig ->
//                            bindConfig(metaServiceConfig,
//                                    MetaServiceConfig::getMetaValueConfig,
//                                    MetaServiceConfig::setMetaValueConfig,
//                                    MetaValueConfig.class));
//        });
//        bindConfig(AppConfig::getDataSourceUrlConfig, AppConfig::setDataSourceUrlConfig, DataSourceUrlConfig.class);
//        bindConfig(AppConfig::getDocStoreConfig, AppConfig::setDocStoreConfig, DocStoreConfig.class);
//        bindConfig(AppConfig::getExplorerConfig, AppConfig::setExplorerConfig, ExplorerConfig.class);
//        bindConfig(AppConfig::getExportConfig, AppConfig::setExportConfig, ExportConfig.class);
//        bindConfig(AppConfig::getFeedConfig, AppConfig::setFeedConfig, FeedConfig.class);
//        bindConfig(AppConfig::getIndexConfig, AppConfig::setIndexConfig, IndexConfig.class, indexConfig ->
//                bindConfig(indexConfig,
//                        IndexConfig::getIndexWriterConfig,
//                        IndexConfig::setIndexWriterConfig,
//                        IndexWriterConfig.class,
//                        indexWriterConfig ->
//                                bindConfig(indexWriterConfig,
//                                        IndexWriterConfig::getIndexCacheConfig,
//                                        IndexWriterConfig::setIndexCacheConfig,
//                                        IndexCacheConfig.class)));
//        bindConfig(AppConfig::getJobSystemConfig, AppConfig::setJobSystemConfig, JobSystemConfig.class);
//        bindConfig(AppConfig::getKafkaConfig, AppConfig::setKafkaConfig, KafkaConfig.class);
//        bindConfig(AppConfig::getLifecycleConfig, AppConfig::setLifecycleConfig, LifecycleConfig.class);
//        bindConfig(AppConfig::getLmdbLibraryConfig, AppConfig::setLmdbLibraryConfig, LmdbLibraryConfig.class);
//        bindConfig(AppConfig::getNodeConfig, AppConfig::setNodeConfig, NodeConfig.class, nodeConfig ->
//                bindConfig(nodeConfig,
//                        NodeConfig::getStatusConfig,
//                        NodeConfig::setStatusConfig,
//                        StatusConfig.class,
//                        statusConfig ->
//                                bindConfig(statusConfig,
//                                        StatusConfig::getHeapHistogramConfig,
//                                        StatusConfig::setHeapHistogramConfig,
//                                        HeapHistogramConfig.class)));
//        bindConfig(AppConfig::getNodeUri, AppConfig::setNodeUri, NodeUriConfig.class);
//        // Bind it to PathConfig and StroomPathConfig so classes that inject PathConfig work
//        bindConfig(AppConfig::getPathConfig, AppConfig::setPathConfig, StroomPathConfig.class, PathConfig.class);
//        bindConfig(AppConfig::getPipelineConfig, AppConfig::setPipelineConfig, PipelineConfig.class, pipelineConfig -> {
//            bindConfig(pipelineConfig,
//                    PipelineConfig::getAppenderConfig,
//                    PipelineConfig::setAppenderConfig,
//                    AppenderConfig.class);
//            bindConfig(pipelineConfig,
//                    PipelineConfig::getParserConfig,
//                    PipelineConfig::setParserConfig,
//                    ParserConfig.class);
//            bindConfig(pipelineConfig,
//                    PipelineConfig::getReferenceDataConfig,
//                    PipelineConfig::setReferenceDataConfig,
//                    ReferenceDataConfig.class,
//                    referenceDataConfig -> {
//                        bindConfig(referenceDataConfig,
//                                ReferenceDataConfig::getLmdbConfig,
//                                ReferenceDataConfig::setLmdbConfig,
//                                ReferenceDataLmdbConfig.class);
//                    });
//            bindConfig(pipelineConfig,
//                    PipelineConfig::getXmlSchemaConfig,
//                    PipelineConfig::setXmlSchemaConfig,
//                    XmlSchemaConfig.class);
//            bindConfig(pipelineConfig, PipelineConfig::getXsltConfig, PipelineConfig::setXsltConfig, XsltConfig.class);
//        });
//        bindConfig(AppConfig::getProcessorConfig, AppConfig::setProcessorConfig, ProcessorConfig.class);
//        bindConfig(AppConfig::getPropertyServiceConfig,
//                AppConfig::setPropertyServiceConfig,
//                PropertyServiceConfig.class);
//        bindConfig(AppConfig::getProxyAggregationConfig,
//                AppConfig::setProxyAggregationConfig,
//                ProxyAggregationConfig.class);
//        bindConfig(AppConfig::getPublicUri, AppConfig::setPublicUri, PublicUriConfig.class);
//        bindConfig(AppConfig::getReceiveDataConfig, AppConfig::setReceiveDataConfig, ReceiveDataConfig.class);
//        bindConfig(AppConfig::getRequestLoggingConfig, AppConfig::setRequestLoggingConfig, LoggingConfig.class);
//        bindConfig(AppConfig::getSearchConfig, AppConfig::setSearchConfig, SearchConfig.class, searchConfig -> {
//            bindConfig(searchConfig,
//                    SearchConfig::getExtractionConfig,
//                    SearchConfig::setExtractionConfig,
//                    ExtractionConfig.class);
//            bindConfig(searchConfig,
//                    SearchConfig::getLmdbConfig,
//                    SearchConfig::setLmdbConfig,
//                    ResultStoreConfig.class,
//                    resultStoreConfig -> {
//                        bindConfig(resultStoreConfig,
//                                ResultStoreConfig::getLmdbConfig,
//                                ResultStoreConfig::setLmdbConfig,
//                                ResultStoreLmdbConfig.class);
//                    });
//            bindConfig(searchConfig,
//                    SearchConfig::getShardConfig,
//                    SearchConfig::setShardConfig,
//                    IndexShardSearchConfig.class);
//        });
//        bindConfig(AppConfig::getSearchableConfig, AppConfig::setSearchableConfig, SearchableConfig.class);
//        bindConfig(AppConfig::getSecurityConfig, AppConfig::setSecurityConfig, SecurityConfig.class, securityConfig -> {
//            bindConfig(securityConfig,
//                    SecurityConfig::getAuthenticationConfig,
//                    SecurityConfig::setAuthenticationConfig,
//                    AuthenticationConfig.class,
//                    c2 ->
//                            bindConfig(c2,
//                                    AuthenticationConfig::getOpenIdConfig,
//                                    AuthenticationConfig::setOpenIdConfig,
//                                    stroom.security.impl.OpenIdConfig.class));
//            bindConfig(securityConfig,
//                    SecurityConfig::getContentSecurityConfig,
//                    SecurityConfig::setContentSecurityConfig,
//                    ContentSecurityConfig.class);
//            bindConfig(securityConfig,
//                    SecurityConfig::getAuthorisationConfig,
//                    SecurityConfig::setAuthorisationConfig,
//                    AuthorisationConfig.class);
//            bindConfig(securityConfig,
//                    SecurityConfig::getIdentityConfig,
//                    SecurityConfig::setIdentityConfig,
//                    IdentityConfig.class,
//                    identityConfig -> {
//                        bindConfig(identityConfig,
//                                IdentityConfig::getEmailConfig,
//                                IdentityConfig::setEmailConfig,
//                                EmailConfig.class);
//                        bindConfig(identityConfig,
//                                IdentityConfig::getTokenConfig,
//                                IdentityConfig::setTokenConfig,
//                                TokenConfig.class);
//                        bindConfig(identityConfig,
//                                IdentityConfig::getOpenIdConfig,
//                                IdentityConfig::setOpenIdConfig,
//                                OpenIdConfig.class);
//                        bindConfig(identityConfig,
//                                IdentityConfig::getPasswordPolicyConfig,
//                                IdentityConfig::setPasswordPolicyConfig,
//                                PasswordPolicyConfig.class);
//                    });
//        });
//        bindConfig(AppConfig::getServiceDiscoveryConfig,
//                AppConfig::setServiceDiscoveryConfig,
//                ServiceDiscoveryConfig.class);
//        bindConfig(AppConfig::getSessionCookieConfig, AppConfig::setSessionCookieConfig, SessionCookieConfig.class);
//        bindConfig(AppConfig::getSolrConfig, AppConfig::setSolrConfig, SolrConfig.class, solrConfig ->
//                bindConfig(solrConfig,
//                        SolrConfig::getSolrSearchConfig,
//                        SolrConfig::setSolrSearchConfig,
//                        SolrSearchConfig.class));
//        bindConfig(AppConfig::getStatisticsConfig,
//                AppConfig::setStatisticsConfig,
//                StatisticsConfig.class,
//                statisticsConfig -> {
//                    bindConfig(statisticsConfig,
//                            StatisticsConfig::getHbaseStatisticsConfig,
//                            StatisticsConfig::setHbaseStatisticsConfig,
//                            HBaseStatisticsConfig.class,
//                            hBaseStatisticsConfig ->
//                                    bindConfig(hBaseStatisticsConfig,
//                                            HBaseStatisticsConfig::getKafkaTopicsConfig,
//                                            HBaseStatisticsConfig::setKafkaTopicsConfig,
//                                            KafkaTopicsConfig.class));
//                    bindConfig(statisticsConfig,
//                            StatisticsConfig::getInternalStatisticsConfig,
//                            StatisticsConfig::setInternalStatisticsConfig,
//                            InternalStatisticsConfig.class);
//                    bindConfig(statisticsConfig,
//                            StatisticsConfig::getSqlStatisticsConfig,
//                            StatisticsConfig::setSqlStatisticsConfig,
//                            SQLStatisticsConfig.class,
//                            sqlStatisticsConfig ->
//                                    bindConfig(sqlStatisticsConfig,
//                                            SQLStatisticsConfig::getSearchConfig,
//                                            SQLStatisticsConfig::setSearchConfig,
//                                            stroom.statistics.impl.sql.search.SearchConfig.class));
//                });
//        bindConfig(AppConfig::getStoredQueryConfig, AppConfig::setStoredQueryConfig, StoredQueryConfig.class);
//        bindConfig(AppConfig::getUiConfig, AppConfig::setUiConfig, UiConfig.class, uiConfig -> {
//            bindConfig(uiConfig, UiConfig::getActivity, UiConfig::setActivity, ActivityConfig.class);
//            bindConfig(uiConfig,
//                    UiConfig::getProcess,
//                    UiConfig::setProcess,
//                    stroom.ui.config.shared.ProcessConfig.class);
//            bindConfig(uiConfig, UiConfig::getQuery, UiConfig::setQuery, QueryConfig.class, queryConfig ->
//                    bindConfig(queryConfig,
//                            QueryConfig::getInfoPopup,
//                            QueryConfig::setInfoPopup,
//                            InfoPopupConfig.class));
//            bindConfig(uiConfig, UiConfig::getSplash, UiConfig::setSplash, SplashConfig.class);
//            bindConfig(uiConfig, UiConfig::getTheme, UiConfig::setTheme, ThemeConfig.class);
//            bindConfig(uiConfig, UiConfig::getUiPreferences, UiConfig::setUiPreferences, UiPreferences.class);
//            bindConfig(uiConfig, UiConfig::getSource, UiConfig::setSource, SourceConfig.class);
//        });
//        bindConfig(AppConfig::getUiUri, AppConfig::setUiUri, UiUriConfig.class);
//        bindConfig(AppConfig::getVolumeConfig, AppConfig::setVolumeConfig, VolumeConfig.class);
    }

    private <T extends AbstractConfig> void bindConfig(
            final Function<AppConfig, T> configGetter,
            final BiConsumer<AppConfig, T> configSetter,
            final Class<T> clazz) {
        bindConfig(configHolder.getAppConfig(), configGetter, configSetter, clazz, clazz, null);
    }

    private <T extends AbstractConfig> void bindConfig(
            final Function<AppConfig, T> configGetter,
            final BiConsumer<AppConfig, T> configSetter,
            final Class<T> instanceClass,
            final Class<? super T> bindClass) {
        bindConfig(configHolder.getAppConfig(), configGetter, configSetter, instanceClass, bindClass, null);
    }

    private <T extends AbstractConfig> void bindConfig(
            final Function<AppConfig, T> configGetter,
            final BiConsumer<AppConfig, T> configSetter,
            final Class<T> clazz,
            final Consumer<T> childConfigConsumer) {
        bindConfig(configHolder.getAppConfig(), configGetter, configSetter, clazz, clazz, childConfigConsumer);
    }

    private <X extends AbstractConfig, T extends AbstractConfig> void bindConfig(
            final X parentObject,
            final Function<X, T> configGetter,
            final BiConsumer<X, T> configSetter,
            final Class<T> clazz) {
        bindConfig(parentObject, configGetter, configSetter, clazz, clazz, null);
    }

    private <X extends AbstractConfig, T extends AbstractConfig> void bindConfig(
            final X parentObject,
            final Function<X, T> configGetter,
            final BiConsumer<X, T> configSetter,
            final Class<T> clazz,
            final Consumer<T> childConfigConsumer) {
        bindConfig(parentObject, configGetter, configSetter, clazz, clazz, childConfigConsumer);
    }

    private <X extends AbstractConfig, T extends AbstractConfig> void bindConfig(
            final X parentObject,
            final Function<X, T> configGetter,
            final BiConsumer<X, T> configSetter,
            final Class<T> instanceClass,
            final Class<? super T> bindClass,
            final Consumer<T> childConfigConsumer) {

        // If a class is marked with NotInjectableConfig then it is likely used by multiple parent config
        // classes so should be accessed via its parent rather than by injection
        if (instanceClass.isAnnotationPresent(NotInjectableConfig.class)) {
            throw new RuntimeException(LogUtil.message(
                    "You should not be binding an instance class annotated with {} - {}",
                    NotInjectableConfig.class.getSimpleName(),
                    instanceClass.getName()));
        }
        if (bindClass.isAnnotationPresent(NotInjectableConfig.class)) {
            throw new RuntimeException(LogUtil.message("You should not be binding a bind class annotated with {} - {}",
                    NotInjectableConfig.class.getSimpleName(),
                    bindClass.getName()));
        }
        if (parentObject == null) {
            throw new RuntimeException(LogUtil.message("Unable to bind config to {} as the parent is null. " +
                            "You may have an empty branch in your config YAML file.",
                    instanceClass.getCanonicalName()));
        }

        try {
            // Get the config instance
            T configInstance = configGetter.apply(parentObject);

            if (configInstance == null) {
                // branch with no children in the yaml so just create a default one
                try {
                    LOGGER.debug(() -> LogUtil.message("Constructing new default instance of {} on {}",
                            instanceClass.getSimpleName(), parentObject.getClass().getSimpleName()));
                    configInstance = instanceClass.getConstructor().newInstance();
                    // Now set the new instance on the parent
                    configSetter.accept(parentObject, configInstance);
                } catch (Exception e) {
                    throw new RuntimeException(LogUtil.message(
                            "Class {} does not have a no args constructor", instanceClass.getName()));
                }
            }

            LOGGER.debug("Binding instance of {} to class {}", instanceClass.getName(), bindClass.getName());
            bind(bindClass).toInstance(configInstance);
            if (!bindClass.equals(instanceClass)) {
                // bind class and instance class differ so bind to both, e.g.
                // an instance of StroomPathConfig gets bound to PathConfig and ProxyPathConfig
                LOGGER.debug("Binding instance of {} to class {}", instanceClass.getName(), instanceClass.getName());
                bind(instanceClass).toInstance(configInstance);
            }
            if (childConfigConsumer != null) {
                childConfigConsumer.accept(configInstance);
            }
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error binding getter on object {} to class {}",
                    parentObject.getClass().getCanonicalName(),
                    bindClass.getCanonicalName()),
                    e);
        }
    }

    protected ConfigHolder getConfigHolder() {
        return configHolder;
    }
}
