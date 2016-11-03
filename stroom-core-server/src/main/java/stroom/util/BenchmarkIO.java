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

package stroom.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import stroom.streamstore.server.fs.BlockGZIPInputFile;
import stroom.streamstore.server.fs.BlockGZIPOutputFile;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.streamstore.server.fs.LockingFileOutputStream;
import stroom.streamstore.server.fs.UncompressedInputStream;
import stroom.streamstore.server.fs.serializable.RASegmentInputStream;
import stroom.streamstore.server.fs.serializable.RASegmentOutputStream;
import stroom.streamstore.server.fs.serializable.RawInputSegmentWriter;
import stroom.streamstore.server.fs.serializable.SegmentOutputStream;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;

public class BenchmarkIO {
    private static final int MB = 1000000;

    private enum StreamType {
        PLAIN, GZIP, BGZIP, BGZIP_SEG, BGZIP_SEG_COMPRESS, RAW_SEG_TEXT, RAW_SEG_XML
    }

    private static HashMap<StreamType, Integer> writeSpeed = new HashMap<>();
    private static HashMap<StreamType, Integer> readSpeed = new HashMap<>();

    public static void main(final String[] args) throws Exception {
        new BenchmarkIO().run(args);
    }

    static {
        for (final StreamType streamType : StreamType.values()) {
            writeSpeed.put(streamType, 0);
            readSpeed.put(streamType, 0);
        }
    }

