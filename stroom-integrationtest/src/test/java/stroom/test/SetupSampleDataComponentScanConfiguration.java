/*
 * Copyright 2016 Crown Copyright
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

import stroom.cluster.server.ClusterNodeManagerImpl;
import stroom.cluster.server.MockClusterNodeManager;
import stroom.dashboard.server.MockQueryService;
import stroom.datafeed.server.MockHeaderMapFactory;
import stroom.datafeed.server.MockHttpServletRequest;
import stroom.datafeed.server.MockHttpServletResponse;
import stroom.dictionary.MockDictionaryService;
import stroom.entity.server.MockDocumentEntityService;
import stroom.entity.server.MockEntityService;
import stroom.entity.server.util.StroomEntityManager;
import stroom.feed.server.MockFeedService;
import stroom.importexport.server.MockImportExportSerializer;
import stroom.importexport.server.MockImportExportService;
import stroom.index.server.MockIndexService;
import stroom.index.server.MockIndexShardService;
import stroom.index.server.MockIndexShardWriter;
import stroom.jobsystem.server.MockClusterLockService;
import stroom.jobsystem.server.MockJobManager;
import stroom.jobsystem.server.MockJobNodeService;
import stroom.jobsystem.server.MockJobService;
import stroom.jobsystem.server.MockScheduleService;
import stroom.jobsystem.server.MockTask;
import stroom.jobsystem.server.MockTaskFactory;
import stroom.jobsystem.server.MockTaskHandler;
import stroom.node.server.MockGlobalPropertyService;
import stroom.node.server.MockNodeService;
import stroom.node.server.MockRecordCountService;
import stroom.node.server.NodeConfigForTesting;
import stroom.node.server.NodeConfigImpl;
import stroom.node.shared.NodeService;
import stroom.node.shared.VolumeService;
import stroom.pipeline.server.MockPipelineEntityService;
import stroom.pipeline.server.MockTextConverterService;
import stroom.pipeline.server.MockXSLTService;
import stroom.pipeline.server.factory.MockPipelineElementRegistryFactory;
import stroom.resource.server.MockResourceStore;
import stroom.security.server.MockFolderService;
import stroom.security.server.MockUserService;
import stroom.statistic.server.MockMetaDataStatistic;
import stroom.statistics.server.MockStatisticEventStoreFactory;
import stroom.streamstore.server.MockStreamStore;
import stroom.streamstore.server.MockStreamTypeService;
import stroom.streamtask.server.MockStreamProcessorFilterService;
import stroom.streamtask.server.MockStreamProcessorService;
import stroom.streamtask.server.MockStreamTaskCreator;
import stroom.streamtask.server.MockStreamTaskService;
import stroom.volume.server.MockVolumeService;
import stroom.xmlschema.server.MockXMLSchemaService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

/**
 * Package-private because we don't want anything else using this configuration.
 */

/**
 * Exclude other configurations that might be found accidentally during a
 * component scan as configurations should be specified explicitly.
 */
@Configuration
@ComponentScan(basePackages = {"stroom"}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),

        // We need test volumes.
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = NodeConfigImpl.class),

        // Exclude all mocks
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockClusterNodeManager.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockCommonTestControl.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockDictionaryService.class),
        // @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value =
        // MockEntityPathResolver.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockEntityService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockFeedService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockGlobalPropertyService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockHeaderMapFactory.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockHttpServletRequest.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockHttpServletResponse.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockImportExportSerializer.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockImportExportService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockIndexService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockIndexShardService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockIndexShardWriter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockJobManager.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockJobNodeService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockJobService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockMetaDataStatistic.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockClusterLockService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockDocumentEntityService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockNodeService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockPipelineElementRegistryFactory.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockPipelineEntityService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockQueryService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockRecordCountService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockResourceStore.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockScheduleService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStatisticEventStoreFactory.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamProcessorFilterService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamProcessorService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamStore.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamTaskCreator.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamTaskService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamTypeService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockFolderService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockUserService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockTask.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockTaskFactory.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockTaskHandler.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockTextConverterService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockVolumeService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockXMLSchemaService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockXSLTService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ClusterNodeManagerImpl.class),})
public class SetupSampleDataComponentScanConfiguration implements BeanFactoryAware {
    private BeanFactory beanFactory;

    @Bean
    SetupSampleDataBean setupSampleDataBean() {
        return new SetupSampleDataBean();
    }

    @Bean
    NodeConfigForTesting nodeConfig() {
        return new NodeConfigForTesting((NodeService) beanFactory.getBean("nodeService"),
                (VolumeService) beanFactory.getBean("volumeService"), beanFactory.getBean(StroomEntityManager.class));
    }

    @Override
    public void setBeanFactory(final BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
