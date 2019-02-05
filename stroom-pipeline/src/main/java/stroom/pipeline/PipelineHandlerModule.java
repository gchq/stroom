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

package stroom.pipeline;

import com.google.inject.AbstractModule;
import stroom.pipeline.shared.FetchDataAction;
import stroom.pipeline.shared.FetchDataWithPipelineAction;
import stroom.pipeline.shared.FetchPipelineDataAction;
import stroom.pipeline.shared.FetchPipelineXmlAction;
import stroom.pipeline.shared.FetchPropertyTypesAction;
import stroom.pipeline.shared.PipelineStepAction;
import stroom.pipeline.shared.SavePipelineXmlAction;
import stroom.task.api.TaskHandlerBinder;

public class PipelineHandlerModule extends AbstractModule {
    @Override
    protected void configure() {
        TaskHandlerBinder.create(binder())
                .bind(FetchPipelineDataAction.class, FetchPipelineDataHandler.class)
                .bind(FetchPipelineXmlAction.class, FetchPipelineXmlHandler.class)
                .bind(FetchPropertyTypesAction.class, FetchPropertyTypesHandler.class)
                .bind(PipelineStepAction.class, PipelineStepActionHandler.class)
                .bind(SavePipelineXmlAction.class, SavePipelineXmlHandler.class);
    }
}