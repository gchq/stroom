/*
 * Copyright 2016-2026 Crown Copyright
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


import stroom.util.NullSafeExtra;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.esotericsoftware.kryo.io.ByteBufferInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public class FileFrameSupplierImpl extends AbstractZstdFrameSupplier {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileFrameSupplierImpl.class);

    private final Path file;
    private final boolean closeResources;
    private final MemorySegment fileMemorySegment;
    private FileChannel fileChannel = null;
    private Arena arena = null;

    public FileFrameSupplierImpl(final Path file) throws IOException {
        this.file = Objects.requireNonNull(file);
        this.fileChannel = createFileChannel();
        this.arena = Arena.ofConfined();
        this.closeResources = true;
        // Memory map the whole file so we can have random access to any frames in it
        this.fileMemorySegment = fileChannel.map(
                MapMode.READ_ONLY,
                0,
                zstdSeekTable.getTotalCompressedDataSize(),
                arena);
    }

    /**
     * Caller is responsible for closing fileChannel and arena.
     *
     * @param fileMemorySegment A memory segment covering all data frames in the seek table.
     */
    public FileFrameSupplierImpl(final Path file,
                                 final MemorySegment fileMemorySegment) throws IOException {
        this.file = Objects.requireNonNull(file);
        this.closeResources = false;
        this.fileMemorySegment = Objects.requireNonNull(fileMemorySegment);
        // We are using the provided fileMemorySegment, so don't need to create the fileChannel and arena
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
        if (closeResources) {
            fileChannel = NullSafeExtra.close(fileChannel, true, "fileChannel");
            fileChannel = NullSafeExtra.close(arena, true, "arena");
        }
    }

    public Path getFile() {
        return file;
    }

    @Override
    public InputStream next() {
        checkInitialised();
        final FrameLocation frameLocation = nextFrameLocation();
        if (frameLocation != null) {
            LOGGER.debug("next() - frameLocation: {}", frameLocation);
            final MemorySegment frameMemSegment = frameLocation.asSlice(fileMemorySegment);
            return new ByteBufferInputStream(frameMemSegment.asByteBuffer());
        } else {
            return null;
        }
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
