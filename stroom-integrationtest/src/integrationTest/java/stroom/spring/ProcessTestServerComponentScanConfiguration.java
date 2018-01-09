/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import stroom.cluster.server.ClusterNodeManagerImpl;
import stroom.dashboard.server.QueryServiceImpl;
import stroom.dictionary.server.DictionaryStoreImpl;
import stroom.externaldoc.server.ExternalDocumentEntityServiceImpl;
import stroom.explorer.server.ExplorerActionHandlers;
import stroom.feed.server.FeedServiceImpl;
import stroom.feed.server.MockFeedService;
import stroom.importexport.server.ImportExportActionHandlers;
import stroom.internalstatistics.MetaDataStatisticImpl;
import stroom.jobsystem.server.ClusterLockServiceImpl;
import stroom.jobsystem.server.JobManagerImpl;
import stroom.jobsystem.server.JobNodeServiceImpl;
import stroom.jobsystem.server.JobServiceImpl;
import stroom.jobsystem.server.ScheduleServiceImpl;
import stroom.node.server.GlobalPropertyServiceImpl;
import stroom.node.server.NodeConfigImpl;
import stroom.node.server.NodeServiceImpl;
import stroom.node.server.RecordCountServiceImpl;
import stroom.pipeline.server.MockPipelineService;
import stroom.pipeline.server.PipelineService;
import stroom.pipeline.server.PipelineServiceImpl;
import stroom.pipeline.server.TextConverterService;
import stroom.pipeline.server.TextConverterServiceImpl;
import stroom.pipeline.server.XSLTService;
import stroom.pipeline.server.XSLTServiceImpl;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.shared.TextConverter;
import stroom.pipeline.shared.XSLT;
import stroom.policy.server.DataRetentionExecutor;
import stroom.resource.server.ResourceStoreImpl;
import stroom.security.server.UserServiceImpl;
import stroom.streamstore.server.MockStreamTypeService;
import stroom.streamstore.server.StreamAttributeKeyServiceImpl;
import stroom.streamstore.server.StreamTypeServiceImpl;
import stroom.streamstore.server.fs.DataRetentionTransactionHelper;
import stroom.streamstore.server.fs.FileSystemStreamStore;
import stroom.streamtask.server.MockStreamProcessorFilterService;
import stroom.streamtask.server.MockStreamProcessorService;
import stroom.streamtask.server.StreamProcessorFilterServiceImpl;
import stroom.streamtask.server.StreamProcessorServiceImpl;
import stroom.streamtask.server.StreamProcessorTaskFactory;
import stroom.streamtask.server.StreamTaskCreatorImpl;
import stroom.streamtask.server.StreamTaskServiceImpl;
import stroom.test.DatabaseCommonTestControl;
import stroom.volume.server.VolumeServiceImpl;
import stroom.xmlschema.server.XMLSchemaService;
import stroom.xmlschema.server.XMLSchemaServiceImpl;
import stroom.xmlschema.shared.XMLSchema;

/**
 * Configures the context for process integration tests.
 * <p>
 * Reuses production configurations but defines its own component scan.
 * <p>
 * This configuration relies on @ActiveProfile(StroomSpringProfiles.TEST) being
 * applied to the tests.
 */

/**
 * Exclude other configurations that might be found accidentally during a
 * component scan as configurations should be specified explicitly.
 */
