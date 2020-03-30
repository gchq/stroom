package stroom.config.app;

import com.google.inject.AbstractModule;
import stroom.cluster.api.ClusterConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig;
import stroom.cluster.task.impl.ClusterTaskConfig;
import stroom.config.common.CommonDbConfig;
import stroom.core.benchmark.BenchmarkClusterConfig;
import stroom.core.db.CoreConfig;
import stroom.core.receive.ProxyAggregationConfig;
import stroom.core.receive.ReceiveDataConfig;
import stroom.dashboard.impl.DashboardConfig;
import stroom.dashboard.impl.datasource.DataSourceUrlConfig;
import stroom.data.retention.impl.DataRetentionConfig;
import stroom.data.store.impl.fs.DataStoreServiceConfig;
import stroom.data.store.impl.fs.FsVolumeConfig;
import stroom.docstore.impl.db.DocStoreConfig;
import stroom.explorer.impl.db.ExplorerConfig;
import stroom.feed.impl.FeedConfig;
import stroom.importexport.impl.ContentPackImportConfig;
import stroom.importexport.impl.ExportConfig;
import stroom.index.impl.IndexConfig;
import stroom.index.impl.selection.VolumeConfig;
import stroom.job.impl.JobSystemConfig;
import stroom.lifecycle.impl.LifecycleConfig;
import stroom.meta.impl.db.MetaServiceConfig;
import stroom.node.impl.HeapHistogramConfig;
import stroom.node.impl.NodeConfig;
import stroom.node.impl.StatusConfig;
import stroom.pipeline.PipelineConfig;
import stroom.pipeline.destination.AppenderConfig;
import stroom.pipeline.filter.XmlSchemaConfig;
import stroom.pipeline.filter.XsltConfig;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.processor.impl.ProcessorConfig;
import stroom.search.extraction.ExtractionConfig;
import stroom.search.impl.SearchConfig;
import stroom.search.impl.shard.IndexShardSearchConfig;
import stroom.search.solr.SolrConfig;
import stroom.search.solr.search.SolrSearchConfig;
import stroom.searchable.impl.SearchableConfig;
import stroom.security.impl.AuthenticationConfig;
import stroom.security.impl.ContentSecurityConfig;
import stroom.security.impl.OpenIdConfig;
import stroom.security.impl.SecurityConfig;
import stroom.servicediscovery.impl.ServiceDiscoveryConfig;
import stroom.statistics.impl.InternalStatisticsConfig;
import stroom.statistics.impl.hbase.internal.HBaseStatisticsConfig;
import stroom.statistics.impl.sql.SQLStatisticsConfig;
import stroom.storedquery.impl.StoredQueryConfig;
import stroom.ui.config.shared.ActivityConfig;
import stroom.ui.config.shared.QueryConfig;
import stroom.ui.config.shared.SplashConfig;
import stroom.ui.config.shared.ThemeConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UrlConfig;
import stroom.util.io.PathConfig;
import stroom.util.logging.LogUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.xml.ParserConfig;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

public class AppConfigModule extends AbstractModule {
    private final ConfigHolder configHolder;

    public AppConfigModule(final ConfigHolder configHolder) {
        this.configHolder = configHolder;
    }

