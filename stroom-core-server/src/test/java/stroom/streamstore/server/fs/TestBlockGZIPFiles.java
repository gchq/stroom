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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestBlockGZIPFiles extends StroomUnitTest {
    @Test
    public void testSimpleSmallDataInBigBlock() throws Exception {
        testWriteAndRead(10000, 99);
    }

    @Test
    public void testSimpleDataInLotsOfSmallBlocks() throws Exception {
        testWriteAndRead(100, 999);
    }

    @Test
    public void testSimpleBounds() throws Exception {
        testWriteAndRead(10, 0);
        testWriteAndRead(10, 1);
        testWriteAndRead(10, 9);
        testWriteAndRead(10, 10);
        testWriteAndRead(10, 11);
        testWriteAndRead(10, 19);
        testWriteAndRead(10, 20);
        testWriteAndRead(10, 21);
    }

    @Test
    public void testBroken() throws Exception {
        for (int inBuf = 2; inBuf < 5; inBuf++) {
            for (int outBuf = 2; outBuf < 5; outBuf++) {
                testWriteAndReadBuffered(9, 100, inBuf, outBuf);
                testWriteAndReadBuffered(10, 100, inBuf, outBuf);
                testWriteAndReadBuffered(11, 100, inBuf, outBuf);

            }
        }
    }

    @Test
    public void testBufferedSmall() throws Exception {
        testWriteAndReadBuffered(10, 30, 3, 3);
        testWriteAndReadBuffered(10, 29, 3, 3);
        testWriteAndReadBuffered(10, 31, 3, 3);
    }

    @Test
    public void testBufferedBig() throws Exception {
        testWriteAndReadBuffered(1000, 1000000, 100, 100);
    }

    @Test
    public void testBig() throws IOException {
        final File testFile = new File(getCurrentTestDir(), "testBig.bgz");
        FileUtil.deleteFile(testFile);
        final OutputStream os = new BufferedOutputStream(new BlockGZIPOutputFile(testFile, 1000000));
        for (int i = 0; i < 10000; i++) {
            os.write("some data that may compress quite well TEST\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write(("some other information TEST\n" + i).getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write("concurrent testing TEST\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write("TEST TEST TEST\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write("JAMES BETTY TEST\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write("FRED TEST\n".getBytes(StreamUtil.DEFAULT_CHARSET));
            os.write("<XML> TEST\n".getBytes(StreamUtil.DEFAULT_CHARSET));
        }

        os.close();

        final InputStream is = new BlockGZIPInputFile(testFile);

        is.mark(-1);
        is.skip(1);
        is.read();
        is.reset();

        final byte[] testBuf = new byte[10000];
        while ((is.read(testBuf)) != -1)
            ;

        is.close();

        Assert.assertTrue("Should not have any locks on file", testFile.delete());
        Assert.assertFalse("file deleted", testFile.isFile());

    }

    private void testWriteAndRead(final int blockSize, final int fileSize) throws IOException {
        final File file = File.createTempFile("test", ".bgz", getCurrentTestDir());
        FileUtil.deleteFile(file);

        // Stupid Block Size For Testing
        final BlockGZIPOutputFile outStream = new BlockGZIPOutputFile(file, blockSize);

        for (int i = 0; i < fileSize; i++) {
            outStream.write((byte) i);
        }

        outStream.close();

        final BlockGZIPInputFile inStream = new BlockGZIPInputFile(file);

        byte expected = 0;
        int actual;

        while ((actual = inStream.read()) != -1) {
            Assert.assertEquals(expected, (byte) actual);
            expected++;
        }

        inStream.close();

        Assert.assertEquals("Expected to load records", (byte) fileSize, expected);
    }

    private void testWriteAndReadBuffered(final int blockSize, final int fileSize, final int inBuff, final int outBuf)
            throws IOException {
        final File file = File.createTempFile("test", ".bgz", getCurrentTestDir());
        FileUtil.deleteFile(file);

        // Stupid Block Size For Testing
        final OutputStream outStream = new BufferedOutputStream(new BlockGZIPOutputFile(file, blockSize), outBuf);

        for (int i = 0; i < fileSize; i++) {
            outStream.write((byte) i);
        }

        outStream.close();

        final InputStream inStream = new BufferedInputStream(new BlockGZIPInputFile(file, inBuff), inBuff);

        byte expected = 0;
        int actual;

        while ((actual = inStream.read()) != -1) {
            Assert.assertEquals(expected, (byte) actual);
            expected++;
        }
        inStream.close();

        Assert.assertEquals("Expected to load records", (byte) fileSize, expected);
    }

    @Test
    public void testSeeking() throws Exception {
        final File file = new File(getCurrentTestDir(), "tom.bgz");
        FileUtil.deleteFile(file);

        // Stupid Block Size For Testing
        final BlockGZIPOutputFile outStream = new BlockGZIPOutputFile(file, 10);

        for (byte i = 0; i < 105; i++) {
            outStream.write(i);
        }

        outStream.close();

        BlockGZIPInputFile inStream = new BlockGZIPInputFile(file, 10);

        inStream.mark(0);

        Assert.assertEquals(9, inStream.skip(9));
        Assert.assertEquals(9, inStream.read());
        inStream.reset();

        // inStream.reset();
        Assert.assertEquals(50, inStream.skip(50));
        Assert.assertEquals(50, inStream.read());

        inStream.reset();
        Assert.assertEquals(100, inStream.skip(100));
        Assert.assertEquals(100, inStream.read());

        inStream.reset();
        Assert.assertEquals(104, inStream.skip(104));
        Assert.assertEquals(104, inStream.read());

        inStream.reset();
        Assert.assertEquals(50, inStream.skip(50));
        inStream.mark(-1);
        Assert.assertEquals(50, inStream.read());
        inStream.reset();
        Assert.assertEquals(50, inStream.read());

        inStream.close();

        inStream = new BlockGZIPInputFile(file, 10);

        final byte[] testRead = new byte[50];
        Assert.assertEquals(0, inStream.getPosition());
        StreamUtil.fillBuffer(inStream, testRead);
        Assert.assertEquals(50, inStream.getPosition());
        // Seek back and re-read
        inStream.seek(0);
        StreamUtil.fillBuffer(inStream, testRead);
        Assert.assertEquals(50, inStream.getPosition());

        // Go back
        for (byte i = 94; i >= 0; i--) {
            inStream.seek(i);
            Assert.assertEquals(i, inStream.read());
            inStream.skip(9);
            Assert.assertEquals(i + 10, inStream.read());
        }

        // Go forward
        for (byte i = 3; i < 100; i += 8) {
            inStream.seek(i);
            Assert.assertEquals(i, inStream.read());
        }

        inStream.close();

    }

}
