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

package stroom.proxy.app.pipeline.stress;

import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The ownership-transfer contract, on its own, with nothing else in the way.
 * <p>
 * Write the output, publish the reference, delete the input - in that order, and
 * only ever in that order. Every stage in the real pipeline does this around
 * whatever work it actually performs; this processor stands in for the
 * pre-aggregate and aggregate stages in the stress harness, whose real
 * implementations batch across many inputs and are covered by their own tests.
 * What is under test here is the transfer itself under injected faults, so the
 * work in the middle is deliberately nothing.
 * </p>
 * <p>
 * The ordering is the whole point. Publishing before the output is durable loses
 * data if the process dies in between. Deleting the input before publishing
 * loses data outright. Deleting after publishing risks only a duplicate, which
 * is what at-least-once buys and why this order is the correct one.
 * </p>
 */
public class TransferStageProcessor implements FileGroupQueueItemProcessor {

    private final FileStoreRegistry fileStoreRegistry;
    private final FileStore outputStore;
    private final FileGroupQueue outputQueue;
    private final String stageName;
    private final String producerId;

    public TransferStageProcessor(final FileStoreRegistry fileStoreRegistry,
                                  final FileStore outputStore,
                                  final FileGroupQueue outputQueue,
                                  final String stageName,
                                  final String producerId) {
        this.fileStoreRegistry = Objects.requireNonNull(fileStoreRegistry, "fileStoreRegistry");
        this.outputStore = Objects.requireNonNull(outputStore, "outputStore");
        this.outputQueue = Objects.requireNonNull(outputQueue, "outputQueue");
        this.stageName = Objects.requireNonNull(stageName, "stageName");
        this.producerId = Objects.requireNonNull(producerId, "producerId");
    }

    @Override
    public void process(final FileGroupQueueItem item) throws Exception {
        final FileGroupQueueMessage message = Objects.requireNonNull(item.getMessage(), "item.message");

        final Path sourceDir = fileStoreRegistry.resolve(message);
        if (!Files.isDirectory(sourceDir)) {
            throw new IOException(stageName + " message '" + message.messageId()
                                  + "' references '" + sourceDir + "' which is not a directory");
        }

        // 1. Write the output and make it durable.
        final FileStoreLocation location;
        try (final FileStoreWrite write = outputStore.newWrite()) {
            copyDirectoryContents(sourceDir, write.getPath());
            location = write.commit();
        }

        // 2. Publish the reference. Until this returns the output is unreferenced;
        //    if it throws, the input is still ours and we will be given it again.
        outputQueue.publish(FileGroupQueueMessage.create(
                outputQueue.getName(),
                UUID.randomUUID().toString(),
                location,
                stageName,
                producerId,
                message.traceId(),
                Map.of()));

        // 3. Release the input. A failure here costs a duplicate, never data.
        final FileStore inputStore = fileStoreRegistry.requireFileStore(
                message.fileStoreLocation().storeName());
        inputStore.delete(message.fileStoreLocation());
    }

    static void copyDirectoryContents(final Path sourceDir, final Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
            for (final Path source : stream) {
                if (Files.isRegularFile(source)) {
                    Files.copy(
                            source,
                            targetDir.resolve(source.getFileName().toString()),
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
