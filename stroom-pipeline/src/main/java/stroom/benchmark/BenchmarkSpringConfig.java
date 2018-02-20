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

package stroom.benchmark;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.feed.server.FeedService;
import stroom.jobsystem.shared.JobManager;
import stroom.node.server.NodeService;
import stroom.pipeline.server.PipelineService;
import stroom.statistics.server.sql.Statistics;
import stroom.streamstore.server.StreamAttributeMapService;
import stroom.streamstore.server.StreamStore;
import stroom.streamtask.server.StreamProcessorFilterService;
import stroom.streamtask.server.StreamProcessorService;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.task.server.TaskManager;
import stroom.util.spring.StroomScope;
import stroom.util.task.TaskMonitor;

@Configuration
public class BenchmarkSpringConfig {
    @Bean
    @Scope(StroomScope.TASK)
    public BenchmarkClusterExecutor benchmarkClusterExecutor(final FeedService feedService,
                                                             final PipelineService pipelineService,
                                                             final StreamProcessorFilterService streamProcessorFilterService,
                                                             final StreamProcessorService streamProcessorService,
                                                             final ClusterDispatchAsyncHelper dispatchHelper,
                                                             final StreamAttributeMapService streamAttributeMapService,
                                                             final StreamStore streamStore,
                                                             final JobManager jobManager,
                                                             final NodeService nodeService,
                                                             final TaskMonitor taskMonitor,
                                                             final TaskManager taskManager,
                                                             final Statistics statistics,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.benchmark.streamCount')}") final int streamCount,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.benchmark.recordCount')}") final int recordCount,
                                                             @Value("#{propertyConfigurer.getProperty('stroom.benchmark.concurrentWriters')}") final int concurrentWriters) {
        return new BenchmarkClusterExecutor(feedService, pipelineService, streamProcessorFilterService, streamProcessorService, dispatchHelper, streamAttributeMapService, streamStore, jobManager, nodeService, taskMonitor, taskManager, statistics, streamCount, recordCount, concurrentWriters);
    }
}