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

package stroom.node.impl;

import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.job.api.ScheduledJobsBinder;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.shared.Node;
import stroom.node.shared.NodeResource;
import stroom.pipeline.writer.ExtendedPathCreator;
import stroom.util.RunnableWrapper;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.io.PathCreator;
import stroom.util.shared.scheduler.CronExpressions;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public class NodeModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(NodeInfo.class).to(NodeInfoImpl.class);
        bind(NodeService.class).to(NodeServiceImpl.class);
        bind(NodeResource.class).to(NodeResourceImpl.class);
        bind(PathCreator.class).to(ExtendedPathCreator.class);

        RestResourcesBinder.create(binder())
                .bind(NodeResourceImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(Node.class, NodeObjectInfoProvider.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(JavaHeapHistogramStatistics.class, jobBuilder -> jobBuilder
                        .name("Java Heap Histogram Statistics")
                        .description("Generate Java heap map histogram and record statistic events " +
                                     "for the entries. CAUTION: this will pause the JVM, only enable this if you " +
                                     "understand the consequences!")
                        .cronSchedule(CronExpressions.EVERY_HOUR.getExpression())
                        .enabledOnBootstrap(false) // Not needed in a test environment
                        .enabled(false))
                .bindJobTo(NodeStatus.class, jobBuilder -> jobBuilder
                        .name("Node Status")
                        .description("Job to record status of node (CPU and Memory usage)")
                        .cronSchedule(CronExpressions.EVERY_MINUTE.getExpression())
                        .advanced(false));
    }

    private static class JavaHeapHistogramStatistics extends RunnableWrapper {

        @Inject
        JavaHeapHistogramStatistics(final HeapHistogramStatisticsExecutor heapHistogramStatisticsExecutor) {
            super(heapHistogramStatisticsExecutor::exec);
        }
    }

    private static class NodeStatus extends RunnableWrapper {

        @Inject
        NodeStatus(final NodeStatusExecutor nodeStatusExecutor) {
            super(nodeStatusExecutor::exec);
        }
    }
}
