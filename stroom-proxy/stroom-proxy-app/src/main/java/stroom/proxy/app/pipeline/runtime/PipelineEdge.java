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

import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.store.FileStore;

import java.util.Objects;

/**
 * Directed edge between two enabled pipeline stages.
 * <p>
 * An edge represents the logical queue used to hand work from one stage to the
 * next. The queue transports {@link FileGroupQueueMessage} instances containing
 * references to data already written to a {@link FileStore}; the edge does not
 * imply any movement of the referenced file-group data.
 * </p>
 *
 * @param sourceStage The stage that publishes messages.
 * @param targetStage The stage that consumes messages.
 * @param queueName The logical queue name connecting the stages.
 */
record PipelineEdge(
        PipelineStageName sourceStage,
        PipelineStageName targetStage,
        String queueName) {

    public PipelineEdge {
        sourceStage = Objects.requireNonNull(sourceStage, "sourceStage");
        targetStage = Objects.requireNonNull(targetStage, "targetStage");
        queueName = requireNonBlank(queueName, "queueName");

        if (sourceStage == targetStage) {
            throw new IllegalArgumentException("sourceStage and targetStage must be different");
        }
    }

    static PipelineEdge of(final PipelineStageName sourceStage,
                                  final PipelineStageName targetStage,
                                  final String queueName) {
        return new PipelineEdge(sourceStage, targetStage, queueName);
    }

    public boolean connects(final PipelineStageName sourceStage,
                            final PipelineStageName targetStage) {
        return this.sourceStage == sourceStage
               && this.targetStage == targetStage;
    }

    public boolean involves(final PipelineStageName stageName) {
        return sourceStage == stageName
               || targetStage == stageName;
    }

    public boolean usesQueue(final String queueName) {
        return this.queueName.equals(queueName);
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
