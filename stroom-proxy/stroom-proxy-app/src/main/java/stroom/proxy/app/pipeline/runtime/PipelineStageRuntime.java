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
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.store.FileStore;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime model for a single configured pipeline stage.
 * <p>
 * This class binds the logical stage configuration/topology model to the
 * runtime objects needed by that stage:
 * </p>
 * <ul>
 *     <li>the logical stage model,</li>
 *     <li>the input queue for queue-consuming stages,</li>
 *     <li>the output queue for stages that publish normal work,</li>
 *     <li>the split zip queue for receive-stage split zip handoff,</li>
 *     <li>the file store used by stages that write new file groups, and</li>
 *     <li>the queue worker used by queue-consuming stages.</li>
 * </ul>
 * <p>
 * The runtime model is intentionally lightweight. It does not start threads or
 * perform stage processing itself; it simply captures the resolved dependencies
 * that a later runtime runner can use to execute enabled stages.
 * </p>
 */
class PipelineStageRuntime {

    private final PipelineStage stage;
    private final FileGroupQueue inputQueue;
    private final FileGroupQueue outputQueue;
    private final FileGroupQueue splitZipQueue;
    private final FileStore fileStore;
    private final FileGroupQueueWorker worker;

    public PipelineStageRuntime(final PipelineStage stage,
                                final FileGroupQueue inputQueue,
                                final FileGroupQueue outputQueue,
                                final FileGroupQueue splitZipQueue,
                                final FileStore fileStore,
                                final FileGroupQueueWorker worker) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.splitZipQueue = splitZipQueue;
        this.fileStore = fileStore;
        this.worker = worker;
    }

    public PipelineStage getStage() {
        return stage;
    }

    public PipelineStageName getStageName() {
        return stage.name();
    }

    public String getStageConfigName() {
        return stage.getConfigName();
    }

    public boolean isEnabled() {
        return stage.enabled();
    }

    public ConsumerStageThreadsConfig getThreads() {
        return stage.consumerThreads();
    }

    public Optional<FileGroupQueue> getInputQueue() {
        return Optional.ofNullable(inputQueue);
    }

    public Optional<FileGroupQueue> getOutputQueue() {
        return Optional.ofNullable(outputQueue);
    }

    public Optional<FileGroupQueue> getSplitZipQueue() {
        return Optional.ofNullable(splitZipQueue);
    }

    public Optional<FileStore> getFileStore() {
        return Optional.ofNullable(fileStore);
    }

    public Optional<FileGroupQueueWorker> getWorker() {
        return Optional.ofNullable(worker);
    }

    public boolean hasInputQueue() {
        return inputQueue != null;
    }

    public boolean hasOutputQueue() {
        return outputQueue != null;
    }

    public boolean hasSplitZipQueue() {
        return splitZipQueue != null;
    }

    public boolean hasFileStore() {
        return fileStore != null;
    }

    public boolean hasWorker() {
        return worker != null;
    }

    public boolean isQueueConsumingStage() {
        return hasInputQueue();
    }

    public boolean isQueuePublishingStage() {
        return hasOutputQueue() || hasSplitZipQueue();
    }

    public boolean isFileStoreWritingStage() {
        return hasFileStore();
    }

    @Override
    public String toString() {
        return "PipelineStageRuntime{" +
               "stage=" + stage +
               ", inputQueue=" + queueName(inputQueue) +
               ", outputQueue=" + queueName(outputQueue) +
               ", splitZipQueue=" + queueName(splitZipQueue) +
               ", fileStore=" + fileStoreName(fileStore) +
               ", hasWorker=" + hasWorker() +
               '}';
    }

    private static String queueName(final FileGroupQueue queue) {
        return queue == null
                ? null
                : queue.getName();
    }

    private static String fileStoreName(final FileStore fileStore) {
        return fileStore == null
                ? null
                : fileStore.getName();
    }
}