@Configuration
@ComponentScan(basePackages = {
        "stroom.apiclients",
        "stroom.cache",
        "stroom.cluster",
        "stroom.datafeed",
        "stroom.datasource",
        "stroom.db",
        "stroom.dictionary",
        "stroom.dispatch",
        "stroom.docstore.server",
        "stroom.entity",
        "stroom.feed",
        "stroom.folder",
        "stroom.importexport",
        "stroom.internalstatistics",
        "stroom.io",
        "stroom.jobsystem",
        "stroom.connectors",
        "stroom.connectors.kafka",
        "stroom.lifecycle",
        "stroom.logging",
        "stroom.node",
        "stroom.pipeline",
        "stroom.refdata",
        "stroom.policy",
        "stroom.pool",
        "stroom.process",
        "stroom.proxy",
        "stroom.query",
        "stroom.resource",
        "stroom.servicediscovery",
        "stroom.servlet",
        "stroom.spring",
        "stroom.streamstore",
        "stroom.streamtask",
        "stroom.task",
        "stroom.test",
        "stroom.upgrade",
        "stroom.util",
        "stroom.volume",
        "stroom.xml",
        "stroom.xmlschema"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),

        // Exclude these so we get the mocks instead.
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ClusterLockServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ClusterNodeManagerImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = DatabaseCommonTestControl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = DictionaryStoreImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ExternalDocumentEntityServiceImpl.class),
        // @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value =
        // EntityPathResolverImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = FeedServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = FileSystemStreamStore.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = DataRetentionTransactionHelper.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = DataRetentionExecutor.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = GlobalPropertyServiceImpl.class),
        // @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value =
        // ImportExportSerializerImpl.class),
//        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ImportExportServiceImpl.class),
//        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ImportExportServiceImpl.class),
        // @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value =
        // IndexServiceImpl.class),
        // @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value =
        // IndexShardServiceImpl.class),
        // @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value =
        // IndexShardWriterCacheImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = JobManagerImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = JobNodeServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = JobServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MetaDataStatisticImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = NodeConfigImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = NodeServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = PipelineServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = QueryServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = RecordCountServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ResourceStoreImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ScheduleServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StreamAttributeKeyServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StreamProcessorFilterServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StreamProcessorServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StreamProcessorTaskFactory.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StreamTaskCreatorImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StreamTaskServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = StreamTypeServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = UserServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = TextConverterServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = VolumeServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = XMLSchemaServiceImpl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = XSLTServiceImpl.class)})
public class ProcessTestServerComponentScanConfiguration {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessTestServerComponentScanConfiguration.class);

    public ProcessTestServerComponentScanConfiguration(final ExplorerActionHandlers explorerActionHandlers,
                                                       final ImportExportActionHandlers importExportActionHandlers,
                                                       final TextConverterService textConverterService,
                                                       final XSLTService xsltService,
                                                       final PipelineService pipelineService,
                                                       final XMLSchemaService xmlSchemaService) {
        explorerActionHandlers.add(4, TextConverter.ENTITY_TYPE, "Text Converter", textConverterService);
        explorerActionHandlers.add(5, XSLT.ENTITY_TYPE, XSLT.ENTITY_TYPE, xsltService);
        explorerActionHandlers.add(6, PipelineEntity.ENTITY_TYPE, PipelineEntity.ENTITY_TYPE, pipelineService);
        explorerActionHandlers.add(13, XMLSchema.ENTITY_TYPE, "XML Schema", xmlSchemaService);
        importExportActionHandlers.add(TextConverter.ENTITY_TYPE, textConverterService);
        importExportActionHandlers.add(XSLT.ENTITY_TYPE, xsltService);
        importExportActionHandlers.add(PipelineEntity.ENTITY_TYPE, pipelineService);
        importExportActionHandlers.add(XMLSchema.ENTITY_TYPE, xmlSchemaService);
    }

    @Bean(name = "cachedStreamTypeService")
    public MockStreamTypeService getCachedStreamTypeService(final MockStreamTypeService streamTypeService) {
        return streamTypeService;
    }

    @Bean(name = "cachedFeedService")
    public MockFeedService getCachedFeedService(final MockFeedService feedService) {
        return feedService;
    }

    @Bean(name = "cachedPipelineService")
    public MockPipelineService getCachedPipelineService(final MockPipelineService pipelineService) {
        return pipelineService;
    }

    @Bean(name = "cachedStreamProcessorService")
    public MockStreamProcessorService getCachedStreamProcessorService(final MockStreamProcessorService streamProcessorService) {
        return streamProcessorService;
    }

    @Bean(name = "cachedStreamProcessorFilterService")
    public MockStreamProcessorFilterService getCachedStreamProcessorFilterService(final MockStreamProcessorFilterService streamProcessorFilterService) {
        return streamProcessorFilterService;
    }
}
