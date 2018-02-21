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

package stroom.test;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import stroom.cache.CacheSpringConfig;
import stroom.cluster.ClusterNodeManagerImpl;
import stroom.cluster.MockClusterNodeManager;
import stroom.connectors.ConnectorsSpringConfig;
import stroom.dashboard.MockQueryService;
import stroom.dashboard.MockVisualisationService;
import stroom.datafeed.MockHttpServletRequest;
import stroom.datafeed.MockHttpServletResponse;
import stroom.entity.MockEntityService;
import stroom.entity.util.StroomEntityManager;
import stroom.feed.MockFeedService;
import stroom.index.MockIndexService;
import stroom.index.MockIndexShardService;
import stroom.index.MockIndexShardWriter;
import stroom.internalstatistics.MockMetaDataStatistic;
import stroom.jobsystem.MockClusterLockService;
import stroom.jobsystem.MockJobManager;
import stroom.jobsystem.MockJobNodeService;
import stroom.jobsystem.MockJobService;
import stroom.jobsystem.MockScheduleService;
import stroom.jobsystem.MockTask;
import stroom.jobsystem.MockTaskFactory;
import stroom.jobsystem.MockTaskHandler;
import stroom.node.MockGlobalPropertyService;
import stroom.node.MockNodeService;
import stroom.node.MockRecordCountService;
import stroom.node.NodeConfigForTesting;
import stroom.node.NodeConfigImpl;
import stroom.node.NodeService;
import stroom.node.VolumeService;
import stroom.pipeline.MockPipelineService;
import stroom.pipeline.MockTextConverterService;
import stroom.pipeline.MockXSLTService;
import stroom.pipeline.factory.MockPipelineElementRegistryFactory;
import stroom.resource.MockResourceStore;
import stroom.security.MockUserService;
import stroom.streamstore.MockStreamStore;
import stroom.streamstore.MockStreamTypeService;
import stroom.streamtask.MockStreamProcessorFilterService;
import stroom.streamtask.MockStreamProcessorService;
import stroom.streamtask.MockStreamTaskCreator;
import stroom.streamtask.MockStreamTaskService;
import stroom.task.cluster.ClusterTaskSpringConfig;
import stroom.volume.MockVolumeService;
import stroom.xmlschema.MockXMLSchemaService;

/**
 * Package-private because we don't want anything else using this configuration.
 */

/**
 * Exclude other configurations that might be found accidentally during a
 * component scan as configurations should be specified explicitly.
 */
@Configuration
@ComponentScan(basePackages = {
        "stroom.datafeed",
        "stroom.datasource",
        "stroom.docstore.server",
        "stroom.db",
        "stroom.dictionary",
        "stroom.dispatch",
        "stroom.entity",
        "stroom.feed",
        "stroom.folder",
        "stroom.importexport",
        "stroom.internalstatistics",
        "stroom.io",
        "stroom.jobsystem",
        "stroom.connectors.kafka",
        "stroom.lifecycle",
        "stroom.logging",
        "stroom.node",
        "stroom.pipeline",
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
        "stroom.xmlschema"
}, excludeFilters = {
        // Exclude other configurations that might be found accidentally during
        // a component scan as configurations should be specified explicitly.
        @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Configuration.class),

        // We need test volumes.
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = NodeConfigImpl.class),

        // Exclude all mocks
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockClusterNodeManager.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockCommonTestControl.class),
        // @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value =
        // MockEntityPathResolver.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockEntityService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockFeedService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockGlobalPropertyService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockHttpServletRequest.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockHttpServletResponse.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockIndexService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockIndexShardService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockIndexShardWriter.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockJobManager.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockJobNodeService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockJobService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockMetaDataStatistic.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockClusterLockService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockNodeService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockPipelineElementRegistryFactory.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockPipelineService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockQueryService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockRecordCountService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockResourceStore.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockScheduleService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamProcessorFilterService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamProcessorService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamStore.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamTaskCreator.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamTaskService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockStreamTypeService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockUserService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockTask.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockTaskFactory.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockTaskHandler.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockTextConverterService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockVisualisationService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockVolumeService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockXMLSchemaService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = MockXSLTService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = ClusterNodeManagerImpl.class),})
@Import({CacheSpringConfig.class, ClusterTaskSpringConfig.class, ConnectorsSpringConfig.class})
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
