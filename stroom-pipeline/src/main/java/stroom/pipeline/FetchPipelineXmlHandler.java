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
 */

package stroom.pipeline;

import stroom.pipeline.shared.FetchPipelineXmlAction;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.SharedString;

import javax.inject.Inject;

@TaskHandlerBean(task = FetchPipelineXmlAction.class)
class FetchPipelineXmlHandler extends AbstractTaskHandler<FetchPipelineXmlAction, SharedString> {
    private final PipelineStore pipelineStore;
    private final PipelineSerialiser pipelineSerialiser;
    private final Security security;

    @Inject
    FetchPipelineXmlHandler(final PipelineStore pipelineStore,
                            final PipelineSerialiser pipelineSerialiser,
                            final Security security) {
        this.pipelineStore = pipelineStore;
        this.pipelineSerialiser = pipelineSerialiser;
        this.security = security;
    }

    @Override
    public SharedString exec(final FetchPipelineXmlAction action) {
        return security.secureResult(() -> {
            SharedString result = null;

            final PipelineDoc pipelineDoc = pipelineStore.readDocument(action.getPipeline());
            if (pipelineDoc != null) {
                result = SharedString.wrap(pipelineSerialiser.getXmlFromPipelineData(pipelineDoc.getPipelineData()));
            }

            return result;
        });
    }
}
