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

package stroom.streamstore.store.impl.fs;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.streamstore.store.impl.fs.serializable.RASegmentInputStream;
import stroom.streamstore.store.impl.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.store.impl.fs.serializable.SegmentOutputStream;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestRASegmentStreamsWindow extends StroomUnitTest {
    @Test
    public void testFullWindowNoDataSegments() throws IOException {
        final Path dir = getCurrentTestDir();
        final SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")));

        os.addSegment();
        os.addSegment();
        os.addSegment();
        os.addSegment();
        os.addSegment();

        os.close();

        RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        Assert.assertEquals(6, is.count());

        Assert.assertEquals(0, is.segmentAtByteOffset(0, true));
        Assert.assertEquals(5, is.segmentAtByteOffset(0, false));

        is.include(3);

        Assert.assertEquals("", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        Assert.assertEquals(6, is.count());
        is.include(5);

        Assert.assertEquals("", StreamUtil.streamToString(is, true));
    }

    @Test
    public void testFullWindowNoDataSegments1() throws IOException {
        final Path dir = getCurrentTestDir();
        final SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")));

        os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));
        os.addSegment();
        os.addSegment();
        os.addSegment();
        os.addSegment();
        os.addSegment();
        os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));

        os.close();

        RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        Assert.assertEquals(6, is.count());
        // Start Segments
        Assert.assertEquals(0, is.segmentAtByteOffset(0, true));
        Assert.assertEquals(0, is.segmentAtByteOffset(1, true));
        Assert.assertEquals(0, is.segmentAtByteOffset(3, true));
        Assert.assertEquals(1, is.segmentAtByteOffset(4, true));
        Assert.assertEquals(5, is.segmentAtByteOffset(5, true));
        Assert.assertEquals(5, is.segmentAtByteOffset(8, true));

        // End Segments
        Assert.assertEquals(0, is.segmentAtByteOffset(0, false));
        Assert.assertEquals(0, is.segmentAtByteOffset(1, false));
        Assert.assertEquals(0, is.segmentAtByteOffset(3, false));
        Assert.assertEquals(5, is.segmentAtByteOffset(4, false));
        Assert.assertEquals(5, is.segmentAtByteOffset(5, false));
        Assert.assertEquals(5, is.segmentAtByteOffset(8, false));

        is.include(3);

        Assert.assertEquals("", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        Assert.assertEquals(6, is.count());
        is.include(5);

        Assert.assertEquals("TEST", StreamUtil.streamToString(is, true));
    }

    @Test
    public void testPartailWindowSegments1() throws IOException {
        final Path dir = getCurrentTestDir();
        final SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")));

        // 0
        os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));
        os.addSegment();
        // 1
        os.addSegment();
        // 2
        os.addSegment();
        // 3
        os.addSegment();
        // 4
        os.addSegment();
        // 5
        os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));

        os.close();

        RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 3);

        Assert.assertEquals(1, is.count());
        is.include(0);

        Assert.assertEquals("TES", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 4);

        Assert.assertEquals(6, is.count());
        is.include(4);
        Assert.assertEquals("", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 4);

        Assert.assertEquals(6, is.count());
        is.include(5);
        Assert.assertEquals("", StreamUtil.streamToString(is, true));

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 5);

        Assert.assertEquals(6, is.count());
        is.include(5);

        Assert.assertEquals("T", StreamUtil.streamToString(is, true));
    }

    @Test
    public void testPartailWindowSegments2() throws IOException {
        final Path dir = getCurrentTestDir();
        try (SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")))) {
            os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.addSegment();
            os.addSegment();
            os.addSegment();
            os.addSegment();
            os.addSegment();
            os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));

            os.close();

            RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 2, 6);

            Assert.assertEquals(6, is.count());
            is.include(3);

            Assert.assertEquals("", StreamUtil.streamToString(is, true));

            is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 2, 6);
            Assert.assertEquals(6, is.count());
            is.include(0);
            Assert.assertEquals("ST", StreamUtil.streamToString(is, true));

            is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 2, 6);
            Assert.assertEquals(6, is.count());
            is.include(5);
            Assert.assertEquals("TE", StreamUtil.streamToString(is, true));
        }
    }

    @Test
    public void testFullWindowNoSegments() throws IOException {
        final Path dir = getCurrentTestDir();
        try (SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")))) {
            // 0
            os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));

            os.close();

            RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 4);

            Assert.assertEquals(1, is.count());
            is.include(0);

            Assert.assertEquals("TEST", StreamUtil.streamToString(is, true));

            is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true));

            Assert.assertEquals(1, is.count());
            is.include(0);

            Assert.assertEquals("TEST", StreamUtil.streamToString(is, true));
        }
    }

    @Test
    public void testPartailWindowNoSegments() throws IOException {
        final Path dir = getCurrentTestDir();
        try (SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")))) {
            // 0
            os.write("TEST".getBytes(StreamUtil.DEFAULT_CHARSET));

            os.close();

            RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 2);
            Assert.assertEquals(1, is.count());
            is.include(0);
            Assert.assertEquals("TE", StreamUtil.streamToString(is, true));

            is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 1, 3);
            Assert.assertEquals(1, is.count());
            is.include(0);
            Assert.assertEquals("ES", StreamUtil.streamToString(is, true));

            is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                    new UncompressedInputStream(dir.resolve("test.idx"), true), 2, 4);
            Assert.assertEquals(1, is.count());
            is.include(0);
            Assert.assertEquals("ST", StreamUtil.streamToString(is, true));
        }
    }
}
