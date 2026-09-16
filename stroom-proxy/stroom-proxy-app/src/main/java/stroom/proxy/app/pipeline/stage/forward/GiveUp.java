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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.proxy.app.handler.Destination;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.Refused;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Where a group goes when the proxy stops trying to forward it: written whole to a destination of
 * its own, a directory or an S3 bucket, with an {@code error.log} beside it saying why, how many
 * attempts were made and how old it was. The delivery must succeed before the group's message is
 * acknowledged; if it throws, the message is failed and the give-up is attempted again on
 * redelivery ({@code designs/stages/forward.md} F5, §4.3).
 */
public final class GiveUp {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GiveUp.class);

    private final Destination destination;

    public GiveUp(final Destination destination) {
        this.destination = Objects.requireNonNull(destination, "destination");
    }

    public Destination getDestination() {
        return destination;
    }

    /**
     * @param group          The resolved group, which the give-up destination may move.
     * @param forDestination The forward destination that gave up.
     * @param attempt        This message's delivery attempt.
     * @param age            How long since the message was published.
     * @param failure        The final failure: a refusal, or the transient failure that came after
     *                       {@code maxRetryAge}.
     */
    public void giveUp(final Path group,
                       final FileGroupQueueMessage message,
                       final String forDestination,
                       final int attempt,
                       final Duration age,
                       final Throwable failure) throws IOException {
        Files.writeString(
                group.resolve(FileGroup.ERROR_LOG_FILE_NAME),
                errorLog(message, forDestination, attempt, age, failure),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        try {
            destination.deliver(group);
        } catch (final Refused e) {
            throw new IOException(LogUtil.message(
                    "The give-up destination {} refused {}: {}", destination, message.messageId(), e.getMessage()), e);
        }
        LOGGER.error(() -> LogUtil.message(
                "Gave up forwarding {} (feed {}) to '{}' after {} attempt(s) over {}: {}. It is at {}",
                message.messageId(), message.feed(), forDestination, attempt, age,
                LogUtil.exceptionMessage(failure), destination.getDescription()));
    }

    /**
     * Complete a give-up that was decided on an earlier attempt: the group already carries its
     * {@code error.log}, which is kept, with a line appended saying the give-up was resumed.
     */
    public void resume(final Path group,
                       final FileGroupQueueMessage message,
                       final String forDestination,
                       final int attempt,
                       final Duration age) throws IOException {
        Files.writeString(
                group.resolve(FileGroup.ERROR_LOG_FILE_NAME),
                "resumed: " + Instant.now() + " on attempt " + attempt + ", age " + age + '\n',
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
                StandardOpenOption.WRITE);
        try {
            destination.deliver(group);
        } catch (final Refused e) {
            throw new IOException(LogUtil.message(
                    "The give-up destination {} refused {}: {}", destination, message.messageId(), e.getMessage()), e);
        }
        LOGGER.error(() -> LogUtil.message(
                "Completed the give-up of {} (feed {}) for '{}' on attempt {}. It is at {}",
                message.messageId(), message.feed(), forDestination, attempt, destination.getDescription()));
    }

    static String errorLog(final FileGroupQueueMessage message,
                           final String forDestination,
                           final int attempt,
                           final Duration age,
                           final Throwable failure) {
        final StringBuilder sb = new StringBuilder();
        sb.append("time: ").append(Instant.now()).append('\n');
        sb.append("messageId: ").append(message.messageId()).append('\n');
        if (message.traceId() != null) {
            sb.append("traceId: ").append(message.traceId()).append('\n');
        }
        sb.append("destination: ").append(forDestination).append('\n');
        sb.append("attempts: ").append(attempt).append('\n');
        sb.append("age: ").append(age).append('\n');
        sb.append("failure: ").append(failure.getClass().getSimpleName());
        if (failure instanceof final Refused refused && refused.getStatus() != null) {
            sb.append(" status ").append(refused.getStatus().getCode())
                    .append(' ').append(refused.getStatus().name());
        }
        if (failure.getMessage() != null) {
            sb.append(" - ").append(failure.getMessage().replace('\n', ' '));
        }
        sb.append('\n');
        return sb.toString();
    }
}
