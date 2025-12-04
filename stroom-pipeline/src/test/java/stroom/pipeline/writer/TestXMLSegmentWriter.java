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

package stroom.pipeline.writer;

import stroom.test.common.util.test.StroomUnitTest;

// TODO : Add test data

class TestXMLSegmentWriter extends StroomUnitTest {
//    private static final int N20 = 20;
//    private static final int N100 = 100;
//
//    private static final String RESOURCE = "TestXMLSegmentWriter/TestXMLSegmentWriter.out";
//
//    @Test
//    public void testXMLSegmentWriter() throws IOException {
//        createOutput();
//
//        // Test includes and excludes 100 times.
//        for (int i = 0; i < N100; i++) {
//            final int bufferLength = (int) (Math.random() * N100) + 1;
//
//            test(true, bufferLength);
//            test(false, bufferLength);
//        }
//    }
//
//    private void createOutput() throws IOException {
//        final Path dir = getCurrentTestDir();
//        final Path dataFile = dir.resolve("test.dat");
//        final Path indexFile = dir.resolve("test.idx");
//
//        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());
//
//        final OutputStream outputStream = new RASegmentOutputStream(new BlockGZIPOutputFile(dataFile),
//                Files.newOutputStream(indexFile));
//        final OutputStreamAppender appender = new OutputStreamAppender(errorReceiverProxy, outputStream);
//        final XMLWriter segmentWriter = new XMLWriter(errorReceiverProxy, null);
//        segmentWriter.setTarget(appender);
//
//        final InputStream inputStream = StroomPipelineTestFileUtil.getInputStream(RESOURCE);
//
//        ProcessorUtil.processXml(inputStream, errorReceiverProxy, segmentWriter, new LocationFactoryProxy());
//    }
//
//    private void test(final boolean testInclude, final int bufferLength) throws IOException {
//        final Path dir = getCurrentTestDir();
//        final Path dataFile = dir.resolve("test.dat");
//        final Path indexFile = dir.resolve("test.idx");
//        try (BlockGZIPInputFile data = new BlockGZIPInputFile(dataFile);
//             final UncompressedInputStream index = new UncompressedInputStream(indexFile, true)) {
//            try (SegmentInputStream inputStream = new RASegmentInputStream(data, index)) {
//                final Set<Long> seenBefore = new HashSet<>();
//                long expected = 0;
//
//                if (testInclude) {
//                    inputStream.include(0);
//                    inputStream.include(inputStream.count() - 1);
//
//                    // Test between 0 and 20 includes.
//                    final int number = (int) (Math.random() * N20);
//
//                    for (int i = 0; i < number; i++) {
//                        final long segmentNo = (long) (Math.random() * inputStream.count() - 1);
//                        if (segmentNo > 0 && segmentNo < inputStream.count() - 2 && !seenBefore.contains(segmentNo)) {
//                            seenBefore.add(segmentNo);
//                            expected++;
//                            inputStream.include(segmentNo);
//                        }
//                    }
//                } else {
//                    // Without any excludes we should expect to get (records =
//                    // segmentCount - 2)
//                    // this is because the segment count includes the start and
//                    // end of the root elements.
//                    expected = inputStream.count() - 2;
//
//                    // Test between 0 and 20 excludes.
//                    final int number = (int) (Math.random() * 20);
//
//                    for (int i = 0; i < number; i++) {
//                        final long segmentNo = (long) (Math.random() * inputStream.count() - 1);
//                        if (segmentNo > 0 && segmentNo < inputStream.count() - 2 && !seenBefore.contains(segmentNo)) {
//                            seenBefore.add(segmentNo);
//                            expected--;
//                            inputStream.exclude(segmentNo);
//                        }
//                    }
//                }
//
//                // Read the input stream into a byte array output stream using
//                // the byte buffer.
//                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
//                int len = 0;
//                final byte[] buffer = new byte[bufferLength];
//                while ((len = inputStream.read(buffer)) != -1) {
//                    baos.write(buffer, 0, len);
//                }
//                baos.close();
//                inputStream.close();
//
//                // Now parse the contents of the byte buffer to ensure the XML
//                // is valid and that the number of records matches what we
//                // expect.
//                final RecordCount recordCount = new RecordCount();
//                final RecordCountFilter recordCountFilter = new RecordCountFilter(null, recordCount);
//
//                ProcessorUtil.processXml(new ByteArrayInputStream(baos.toByteArray()),
//                        new ErrorReceiverProxy(new FatalErrorReceiver()), recordCountFilter,
//                        new LocationFactoryProxy());
//
//                assertThat(recordCount.getRead()).as(recordCount.toString()).isEqualTo(expected);
//            }
//        }
//    }
}
