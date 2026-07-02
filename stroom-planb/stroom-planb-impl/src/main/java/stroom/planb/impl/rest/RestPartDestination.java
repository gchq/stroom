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

package stroom.planb.impl.rest;

import stroom.meta.shared.Meta;
import stroom.planb.impl.data.SequentialFileStore;
import stroom.planb.impl.db.PartDestination;
import stroom.planb.impl.db.WrittenPart;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Transfers a {@link WrittenPart} to a remote data node by zipping its local
 * writer directory and uploading the zip via {@link FileTransferClient#storePart}.
 *
 * <p>The zip file is always deleted in {@code finally}, regardless of whether
 * the upload succeeded or failed.
 *
 * <p>This class is stateless beyond the injected client and therefore
 * thread-safe.
 */
@Singleton
public class RestPartDestination implements PartDestination {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(RestPartDestination.class);

    private final FileTransferClient fileTransferClient;

    @Inject
    public RestPartDestination(final FileTransferClient fileTransferClient) {
        this.fileTransferClient = fileTransferClient;
    }

    @Override
    public boolean transfer(final WrittenPart part, final Meta meta) {
        final Path zipFile = part.localWriterDir().getParent()
                .resolve(part.localWriterDir().getFileName() + SequentialFileStore.ZIP_EXTENSION);

        LOGGER.debug(() -> LogUtil.message(
                "Plan B zipping writer dir {} for meta {}",
                part.localWriterDir(), meta.getId()));
        try {
            final String docUuidEntry = part.localWriterDir().getFileName().toString();
            ZipUtil.zip(
                    zipFile,
                    part.localWriterDir().getParent(),
                    path -> !path.equals(zipFile),
                    name -> name.startsWith(docUuidEntry + "/") || name.equals(docUuidEntry));
            try {
                final String fileHash = FileHashUtil.hash(zipFile);
                final FileDescriptor fileDescriptor = new FileDescriptor(
                        System.currentTimeMillis(),
                        meta.getId(),
                        fileHash);
                LOGGER.debug(() -> LogUtil.message(
                        "Plan B sending {} for meta {}",
                        zipFile.getFileName(), meta.getId()));
                fileTransferClient.storePart(fileDescriptor, zipFile, part.synchroniseMerge());
                return true;
            } finally {
                Files.deleteIfExists(zipFile);
            }
        } catch (final Exception e) {
            LOGGER.error(() -> LogUtil.message(
                    "REST upload failed for writer dir {} (doc={}, meta={}); "
                    + "writer dir will be retained for operator inspection",
                    part.localWriterDir(), part.doc().getUuid(), meta.getId()), e);
            return false;
        }
    }
}
