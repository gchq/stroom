/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.core.dataprocess;

import stroom.data.store.api.Store;
import stroom.feed.api.FeedProperties;
import stroom.feed.api.VolumeGroupNameProvider;
import stroom.meta.api.MetaService;
import stroom.node.api.NodeInfo;
import stroom.pipeline.ErrorWriterProxy;
import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.RecordErrorReceiver;
import stroom.pipeline.factory.PipelineDataCache;
import stroom.pipeline.factory.PipelineFactory;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.MetaData;
import stroom.pipeline.state.MetaDataHolder;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.RecordCount;
import stroom.pipeline.state.SearchIdHolder;
import stroom.pipeline.state.StreamProcessorHolder;
import stroom.processor.api.ProcessorTaskService;
import stroom.statistics.api.InternalStatisticsReceiver;

import jakarta.inject.Inject;

public class StandardProcessorTaskExecutor extends AbstractProcessorTaskExecutor {

    private final ProcessorTaskDecorator processDecorator;

    @Inject
    public StandardProcessorTaskExecutor(final PipelineFactory pipelineFactory,
                                         final Store store,
                                         final PipelineStore pipelineStore,
                                         final MetaService metaService,
                                         final ProcessorTaskService processorTaskService,
                                         final PipelineHolder pipelineHolder,
                                         final FeedHolder feedHolder,
                                         final FeedProperties feedProperties,
                                         final MetaDataHolder metaDataHolder,
                                         final MetaHolder metaHolder,
                                         final SearchIdHolder searchIdHolder,
                                         final LocationFactoryProxy locationFactory,
                                         final StreamProcessorHolder streamProcessorHolder,
                                         final ErrorReceiverProxy errorReceiverProxy,
                                         final ErrorWriterProxy errorWriterProxy,
                                         final MetaData metaData,
                                         final RecordCount recordCount,
                                         final RecordErrorReceiver recordErrorReceiver,
                                         final NodeInfo nodeInfo,
                                         final PipelineDataCache pipelineDataCache,
                                         final InternalStatisticsReceiver internalStatisticsReceiver,
                                         final VolumeGroupNameProvider volumeGroupNameProvider) {
        super(pipelineFactory,
                store,
                pipelineStore,
                metaService,
                processorTaskService,
                pipelineHolder,
                feedHolder,
                feedProperties,
                metaDataHolder,
                metaHolder,
                searchIdHolder,
                locationFactory,
                streamProcessorHolder,
                errorReceiverProxy,
                errorWriterProxy,
                metaData,
                recordCount,
                recordErrorReceiver,
                nodeInfo,
                pipelineDataCache,
                internalStatisticsReceiver,
                volumeGroupNameProvider);
        this.processDecorator = new StandardProcessorTaskDecorator();
    }

    @Override
    protected ProcessorTaskDecorator getProcessDecorator() {
        return processDecorator;
    }
}
