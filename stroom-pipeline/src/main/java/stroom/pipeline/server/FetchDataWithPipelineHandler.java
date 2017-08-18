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

package stroom.pipeline.server;

import org.springframework.context.annotation.Scope;
import stroom.dashboard.server.logging.StreamEventLog;
import stroom.entity.shared.EntityServiceException;
import stroom.feed.server.FeedService;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.factory.PipelineDataCache;
import stroom.pipeline.server.factory.PipelineFactory;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataWithPipelineAction;
import stroom.pipeline.state.FeedHolder;
import stroom.pipeline.state.PipelineHolder;
import stroom.pipeline.state.StreamHolder;
import stroom.security.Secured;
import stroom.security.SecurityContext;
import stroom.streamstore.server.StreamStore;
import stroom.streamstore.shared.Stream;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchDataWithPipelineAction.class)
@Scope(StroomScope.TASK)
@Secured(Stream.VIEW_DATA_WITH_PIPELINE_PERMISSION)
public class FetchDataWithPipelineHandler extends AbstractFetchDataHandler<FetchDataWithPipelineAction> {
    @Inject
    FetchDataWithPipelineHandler(final StreamStore streamStore, final FeedService feedService, final FeedHolder feedHolder, final PipelineHolder pipelineHolder, final StreamHolder streamHolder, final PipelineService pipelineService, final PipelineFactory pipelineFactory, final ErrorReceiverProxy errorReceiverProxy, final PipelineDataCache pipelineDataCache, final StreamEventLog streamEventLog, final SecurityContext securityContext) {
        super(streamStore, feedService, feedHolder, pipelineHolder, streamHolder, pipelineService, pipelineFactory, errorReceiverProxy, pipelineDataCache, streamEventLog, securityContext);
    }

    @Override
    public AbstractFetchDataResult exec(final FetchDataWithPipelineAction action) {
        // Because we are securing this to require XSLT then we must check that
        // some has been provided
        if (action.getPipeline() == null) {
            throw new EntityServiceException("No pipeline has been supplied");
        }

        final Long streamId = action.getStreamId();

        if (streamId != null) {
            return getData(streamId, action.getChildStreamType(), action.getStreamRange(), action.getPageRange(),
                    action.isMarkerMode(), action.getPipeline(), action.isShowAsHtml());
        }

        return null;
    }
}
