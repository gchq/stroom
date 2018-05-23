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

import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.SavePipelineXmlAction;
import stroom.pipeline.shared.data.PipelineData;
import stroom.security.Security;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = SavePipelineXmlAction.class)
class SavePipelineXmlHandler extends AbstractTaskHandler<SavePipelineXmlAction, VoidResult> {
    private final PipelineStore pipelineStore;
    private final PipelineSerialiser pipelineSerialiser;
    private final Security security;

    @Inject
    SavePipelineXmlHandler(final PipelineStore pipelineStore,
                           final PipelineSerialiser pipelineSerialiser,
                           final Security security) {
        this.pipelineStore = pipelineStore;
        this.pipelineSerialiser = pipelineSerialiser;
        this.security = security;
    }

    @Override
    public VoidResult exec(final SavePipelineXmlAction action) {
        return security.secureResult(() -> {
            final PipelineDoc pipelineDoc = pipelineStore.readDocument(action.getPipeline());

            if (pipelineDoc != null) {
                final PipelineData pipelineData = pipelineSerialiser.getPipelineDataFromXml(action.getXml());
                pipelineDoc.setPipelineData(pipelineData);
                pipelineStore.writeDocument(pipelineDoc);
            }

            return VoidResult.INSTANCE;
        });
    }
}
