/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamstore.server.fs;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.streamstore.server.fs.serializable.NestedInputStream;
import stroom.streamstore.server.fs.serializable.RANestedInputStream;
import stroom.streamstore.server.fs.serializable.RANestedOutputStream;
import stroom.streamstore.server.fs.serializable.RASegmentInputStream;
import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestRASegmentStreamsWithBoundary extends StroomUnitTest {
    private Path datFile;
    private Path segFile;
    private Path bdyFile;

    @Before
    public void setup() {
        final Path dir = getCurrentTestDir();
        datFile = dir.resolve("test.bzg");
        segFile = dir.resolve("test.seg.dat");
        bdyFile = dir.resolve("test.bdy.dat");
    }

    @After
    public void clean() {
        FileUtil.deleteFile(datFile);
        FileUtil.deleteFile(segFile);
        FileUtil.deleteFile(bdyFile);
    }

    @Test
    public void testSimpleLowLevelAPI() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                new LockingFileOutputStream(segFile, true));

        final RASegmentOutputStream boundaryStream = new RASegmentOutputStream(segmentStream,
                new LockingFileOutputStream(bdyFile, true));

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

        Assert.assertTrue(Files.isRegularFile(Paths.get(datFile.toString() + ".lock")));
        Assert.assertTrue(Files.isRegularFile(Paths.get(segFile.toString() + ".lock")));
        Assert.assertTrue(Files.isRegularFile(Paths.get(bdyFile.toString() + ".lock")));

        boundaryStream.close();

        Assert.assertTrue(Files.isRegularFile(datFile));
        Assert.assertTrue(Files.isRegularFile(segFile));
        Assert.assertTrue(Files.isRegularFile(bdyFile));

        final RASegmentInputStream boundaryInputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        boundaryInputStream.include(1);

        Assert.assertEquals("2A\n2B\n2C\n2D\n", StreamUtil.streamToString(boundaryInputStream));
    }

    @Test
    public void testHighLevelAPI_Basic() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                new LockingFileOutputStream(segFile, true));

        final RANestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
                new LockingFileOutputStream(bdyFile, true));

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

        Assert.assertTrue(Files.isRegularFile(Paths.get(datFile.toString() + ".lock")));
        Assert.assertTrue(Files.isRegularFile(Paths.get(segFile.toString() + ".lock")));
        Assert.assertTrue(Files.isRegularFile(Paths.get(bdyFile.toString() + ".lock")));

        boundaryStream.close();

        Assert.assertTrue(Files.isRegularFile(datFile));
        Assert.assertTrue(Files.isRegularFile(segFile));
        Assert.assertTrue(Files.isRegularFile(bdyFile));

        final RANestedInputStream boundaryInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        Assert.assertEquals(2, boundaryInputStream.getEntryCount());

        Assert.assertTrue(boundaryInputStream.getNextEntry());
        Assert.assertEquals("1A\n1B\n1C\n1D\n", StreamUtil.streamToString(boundaryInputStream, false));
        Assert.assertEquals(0, boundaryInputStream.entryByteOffsetStart());
        boundaryInputStream.closeEntry();

        Assert.assertEquals(2, boundaryInputStream.getEntryCount());

        Assert.assertTrue(boundaryInputStream.getNextEntry());
        Assert.assertEquals("2A\n2B\n2C\n2D\n", StreamUtil.streamToString(boundaryInputStream, false));
        Assert.assertEquals(12, boundaryInputStream.entryByteOffsetStart());
        boundaryInputStream.closeEntry();
        boundaryInputStream.close();

        Assert.assertEquals(2, boundaryInputStream.getEntryCount());

        final RANestedInputStream boundaryInputStream2 = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        Assert.assertTrue(boundaryInputStream2.getNextEntry(1));
        Assert.assertEquals("2A\n2B\n2C\n2D\n", StreamUtil.streamToString(boundaryInputStream2, false));
        boundaryInputStream2.closeEntry();
        boundaryInputStream2.close();

        final RANestedInputStream boundaryInputStream3 = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        Assert.assertFalse(boundaryInputStream3.getNextEntry(2));
        boundaryInputStream3.close();

    }

    @Test
    public void testHighLevelAPI_RandomAccess() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                new LockingFileOutputStream(segFile, true));

        final RANestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
                new LockingFileOutputStream(bdyFile, true));

        boundaryStream.putNextEntry();
        boundaryStream.write("1A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1B\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        Assert.assertEquals(6, segmentStream.getPosition());
        segmentStream.addSegment();

        boundaryStream.write("1C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1D\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        Assert.assertEquals(12, segmentStream.getPosition());
        boundaryStream.closeEntry();

        boundaryStream.putNextEntry();
        boundaryStream.write("2A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("2B\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        Assert.assertEquals(18, segmentStream.getPosition());
        segmentStream.addSegment();

        boundaryStream.write("2C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("2D\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.closeEntry();

        boundaryStream.close();

        final NestedInputStream boundaryInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        // Read 2nd segment from 2nd boundary
        boundaryInputStream.getNextEntry(1);
        final long bdyStartByteOffsetStart = boundaryInputStream.entryByteOffsetStart();
        final long bdyStartByteOffsetEnd = boundaryInputStream.entryByteOffsetEnd();

        //
        final RASegmentInputStream segmentInputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(segFile, true), bdyStartByteOffsetStart, bdyStartByteOffsetEnd);

        segmentInputStream.include(1);

        Assert.assertEquals("2C\n2D\n", StreamUtil.streamToString(segmentInputStream, false));

        boundaryInputStream.close();
        segmentInputStream.close();

    }

    @Test
    public void testHighLevelAPI_SingleBoundaryNoBoundaryIndex() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                new LockingFileOutputStream(segFile, true));

        final RANestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
                new LockingFileOutputStream(bdyFile, true));

        boundaryStream.putNextEntry();
        boundaryStream.write("1A\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1B\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        segmentStream.addSegment();
        boundaryStream.write("1C\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        boundaryStream.write("1D\n".getBytes(StreamUtil.DEFAULT_CHARSET));

        boundaryStream.closeEntry();

        boundaryStream.close();

        Assert.assertTrue(Files.isRegularFile(datFile));
        Assert.assertTrue(Files.isRegularFile(segFile));
        Assert.assertFalse(Files.isRegularFile(bdyFile));

        final RANestedInputStream boundaryInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        Assert.assertTrue(boundaryInputStream.getNextEntry());
        Assert.assertEquals("1A\n1B\n1C\n1D\n", StreamUtil.streamToString(boundaryInputStream, false));
        boundaryInputStream.closeEntry();

        Assert.assertFalse(boundaryInputStream.getNextEntry());
        boundaryInputStream.close();

    }

    @Test
    public void testHighLevelAPI_MultipleBlankStreams() throws IOException {
        final RASegmentOutputStream segmentStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                new LockingFileOutputStream(segFile, true));

        final RANestedOutputStream boundaryStream = new RANestedOutputStream(segmentStream,
                new LockingFileOutputStream(bdyFile, true));

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

        Assert.assertTrue(Files.isRegularFile(datFile));
        Assert.assertTrue(Files.isRegularFile(segFile));
        Assert.assertTrue(Files.isRegularFile(bdyFile));

        final RANestedInputStream boundaryInputStream = new RANestedInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(bdyFile, true));

        // 1
        Assert.assertTrue(boundaryInputStream.getNextEntry());
        Assert.assertEquals("", StreamUtil.streamToString(boundaryInputStream, false));
        boundaryInputStream.closeEntry();
        // 2
        Assert.assertTrue(boundaryInputStream.getNextEntry());
        Assert.assertEquals("", StreamUtil.streamToString(boundaryInputStream, false));
        boundaryInputStream.closeEntry();
        // 3
        Assert.assertTrue(boundaryInputStream.getNextEntry());
        Assert.assertEquals("", StreamUtil.streamToString(boundaryInputStream, false));
        boundaryInputStream.closeEntry();
        // 4
        Assert.assertTrue(boundaryInputStream.getNextEntry());
        Assert.assertEquals("1A\n1B\n1C\n1D\n", StreamUtil.streamToString(boundaryInputStream, false));
        boundaryInputStream.closeEntry();

        Assert.assertFalse(boundaryInputStream.getNextEntry());
        boundaryInputStream.close();

    }

}
