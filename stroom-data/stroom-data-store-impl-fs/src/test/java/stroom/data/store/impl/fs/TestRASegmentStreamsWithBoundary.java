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

package stroom.data.store.impl.fs;

import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class TestRASegmentStreamsWithBoundary {

    @SuppressWarnings("unused")
    @TempDir
    Path tempDir;

    private Path datFile;
    private Path segFile;
    private Path bdyFile;

    @BeforeEach
    void setup() {
        datFile = tempDir.resolve("test.bzg");
        segFile = tempDir.resolve("test.seg.dat");
        bdyFile = tempDir.resolve("test.bdy.dat");
    }

    @AfterEach
    void clean() {
        FileUtil.deleteFile(datFile);
        FileUtil.deleteFile(segFile);
        FileUtil.deleteFile(bdyFile);
    }

    @Test
    void testSimpleLowLevelAPI() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                () -> new LockingFileOutputStream(segFile, true));

        final RASegmentOutputStream boundaryStream = new RASegmentOutputStream(segmentStream,
                () -> new LockingFileOutputStream(bdyFile, true));

        boundaryStream.write("1A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1B\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        segmentStream.addSegment();
        boundaryStream.write("1C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1D\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        boundaryStream.addSegment();

        boundaryStream.write("2A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("2B\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        segmentStream.addSegment();
        boundaryStream.write("2C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("2D\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        // This will flush all the index files (to create them)
        boundaryStream.flush();

        assertThat(Files.isRegularFile(Paths.get(datFile.toString() + ".lock"))).isTrue();
        assertThat(Files.isRegularFile(Paths.get(segFile.toString() + ".lock"))).isTrue();
        assertThat(Files.isRegularFile(Paths.get(bdyFile.toString() + ".lock"))).isTrue();

        boundaryStream.close();

        assertThat(Files.isRegularFile(datFile)).isTrue();
        assertThat(Files.isRegularFile(segFile)).isTrue();
        assertThat(Files.isRegularFile(bdyFile)).isTrue();

        final RASegmentInputStream boundaryInputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        boundaryInputStream.include(1);

        assertThat(StreamUtil.streamToString(boundaryInputStream)).isEqualTo("2A\n2B\n2C\n2D\n");
    }

    @Test
    void testHighLevelAPI_Basic() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                () -> new LockingFileOutputStream(segFile, true));

        final RANestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
                () -> new LockingFileOutputStream(bdyFile, true));

        boundaryStream.putNextEntry();
        boundaryStream.write("1A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1B\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        segmentStream.addSegment();
        boundaryStream.write("1C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1D\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        boundaryStream.closeEntry();
        boundaryStream.putNextEntry();
        boundaryStream.write("2A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("2B\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        segmentStream.addSegment();
        boundaryStream.write("2C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("2D\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.closeEntry();

        // This will flush all the index files (to create them)
        boundaryStream.flush();

        assertThat(Files.isRegularFile(Paths.get(datFile.toString() + ".lock"))).isTrue();
        assertThat(Files.isRegularFile(Paths.get(segFile.toString() + ".lock"))).isTrue();
        assertThat(Files.isRegularFile(Paths.get(bdyFile.toString() + ".lock"))).isTrue();

        boundaryStream.close();

        assertThat(Files.isRegularFile(datFile)).isTrue();
        assertThat(Files.isRegularFile(segFile)).isTrue();
        assertThat(Files.isRegularFile(bdyFile)).isTrue();

        final RANestedInputStream boundaryInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        assertThat(boundaryInputStream.getEntryCount()).isEqualTo(2);

        assertThat(boundaryInputStream.getNextEntry()).isTrue();
        assertThat(StreamUtil.streamToString(boundaryInputStream, false)).isEqualTo("1A\n1B\n1C\n1D\n");
        assertThat(boundaryInputStream.entryByteOffsetStart()).isEqualTo(0);
        boundaryInputStream.closeEntry();

        assertThat(boundaryInputStream.getEntryCount()).isEqualTo(2);

        assertThat(boundaryInputStream.getNextEntry()).isTrue();
        assertThat(StreamUtil.streamToString(boundaryInputStream, false)).isEqualTo("2A\n2B\n2C\n2D\n");
        assertThat(boundaryInputStream.entryByteOffsetStart()).isEqualTo(12);
        boundaryInputStream.closeEntry();
        boundaryInputStream.close();

        assertThat(boundaryInputStream.getEntryCount()).isEqualTo(2);

        final RANestedInputStream boundaryInputStream2 = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        assertThat(boundaryInputStream2.getNextEntry(1)).isTrue();
        assertThat(StreamUtil.streamToString(boundaryInputStream2, false)).isEqualTo("2A\n2B\n2C\n2D\n");
        boundaryInputStream2.closeEntry();
        boundaryInputStream2.close();

        final RANestedInputStream boundaryInputStream3 = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        assertThat(boundaryInputStream3.getNextEntry(2)).isFalse();
        boundaryInputStream3.close();

    }

    @Test
    void testHighLevelAPI_RandomAccess() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                () -> new LockingFileOutputStream(segFile, true));

        final RANestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
                () -> new LockingFileOutputStream(bdyFile, true));

        boundaryStream.putNextEntry();
        boundaryStream.write("1A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1B\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        assertThat(segmentStream.getPosition()).isEqualTo(6);
        segmentStream.addSegment();

        boundaryStream.write("1C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1D\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        assertThat(segmentStream.getPosition()).isEqualTo(12);
        boundaryStream.closeEntry();

        boundaryStream.putNextEntry();
        boundaryStream.write("2A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("2B\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        assertThat(segmentStream.getPosition()).isEqualTo(18);
        segmentStream.addSegment();

        boundaryStream.write("2C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("2D\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.closeEntry();

        boundaryStream.close();

        final RANestedInputStream boundaryInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        // Read 2nd segment from 2nd boundary
        boundaryInputStream.getNextEntry(1);
        final long bdyStartByteOffsetStart = boundaryInputStream.entryByteOffsetStart();
        final long bdyStartByteOffsetEnd = boundaryInputStream.entryByteOffsetEnd();

        //
        final RASegmentInputStream segmentInputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(segFile, true), bdyStartByteOffsetStart, bdyStartByteOffsetEnd);

        segmentInputStream.include(1);

        assertThat(StreamUtil.streamToString(segmentInputStream, false)).isEqualTo("2C\n2D\n");

        boundaryInputStream.close();
        segmentInputStream.close();

    }

    @Test
    void testHighLevelAPI_SingleBoundaryNoBoundaryIndex() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                () -> new LockingFileOutputStream(segFile, true));

        final RANestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
                () -> new LockingFileOutputStream(bdyFile, true));

        boundaryStream.putNextEntry();
        boundaryStream.write("1A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1B\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        segmentStream.addSegment();
        boundaryStream.write("1C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1D\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        boundaryStream.closeEntry();

        boundaryStream.close();

        assertThat(Files.isRegularFile(datFile)).isTrue();
        assertThat(Files.isRegularFile(segFile)).isTrue();
        assertThat(Files.isRegularFile(bdyFile)).isFalse();

        final RANestedInputStream boundaryInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        assertThat(boundaryInputStream.getNextEntry()).isTrue();
        assertThat(StreamUtil.streamToString(boundaryInputStream, false)).isEqualTo("1A\n1B\n1C\n1D\n");
        boundaryInputStream.closeEntry();

        assertThat(boundaryInputStream.getNextEntry()).isFalse();
        boundaryInputStream.close();

    }

    @Test
    void testHighLevelAPI_MultipleBlankStreams() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                () -> new LockingFileOutputStream(segFile, true));

        final RANestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
                () -> new LockingFileOutputStream(bdyFile, true));

        // 1
        boundaryStream.putNextEntry();
        boundaryStream.closeEntry();
        // 2
        boundaryStream.putNextEntry();
        boundaryStream.closeEntry();
        // 3
        boundaryStream.putNextEntry();
        boundaryStream.closeEntry();
        // 4
        boundaryStream.putNextEntry();
        boundaryStream.write("1A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1B\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        segmentStream.addSegment();
        boundaryStream.write("1C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1D\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.closeEntry();

        boundaryStream.close();

        assertThat(Files.isRegularFile(datFile)).isTrue();
        assertThat(Files.isRegularFile(segFile)).isTrue();
        assertThat(Files.isRegularFile(bdyFile)).isTrue();

        final RANestedInputStream boundaryInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        // 1
        assertThat(boundaryInputStream.getNextEntry()).isTrue();
        assertThat(StreamUtil.streamToString(boundaryInputStream, false)).isEqualTo("");
        boundaryInputStream.closeEntry();
        // 2
        assertThat(boundaryInputStream.getNextEntry()).isTrue();
        assertThat(StreamUtil.streamToString(boundaryInputStream, false)).isEqualTo("");
        boundaryInputStream.closeEntry();
        // 3
        assertThat(boundaryInputStream.getNextEntry()).isTrue();
        assertThat(StreamUtil.streamToString(boundaryInputStream, false)).isEqualTo("");
        boundaryInputStream.closeEntry();
        // 4
        assertThat(boundaryInputStream.getNextEntry()).isTrue();
        assertThat(StreamUtil.streamToString(boundaryInputStream, false)).isEqualTo("1A\n1B\n1C\n1D\n");
        boundaryInputStream.closeEntry();

        assertThat(boundaryInputStream.getNextEntry()).isFalse();
        boundaryInputStream.close();
    }
}
