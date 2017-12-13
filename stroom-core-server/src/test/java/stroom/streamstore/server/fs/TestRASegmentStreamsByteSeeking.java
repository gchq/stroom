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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.streamstore.server.fs.serializable.RASegmentInputStream;
import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.server.fs.serializable.SegmentOutputStream;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestRASegmentStreamsByteSeeking extends StroomUnitTest {
    @Test
    public void testByteSeeking() throws IOException {
        final Path dir = getCurrentTestDir();
        try (SegmentOutputStream os = new RASegmentOutputStream(new BlockGZIPOutputFile(dir.resolve("test.dat")),
                Files.newOutputStream(dir.resolve("test.idx")))) {
            os.write("LINE ONE\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.addSegment();
            os.write("LINE TWO\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.addSegment();
            os.write("LINE THREE\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.addSegment();
            os.write("LINE FOUR\n".getBytes(StreamUtil.DEFAULT_CHARSET));

            os.flush();
            os.close();
        }

        final UncompressedInputStream debug = new UncompressedInputStream(dir.resolve("test.idx"), true);

        RASegmentInputStream is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true));

        Assert.assertEquals(4, is.count());

        Assert.assertEquals(0, is.byteOffset(0));
        Assert.assertEquals(9, is.byteOffset(1));
        Assert.assertEquals(18, is.byteOffset(2));
        Assert.assertEquals(29, is.byteOffset(3));

        Assert.assertEquals(0, is.segmentAtByteOffset(0));
        Assert.assertEquals(0, is.segmentAtByteOffset(1));
        Assert.assertEquals(0, is.segmentAtByteOffset(8));
        Assert.assertEquals(1, is.segmentAtByteOffset(9));
        Assert.assertEquals(1, is.segmentAtByteOffset(17));
        Assert.assertEquals(2, is.segmentAtByteOffset(18));
        Assert.assertEquals(2, is.segmentAtByteOffset(28));
        Assert.assertEquals(3, is.segmentAtByteOffset(29));
        Assert.assertEquals(3, is.segmentAtByteOffset(38));
        Assert.assertEquals(3, is.segmentAtByteOffset(39));
        Assert.assertEquals(-1, is.segmentAtByteOffset(40));
        Assert.assertEquals(-1, is.segmentAtByteOffset(9999));

        debug.close();
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 5, 8);

        Assert.assertEquals("ONE", StreamUtil.streamToString(is));
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 5, 39);

        is.include(0);
        is.include(2);
        Assert.assertEquals("ONE\nLINE THREE\n", StreamUtil.streamToString(is));
        is.close();

        is = new RASegmentInputStream(new BlockGZIPInputFile(dir.resolve("test.dat")),
                new UncompressedInputStream(dir.resolve("test.idx"), true), 0, 13);

        is.include(0);
        is.include(1);
        Assert.assertEquals("LINE ONE\nLINE", StreamUtil.streamToString(is));
        is.close();
    }
}
