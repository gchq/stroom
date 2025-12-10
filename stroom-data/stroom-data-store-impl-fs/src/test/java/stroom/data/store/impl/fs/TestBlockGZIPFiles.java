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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TestBlockGZIPFiles {

    @TempDir
    Path tempDir;

    @Test
    void testSimpleSmallDataInBigBlock() throws IOException {
        testWriteAndRead(10000, 99);
    }

    @Test
    void testSimpleDataInLotsOfSmallBlocks() throws IOException {
        testWriteAndRead(100, 999);
    }

    @Test
    void testSimpleBounds() throws IOException {
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
    void testBroken() throws IOException {
        for (int inBuf = 2; inBuf < 5; inBuf++) {
            for (int outBuf = 2; outBuf < 5; outBuf++) {
                testWriteAndReadBuffered(9, 100, inBuf, outBuf);
                testWriteAndReadBuffered(10, 100, inBuf, outBuf);
                testWriteAndReadBuffered(11, 100, inBuf, outBuf);

            }
        }
    }

    @Test
    void testBufferedSmall() throws IOException {
        testWriteAndReadBuffered(10, 30, 3, 3);
        testWriteAndReadBuffered(10, 29, 3, 3);
        testWriteAndReadBuffered(10, 31, 3, 3);
    }

    @Test
    void testBufferedBig() throws IOException {
        testWriteAndReadBuffered(1000, 1000000, 100, 100);
    }

    @Test
    void testBig() throws IOException {
        final Path testFile = tempDir.resolve("testBig.bgz");
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
        while ((is.read(testBuf)) != -1) {
            // Ignore
        }

        is.close();

        assertThat(FileUtil.delete(testFile))
                .withFailMessage("Should not have any locks on file")
                .isTrue();
        assertThat(Files.isRegularFile(testFile))
                .withFailMessage("file deleted")
                .isFalse();

    }

    private void testWriteAndRead(final int blockSize, final int fileSize) throws IOException {
        final Path file = Files.createTempFile(tempDir, "test", ".bgz");
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
            assertThat((byte) actual).isEqualTo(expected);
            expected++;
        }

        inStream.close();

        assertThat((byte) fileSize).withFailMessage("Expected to load records").isEqualTo(expected);
    }

    private void testWriteAndReadBuffered(final int blockSize, final int fileSize, final int inBuff, final int outBuf)
            throws IOException {
        final Path file = Files.createTempFile(tempDir, "test", ".bgz");
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
            assertThat((byte) actual).isEqualTo(expected);
            expected++;
        }
        inStream.close();

        assertThat((byte) fileSize).withFailMessage("Expected to load records").isEqualTo(expected);
    }

    @Test
    void testSeeking() throws IOException {
        final Path file = tempDir.resolve("test.bgz");
        FileUtil.deleteFile(file);

        // Stupid Block Size For Testing
        final BlockGZIPOutputFile outStream = new BlockGZIPOutputFile(file, 10);

        for (byte i = 0; i < 105; i++) {
            outStream.write(i);
        }

        outStream.close();

        BlockGZIPInputFile inStream = new BlockGZIPInputFile(file, 10);

        inStream.mark(0);

        assertThat(inStream.skip(9)).isEqualTo(9);
        assertThat(inStream.read()).isEqualTo(9);
        inStream.reset();

        // inStream.reset();
        assertThat(inStream.skip(50)).isEqualTo(50);
        assertThat(inStream.read()).isEqualTo(50);

        inStream.reset();
        assertThat(inStream.skip(100)).isEqualTo(100);
        assertThat(inStream.read()).isEqualTo(100);

        inStream.reset();
        assertThat(inStream.skip(104)).isEqualTo(104);
        assertThat(inStream.read()).isEqualTo(104);

        inStream.reset();
        assertThat(inStream.skip(50)).isEqualTo(50);
        inStream.mark(-1);
        assertThat(inStream.read()).isEqualTo(50);
        inStream.reset();
        assertThat(inStream.read()).isEqualTo(50);

        inStream.close();

        inStream = new BlockGZIPInputFile(file, 10);

        final byte[] testRead = new byte[50];
        assertThat(inStream.getPosition()).isEqualTo(0);
        StreamUtil.fillBuffer(inStream, testRead);
        assertThat(inStream.getPosition()).isEqualTo(50);
        // Seek back and re-read
        inStream.seek(0);
        StreamUtil.fillBuffer(inStream, testRead);
        assertThat(inStream.getPosition()).isEqualTo(50);

        // Go back
        for (byte i = 94; i >= 0; i--) {
            inStream.seek(i);
            assertThat(inStream.read()).isEqualTo(i);
            inStream.skip(9);
            assertThat(inStream.read()).isEqualTo(i + 10);
        }

        // Go forward
        for (byte i = 3; i < 100; i += 8) {
            inStream.seek(i);
            assertThat(inStream.read()).isEqualTo(i);
        }

        inStream.close();

    }

}
