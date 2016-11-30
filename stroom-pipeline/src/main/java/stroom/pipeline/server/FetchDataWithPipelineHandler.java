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

package stroom.pipeline.server;

import org.springframework.context.annotation.Scope;
import stroom.entity.shared.EntityServiceException;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataWithPipelineAction;
import stroom.security.Secured;
import stroom.streamstore.shared.Stream;
import stroom.task.server.TaskHandlerBean;
import stroom.util.spring.StroomScope;

@TaskHandlerBean(task = FetchDataWithPipelineAction.class)
@Secured(Stream.VIEW_DATA_WITH_PIPELINE_PERMISSION)
//(feature = Stream.ENTITY_TYPE, permission = DocumentPermissionNames.VIEW_WITH_PIPELINE)
@Scope(StroomScope.TASK)
public class FetchDataWithPipelineHandler extends AbstractFetchDataHandler<FetchDataWithPipelineAction> {
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

//    //@Secured(feature = Stream.ENTITY_TYPE, permission = DocumentPermissionNames.VIEW_WITH_PIPELINE)
//    public void requireXSLT() {
//    }
}
