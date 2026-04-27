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

package stroom.proxy.app.pipeline;

import java.util.Objects;
import java.util.Optional;

/**
 * Topology model for a logical pipeline stage.
 * <p>
 * This class adapts the configured stage settings into a small immutable model
 * that can be used by topology building, validation, and later runtime assembly.
 * It deliberately contains only logical references to queues and file stores.
 * Queue construction is owned by {@link FileGroupQueueFactory}; storage
 * construction is owned by the file-store layer.
 * </p>
 */
public record PipelineStage(
        PipelineStageName name,
        PipelineStageConfig config) {

    public PipelineStage {
        name = Objects.requireNonNull(name, "name");
        config = Objects.requireNonNullElseGet(config, PipelineStageConfig::new);
    }

    public static PipelineStage receive(final PipelineStagesConfig stagesConfig) {
        return new PipelineStage(
                PipelineStageName.RECEIVE,
                Objects.requireNonNull(stagesConfig, "stagesConfig").getReceive());
    }

    public static PipelineStage splitZip(final PipelineStagesConfig stagesConfig) {
        return new PipelineStage(
                PipelineStageName.SPLIT_ZIP,
                Objects.requireNonNull(stagesConfig, "stagesConfig").getSplitZip());
    }

    public static PipelineStage preAggregate(final PipelineStagesConfig stagesConfig) {
        return new PipelineStage(
                PipelineStageName.PRE_AGGREGATE,
                Objects.requireNonNull(stagesConfig, "stagesConfig").getPreAggregate());
    }

    public static PipelineStage aggregate(final PipelineStagesConfig stagesConfig) {
        return new PipelineStage(
                PipelineStageName.AGGREGATE,
                Objects.requireNonNull(stagesConfig, "stagesConfig").getAggregate());
    }

    public static PipelineStage forward(final PipelineStagesConfig stagesConfig) {
        return new PipelineStage(
                PipelineStageName.FORWARD,
                Objects.requireNonNull(stagesConfig, "stagesConfig").getForward());
    }

    /**
     * @return The stage name as used in pipeline configuration.
     */
    public String getConfigName() {
        return name.getConfigName();
    }

    /**
     * @return True if this stage is enabled on this proxy process.
     */
    public boolean isEnabled() {
        return config.isEnabled();
    }

    /**
     * @return The configured input queue name, if any.
     */
    public Optional<String> getInputQueue() {
        return Optional.ofNullable(config.getInputQueue());
    }

    /**
     * @return The configured output queue name, if any.
     */
    public Optional<String> getOutputQueue() {
        return Optional.ofNullable(config.getOutputQueue());
    }

    /**
     * @return The configured split zip queue name, if any.
     */
    public Optional<String> getSplitZipQueue() {
        return Optional.ofNullable(config.getSplitZipQueue());
    }

    /**
     * @return The configured file store name, if any.
     */
    public Optional<String> getFileStore() {
        return Optional.ofNullable(config.getFileStore());
    }

    /**
     * @return The configured per-stage thread settings.
     */
    public PipelineStageThreadsConfig getThreads() {
        return config.getThreads();
    }

    /**
     * @return True if this stage consumes work from a queue.
     */
    public boolean consumesInputQueue() {
        return getInputQueue().isPresent();
    }

    /**
     * @return True if this stage publishes normal work to another queue.
     */
    public boolean publishesOutputQueue() {
        return getOutputQueue().isPresent();
    }

    /**
     * @return True if this stage publishes split zip work to a dedicated queue.
     */
    public boolean publishesSplitZipQueue() {
        return getSplitZipQueue().isPresent();
    }

    /**
     * @return True if this stage writes data to a named file store.
     */
    public boolean writesFileStore() {
        return getFileStore().isPresent();
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
               ", enabled=" + isEnabled() +
               ", inputQueue=" + getInputQueue().orElse(null) +
               ", outputQueue=" + getOutputQueue().orElse(null) +
               ", splitZipQueue=" + getSplitZipQueue().orElse(null) +
               ", fileStore=" + getFileStore().orElse(null) +
               '}';
    }
}
