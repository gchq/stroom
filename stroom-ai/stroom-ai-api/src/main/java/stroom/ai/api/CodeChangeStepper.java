/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.api;

import stroom.docref.DocRef;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/// Steps one record through a pipeline with some elements' code replaced, as the user, so a proposed code change
/// can be tried before it is offered: whether it still compiles and runs, and whether the pipeline's later
/// elements, such as a schema filter, accept its output. Implemented by Stroom's stepping, which needs the user's
/// Stepping permission and View on the pipeline.
public interface CodeChangeStepper {

    /// Steps the record once for each set of code, in one stepping session that is ended afterwards.
    ///
    /// @param trial What to step, and the code to step it with each time.
    /// @return One outcome for each set of code, in the same order.
    /// @throws RuntimeException if the user may not step the pipeline, or stepping fails.
    List<StepOutcome> step(StepTrial trial);


    // --------------------------------------------------------------------------------


    /// One record of a stream to step through a pipeline, with the code to step it with.
    ///
    /// @param pipeline        The pipeline, as saved.
    /// @param metaId          The stream.
    /// @param childStreamType The child stream type being stepped, or null.
    /// @param partIndex       The zero based part of the stream.
    /// @param recordIndex     The zero based record within the part.
    /// @param codes           Each set of code to step with: element id to the code it uses instead of its saved
    ///                        code, e.g. the code as the user has it and then the proposed code.
    /// @param timeLimit       The longest to wait for all the steps.
    record StepTrial(DocRef pipeline,
                     long metaId,
                     String childStreamType,
                     long partIndex,
                     long recordIndex,
                     List<Map<String, String>> codes,
                     Duration timeLimit) {

    }


    // --------------------------------------------------------------------------------


    /// What happened when the record was stepped with one set of code.
    ///
    /// @param foundRecord True if the record was found and stepped.
    /// @param problems    The errors and warnings each element reported, and the pipeline's own.
    /// @param outputs     Each element's output, by element id.
    record StepOutcome(boolean foundRecord, List<StepProblem> problems, Map<String, String> outputs) {

    }


    // --------------------------------------------------------------------------------


    /// An error or warning from stepping.
    ///
    /// @param element  The element that reported it, or "pipeline" for the pipeline's own.
    /// @param severity Its severity, e.g. "ERROR".
    /// @param message  What it says.
    /// @param line     The line it is at, or 0 if not known.
    /// @param column   The column it is at, or 0 if not known.
    record StepProblem(String element, String severity, String message, int line, int column) {

    }
}
