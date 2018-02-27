/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.test;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import stroom.externaldoc.ExternalDocRefSpringConfig;
import stroom.feed.FeedService;
import stroom.importexport.ImportExportSerializer;
import stroom.index.IndexService;
import stroom.kafka.KafkaSpringConfig;
import stroom.node.VolumeService;
import stroom.pipeline.PipelineService;
import stroom.statistics.sql.datasource.StatisticStoreEntityService;
import stroom.statistics.stroomstats.entity.StroomStatsStoreEntityService;
import stroom.streamstore.StreamAttributeKeyService;
import stroom.streamstore.StreamStore;
import stroom.streamtask.StreamProcessorFilterService;


@ComponentScan("ignore")
//@Configuration
////@OldScan(basePackages = {
////        "stroom.datafeed",
////        "stroom.datasource",
////        "stroom.docstore.server",
////        "stroom.db",
////        "stroom.dictionary",
////        "stroom.dispatch",
////        "stroom.entity",
////        "stroom.feed",
////        "stroom.folder",
////        "stroom.importexport",
////        "stroom.internalstatistics",
////        "stroom.io",
////        "stroom.jobsystem",
////        "stroom.connectors.kafka",
////        "stroom.lifecycle",
////        "stroom.logging",
////        "stroom.node",
////        "stroom.pipeline",
////        "stroom.policy",
////        "stroom.pool",
////        "stroom.process",
////        "stroom.proxy",
////        "stroom.query",
////        "stroom.resource",
////        "stroom.servicediscovery",
////        "stroom.servlet",
////        "stroom.spring",
////        "stroom.streamstore",
////        "stroom.streamtask",
////        "stroom.task",
////        "stroom.test",
////        "stroom.upgrade",
////        "stroom.util",
////        "stroom.volume",
////        "stroom.xmlschema"
////}, excludeFilters = {
////        // Exclude other configurations that might be found accidentally during
////        // a component scan as configurations should be specified explicitly.
////        @OldFilter(type = FilterType.ANNOTATION, value = Configuration.class),
////
////        // We need test volumes.
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = NodeConfigImpl.class),
////
////        // Exclude all mocks
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockClusterNodeManager.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockCommonTestControl.class),
////        // @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value =
////        // MockEntityPathResolver.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockEntityService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockFeedService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockGlobalPropertyService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockHttpServletRequest.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockHttpServletResponse.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockIndexService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockIndexShardService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockIndexShardWriter.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockJobManager.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockJobNodeService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockJobService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockMetaDataStatistic.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockClusterLockService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockNodeService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockPipelineElementRegistryFactory.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockPipelineService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockQueryService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockRecordCountService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockResourceStore.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockScheduleService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamProcessorFilterService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamProcessorService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamStore.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamTaskCreator.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamTaskService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamTypeService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockUserService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockTask.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockTaskFactory.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockTaskHandler.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockTextConverterService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockVisualisationService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockVolumeService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockXMLSchemaService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = MockXSLTService.class),
////        @OldFilter(type = FilterType.ASSIGNABLE_TYPE, value = ClusterNodeManagerImpl.class),})
////@Import({CacheSpringConfig.class, ClusterTaskSpringConfig.class, ConnectorsSpringConfig.class})
//
//
//
//
//
//
////        "stroom.datafeed",
////                "stroom.datasource",
////                "stroom.docstore.server",
////                "stroom.db",
////                "stroom.dictionary",
////                "stroom.dispatch",
////                "stroom.entity",
////                "stroom.feed",
////                "stroom.folder",
////                "stroom.importexport",
////                "stroom.internalstatistics",
////                "stroom.io",
////                "stroom.jobsystem",
////                "stroom.connectors.kafka",
////                "stroom.lifecycle",
////                "stroom.logging",
////                "stroom.node",
////                "stroom.pipeline",
////                "stroom.policy",
////                "stroom.pool",
////                "stroom.process",
////                "stroom.proxy",
////                "stroom.query",
////                "stroom.resource",
////                "stroom.servicediscovery",
////                "stroom.servlet",
////                "stroom.spring",
////                "stroom.streamstore",
////                "stroom.streamtask",
////                "stroom.task",
////                "stroom.test",
////                "stroom.upgrade",
////                "stroom.util",
////                "stroom.volume",
////                "stroom.xmlschema"
//
//
//
//@Import({
//
//
//
////        stroom.benchmark.BenchmarkSpringConfig.class,
////        stroom.cache.CacheSpringConfig.class,
////        stroom.cache.PipelineCacheSpringConfig.class,
////        stroom.cluster.ClusterSpringConfig.class,
////        stroom.cluster.MockClusterSpringConfig.class,
////        stroom.connectors.ConnectorsSpringConfig.class,
////        stroom.connectors.elastic.ElasticSpringConfig.class,
////        stroom.connectors.kafka.KafkaSpringConfig.class,
////        stroom.connectors.kafka.filter.FilterSpringConfig.class,
////        stroom.dashboard.DashboardSpringConfig.class,
////        stroom.dashboard.MockDashboardSpringConfig.class,
////        stroom.dashboard.logging.LoggingSpringConfig.class,
////        stroom.datafeed.DataFeedSpringConfig.class,
////        stroom.datafeed.MockDataFeedSpringConfig.class,
////        stroom.datafeed.TestDataFeedServiceImplConfiguration.class,
////        stroom.datasource.DatasourceSpringConfig.class,
////        stroom.dictionary.DictionarySpringConfig.class,
////        stroom.dispatch.DispatchSpringConfig.class,
////        stroom.docstore.DocstoreSpringConfig.class,
////        stroom.docstore.db.DBSpringConfig.class,
////        stroom.document.DocumentSpringConfig.class,
////        stroom.elastic.ElasticSpringConfig.class,
////        stroom.entity.EntitySpringConfig.class,
////        stroom.entity.EntityTestSpringConfig.class,
////        stroom.entity.cluster.EntityClusterSpringConfig.class,
////        stroom.entity.event.EntityEventSpringConfig.class,
////        stroom.entity.util.EntityUtilSpringConfig.class,
////        stroom.explorer.ExplorerSpringConfig.class,
////        ExternalDocRefSpringConfig.class,
////        stroom.feed.FeedSpringConfig.class,
////        stroom.feed.MockFeedSpringConfig.class,
////        stroom.headless.HeadlessConfiguration.class,
////        stroom.headless.HeadlessSpringConfig.class,
////        stroom.importexport.ImportExportSpringConfig.class,
////        stroom.index.IndexSpringConfig.class,
////        stroom.index.MockIndexSpringConfig.class,
////        stroom.internalstatistics.MockInternalStatisticsSpringConfig.class,
////        stroom.io.IOSpringConfig.class,
////        stroom.jobsystem.ClusterLockTestSpringConfig.class,
////        stroom.jobsystem.JobSystemSpringConfig.class,
////        stroom.jobsystem.MockJobSystemSpringConfig.class,
////        stroom.lifecycle.LifecycleSpringConfig.class,
////        stroom.logging.LoggingSpringConfig.class,
////        stroom.node.MockNodeSpringConfig.class,
////        stroom.node.NodeSpringConfig.class,
////        stroom.node.NodeTestSpringConfig.class,
////        stroom.pipeline.MockPipelineSpringConfig.class,
////        stroom.pipeline.PipelineSpringConfig.class,
////        stroom.pipeline.destination.DestinationSpringConfig.class,
////        stroom.pipeline.errorhandler.ErrorHandlerSpringConfig.class,
////        stroom.pipeline.factory.FactorySpringConfig.class,
////        stroom.pipeline.filter.FilterSpringConfig.class,
////        stroom.pipeline.parser.ParserSpringConfig.class,
////        stroom.pipeline.reader.ReaderSpringConfig.class,
////        stroom.pipeline.source.SourceSpringConfig.class,
////        stroom.pipeline.spring.PipelineConfiguration.class,
////        stroom.pipeline.state.PipelineStateSpringConfig.class,
////        stroom.pipeline.stepping.PipelineSteppingSpringConfig.class,
////        stroom.pipeline.task.PipelineStreamTaskSpringConfig.class,
////        stroom.pipeline.writer.WriterSpringConfig.class,
////        stroom.pipeline.xsltfunctions.XsltFunctionsSpringConfig.class,
////        stroom.policy.PolicySpringConfig.class,
////        stroom.properties.PropertySpringConfig.class,
////        stroom.proxy.repo.RepoSpringConfig.class,
////        stroom.query.QuerySpringConfig.class,
////        stroom.refdata.ReferenceDataSpringConfig.class,
////        stroom.resource.MockResourceSpringConfig.class,
////        stroom.resource.ResourceSpringConfig.class,
////        stroom.ruleset.RulesetSpringConfig.class,
////        stroom.script.ScriptSpringConfig.class,
////        stroom.search.SearchSpringConfig.class,
////        stroom.search.SearchTestSpringConfig.class,
////        stroom.search.extraction.ExtractionSpringConfig.class,
////        stroom.search.shard.ShardSpringConfig.class,
////        stroom.security.MockSecuritySpringConfig.class,
////        stroom.security.SecuritySpringConfig.class,
////        stroom.servicediscovery.ServiceDiscoverySpringConfig.class,
////        stroom.servlet.ServletSpringConfig.class,
////        stroom.spring.MetaDataStatisticConfiguration.class,
////        stroom.spring.PersistenceConfiguration.class,
////        stroom.spring.ProcessTestServerComponentScanConfiguration.class,
////        stroom.spring.ScopeConfiguration.class,
////        stroom.spring.ScopeTestConfiguration.class,
////        stroom.spring.ServerComponentScanConfiguration.class,
////        stroom.spring.ServerComponentScanTestConfiguration.class,
////        stroom.spring.ServerConfiguration.class,
////        stroom.startup.AppSpringConfig.class,
////        stroom.statistics.internal.InternalStatisticsSpringConfig.class,
////        stroom.statistics.spring.StatisticsConfiguration.class,
////        stroom.statistics.sql.SQLStatisticSpringConfig.class,
////        stroom.statistics.sql.datasource.DataSourceSpringConfig.class,
////        stroom.statistics.sql.internal.InternalSpringConfig.class,
////        stroom.statistics.sql.pipeline.filter.FilterSpringConfig.class,
////        stroom.statistics.sql.rollup.SQLStatisticRollupSpringConfig.class,
////        stroom.statistics.sql.search.SearchSpringConfig.class,
////        stroom.statistics.stroomstats.entity.StroomStatsEntitySpringConfig.class,
////        stroom.statistics.stroomstats.internal.InternalSpringConfig.class,
////        stroom.statistics.stroomstats.kafka.KafkaSpringConfig.class,
////        stroom.statistics.stroomstats.pipeline.filter.FilterSpringConfig.class,
////        stroom.statistics.stroomstats.rollup.StroomStatsRollupSpringConfig.class,
////        stroom.streamstore.MockStreamStoreSpringConfig.class,
////        stroom.streamstore.StreamStoreSpringConfig.class,
////        stroom.streamstore.fs.FSSpringConfig.class,
////        stroom.streamstore.tools.ToolsSpringConfig.class,
////        stroom.streamtask.MockStreamTaskSpringConfig.class,
////        stroom.streamtask.StreamTaskSpringConfig.class,
////        stroom.task.TaskSpringConfig.class,
////        stroom.task.cluster.ClusterTaskSpringConfig.class,
////        stroom.test.AbstractCoreIntegrationTestSpringConfig.class,
////        stroom.test.AbstractProcessIntegrationTestSpringConfig.class,
////        stroom.test.SetupSampleDataComponentScanConfiguration.class,
////        stroom.test.SetupSampleDataSpringConfig.class,
////        stroom.test.TestSpringConfig.class,
////        stroom.upgrade.UpgradeSpringConfig.class,
////        stroom.util.cache.CacheManagerSpringConfig.class,
////        stroom.util.spring.MockUtilSpringConfig.class,
////        stroom.util.spring.StroomBeanLifeCycleTestConfiguration.class,
////        stroom.util.spring.UtilSpringConfig.class,
////        stroom.util.task.TaskScopeTestConfiguration.class,
////        stroom.visualisation.VisualisationSpringConfig.class,
////        stroom.volume.MockVolumeSpringConfig.class,
////        stroom.volume.VolumeSpringConfig.class,
////        stroom.xml.XmlSpringConfig.class,
////        stroom.xml.converter.ds3.DS3SpringConfig.class,
////        stroom.xml.converter.json.JsonSpringConfig.class,
////        stroom.xmlschema.MockXmlSchemaSpringConfig.class,
////        stroom.xmlschema.XmlSchemaSpringConfig.class
//
//
//
//
//})
//
//
//
//
//
//public class SetupSampleDataComponentScanConfiguration implements BeanFactoryAware {
//    private BeanFactory beanFactory;
//
//    @Bean
//    SetupSampleDataBean setupSampleDataBean() {
//        return new SetupSampleDataBean();
//    }
//
//    @Bean
//    NodeConfigForTesting nodeConfig() {
//        return new NodeConfigForTesting((NodeService) beanFactory.getBean("nodeService"),
//                (VolumeService) beanFactory.getBean("volumeService"), beanFactory.getBean(StroomEntityManager.class));
//    }
//
//    @Override
//    public void setBeanFactory(final BeanFactory beanFactory) throws BeansException {
//        this.beanFactory = beanFactory;
//    }
//}


