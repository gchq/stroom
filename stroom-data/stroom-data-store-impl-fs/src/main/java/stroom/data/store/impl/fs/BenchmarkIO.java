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

import stroom.data.store.api.SegmentOutputStream;
import stroom.data.store.api.TargetUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

class BenchmarkIO {

    private static final int MB = 1000000;
    private static final Map<StreamType, Integer> writeSpeed = new HashMap<>();
    private static final Map<StreamType, Integer> readSpeed = new HashMap<>();

    static {
        for (final StreamType streamType : StreamType.values()) {
            writeSpeed.put(streamType, 0);
            readSpeed.put(streamType, 0);
        }
    }

    static void main(final String[] args) throws IOException {
        new BenchmarkIO().run(args);
    }

    private void run(final String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("You must specify the file path where the test should be performed");
            System.out.println("Usage = <PATH> <RECORDS> <RUNS>");
        } else {
            final String path = args[0];
            final Path dir = Paths.get(path);
            if (!Files.isDirectory(dir)) {
                System.out.println("Specified directory \"" + path + "\" does not exist.");
            } else {
                int recordCount = 2000000;
                if (args.length > 1) {
                    recordCount = Integer.parseInt(args[1]);
                }

                int runs = 1;
                if (args.length > 2) {
                    runs = Integer.parseInt(args[2]);
                }

                for (int i = 0; i < runs; i++) {
                    test(dir, recordCount);

                    final int run = i + 1;

                    for (final StreamType streamType : StreamType.values()) {
                        System.out.println("Average " + streamType + " write = " + (writeSpeed.get(streamType) / run)
                                           + "Mb/s, read = " + (readSpeed.get(streamType) / run) + "Mb/s");
                    }
                    System.out.println();
                }
            }
        }
    }

    private void test(final Path dir, final int recordCount) throws IOException {
        final byte[] data = createData(recordCount);

        final Path rawFile = dir.resolve("test.dat");
        final Path gzipFile = dir.resolve("test.gzip");
        final Path bgzipFile = dir.resolve("test.bgzip");
        final Path bgzipDatFile1a = dir.resolve("test1a.dat.bgzip");
        final Path bgzipIdxFile1a = dir.resolve("test1a.idx");
        final Path bgzipDatFile1b = dir.resolve("test1b.dat.bgzip");
        final Path bgzipIdxFile1b = dir.resolve("test1b.idx.bgzip");
        final Path bgzipDatFile2 = dir.resolve("test2.dat.bgzip");
        final Path bgzipIdxFile2 = dir.resolve("test2.idx");
        final Path bgzipDatFile3 = dir.resolve("test3.dat.bgzip");
        final Path bgzipIdxFile3 = dir.resolve("test4.idx");

        Files.createDirectories(dir);

        doTest(rawFile, null, data, StreamType.PLAIN);
        doTest(gzipFile, null, data, StreamType.GZIP);
        doTest(bgzipFile, null, data, StreamType.BGZIP);
        doTest(bgzipDatFile1a, bgzipIdxFile1a, data, StreamType.BGZIP_SEG);
        doTest(bgzipDatFile1b, bgzipIdxFile1b, data, StreamType.BGZIP_SEG_COMPRESS);
        doTest(bgzipDatFile2, bgzipIdxFile2, data, StreamType.RAW_SEG_TEXT);
        doTest(bgzipDatFile3, bgzipIdxFile3, data, StreamType.RAW_SEG_XML);

        Files.delete(rawFile);
        Files.delete(gzipFile);
        Files.delete(bgzipFile);
        Files.delete(bgzipDatFile1a);
        Files.delete(bgzipIdxFile1a);
        Files.delete(bgzipDatFile1b);
        Files.delete(bgzipIdxFile1b);
        Files.delete(bgzipDatFile2);
        Files.delete(bgzipIdxFile2);
        Files.delete(bgzipDatFile3);
        Files.delete(bgzipIdxFile3);
    }

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    private void doTest(final Path file1, final Path file2, final byte[] data, final StreamType streamType)
            throws IOException {
        // Write the data.
        OutputStream os;
        switch (streamType) {
            case PLAIN:
                os = new BufferedOutputStream(Files.newOutputStream(file1), FileSystemUtil.STREAM_BUFFER_SIZE);
                break;
            case GZIP:
                os = new GzipCompressorOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(file1),
                                FileSystemUtil.STREAM_BUFFER_SIZE));
                break;
            case BGZIP:
                os = new BlockGZIPOutputFile(file1);
                break;
            case BGZIP_SEG:
                os = new RASegmentOutputStream(new BlockGZIPOutputFile(file1),
                        () -> new LockingFileOutputStream(file2, false));
                break;
            case BGZIP_SEG_COMPRESS:
                os = new RASegmentOutputStream(new BlockGZIPOutputFile(file1), () ->
                        new BlockGZIPOutputFile(file2));
                break;
            default:
                throw new IllegalArgumentException("Unexpected stream type: " + streamType);
        }

        long startTime = System.currentTimeMillis();

        if (StreamType.RAW_SEG_TEXT.equals(streamType)) {
            os = new RASegmentOutputStream(new BlockGZIPOutputFile(file1), () ->
                    new LockingFileOutputStream(file2, false));
            TargetUtil.write(new ByteArrayInputStream(data), os, true);
        } else if (StreamType.RAW_SEG_XML.equals(streamType)) {
            os = new RASegmentOutputStream(new BlockGZIPOutputFile(file1), () ->
                    new LockingFileOutputStream(file2, false));
            TargetUtil.write(new ByteArrayInputStream(data), os, true);
        } else {
            int startPos = 0;
            int blockSize = 100;
            while (startPos < data.length) {
                if ((startPos + blockSize) > data.length) {
                    blockSize = data.length - startPos;
                }
                os.write(data, startPos, blockSize);
                if (os instanceof SegmentOutputStream) {
                    ((SegmentOutputStream) os).addSegment();
                }
                startPos += blockSize;
            }
        }

        os.flush();
        os.close();

        double elapsed = System.currentTimeMillis() - startTime;
        final double mb = ((double) data.length) / MB;
        double sec = elapsed / 1000;
        int mbps = (int) (mb / sec);
        final long fileLength = Files.size(file1);

        System.out.println("Writing " + streamType + " " + (int) mb + "Mb to \"" +
                           FileUtil.getCanonicalPath(file1) + "\" took " + (int) elapsed + "ms = " + mbps + "Mb/s");

        System.out.println("Output file is " + (int) (fileLength / MB) + "Mb, compression ratio = " +
                           (int) (100 - ((100D / data.length) * fileLength)) + "%");

        if (file2 != null) {
            final long fileLength2 = Files.size(file2);
            System.out.println("Output file 2 is " + (int) (fileLength2 / MB) + "Mb");

        }

        writeSpeed.put(streamType, writeSpeed.get(streamType) + mbps);

        // Read the data.
        InputStream is = null;
        switch (streamType) {
            case PLAIN:
                is = Files.newInputStream(file1);
                break;
            case GZIP:
                is = new GzipCompressorInputStream(
                        new BufferedInputStream(Files.newInputStream(file1), FileSystemUtil.STREAM_BUFFER_SIZE));
                break;
            case BGZIP:
                is = new BlockGZIPInputFile(file1);
                break;
            case BGZIP_SEG:
                is = new RASegmentInputStream(
                        new BlockGZIPInputFile(file1), new UncompressedInputStream(file2, false));
                break;
            case BGZIP_SEG_COMPRESS:
                is = new RASegmentInputStream(
                        new BlockGZIPInputFile(file1), new BlockGZIPInputFile(file2));
                break;
            case RAW_SEG_TEXT:
                is = new RASegmentInputStream(
                        new BlockGZIPInputFile(file1), new UncompressedInputStream(file2, false));
                break;
            case RAW_SEG_XML:
                is = new RASegmentInputStream(
                        new BlockGZIPInputFile(file1), new UncompressedInputStream(file2, false));
                break;
            default:
                throw new RuntimeException("Unknown type " + streamType);
        }
        is = new BufferedInputStream(is, FileSystemUtil.STREAM_BUFFER_SIZE);

        byte[] buffer = new byte[1000];
        startTime = System.currentTimeMillis();

        // Read in a long winded way...
        int len;
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((len = is.read(buffer)) != -1) {
            bos.write(buffer, 0, len);
        }
        bos.flush();
        bos.close();
        buffer = bos.toByteArray();

        is.close();
        elapsed = System.currentTimeMillis() - startTime;
        sec = elapsed / 1000;
        mbps = (int) (mb / sec);
        System.out.println("Reading " + streamType + " " + (int) mb + "Mb from \"" + FileUtil.getCanonicalPath(file1)
                           + "\" took " + (int) elapsed + "ms = " + mbps + "Mb/s");

        readSpeed.put(streamType, readSpeed.get(streamType) + mbps);

        if (streamType != StreamType.RAW_SEG_XML) {
            // Verify the data.
            if (data.length != buffer.length) {
                System.out.println("Streams are not the same size");
            }
            for (int i = 0; i < data.length; i++) {
                final byte a = data[i];
                final byte b = buffer[i];
                if (a != b) {
                    System.out.println("Bytes differ at position " + i + " of " + data.length + " (" + (char) a + " "
                                       + (char) b + ")");
                    System.exit(1);
                }
            }
        }

        System.out.println();
    }

    private byte[] createData(final int recordCount) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<records>\n");

        for (int i = 0; i < recordCount; i++) {
            sb.append("<record>01/01/2010,00:00:00,");
            sb.append(i);
            sb.append(",1,user1,Some message 1</record>\n");
        }
        sb.append("</records>");

        return sb.toString().getBytes(StreamUtil.DEFAULT_CHARSET);
    }

    private enum StreamType {
        PLAIN,
        GZIP,
        BGZIP,
        BGZIP_SEG,
        BGZIP_SEG_COMPRESS,
        RAW_SEG_TEXT,
        RAW_SEG_XML
    }
}
