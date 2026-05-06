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

package stroom.proxy.app.pipeline.runtime;

import stroom.proxy.app.pipeline.config.ConsumerStageThreadsConfig;
import stroom.proxy.app.pipeline.config.PipelineStagesConfig;
import stroom.proxy.app.pipeline.stage.aggregate.AggregateStageConfig;
import stroom.proxy.app.pipeline.stage.forward.ForwardStageConfig;
import stroom.proxy.app.pipeline.stage.preaggregate.PreAggregateStageConfig;
import stroom.proxy.app.pipeline.stage.receive.ReceiveStageConfig;
import stroom.proxy.app.pipeline.stage.splitzip.SplitZipStageConfig;
import java.util.Objects;
import java.util.Optional;

/**
 * Topology model for a logical pipeline stage.
 * <p>
 * Adapts stage-specific configuration into a uniform model used by topology
 * building, validation, and runtime assembly. Each stage config type has
 * different fields — this class normalises them into a consistent interface.
 * </p>
 */
record PipelineStage(
        PipelineStageName name,
        boolean enabled,
        String inputQueue,
        String outputQueue,
        String splitZipQueue,
        String fileStore,
        ConsumerStageThreadsConfig consumerThreads) {

    public PipelineStage {
        name = Objects.requireNonNull(name, "name");
    }

    // --- Factory methods for each stage ---

    static PipelineStage receive(final PipelineStagesConfig stagesConfig) {
        final ReceiveStageConfig cfg = Objects.requireNonNull(stagesConfig, "stagesConfig").getReceive();
        return new PipelineStage(
                PipelineStageName.RECEIVE,
                cfg.isEnabled(),
                null,  // receive has no input queue
                cfg.getOutputQueue(),
                cfg.getSplitZipQueue(),
                cfg.getFileStore(),
                null); // receive has no consumerThreads
    }

    static PipelineStage splitZip(final PipelineStagesConfig stagesConfig) {
        final SplitZipStageConfig cfg = Objects.requireNonNull(stagesConfig, "stagesConfig").getSplitZip();
        return new PipelineStage(
                PipelineStageName.SPLIT_ZIP,
                cfg.isEnabled(),
                cfg.getInputQueue(),
                cfg.getOutputQueue(),
                null,
                cfg.getFileStore(),
                cfg.getThreads());
    }

    static PipelineStage preAggregate(final PipelineStagesConfig stagesConfig) {
        final PreAggregateStageConfig cfg = Objects.requireNonNull(stagesConfig, "stagesConfig").getPreAggregate();
        return new PipelineStage(
                PipelineStageName.PRE_AGGREGATE,
                cfg.isEnabled(),
                cfg.getInputQueue(),
                cfg.getOutputQueue(),
                null,
                cfg.getFileStore(),
                cfg.getThreads());
    }

    static PipelineStage aggregate(final PipelineStagesConfig stagesConfig) {
        final AggregateStageConfig cfg = Objects.requireNonNull(stagesConfig, "stagesConfig").getAggregate();
        return new PipelineStage(
                PipelineStageName.AGGREGATE,
                cfg.isEnabled(),
                cfg.getInputQueue(),
                cfg.getOutputQueue(),
                null,
                cfg.getFileStore(),
                cfg.getThreads());
    }

    static PipelineStage forward(final PipelineStagesConfig stagesConfig) {
        final ForwardStageConfig cfg = Objects.requireNonNull(stagesConfig, "stagesConfig").getForward();
        return new PipelineStage(
                PipelineStageName.FORWARD,
                cfg.isEnabled(),
                cfg.getInputQueue(),
                null,  // forward has no output queue
                null,
                null,  // forward has no file store
                cfg.getThreads());
    }

    // --- Accessors ---

    /**
     * @return The stage name as used in pipeline configuration.
     */
    public String getConfigName() {
        return name.getConfigName();
    }

    /**
     * @return The configured input queue name, if any.
     */
    public Optional<String> getInputQueueOpt() {
        return Optional.ofNullable(inputQueue);
    }

    /**
     * @return The configured output queue name, if any.
     */
    public Optional<String> getOutputQueueOpt() {
        return Optional.ofNullable(outputQueue);
    }

    /**
     * @return The configured split zip queue name, if any.
     */
    public Optional<String> getSplitZipQueueOpt() {
        return Optional.ofNullable(splitZipQueue);
    }

    /**
     * @return The configured file store name, if any.
     */
    public Optional<String> getFileStoreOpt() {
        return Optional.ofNullable(fileStore);
    }

    /**
     * @return True if this stage consumes work from a queue.
     */
    public boolean consumesInputQueue() {
        return inputQueue != null;
    }

    /**
     * @return True if this stage publishes normal work to another queue.
     */
    public boolean publishesOutputQueue() {
        return outputQueue != null;
    }

    /**
     * @return True if this stage publishes split zip work to a dedicated queue.
     */
    public boolean publishesSplitZipQueue() {
        return splitZipQueue != null;
    }

    /**
     * @return True if this stage writes data to a named file store.
     */
    public boolean writesFileStore() {
        return fileStore != null;
    }

    /**
     * @return True if this stage may publish to any queue.
     */
    public boolean publishesAnyQueue() {
        return publishesOutputQueue() || publishesSplitZipQueue();
    }

    @Override
    public String toString() {
        return "PipelineStage{" +
               "name=" + name +
               ", enabled=" + enabled +
               ", inputQueue=" + inputQueue +
               ", outputQueue=" + outputQueue +
               ", splitZipQueue=" + splitZipQueue +
               ", fileStore=" + fileStore +
               '}';
    }
}
