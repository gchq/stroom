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


import stroom.util.io.NoCloseInputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public class FileFrameSupplierImpl extends AbstractZstdFrameSupplier {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileFrameSupplierImpl.class);

    private final Path file;
    private final FileChannel fileChannel;

//    private ZstdSeekTable zstdSeekTable = null;
//    private IntSortedSet includedFrameIndexes = IntSortedSets.emptySet();
//    private boolean includeAll = false;
//    private Iterator<FrameLocation> frameLocationIterator = null;
//    private FrameLocation currentFrameLocation = null;

    public FileFrameSupplierImpl(final Path file) throws IOException {
        this.file = Objects.requireNonNull(file);
        this.fileChannel = createFileChannel();
    }

    private FileChannel createFileChannel() throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new RuntimeException(LogUtil.message("File '{}' does not exist or is not a regular file",
                    file.toAbsolutePath().normalize().toString()));
        }
        final FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ);
        fileChannel.position(0);
        LOGGER.debug(() -> LogUtil.message("Opened fileChannel {}", file.toAbsolutePath().normalize().toString()));
        return fileChannel;
    }

    @Override
    public void close() throws Exception {
        if (fileChannel != null) {
            LOGGER.debug(() ->
                    LogUtil.message("Closed fileChannel {}", file.toAbsolutePath().normalize().toString()));
            fileChannel.close();
        }
    }

    public Path getFile() {
        return file;
    }

    @Override
    public InputStream next() {
        checkInitialised();
        final FrameLocation frameLocation = getCurrentFrameLocation();
        try {
            fileChannel.position(frameLocation.position());
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message(
                    "Error positioning fileChannel '{}' at frameLocation: {} - {}",
                    file.toAbsolutePath().normalize(), frameLocation, LogUtil.exceptionMessage(e)), e);
        }

        // TODO not sure we need to create it for each frame
        return new NoCloseInputStream(Channels.newInputStream(fileChannel));
    }

    @Override
    public String toString() {
        return "FileFrameSupplierImpl{" +
               "file=" + file +
               ", zstdSeekTable=" + zstdSeekTable +
               ", includeAll=" + includeAll +
               ", currentFrameLocation=" + currentFrameLocation +
               '}';
    }
}
