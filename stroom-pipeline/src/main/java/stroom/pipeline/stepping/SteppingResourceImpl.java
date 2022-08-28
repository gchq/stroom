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

package stroom.pipeline.stepping;

import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.pipeline.PipelineEventLog;
import stroom.pipeline.shared.stepping.FindElementDocRequest;
import stroom.pipeline.shared.stepping.GetPipelineForMetaRequest;
import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.shared.stepping.SteppingResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class SteppingResourceImpl implements SteppingResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(SteppingResourceImpl.class);

    private final Provider<SteppingService> steppingServiceProvider;
    private final Provider<PipelineEventLog> pipelineEventLogProvider;

    @Inject
    SteppingResourceImpl(final Provider<SteppingService> steppingServiceProvider,
                         final Provider<PipelineEventLog> pipelineEventLog) {
        this.steppingServiceProvider = steppingServiceProvider;
        this.pipelineEventLogProvider = pipelineEventLog;
    }

    @Override
    public DocRef findElementDoc(final FindElementDocRequest request) {
        return steppingServiceProvider.get().findElementDoc(request);
    }

    @Override
    public DocRef getPipelineForStepping(final GetPipelineForMetaRequest request) {
        return steppingServiceProvider.get().getPipelineForStepping(request);
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public SteppingResult step(final PipelineStepRequest request) {
        SteppingResult result = null;
        StepLocation stepLocation = request.getStepLocation();

        try {
            result = steppingServiceProvider.get().step(request);

            if (result.getStepLocation() != null) {
                stepLocation = result.getStepLocation();
            }

            if (stepLocation != null) {
                pipelineEventLogProvider.get().stepStream(
                        stepLocation.getEventId(),
                        null,
                        request.getChildStreamType(),
                        request.getPipeline(),
                        null);
            }
        } catch (final RuntimeException e) {
            if (stepLocation != null) {
                pipelineEventLogProvider.get().stepStream(
                        stepLocation.getEventId(),
                        null,
                        request.getChildStreamType(),
                        request.getPipeline(),
                        e);
            }
            throw e;
        }

        return result;
    }

}
