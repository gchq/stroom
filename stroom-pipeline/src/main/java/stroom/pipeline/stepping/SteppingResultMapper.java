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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.stepping.PipelineStepRequest;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.pipeline.stepping.read.SessionStepResolver.SessionStepResult;
import stroom.pipeline.stepping.session.SteppingSession;

import java.util.Collections;
import java.util.Set;

/**
 * Maps the engine's answer to a step onto the wire result the client receives.
 * <p>
 * Kept apart from the engine because it is the piece that moves when the UI's contract moves, and because
 * {@code complete} means something different on each side of it: on {@link SessionStepResult} the sweep uses
 * "fully captured" for a finished stream, whereas on {@link SteppingResult} <b>complete means "this step
 * query resolved"</b> - the client long-polls while a sweep is still working towards the requested record.
 */
class SteppingResultMapper {

    SteppingResult toResult(final PipelineStepRequest request,
                            final SteppingSession session,
                            final SessionStepResult result) {
        final Set<String> generalErrors = result.generalError() == null
                ? Collections.emptySet()
                : Set.of(result.generalError());

        // The client shows "stream 3 of 7" from this; it is the found stream's position in the session's
        // ordered selection.
        final Integer streamOffset = result.foundLocation() == null
                ? null
                : session.getStreamIdList().indexOf(result.foundLocation().getMetaId());

        return new SteppingResult(
                session.getSessionId(),
                request.getStepFilterMap(),
                result.progressLocation(),
                result.foundLocation(),
                result.stepData(),
                streamOffset,
                result.foundRecord(),
                generalErrors,
                result.segmentedData(),
                result.complete());
    }
}
