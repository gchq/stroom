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

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import stroom.feed.FeedService;
import stroom.jobsystem.shared.JobManager;
import stroom.node.NodeService;
import stroom.pipeline.PipelineService;
import stroom.properties.StroomPropertyService;
import stroom.statistics.sql.Statistics;
import stroom.streamstore.StreamAttributeMapService;
import stroom.streamstore.StreamStore;
import stroom.streamtask.StreamProcessorFilterService;
import stroom.streamtask.StreamProcessorService;
import stroom.task.TaskManager;
import stroom.task.cluster.ClusterDispatchAsyncHelper;
import stroom.util.spring.StroomScope;
import stroom.task.TaskContext;

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
                                                             final TaskContext taskContext,
                                                             final TaskManager taskManager,
                                                             final Statistics statistics,
                                                             final BenchmarkClusterConfig benchmarkClusterConfig) {
        return new BenchmarkClusterExecutor(
                feedService,
                pipelineService,
                streamProcessorFilterService,
                streamProcessorService,
                dispatchHelper,
                streamAttributeMapService,
                streamStore,
                jobManager,
                nodeService,
                taskContext,
                taskManager,
                statistics,
                benchmarkClusterConfig);
    }
}