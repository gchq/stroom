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


import stroom.bytebuffer.ByteBufferPoolConfig;
import stroom.data.store.api.SegmentInputStream;
import stroom.data.store.api.SegmentOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import net.datafaker.Faker;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestSparseFile {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestSparseFile.class);

    private static final int COMPRESSION_LEVEL = 7;
    private static final int FRAME_COUNT = 100;
    private static final long RANDOM_SEED = 57294857573L;

    @Test
    void test(@TempDir Path tempDir) throws IOException {
        final Path sourceFile = tempDir.resolve("data.zst");
        final Path sparseFile = tempDir.resolve("sparse_data.zst");
        final IntSortedSet includeSet = new IntAVLTreeSet(IntSet.of(2, 42, 77));
        // Write the file that is the equivalent of an S3 file
        writeSourceDate(sourceFile);

        // Parse the seek table
        final ZstdSeekTable seekTable;
        try (final Arena arena = Arena.ofShared()) {
            try (final FileChannel channel = openRead(sourceFile)) {
                final long fileSize = Files.size(sourceFile);
                final long seekTableFrameSize = ZstdSegmentUtil.calculateSeekTableFrameSize(FRAME_COUNT);
                final long seekTableOffset = fileSize - seekTableFrameSize;
                final MemorySegment mappedSegment = channel.map(
                        MapMode.READ_ONLY,
                        seekTableOffset,
                        seekTableFrameSize,
                        arena);
                final ByteBuffer byteBuffer = mappedSegment.asByteBuffer();
                seekTable = ZstdSeekTable.parse(byteBuffer)
                        .orElseThrow();

                LOGGER.info("seekTable: {}", seekTable);
            }
        }

        // Write the sparse file
        try (final Arena arena = Arena.ofShared()) {
            // We use CREATE and WRITE. Mapping a large size implies a sparse file on OSs that support it.
            try (final FileChannel sourceFileChannel = openRead(sourceFile);
                    final FileChannel sparseFileChannel = openReadWrite(sparseFile)) {

                // Map the whole sparse file into virtual memory
                final MemorySegment sparseFileMemSegment = sparseFileChannel.map(
                        MapMode.READ_WRITE,
                        0,
                        seekTable.getTotalCompressedDataSize(),
                        arena);

                for (final Integer frameIdx : includeSet) {
                    final FrameLocation frameLocation = seekTable.getFrameLocation(frameIdx);
                    LOGGER.debug("frameLocation: {}", frameLocation);

                    final MemorySegment sourceMemSegment = sourceFileChannel.map(
                            MapMode.READ_ONLY,
                            frameLocation.position(),
                            frameLocation.compressedSize(),
                            arena);

                    final MemorySegment frameMemSegment = sparseFileMemSegment.asSlice(
                            frameLocation.position(),
                            frameLocation.compressedSize());

                    LOGGER.debug("sourceMemSegment: {}, frameMemSegment: {}", sourceMemSegment, frameMemSegment);

                    frameMemSegment.copyFrom(sourceMemSegment);
                }
            }
        }

        LOGGER.info("sparseFile: {}, size: {}", sparseFile, Files.size(sparseFile));

        try (final SegmentInputStream zstdSegmentInputStream = new ZstdSegmentInputStream(
                seekTable,
                new FileFrameSupplierImpl(sourceFile),
                null,
                new HeapBufferPool(ByteBufferPoolConfig::new))) {

            includeSet.forEach(zstdSegmentInputStream::include);

            final byte[] allBytes = IOUtils.toByteArray(zstdSegmentInputStream);
            final String output = new String(allBytes, StandardCharsets.UTF_8);
            final int count = StringUtils.countMatches(output, "idx=");
            LOGGER.debug("count: {}, output:\n{}", count, output);
//            LOGGER.debug("frameSize: {}, allBytes.size: {}",
//                    frameLocation.compressedSize(), allBytes.length);
//            frameSliceByteBuffer.put(allBytes);
//            frameSliceByteBuffer.flip();
        }

//        // Read from the sparse file
//        try (final Arena arena = Arena.ofShared()) {
//            try (final FileChannel sparseFileChannel = openRead(sparseFile)) {
//                final MemorySegment sparseFileMemSegment = sparseFileChannel.map(
//                        MapMode.READ_ONLY,
//                        0,
//                        Files.size(sparseFile),
//                        arena);
//
//
//            }
//        }


//
//                    final ByteBuffer frameSliceByteBuffer = frameMemSegment.asByteBuffer();
//
//                    try (final SegmentInputStream zstdSegmentInputStream = new ZstdSegmentInputStream(
//                            seekTable,
//                            new FileFrameSupplierImpl(sourceFile),
//                            null,
//                            new HeapBufferPool(ByteBufferPoolConfig::new))) {
//
//                        zstdSegmentInputStream.include(frameIdx);
//
//                        final byte[] allBytes = zstdSegmentInputStream.readAllBytes();
//                        LOGGER.debug("frameSize: {}, allBytes.size: {}",
//                                frameLocation.compressedSize(), allBytes.length);
//                        frameSliceByteBuffer.put(allBytes);
//                        frameSliceByteBuffer.flip();
//                    }


//        Path path = Path.of("sparse-data.bin");

//        // 1. Setup: Create a Shared Arena
//        // A shared arena allows the memory segment to be accessed by multiple threads safely.
//        // The try-with-resources ensures the memory is unmapped/released when done.
//        try (final Arena arena = Arena.ofShared()) {
//
//            // 2. Open File and Map it
//            // We use CREATE and WRITE. Mapping a large size implies a sparse file on OSs that support it.
//            try (final FileChannel channel = FileChannel.open(path,
//                    StandardOpenOption.READ,
//                    StandardOpenOption.WRITE,
//                    StandardOpenOption.CREATE)) {
//
//                System.out.println("Mapping 1GB file into memory...");
//
//                // Map the file into virtual memory
//                MemorySegment mappedSegment = channel.map(
//                        FileChannel.MapMode.READ_WRITE,
//                        0,
//                        FILE_SIZE,
//                        arena
//                );
//
//                // 3. Concurrency Setup
//                ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
//                long chunkSize = FILE_SIZE / THREAD_COUNT;
//
//                System.out.println("Starting " + THREAD_COUNT + " threads writing to distinct ranges...");
//
//                for (int i = 0; i < THREAD_COUNT; i++) {
//                    final int threadId = i;
//                    final long startOffset = i * chunkSize;
//
//                    // 4. Submit Tasks
//                    executor.submit(() -> {
//                        // Create a slice for this specific thread.
//                        // This is a lightweight view of the original segment.
//                        // It ensures this thread cannot write outside its assigned chunk.
//                        MemorySegment threadSlice = mappedSegment.asSlice(startOffset, chunkSize);
//
//                        writeData(threadSlice, threadId);
//                    });
//                }
//
//                // Wait for all threads to finish
//                executor.shutdown();
//                boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);
//
//                if (finished) {
//                    System.out.println("All threads finished writing.");
//                    // Optional: Verify data (simple check)
//                    verifyData(mappedSegment, chunkSize);
//                } else {
//                    System.err.println("Timed out waiting for threads.");
//                }
//            }
//        }
//        // Arena closes here, invalidating the MemorySegment and flushing/unmapping the file.
//
//        // Check physical size on disk to prove sparsity (OS dependent)
//        long physicalSize = Files.size(path);
//        System.out.println("Logical File Size: " + FILE_SIZE + " bytes");
//        System.out.println("Physical File Size: " + physicalSize + " bytes (May be smaller due to sparsity)");
//
//        // Cleanup
//        Files.deleteIfExists(path);
    }

    private static FileChannel openRead(final Path sourceFile) throws IOException {
        return FileChannel.open(sourceFile, StandardOpenOption.READ);
    }

    private static FileChannel openReadWrite(final Path sparseFile) throws IOException {
        return FileChannel.open(
                sparseFile,
                StandardOpenOption.READ,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE);
    }

    private void writeSourceDate(final Path sourceFile) throws IOException {
        final Faker faker = new Faker(new Random(RANDOM_SEED));
        final List<String> data = new ArrayList<>(FRAME_COUNT);
        final List<byte[]> dataBytes = new ArrayList<>(FRAME_COUNT);

        for (int i = 0; i < FRAME_COUNT; i++) {
            generateTestData(faker, i, data, dataBytes);
        }

        final FileOutputStream fileOutputStream = new FileOutputStream(sourceFile.toFile());
        try (final SegmentOutputStream segmentOutputStream = new ZstdSegmentOutputStream(
                fileOutputStream,
                null,
                new HeapBufferPool(ByteBufferPoolConfig::new),
                COMPRESSION_LEVEL)) {

            writeDataToStream(dataBytes, segmentOutputStream);
        }

        LOGGER.info("sourceFile: {}, size: {}", sourceFile, Files.size(sourceFile));
    }

    static void writeDataToStream(final List<byte[]> dataBytes,
                                  final SegmentOutputStream segmentOutputStream)
            throws IOException {
        for (int i = 0; i < dataBytes.size(); i++) {
            final byte[] bytes = dataBytes.get(i);
            if (i != 0) {
                segmentOutputStream.addSegment();
            }
            segmentOutputStream.write(bytes);
        }
    }

    static void generateTestData(final Faker faker,
                                 final int iteration,
                                 final List<String> data,
                                 final List<byte[]> dataBytes) {

        final int remainder = iteration % 3;
        String str;
        if (remainder == 0) {
            str = faker.backToTheFuture().quote();
        } else if (remainder == 1) {
            str = faker.simpsons().quote();
        } else {
            str = faker.southPark().quotes();
        }
        str = "<quote idx=\"" + iteration + "\">" + str + "</quote>";
        // Dup[licate the str on each line so it can compress better with no dict
//        str = str + str + str + str;
        final byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
//            LOGGER.info("str: {}", str);
        data.add(str);
        dataBytes.add(bytes);
    }
}
