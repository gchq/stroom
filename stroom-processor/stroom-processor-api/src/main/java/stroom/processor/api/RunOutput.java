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

package stroom.processor.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

/// The streams that one run of a processor task has opened to write to, so that the run can delete exactly those if
/// it doesn't end well. It can't delete those of another run, whatever has happened to its task.
public class RunOutput {

    private final List<Long> outputIds = new CopyOnWriteArrayList<>();
    private final List<Long> errorIds = new CopyOnWriteArrayList<>();

    /// Records that the run has opened an output stream.
    ///
    /// @param metaId The id of the stream.
    public void addOutput(final long metaId) {
        outputIds.add(metaId);
    }

    /// Records that the run has opened an error stream.
    ///
    /// @param metaId The id of the stream.
    public void addError(final long metaId) {
        errorIds.add(metaId);
    }

    /// Gets the output streams that the run has opened, which doesn't include its error stream.
    ///
    /// @return The ids of the streams.
    public List<Long> getOutputIds() {
        return List.copyOf(outputIds);
    }

    /// Gets every stream that the run has opened, including its error stream.
    ///
    /// @return The ids of the streams.
    public List<Long> getAllIds() {
        return Stream.concat(outputIds.stream(), errorIds.stream()).toList();
    }
}