    @Override
    protected void configure() {
        // Bind the de-serialised yaml config to a singleton AppConfig object, whose parts
        // can be injected all over the app.
        bind(AppConfig.class).toInstance(configHolder.getAppConfig());

        // Holder for the location of the yaml config file so the AppConfigMonitor can
        // get hold of it via guice
        bind(ConfigLocation.class)
                .toInstance(new ConfigLocation(configHolder.getConfigFile()));

        // AppConfig will instantiate all of its child config objects so
        // bind each of these instances so we can inject these objects on their own.
        // This allows gradle modules to know nothing about the other modules.
        // Our bind method has the arguments in the reverse way to guice so we can
        // more easily see the tree structure. Each config pojo must implement IsConfig

        bindConfig(AppConfig::getActivityConfig, stroom.activity.impl.db.ActivityConfig.class);
        bindConfig(AppConfig::getAnnotationConfig, stroom.annotation.impl.AnnotationConfig.class);
        bindConfig(AppConfig::getApiGatewayConfig, stroom.config.common.ApiGatewayConfig.class);
        bindConfig(AppConfig::getAuthenticationConfig, stroom.authentication.config.AuthenticationConfig.class);
        bindConfig(AppConfig::getBenchmarkClusterConfig, BenchmarkClusterConfig.class);
        bindConfig(AppConfig::getClusterConfig, ClusterConfig.class);
        bindConfig(AppConfig::getClusterLockConfig, ClusterLockConfig.class);
        bindConfig(AppConfig::getClusterTaskConfig, ClusterTaskConfig.class);
        bindConfig(AppConfig::getCommonDbConfig, CommonDbConfig.class);
        bindConfig(AppConfig::getContentPackImportConfig, ContentPackImportConfig.class);
        bindConfig(AppConfig::getCoreConfig, CoreConfig.class);
        bindConfig(AppConfig::getDashboardConfig, DashboardConfig.class);
        bindConfig(AppConfig::getDataConfig, DataConfig.class, dataConfig -> {
            bindConfig(dataConfig, DataConfig::getDataRetentionConfig, DataRetentionConfig.class);
            bindConfig(dataConfig, DataConfig::getDataStoreServiceConfig, DataStoreServiceConfig.class);
            bindConfig(dataConfig, DataConfig::getFsVolumeConfig, FsVolumeConfig.class);
            bindConfig(dataConfig, DataConfig::getMetaServiceConfig, MetaServiceConfig.class);
        });
        bindConfig(AppConfig::getDataSourceUrlConfig, DataSourceUrlConfig.class);
        bindConfig(AppConfig::getDocStoreConfig, DocStoreConfig.class);
        bindConfig(AppConfig::getExplorerConfig, ExplorerConfig.class);
        bindConfig(AppConfig::getExportConfig, ExportConfig.class);
        bindConfig(AppConfig::getFeedConfig, FeedConfig.class);
        bindConfig(AppConfig::getIndexConfig, IndexConfig.class);
        bindConfig(AppConfig::getJobSystemConfig, JobSystemConfig.class);
        bindConfig(AppConfig::getLifecycleConfig, LifecycleConfig.class);
        bindConfig(AppConfig::getNodeConfig, NodeConfig.class, nodeConfig ->
                bindConfig(nodeConfig, NodeConfig::getStatusConfig, StatusConfig.class, statusConfig ->
                        bindConfig(statusConfig, StatusConfig::getHeapHistogramConfig, HeapHistogramConfig.class)));
        bindConfig(AppConfig::getPathConfig, PathConfig.class);
        bindConfig(AppConfig::getPipelineConfig, PipelineConfig.class, pipelineConfig -> {
            bindConfig(pipelineConfig, PipelineConfig::getAppenderConfig, AppenderConfig.class);
            bindConfig(pipelineConfig, PipelineConfig::getParserConfig, ParserConfig.class);
            bindConfig(pipelineConfig, PipelineConfig::getReferenceDataConfig, ReferenceDataConfig.class);
            bindConfig(pipelineConfig, PipelineConfig::getXmlSchemaConfig, XmlSchemaConfig.class);
            bindConfig(pipelineConfig, PipelineConfig::getXsltConfig, XsltConfig.class);
        });
        bindConfig(AppConfig::getProcessorConfig, ProcessorConfig.class);
        bindConfig(AppConfig::getPropertyServiceConfig, PropertyServiceConfig.class);
        bindConfig(AppConfig::getProxyAggregationConfig, ProxyAggregationConfig.class);
        bindConfig(AppConfig::getReceiveDataConfig, ReceiveDataConfig.class);
        bindConfig(AppConfig::getSearchConfig, SearchConfig.class, searchConfig -> {
            bindConfig(searchConfig, SearchConfig::getExtractionConfig, ExtractionConfig.class);
            bindConfig(searchConfig, SearchConfig::getShardConfig, IndexShardSearchConfig.class);
        });
        bindConfig(AppConfig::getSearchableConfig, SearchableConfig.class);
        bindConfig(AppConfig::getSecurityConfig, SecurityConfig.class, securityConfig -> {
            bindConfig(securityConfig, SecurityConfig::getAuthenticationConfig, AuthenticationConfig.class, c2 ->
                    bindConfig(c2, AuthenticationConfig::getOpenIdConfig, OpenIdConfig.class));
            bindConfig(securityConfig, SecurityConfig::getContentSecurityConfig, ContentSecurityConfig.class);
        });
        bindConfig(AppConfig::getServiceDiscoveryConfig, ServiceDiscoveryConfig.class);
        bindConfig(AppConfig::getSessionCookieConfig, SessionCookieConfig.class);
        bindConfig(AppConfig::getSolrConfig, SolrConfig.class, solrConfig ->
                bindConfig(solrConfig, SolrConfig::getSolrSearchConfig, SolrSearchConfig.class));
        bindConfig(AppConfig::getStatisticsConfig, StatisticsConfig.class, statisticsConfig -> {
            bindConfig(statisticsConfig, StatisticsConfig::getHbaseStatisticsConfig, HBaseStatisticsConfig.class);
            bindConfig(statisticsConfig, StatisticsConfig::getInternalStatisticsConfig, InternalStatisticsConfig.class);
            bindConfig(statisticsConfig, StatisticsConfig::getSqlStatisticsConfig, SQLStatisticsConfig.class, sqlStatisticsConfig ->
                    bindConfig(sqlStatisticsConfig, SQLStatisticsConfig::getSearchConfig, stroom.statistics.impl.sql.search.SearchConfig.class));
        });
        bindConfig(AppConfig::getStoredQueryConfig, StoredQueryConfig.class);
        bindConfig(AppConfig::getUiConfig, UiConfig.class, uiConfig -> {
            bindConfig(uiConfig, UiConfig::getActivity, ActivityConfig.class);
            bindConfig(uiConfig, UiConfig::getProcess, stroom.ui.config.shared.ProcessConfig.class);
            bindConfig(uiConfig, UiConfig::getQuery, QueryConfig.class);
            bindConfig(uiConfig, UiConfig::getSplash, SplashConfig.class);
            bindConfig(uiConfig, UiConfig::getTheme, ThemeConfig.class);
            bindConfig(uiConfig, UiConfig::getUrl, UrlConfig.class);
        });
        bindConfig(AppConfig::getVolumeConfig, VolumeConfig.class);

    }

