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

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.streamstore.server.fs.serializable.RASegmentInputStream;
import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.server.fs.serializable.SegmentOutputStream;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomUnitTest;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestRASegmentStreamsNulls extends StroomUnitTest {
    @Test
    public void testNoSegments() throws IOException {
        final File dir = getCurrentTestDir();
        final File datFile = new File(dir, "test.bzg");
        final File segFile = new File(dir, "test.seg.dat");

        final SegmentOutputStream segStream = new RASegmentOutputStream(new BlockGZIPOutputFile(datFile),
                new LockingFileOutputStream(segFile, true));

        segStream.write("tom1".getBytes(StreamUtil.DEFAULT_CHARSET));

        segStream.close();

        Assert.assertTrue(datFile.isFile());
        Assert.assertFalse(segFile.isFile());

        RASegmentInputStream inputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(segFile, true));

        Assert.assertEquals("tom1", StreamUtil.streamToString(inputStream));

        inputStream = new RASegmentInputStream(new BlockGZIPInputFile(datFile),
                new UncompressedInputStream(segFile, true));

        inputStream.include(0);

        for (int i = 0; i < inputStream.count(); i++) {
            inputStream.include(i);
        }

        Assert.assertEquals("tom1", StreamUtil.streamToString(inputStream));

        datFile.delete();
        segFile.delete();
    }
}
