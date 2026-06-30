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

package stroom.planb.impl.db;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.meta.shared.Meta;
import stroom.planb.impl.fs.SharedFileStorePartDestination;
import stroom.planb.impl.rest.RestPartDestination;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Singleton factory for {@link PlanBStreamWriter} instances.
 *
 * <p>One {@link PlanBStreamWriter} is created per pipeline stream execution via
 * {@link #createWriter(Meta)}. On construction this class clears the local
 * writer directory: any data left from a previous run was never successfully
 * sent, so it is safe to discard.
 *
 * <p>Orphaned {@code .tmp_} directories on the shared filesystem are cleaned
 * up separately by {@link stroom.planb.impl.fs.SharedFileStoreCleaner#startup()}.
 */
@Singleton
public class PlanBStreamWriterFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PlanBStreamWriterFactory.class);

    private final ByteBuffers byteBuffers;
    private final ByteBufferFactory byteBufferFactory;
    private final StatePaths statePaths;
    private final BatchDestination batchPublisher;
    private final SharedFileStorePartDestination sharedFsDestination;
    private final RestPartDestination restDestination;

    @Inject
    public PlanBStreamWriterFactory(final ByteBuffers byteBuffers,
                                    final ByteBufferFactory byteBufferFactory,
                                    final StatePaths statePaths,
                                    final BatchDestination batchPublisher,
                                    final SharedFileStorePartDestination sharedFsDestination,
                                    final RestPartDestination restDestination) {
        this.byteBuffers = byteBuffers;
        this.byteBufferFactory = byteBufferFactory;
        this.statePaths = statePaths;
        this.batchPublisher = batchPublisher;
        this.sharedFsDestination = sharedFsDestination;
        this.restDestination = restDestination;

        // Clear the local writer directory: any data remaining here was never
        // successfully published, so it is safe to discard.
        if (Files.isDirectory(statePaths.getWriterDir())) {
            LOGGER.info("Clearing writer directory: {}", statePaths.getWriterDir());
            FileUtil.deleteDir(statePaths.getWriterDir());
        }
    }

    public PlanBStreamWriter createWriter(final Meta meta) {
        final Path dir;
        try {
            dir = statePaths.getWriterDir()
                    .resolve(meta.getId() + "_" + UUID.randomUUID());
            Files.createDirectories(dir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return new PlanBStreamWriter(
                byteBuffers,
                byteBufferFactory,
                batchPublisher,
                sharedFsDestination,
                restDestination,
                dir,
                meta);
    }
}
