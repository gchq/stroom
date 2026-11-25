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

package stroom.processor.api;

import stroom.data.store.api.Source;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.task.api.TaskContext;

public interface ProcessorTaskExecutor {

    /// Runs a task.
    ///
    /// @param taskContext     The context the task is running in.
    /// @param processor       The processor of the task.
    /// @param processorFilter The processor filter of the task.
    /// @param processorTask   The task.
    /// @param source          The stream to process.
    /// @param runOutput       Has to be told of every stream that the run opens to write to.
    /// @return What was done.
    ProcessorResult exec(TaskContext taskContext,
                         Processor processor,
                         ProcessorFilter processorFilter,
                         ProcessorTask processorTask,
                         Source source,
                         RunOutput runOutput);
}
