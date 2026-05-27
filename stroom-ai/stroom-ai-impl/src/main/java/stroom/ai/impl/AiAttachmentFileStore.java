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

package stroom.ai.impl;

import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Manages local CSV files for AI chat attachments.
 * <p>
 * Files are stored at: {tempDir}/ai-attachments/{attachmentId}.csv
 * <p>
 * Lifecycle: created during download, deleted when the chat/attachment is deleted.
 * If a file is missing at analysis time, it is treated as an error.
 */
@Singleton
public class AiAttachmentFileStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AiAttachmentFileStore.class);

    private static final String ATTACHMENT_DIR_NAME = "ai-attachments";
    private static final String CSV_EXTENSION = ".csv";

    private final TempDirProvider tempDirProvider;

    @Inject
    AiAttachmentFileStore(final TempDirProvider tempDirProvider) {
        this.tempDirProvider = tempDirProvider;
    }

    /**
     * @return The directory where attachment files are stored.
     */
    public Path getAttachmentDir() {
        return tempDirProvider.get().resolve(ATTACHMENT_DIR_NAME);
    }

    /**
     * @return The path to the CSV file for a given attachment ID.
     */
    public Path getAttachmentFile(final int attachmentId) {
        return getAttachmentDir().resolve(attachmentId + CSV_EXTENSION);
    }

    /**
     * Creates the attachment file (and parent directories if needed), returning the path.
     *
     * @param attachmentId the attachment ID
     * @return the path to the newly created file
     */
    public Path createAttachmentFile(final int attachmentId) {
        try {
            final Path dir = getAttachmentDir();
            Files.createDirectories(dir);
            return dir.resolve(attachmentId + CSV_EXTENSION);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to create attachment directory for ID " + attachmentId, e);
        }
    }

    /**
     * Deletes the attachment file if it exists. Does nothing if the file is missing.
     *
     * @param attachmentId the attachment ID
     */
    public void deleteAttachmentFile(final int attachmentId) {
        try {
            final Path file = getAttachmentFile(attachmentId);
            Files.deleteIfExists(file);
        } catch (final IOException e) {
            LOGGER.debug(() -> "Failed to delete attachment file for ID " + attachmentId, e);
        }
    }

    /**
     * Deletes attachment files for a list of attachment IDs.
     *
     * @param attachmentIds the attachment IDs whose files should be deleted
     */
    public void deleteAttachmentFiles(final List<Integer> attachmentIds) {
        if (attachmentIds != null) {
            attachmentIds.forEach(this::deleteAttachmentFile);
        }
    }

    /**
     * @return {@code true} if the CSV file exists on disk for the given attachment ID.
     */
    public boolean exists(final int attachmentId) {
        return Files.exists(getAttachmentFile(attachmentId));
    }
}
