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

package stroom.proxy.app.pipeline.stage.forward;

import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fan-out adapter for the reference-message forward stage.
 * <p>
 * The normal {@link ForwardStageProcessor} resolves an input queue message to a
 * source file-group directory and then delegates to a
 * {@link ForwardStageProcessor.FileGroupForwarder}. This adapter implements that
 * delegate for the multi-destination case.
 * </p>
 * <p>
 * For each configured destination, the source file group is copied into a
 * destination-owned {@link FileStore}. A new {@link FileGroupQueueMessage} is
 * then published to that destination's forwarding queue. Downstream destination
 * workers can therefore treat their queued source directory as disposable owned
 * input and may move or delete it after successful forwarding without affecting
 * any other destination.
 * </p>
 * <p>
 * This class performs only the durable fan-out handoff. It does not delete the
 * original input source — that responsibility belongs to the enclosing
 * {@link ForwardStageProcessor}, which deletes the input from the source
 * {@link FileStore} after the forwarder returns successfully. Queue
 * acknowledgement remains owned by {@link FileGroupQueueWorker}.
 * </p>
 * <p>
 * Publication is at-least-once. If copying/publishing succeeds for one
 * destination and then a later destination fails, the input queue item will be
 * failed by the worker and retried. That can publish duplicate destination work
 * unless a later idempotency layer suppresses duplicates.
 * </p>
 */
public final class ForwardStageFanOutForwarder implements ForwardStageProcessor.FileGroupForwarder {

    public static final String ATTRIBUTE_FORWARD_DESTINATION = "forwardDestination";
    public static final String ATTRIBUTE_SOURCE_MESSAGE_ID = "sourceMessageId";
    public static final String ATTRIBUTE_SOURCE_QUEUE_NAME = "sourceQueueName";
    public static final String DEFAULT_PRODUCING_STAGE = "forwardFanOut";

    private final List<Destination> destinations;
    private final String producerId;
    private final String producingStage;

    public ForwardStageFanOutForwarder(final List<Destination> destinations,
                                       final String producerId) {
        this(destinations, producerId, DEFAULT_PRODUCING_STAGE);
    }

    public ForwardStageFanOutForwarder(final List<Destination> destinations,
                                       final String producerId,
                                       final String producingStage) {
        this.destinations = List.copyOf(Objects.requireNonNull(destinations, "destinations"));
        this.producerId = requireNonBlank(producerId, "producerId");
        this.producingStage = requireNonBlank(producingStage, "producingStage");

        if (this.destinations.isEmpty()) {
            throw new IllegalArgumentException("At least one forward destination must be supplied");
        }
    }

    @Override
    public void forward(final FileGroupQueueMessage message,
                        final Path sourceDir) throws IOException {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(sourceDir, "sourceDir");

        if (!Files.isDirectory(sourceDir)) {
            throw new IOException("Cannot fan out forward message '" + message.messageId()
                                  + "' because source path is not a directory: " + sourceDir);
        }

        for (final Destination destination : destinations) {
            fanOutToDestination(message, sourceDir, destination);
        }
    }

    public List<Destination> getDestinations() {
        return destinations;
    }

    private void fanOutToDestination(final FileGroupQueueMessage sourceMessage,
                                     final Path sourceDir,
                                     final Destination destination) throws IOException {
        final FileStoreLocation destinationLocation;

        try (final FileStoreWrite write = destination.fileStore().newWrite()) {
            copyDirectory(sourceDir, write.getPath());
            destinationLocation = write.commit();
        }

        final FileGroupQueueMessage destinationMessage = FileGroupQueueMessage.create(
                destination.queue().getName(),
                sourceMessage.fileGroupId(),
                destinationLocation,
                producingStage,
                producerId,
                sourceMessage.traceId(),
                createDestinationAttributes(sourceMessage, destination));

        destination.queue().publish(destinationMessage);
    }

    private Map<String, String> createDestinationAttributes(final FileGroupQueueMessage sourceMessage,
                                                            final Destination destination) {
        final Map<String, String> attributes = new LinkedHashMap<>(sourceMessage.attributes());
        attributes.put(ATTRIBUTE_FORWARD_DESTINATION, destination.name());
        attributes.put(ATTRIBUTE_SOURCE_MESSAGE_ID, sourceMessage.messageId());
        attributes.put(ATTRIBUTE_SOURCE_QUEUE_NAME, sourceMessage.queueName());
        return attributes;
    }

    private static void copyDirectory(final Path sourceDir,
                                      final Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir,
                                                     final BasicFileAttributes attrs) throws IOException {
                final Path relativePath = sourceDir.relativize(dir);
                Files.createDirectories(targetDir.resolve(relativePath));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(final Path file,
                                             final BasicFileAttributes attrs) throws IOException {
                final Path relativePath = sourceDir.relativize(file);
                final Path targetFile = targetDir.resolve(relativePath);
                Files.createDirectories(targetFile.getParent());
                Files.copy(
                        file,
                        targetFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * A destination-specific handoff target.
     *
     * @param name A stable logical destination name used in message attributes.
     * @param fileStore The destination-owned source file store.
     * @param queue The destination forwarding queue.
     */
    public record Destination(
            String name,
            FileStore fileStore,
            FileGroupQueue queue) {

        public Destination {
            name = requireNonBlank(name, "name");
            fileStore = Objects.requireNonNull(fileStore, "fileStore");
            queue = Objects.requireNonNull(queue, "queue");
        }
    }
}
