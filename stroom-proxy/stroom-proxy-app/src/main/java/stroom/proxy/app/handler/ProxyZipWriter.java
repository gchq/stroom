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

package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

public class ProxyZipWriter extends ZipWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProxyZipWriter.class);

    private final ProxyZipValidator validator = new ProxyZipValidator();

    public ProxyZipWriter(final Path path, final byte[] buffer) throws IOException {
        super(path, buffer);
    }

    public ProxyZipWriter(final OutputStream outputStream, final byte[] buffer) {
        super(outputStream, buffer);
    }

    public ProxyZipWriter(final ZipArchiveOutputStream zipArchiveOutputStream,
                          final byte[] buffer) {
        super(zipArchiveOutputStream, buffer);
    }

    @Override
    void putArchiveEntry(final ZipArchiveEntry zipArchiveEntry) throws IOException {
        super.putArchiveEntry(zipArchiveEntry);
        validator.addEntry(zipArchiveEntry.getName());
    }

    @Override
    void putRawArchiveEntry(final ZipArchiveEntry zipArchiveEntry, final InputStream inputStream) throws IOException {
        super.putRawArchiveEntry(zipArchiveEntry, inputStream);
        validator.addEntry(zipArchiveEntry.getName());
    }

    @Override
    public void close() throws IOException {
        super.close();

        // Assert that we have written a valid proxy zip.
        if (!validator.isValid()) {
            LOGGER.error(validator.getErrorMessage());
            throw new RuntimeException(validator.getErrorMessage());
        }
    }
}