    private void run(final String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("You must specify the file path where the test should be performed");
            System.out.println("Usage = <PATH> <RECORDS> <RUNS>");
        } else {
            final String path = args[0];
            final File dir = new File(path);
            if (!dir.isDirectory()) {
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

    private void test(final File dir, final int recordCount) throws Exception {
        final byte[] data = createData(recordCount);

        final File rawFile = new File(dir, "test.dat");
        final File gzipFile = new File(dir, "test.gzip");
        final File bgzipFile = new File(dir, "test.bgzip");
        final File bgzipDatFile1a = new File(dir, "test1a.dat.bgzip");
        final File bgzipIdxFile1a = new File(dir, "test1a.idx");
        final File bgzipDatFile1b = new File(dir, "test1b.dat.bgzip");
        final File bgzipIdxFile1b = new File(dir, "test1b.idx.bgzip");
        final File bgzipDatFile2 = new File(dir, "test2.dat.bgzip");
        final File bgzipIdxFile2 = new File(dir, "test2.idx");
        final File bgzipDatFile3 = new File(dir, "test3.dat.bgzip");
        final File bgzipIdxFile3 = new File(dir, "test4.idx");

        FileUtil.mkdirs(rawFile.getParentFile());

        doTest(rawFile, null, data, StreamType.PLAIN);
        doTest(gzipFile, null, data, StreamType.GZIP);
        doTest(bgzipFile, null, data, StreamType.BGZIP);
        doTest(bgzipDatFile1a, bgzipIdxFile1a, data, StreamType.BGZIP_SEG);
        doTest(bgzipDatFile1b, bgzipIdxFile1b, data, StreamType.BGZIP_SEG_COMPRESS);
        doTest(bgzipDatFile2, bgzipIdxFile2, data, StreamType.RAW_SEG_TEXT);
        doTest(bgzipDatFile3, bgzipIdxFile3, data, StreamType.RAW_SEG_XML);

        FileUtil.deleteFile(rawFile);
        FileUtil.deleteFile(gzipFile);
        FileUtil.deleteFile(bgzipFile);
        FileUtil.deleteFile(bgzipDatFile1a);
        FileUtil.deleteFile(bgzipIdxFile1a);
        FileUtil.deleteFile(bgzipDatFile1b);
        FileUtil.deleteFile(bgzipIdxFile1b);
        FileUtil.deleteFile(bgzipDatFile2);
        FileUtil.deleteFile(bgzipIdxFile2);
        FileUtil.deleteFile(bgzipDatFile3);
        FileUtil.deleteFile(bgzipIdxFile3);
    }

    public void doTest(final File file1, final File file2, final byte[] data, final StreamType streamType)
            throws Exception {
        // Write the data.
        OutputStream os = null;
        switch (streamType) {
        case PLAIN:
            os = new BufferedOutputStream(new FileOutputStream(file1), FileSystemUtil.STREAM_BUFFER_SIZE);
            break;
        case GZIP:
            os = new BufferedOutputStream(
                    new GZIPOutputStream(
                            new BufferedOutputStream(new FileOutputStream(file1), FileSystemUtil.STREAM_BUFFER_SIZE)),
                    FileSystemUtil.STREAM_BUFFER_SIZE);
            break;
        case BGZIP:
            os = new BlockGZIPOutputFile(file1);
            break;
        case BGZIP_SEG:
            os = new RASegmentOutputStream(new BlockGZIPOutputFile(file1), new LockingFileOutputStream(file2, false));
            break;
        case BGZIP_SEG_COMPRESS:
            os = new RASegmentOutputStream(new BlockGZIPOutputFile(file1), new BlockGZIPOutputFile(file2));
            break;
        default:
            throw new IllegalArgumentException("Unexpected stream type: " + streamType);
        }

        long startTime = System.currentTimeMillis();

        if (streamType == StreamType.RAW_SEG_TEXT) {
            os = new RASegmentOutputStream(new BlockGZIPOutputFile(file1), new LockingFileOutputStream(file2, false));
            final RawInputSegmentWriter wtr = new RawInputSegmentWriter();
            wtr.write(new ByteArrayInputStream(data), (RASegmentOutputStream) os);
        } else if (streamType == StreamType.RAW_SEG_XML) {
            os = new RASegmentOutputStream(new BlockGZIPOutputFile(file1), new LockingFileOutputStream(file2, false));
            final RawInputSegmentWriter wtr = new RawInputSegmentWriter();
            wtr.write(new ByteArrayInputStream(data), (RASegmentOutputStream) os);

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
        final long fileLength = file1.length();

        System.out.println("Writing " + streamType + " " + (int) mb + "Mb to \"" + file1.getAbsolutePath() + "\" took "
                + (int) elapsed + "ms = " + mbps + "Mb/s");
        System.out.println("Output file is " + (int) (fileLength / MB) + "Mb, compression ratio = "
                + (int) (100 - ((100D / data.length) * fileLength)) + "%");

        if (file2 != null) {
            final long fileLength2 = file2.length();
            System.out.println("Output file 2 is " + (int) (fileLength2 / MB) + "Mb");

        }

        writeSpeed.put(streamType, writeSpeed.get(streamType) + mbps);

        // Read the data.
        InputStream is = null;
        switch (streamType) {
        case PLAIN:
            is = new FileInputStream(file1);
            break;
        case GZIP:
            is = new GZIPInputStream(
                    new BufferedInputStream(new FileInputStream(file1), FileSystemUtil.STREAM_BUFFER_SIZE));
            break;
        case BGZIP:
            is = new BlockGZIPInputFile(file1);
            break;
        case BGZIP_SEG:
            is = new RASegmentInputStream(new BlockGZIPInputFile(file1), new UncompressedInputStream(file2, false));
            break;
        case BGZIP_SEG_COMPRESS:
            is = new RASegmentInputStream(new BlockGZIPInputFile(file1), new BlockGZIPInputFile(file2));
            break;
        case RAW_SEG_TEXT:
            is = new RASegmentInputStream(new BlockGZIPInputFile(file1), new UncompressedInputStream(file2, false));
            break;
        case RAW_SEG_XML:
            is = new RASegmentInputStream(new BlockGZIPInputFile(file1), new UncompressedInputStream(file2, false));
            break;
        }
        is = new BufferedInputStream(is, FileSystemUtil.STREAM_BUFFER_SIZE);

        byte[] buffer = new byte[1000];
        startTime = System.currentTimeMillis();

        // Read in a long winded way...
        int len = 0;
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
        System.out.println("Reading " + streamType + " " + (int) mb + "Mb from \"" + file1.getAbsolutePath()
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
}
