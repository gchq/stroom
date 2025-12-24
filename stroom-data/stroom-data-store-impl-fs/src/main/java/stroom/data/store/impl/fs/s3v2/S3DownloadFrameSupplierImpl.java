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

package stroom.data.store.impl.fs.s3v2;


import stroom.aws.s3.impl.S3Manager;
import stroom.meta.shared.Meta;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.UUID;

class S3DownloadFrameSupplierImpl implements ZstdFrameSupplier {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3DownloadFrameSupplierImpl.class);

    private final S3Manager s3Manager;
    private final Meta meta;
    private final String childStreamType;
    private final String keyNameTemplate;
    private final Path tempDir;
    private final FileFrameSupplierImpl fileFrameSupplierImpl;
    private final S3StreamTypeExtensions s3StreamTypeExtensions;

    public S3DownloadFrameSupplierImpl(final S3Manager s3Manager,
                                       final Meta meta,
                                       final String childStreamType,
                                       final String keyNameTemplate,
                                       final Path tempDir,
                                       final S3StreamTypeExtensions s3StreamTypeExtensions) {
        this.s3Manager = s3Manager;
        this.meta = meta;
        this.childStreamType = childStreamType;
        this.keyNameTemplate = keyNameTemplate;
        this.tempDir = tempDir;
        this.s3StreamTypeExtensions = s3StreamTypeExtensions;
        this.fileFrameSupplierImpl = createFileFrameSupplier();
    }

    @Override
    public InputStream getFrameInputStream(final FrameLocation frameLocation) throws IOException {
        return fileFrameSupplierImpl.getFrameInputStream(frameLocation);
    }

    @Override
    public void close() throws Exception {
        fileFrameSupplierImpl.close();
        final Path tempFile = fileFrameSupplierImpl.getFile();
        LOGGER.debug("close() - Deleting file {}", tempFile);
        FileUtil.deleteFile(tempFile);
    }

    private FileFrameSupplierImpl createFileFrameSupplier() {
        final Path tempFile = createTempFile();
        // Can't use async as this thread needs it
        s3Manager.download(meta, childStreamType, keyNameTemplate, tempFile, false);
        try {
            return new FileFrameSupplierImpl(tempFile);
        } catch (final IOException e) {
            throw new UncheckedIOException(LogUtil.message(
                    "Error creating frame supplier for '{}' - {}",
                    tempFile.toAbsolutePath().normalize(), LogUtil.exceptionMessage(e)), e);
        }
    }

    private Path createTempFile() {
        final String ext = s3StreamTypeExtensions.getExtension(meta.getTypeName(), childStreamType);
        final long id = meta.getId();
        return tempDir.resolve(id + "__" + UUID.randomUUID() + ext);
    }
}
