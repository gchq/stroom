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
import stroom.util.shared.IsConfig;
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
        bind(ConfigLocation.class).toInstance(new ConfigLocation(configHolder.getConfigFile()));

        // AppConfig will instantiate all of its child config objects so
        // bind each of these instances so we can inject these objects on their own.
        // This allows gradle modules to know nothing about the other modules.
        // Our bind method has the arguments in the reverse way to guice so we can
        // more easily see the tree structure

        bind(AppConfig::getActivityConfig, stroom.activity.impl.db.ActivityConfig.class);
        bind(AppConfig::getAnnotationConfig, stroom.annotation.impl.AnnotationConfig.class);
        bind(AppConfig::getBenchmarkClusterConfig, BenchmarkClusterConfig.class);
        bind(AppConfig::getClusterConfig, ClusterConfig.class);
        bind(AppConfig::getClusterLockConfig, ClusterLockConfig.class);
        bind(AppConfig::getClusterTaskConfig, ClusterTaskConfig.class);
        bind(AppConfig::getCommonDbConfig, CommonDbConfig.class);
        bind(AppConfig::getContentPackImportConfig, ContentPackImportConfig.class);
        bind(AppConfig::getCoreConfig, CoreConfig.class);
        bind(AppConfig::getDashboardConfig, DashboardConfig.class);
        bind(AppConfig::getDataConfig, DataConfig.class, c -> {
            bind(c, DataConfig::getDataRetentionConfig, DataRetentionConfig.class);
            bind(c, DataConfig::getDataStoreServiceConfig, DataStoreServiceConfig.class);
            bind(c, DataConfig::getFsVolumeConfig, FsVolumeConfig.class);
            bind(c, DataConfig::getMetaServiceConfig, MetaServiceConfig.class);
        });
        bind(AppConfig::getDataSourceUrlConfig, DataSourceUrlConfig.class);
        bind(AppConfig::getDocStoreConfig, DocStoreConfig.class);
        bind(AppConfig::getExplorerConfig, ExplorerConfig.class);
        bind(AppConfig::getExportConfig, ExportConfig.class);
        bind(AppConfig::getFeedConfig, FeedConfig.class);
        bind(AppConfig::getIndexConfig, IndexConfig.class);
        bind(AppConfig::getJobSystemConfig, JobSystemConfig.class);
        bind(AppConfig::getLifecycleConfig, LifecycleConfig.class);
        bind(AppConfig::getNodeConfig, NodeConfig.class, c -> {
            bind(c, NodeConfig::getStatusConfig, StatusConfig.class, c2 -> {
                bind(c2, StatusConfig::getHeapHistogramConfig, HeapHistogramConfig.class);
            });
        });
        bind(AppConfig::getPathConfig, PathConfig.class);
        bind(AppConfig::getPipelineConfig, PipelineConfig.class, c -> {
            bind(c, PipelineConfig::getAppenderConfig, AppenderConfig.class);
            bind(c, PipelineConfig::getParserConfig, ParserConfig.class);
            bind(c, PipelineConfig::getReferenceDataConfig, ReferenceDataConfig.class);
            bind(c, PipelineConfig::getXmlSchemaConfig, XmlSchemaConfig.class);
            bind(c, PipelineConfig::getXsltConfig, XsltConfig.class);
        });
        bind(AppConfig::getProcessorConfig, ProcessorConfig.class);
        bind(AppConfig::getPropertyServiceConfig, PropertyServiceConfig.class);
        bind(AppConfig::getProxyAggregationConfig, ProxyAggregationConfig.class);
        bind(AppConfig::getReceiveDataConfig, ReceiveDataConfig.class);
        bind(AppConfig::getSearchConfig, SearchConfig.class, c -> {
            bind(c, SearchConfig::getExtractionConfig, ExtractionConfig.class);
            bind(c, SearchConfig::getShardConfig, IndexShardSearchConfig.class);
        });
        bind(AppConfig::getSearchableConfig, SearchableConfig.class);
        bind(AppConfig::getSecurityConfig, SecurityConfig.class, c -> {
            bind(c, SecurityConfig::getAuthenticationConfig, AuthenticationConfig.class);
            bind(c, SecurityConfig::getContentSecurityConfig, ContentSecurityConfig.class);
        });
        bind(AppConfig::getServiceDiscoveryConfig, ServiceDiscoveryConfig.class);
        bind(AppConfig::getSessionCookieConfig, SessionCookieConfig.class);
        bind(AppConfig::getSolrConfig, SolrConfig.class, c -> {
            bind(c, SolrConfig::getSolrSearchConfig, SolrSearchConfig.class);
        });
        bind(AppConfig::getStatisticsConfig, StatisticsConfig.class, c -> {
            bind(c, StatisticsConfig::getHbaseStatisticsConfig, HBaseStatisticsConfig.class);
            bind(c, StatisticsConfig::getInternalStatisticsConfig, InternalStatisticsConfig.class);
            bind(c, StatisticsConfig::getSqlStatisticsConfig, SQLStatisticsConfig.class, c2 -> {
                bind(c2, SQLStatisticsConfig::getSearchConfig, stroom.statistics.impl.sql.search.SearchConfig.class);
            });
        });
        bind(AppConfig::getStoredQueryConfig, StoredQueryConfig.class);
        bind(AppConfig::getUiConfig, UiConfig.class, c -> {
            bind(c, UiConfig::getActivityConfig, ActivityConfig.class);
            bind(c, UiConfig::getProcessConfig, stroom.ui.config.shared.ProcessConfig.class);
            bind(c, UiConfig::getQueryConfig, QueryConfig.class);
            bind(c, UiConfig::getSplashConfig, SplashConfig.class);
            bind(c, UiConfig::getThemeConfig, ThemeConfig.class);
            bind(c, UiConfig::getUrlConfig, UrlConfig.class);
        });
        bind(AppConfig::getVolumeConfig, VolumeConfig.class);
    }

    private <T extends IsConfig> void bind(
            final Function<AppConfig, T> getter,
            final Class<T> clazz) {
        bind(configHolder.getAppConfig(), getter, clazz, null);
    }

    private <T extends IsConfig> void bind(
            final Function<AppConfig, T> getter,
            final Class<T> clazz,
            final Consumer<T> childConfigConsumer) {
        bind(configHolder.getAppConfig(), getter, clazz, childConfigConsumer);
    }

    private <X extends IsConfig, T extends IsConfig> void bind(
            final X object,
            final Function<X, T> getter,
            final Class<T> clazz) {
        bind(object, getter, clazz, null);
    }

    private <X extends IsConfig, T extends IsConfig> void bind(
            final X object,
            final Function<X, T> getter,
            final Class<T> clazz,
            final Consumer<T> childConfigConsumer) {

        T instance = getter.apply(object);
        bind(clazz).toInstance(instance);
        if (childConfigConsumer != null) {
            childConfigConsumer.accept(instance);
        }
    }

    public interface ConfigHolder {
        AppConfig getAppConfig();

        Path getConfigFile();
    }
}
