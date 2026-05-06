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

import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.stage.FileGroupQueueWorker;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Queue item processor scaffold for the reference-message forward stage.
 * <p>
 * The forward stage consumes {@link FileGroupQueueMessage} instances from the
 * forwarding input queue. Each message contains a {@link FileStoreLocation}
 * reference to a file group that is ready to be owned by forwarding. This
 * processor resolves that reference using a {@link FileStoreRegistry}, validates
 * that the expected proxy file-group files are present, and delegates source
 * ownership to a small forwarding adapter.
 * </p>
 * <p>
 * A queue worker owns acknowledgement and retry/fail behaviour, so this class
 * deliberately does not call {@link FileGroupQueueItem#acknowledge()} or
 * {@link FileGroupQueueItem#fail(Throwable)}.
 * </p>
 * <p>
 * The supplied {@link FileGroupForwarder} is intentionally narrow. It allows
 * tests and future production wiring to plug in the existing forwarding logic
 * without coupling queue resolution to queue acknowledgement. The directory
 * supplied to the forwarder is owned by the forwarder from that point and may be
 * moved or deleted after successful forwarding. If data needs to go to multiple
 * destinations, the production adapter should first fan out by copying the
 * resolved file group into destination-owned source directories and queuing
 * those copies for the individual forwarders.
 * </p>
 */
public class ForwardStageProcessor implements FileGroupQueueItemProcessor {

    private final FileStoreRegistry fileStoreRegistry;
    private final FileGroupForwarder fileGroupForwarder;

    public ForwardStageProcessor(final FileStoreRegistry fileStoreRegistry,
                                 final FileGroupForwarder fileGroupForwarder) {
        this.fileStoreRegistry = Objects.requireNonNull(fileStoreRegistry, "fileStoreRegistry");
        this.fileGroupForwarder = Objects.requireNonNull(fileGroupForwarder, "fileGroupForwarder");
    }

    @Override
    public void process(final FileGroupQueueItem item) throws Exception {
        Objects.requireNonNull(item, "item");

        final FileGroupQueueMessage message = Objects.requireNonNull(
                item.getMessage(),
                "item.message");

        final Path sourceDir = fileStoreRegistry.resolve(message);
        validateFileGroup(message, sourceDir);

        // 1. Forward the data (single-dest or fan-out).
        fileGroupForwarder.forward(message, sourceDir);

        // 2. Delete the consumed input from the source file store.
        //    At this point the forwarder has durably handed off the data
        //    (copied to destination stores + published to destination queues).
        //    The input file group is no longer needed and must be deleted
        //    to fulfil the ownership-transfer contract.
        final FileStore inputStore = fileStoreRegistry.requireFileStore(
                message.fileStoreLocation().storeName());
        inputStore.delete(message.fileStoreLocation());
    }

    private static void validateFileGroup(final FileGroupQueueMessage message,
                                          final Path fileGroupDir) throws IOException {
        if (!Files.isDirectory(fileGroupDir)) {
            throw new IOException("Forward stage message '" + message.messageId()
                                  + "' references file group '" + message.fileGroupId()
                                  + "' at '" + fileGroupDir
                                  + "' but the path is not a directory");
        }

        final FileGroup fileGroup = new FileGroup(fileGroupDir);
        requireRegularFile(message, fileGroup.getMeta(), "meta");
        requireRegularFile(message, fileGroup.getZip(), "zip");

        /*
         * The entries file is part of the proxy file-group convention and is
         * expected for groups produced by the new pipeline stages. Keep the
         * validation here so broken handoffs fail before forwarding rather than
         * being acknowledged as successfully processed.
         */
        requireRegularFile(message, fileGroup.getEntries(), "entries");
    }

    private static void requireRegularFile(final FileGroupQueueMessage message,
                                           final Path path,
                                           final String fileDescription) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Forward stage message '" + message.messageId()
                                  + "' references file group '" + message.fileGroupId()
                                  + "' but the expected " + fileDescription
                                  + " file is missing: " + path);
        }
    }

    /**
     * Adapter for the concrete forward operation.
     * <p>
     * Implementations take ownership of the supplied source directory and may
     * move or delete it after successful forwarding. Multi-destination adapters
     * should copy the original file group into one owned source directory per
     * destination before queuing or forwarding those copies. Any exception thrown
     * from this method is treated as processing failure by
     * {@link FileGroupQueueWorker}.
     * </p>
     */
    @FunctionalInterface
    public interface FileGroupForwarder {

        void forward(FileGroupQueueMessage message,
                     Path sourceDir) throws Exception;
    }
}