@Configuration
@Import({
//        ScopeConfiguration.class,
//        PersistenceConfiguration.class,
//        ServerConfiguration.class,
////        ExplorerConfiguration.class,
////        RuleSetConfiguration.class,
////        SecurityConfiguration.class,
//        ScopeTestConfiguration.class,
////        DictionaryConfiguration.class,
//
////        EventLoggingConfiguration.class,
////        IndexConfiguration.class,
////        SearchConfiguration.class,
////        ScriptConfiguration.class,
////        VisualisationConfiguration.class,
////        DashboardConfiguration.class,
//        StatisticsConfiguration.class


        stroom.benchmark.BenchmarkSpringConfig.class,
        stroom.cache.CacheSpringConfig.class,
        stroom.cache.PipelineCacheSpringConfig.class,
        stroom.cluster.ClusterSpringConfig.class,
//        stroom.cluster.MockClusterSpringConfig.class,
        stroom.connectors.ConnectorsSpringConfig.class,
        stroom.connectors.elastic.ElasticSpringConfig.class,
        KafkaSpringConfig.class,
        stroom.dashboard.DashboardSpringConfig.class,
//        stroom.dashboard.MockDashboardSpringConfig.class,
        stroom.dashboard.logging.LoggingSpringConfig.class,
        stroom.datafeed.DataFeedSpringConfig.class,
//        stroom.datafeed.MockDataFeedSpringConfig.class,
//        stroom.datafeed.TestDataFeedServiceImplConfiguration.class,
        stroom.datasource.DatasourceSpringConfig.class,
        stroom.dictionary.DictionarySpringConfig.class,
        stroom.dispatch.DispatchSpringConfig.class,
        stroom.docstore.DocstoreSpringConfig.class,
        stroom.docstore.db.DBSpringConfig.class,
        stroom.document.DocumentSpringConfig.class,
        stroom.elastic.ElasticSpringConfig.class,
        stroom.entity.EntitySpringConfig.class,
//        stroom.entity.EntityTestSpringConfig.class,
        stroom.entity.cluster.EntityClusterSpringConfig.class,
        stroom.entity.event.EntityEventSpringConfig.class,
//        stroom.entity.util.EntityUtilSpringConfig.class,
        stroom.explorer.ExplorerSpringConfig.class,
        ExternalDocRefSpringConfig.class,
        stroom.feed.FeedSpringConfig.class,
//        stroom.feed.MockFeedSpringConfig.class,
//        stroom.headless.HeadlessConfiguration.class,
//        stroom.headless.HeadlessSpringConfig.class,
        stroom.importexport.ImportExportSpringConfig.class,
        stroom.index.IndexSpringConfig.class,
//        stroom.index.MockIndexSpringConfig.class,
//        stroom.internalstatistics.MockInternalStatisticsSpringConfig.class,
        stroom.io.IOSpringConfig.class,
//        stroom.jobsystem.ClusterLockTestSpringConfig.class,
        stroom.jobsystem.JobSystemSpringConfig.class,
//        stroom.jobsystem.MockJobSystemSpringConfig.class,
        stroom.lifecycle.LifecycleSpringConfig.class,
        stroom.logging.LoggingSpringConfig.class,
//        stroom.node.MockNodeServiceSpringConfig.class,
        stroom.node.NodeServiceSpringConfig.class,
//        stroom.node.NodeSpringConfig.class,
        stroom.node.NodeTestSpringConfig.class,
//        stroom.pipeline.MockPipelineSpringConfig.class,
        stroom.pipeline.PipelineSpringConfig.class,
        stroom.pipeline.destination.DestinationSpringConfig.class,
        stroom.pipeline.errorhandler.ErrorHandlerSpringConfig.class,
        stroom.pipeline.factory.FactorySpringConfig.class,
        stroom.pipeline.filter.FilterSpringConfig.class,
        stroom.pipeline.parser.ParserSpringConfig.class,
        stroom.pipeline.reader.ReaderSpringConfig.class,
        stroom.pipeline.source.SourceSpringConfig.class,
        stroom.pipeline.state.PipelineStateSpringConfig.class,
        stroom.pipeline.stepping.PipelineSteppingSpringConfig.class,
        stroom.pipeline.task.PipelineStreamTaskSpringConfig.class,
        stroom.pipeline.writer.WriterSpringConfig.class,
        stroom.pipeline.xsltfunctions.XsltFunctionsSpringConfig.class,
        stroom.policy.PolicySpringConfig.class,
        stroom.properties.PropertySpringConfig.class,
        stroom.proxy.repo.RepoSpringConfig.class,
        stroom.query.QuerySpringConfig.class,
        stroom.refdata.ReferenceDataSpringConfig.class,
//        stroom.resource.MockResourceSpringConfig.class,
        stroom.resource.ResourceSpringConfig.class,
        stroom.ruleset.RulesetSpringConfig.class,
        stroom.script.ScriptSpringConfig.class,
        stroom.search.SearchSpringConfig.class,
//        stroom.search.SearchTestSpringConfig.class,
        stroom.search.extraction.ExtractionSpringConfig.class,
        stroom.search.shard.ShardSpringConfig.class,
        stroom.security.MockSecuritySpringConfig.class,
        stroom.security.MockSecurityContextSpringConfig.class,
//        stroom.security.SecuritySpringConfig.class,
        stroom.servicediscovery.ServiceDiscoverySpringConfig.class,
        stroom.servlet.ServletSpringConfig.class,
        stroom.spring.MetaDataStatisticConfiguration.class,
        stroom.spring.PersistenceConfiguration.class,
//        stroom.spring.ProcessTestServerComponentScanConfiguration.class,
        stroom.spring.ScopeConfiguration.class,
//        stroom.spring.ScopeTestConfiguration.class,
//        stroom.spring.ServerComponentScanConfiguration.class,
//        stroom.spring.ServerComponentScanTestConfiguration.class,
        stroom.spring.ServerConfiguration.class,
//        stroom.startup.AppSpringConfig.class,
        stroom.statistics.internal.InternalStatisticsSpringConfig.class,
        stroom.statistics.spring.StatisticsConfiguration.class,
        stroom.statistics.sql.SQLStatisticSpringConfig.class,
        stroom.statistics.sql.datasource.DataSourceSpringConfig.class,
        stroom.statistics.sql.internal.InternalSpringConfig.class,
        stroom.statistics.sql.pipeline.filter.FilterSpringConfig.class,
        stroom.statistics.sql.rollup.SQLStatisticRollupSpringConfig.class,
        stroom.statistics.sql.search.SearchSpringConfig.class,
        stroom.statistics.stroomstats.entity.StroomStatsEntitySpringConfig.class,
        stroom.statistics.stroomstats.internal.InternalSpringConfig.class,
        stroom.statistics.stroomstats.kafka.KafkaSpringConfig.class,
        stroom.statistics.stroomstats.pipeline.filter.FilterSpringConfig.class,
        stroom.statistics.stroomstats.rollup.StroomStatsRollupSpringConfig.class,
//        stroom.streamstore.MockStreamStoreSpringConfig.class,
        stroom.streamstore.StreamStoreSpringConfig.class,
        stroom.streamstore.fs.FSSpringConfig.class,
        stroom.streamstore.tools.ToolsSpringConfig.class,
//        stroom.streamtask.MockStreamTaskSpringConfig.class,
        stroom.streamtask.StreamTaskSpringConfig.class,
        stroom.task.TaskSpringConfig.class,
        stroom.task.cluster.ClusterTaskSpringConfig.class,
//        stroom.test.AbstractCoreIntegrationTestSpringConfig.class,
//        stroom.test.AbstractProcessIntegrationTestSpringConfig.class,
        stroom.test.DatabaseTestControlSpringConfig.class,
//        stroom.test.SetupSampleDataComponentScanConfiguration.class,
//        stroom.test.SetupSampleDataSpringConfig.class,
//        stroom.test.TestSpringConfig.class,
        stroom.upgrade.UpgradeSpringConfig.class,
        stroom.util.cache.CacheManagerSpringConfig.class,
//        stroom.util.spring.MockUtilSpringConfig.class,
//        stroom.util.spring.StroomBeanLifeCycleTestConfiguration.class,
        stroom.util.spring.UtilSpringConfig.class,
//        stroom.util.task.TaskScopeTestConfiguration.class,
        stroom.visualisation.VisualisationSpringConfig.class,
//        stroom.volume.MockVolumeSpringConfig.class,
        stroom.volume.VolumeSpringConfig.class,
//        stroom.xml.XmlSpringConfig.class,
        stroom.xml.converter.ds3.DS3SpringConfig.class,
        stroom.xml.converter.json.JsonSpringConfig.class,
//        stroom.xmlschema.MockXmlSchemaSpringConfig.class,
        stroom.xmlschema.XmlSchemaSpringConfig.class
})
class SetupSampleDataSpringConfig {
    @Bean
    public SetupSampleDataBean setupSampleDataBean(
            final FeedService feedService,
            final StreamStore streamStore,
            final StreamAttributeKeyService streamAttributeKeyService,
            final CommonTestControl commonTestControl,
            final ImportExportSerializer importExportSerializer,
            final StreamProcessorFilterService streamProcessorFilterService,
            final PipelineService pipelineService,
            final VolumeService volumeService,
            final IndexService indexService,
            final StatisticStoreEntityService statisticsDataSourceService,
            final StroomStatsStoreEntityService stroomStatsStoreEntityService) {
        return new SetupSampleDataBean(feedService,
                streamStore,
                streamAttributeKeyService,
                commonTestControl,
                importExportSerializer,
                streamProcessorFilterService,
                pipelineService,
                volumeService,
                indexService,
                statisticsDataSourceService,
                stroomStatsStoreEntityService);
    }
}