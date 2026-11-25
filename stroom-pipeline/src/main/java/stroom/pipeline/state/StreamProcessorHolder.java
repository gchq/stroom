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

package stroom.pipeline.state;

import stroom.processor.api.RunOutput;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorTask;
import stroom.util.pipeline.scope.PipelineScoped;

@PipelineScoped
public class StreamProcessorHolder implements Holder {

    private Processor streamProcessor;
    private ProcessorTask streamTask;
    private RunOutput runOutput;

    public Processor getStreamProcessor() {
        return streamProcessor;
    }

    public ProcessorTask getStreamTask() {
        return streamTask;
    }

    /// Gets what has to be told of every stream that is opened to write to.
    ///
    /// @return What has to be told, or null if this isn't a run of a task.
    public RunOutput getRunOutput() {
        return runOutput;
    }

    /// Sets what is being run.
    ///
    /// @param streamProcessor The processor of the task.
    /// @param streamTask      The task.
    /// @param runOutput       What has to be told of every stream that is opened to write to.
    public void setStreamProcessor(final Processor streamProcessor,
                                   final ProcessorTask streamTask,
                                   final RunOutput runOutput) {
        this.streamProcessor = streamProcessor;
        this.streamTask = streamTask;
        this.runOutput = runOutput;
    }
}
