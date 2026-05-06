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

package stroom.proxy.app.pipeline.stage;

import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;

import java.time.Duration;
import java.util.Objects;

/**
 * Result of a single {@link FileGroupQueueWorker} processing attempt.
 * <p>
 * A result describes what happened when a worker polled a queue and, if an item
 * was available, processed it. The result is intended for tests, logging,
 * metrics, and future worker lifecycle orchestration.
 * </p>
 */
public record FileGroupQueueWorkerResult(
        Outcome outcome,
        String queueName,
        String itemId,
        FileGroupQueueMessage message,
        Throwable error,
        Duration processingDuration) {

    public FileGroupQueueWorkerResult {
        outcome = Objects.requireNonNull(outcome, "outcome");
        queueName = normaliseOptional(queueName);
        itemId = normaliseOptional(itemId);
        processingDuration = processingDuration == null
                ? Duration.ZERO
                : processingDuration;

        if (processingDuration.isNegative()) {
            throw new IllegalArgumentException("processingDuration must not be negative");
        }

        if (outcome == Outcome.NO_ITEM) {
            message = null;
            error = null;
            itemId = null;
        }

        if (outcome == Outcome.PROCESSED && error != null) {
            throw new IllegalArgumentException("Processed queue worker results must not contain an error");
        }

        if (outcome == Outcome.FAILED && error == null) {
            throw new IllegalArgumentException("Failed queue worker results must contain an error");
        }
    }

    public static FileGroupQueueWorkerResult noItem(final String queueName,
                                                    final Duration processingDuration) {
        return new FileGroupQueueWorkerResult(
                Outcome.NO_ITEM,
                queueName,
                null,
                null,
                null,
                processingDuration);
    }

    public static FileGroupQueueWorkerResult processed(final String queueName,
                                                       final String itemId,
                                                       final FileGroupQueueMessage message,
                                                       final Duration processingDuration) {
        return new FileGroupQueueWorkerResult(
                Outcome.PROCESSED,
                queueName,
                itemId,
                Objects.requireNonNull(message, "message"),
                null,
                processingDuration);
    }

    public static FileGroupQueueWorkerResult failed(final String queueName,
                                                    final String itemId,
                                                    final FileGroupQueueMessage message,
                                                    final Throwable error,
                                                    final Duration processingDuration) {
        return new FileGroupQueueWorkerResult(
                Outcome.FAILED,
                queueName,
                itemId,
                message,
                Objects.requireNonNull(error, "error"),
                processingDuration);
    }

    public boolean hasItem() {
        return outcome != Outcome.NO_ITEM;
    }

    public boolean isProcessed() {
        return outcome == Outcome.PROCESSED;
    }

    public boolean isFailed() {
        return outcome == Outcome.FAILED;
    }

    public boolean isNoItem() {
        return outcome == Outcome.NO_ITEM;
    }

    private static String normaliseOptional(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    public enum Outcome {
        /**
         * The worker polled the queue but no item was available.
         */
        NO_ITEM,

        /**
         * The worker processed an item and acknowledged it successfully.
         */
        PROCESSED,

        /**
         * The worker attempted to process an item and failed it back to the queue.
         */
        FAILED
    }
}