    private <T extends AbstractConfig> void bindConfig(
            final Function<AppConfig, T> configGetter,
            final Class<T> clazz) {
        bindConfig(configHolder.getAppConfig(), configGetter, clazz, null);
    }

    private <T extends AbstractConfig> void bindConfig(
            final Function<AppConfig, T> configGetter,
            final Class<T> clazz,
            final Consumer<T> childConfigConsumer) {
        bindConfig(configHolder.getAppConfig(), configGetter, clazz, childConfigConsumer);
    }

    private <X extends AbstractConfig, T extends AbstractConfig> void bindConfig(
            final X parentObject,
            final Function<X, T> configGetter,
            final Class<T> clazz) {
        bindConfig(parentObject, configGetter, clazz, null);
    }

    private <X extends AbstractConfig, T extends AbstractConfig> void bindConfig(
            final X parentObject,
            final Function<X, T> configGetter,
            final Class<T> clazz,
            final Consumer<T> childConfigConsumer) {

        if (parentObject == null) {
            throw new RuntimeException(LogUtil.message("Unable to bind config to {} as the parent is null. " +
                            "You may have an empty branch in your config YAML file.",
                    clazz.getCanonicalName()));
        }

        try {
            // Get the config instance
            T configInstance = configGetter.apply(parentObject);

            bind(clazz).toInstance(configInstance);
            if (childConfigConsumer != null) {
                childConfigConsumer.accept(configInstance);
            }
        } catch (Exception e) {
            throw new RuntimeException(LogUtil.message("Error binding getter on object {} to class {}",
                    parentObject.getClass().getCanonicalName(),
                    clazz.getCanonicalName()),
                    e);
        }
    }

    public interface ConfigHolder {
        AppConfig getAppConfig();

        Path getConfigFile();
    }
}
