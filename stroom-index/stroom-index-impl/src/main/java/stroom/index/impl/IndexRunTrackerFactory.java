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

package stroom.index.impl;

import stroom.processor.api.RunIndexing;
import stroom.processor.shared.ProcessorTask;

/// Makes the record keeping for each run of a processor task that indexes.
public interface IndexRunTrackerFactory {

    /// Makes the record keeping for a run.
    ///
    /// @param task        The task, as the run knows it, with the run's run count.
    /// @param runIndexing What is to be told of how the run went.
    /// @return The record keeping.
    IndexRunTracker create(ProcessorTask task, RunIndexing runIndexing);
}
