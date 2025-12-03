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

package stroom.pipeline.reader;

import stroom.pipeline.stepping.SteppingController;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicLong;

@ExtendWith(MockitoExtension.class)
class TestReaderRecordDetector {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestReaderRecordDetector.class);

    @Mock
    private SteppingController mockSteppingController;

    @Test
    void read_recPerLine() throws IOException {

        final AtomicLong stepIdx = new AtomicLong();
        Mockito.doAnswer(
                        invocation -> {
                            final Long idx = invocation.getArgument(0, Long.class);
                            LOGGER.debug("endRecord: {}", idx);
                            stepIdx.set(idx);
                            return invocation.getArgument(0, Long.class) == 3;
                        })
                .when(mockSteppingController).endRecord(Mockito.anyLong());

        final String jsonLines = """
                { "firstName": "John", "lastName": "Doe" }
                { "firstName": "Anna", "lastName": "Smith" }
                { "firstName": "Peter", "lastName": "Jones" }
                """;

        final StringReader stringReader = new StringReader(jsonLines);
        final StringWriter stringWriter = new StringWriter();

        final ReaderRecordDetector readerRecordDetector = new ReaderRecordDetector(
                stringReader, mockSteppingController);

        final char[] outputBuf = new char[10];

        int readCnt;
        int iter = 0;
        while (true) {
            readCnt = readerRecordDetector.read(outputBuf);
            if (readCnt == -1) {
                break;
            }
            if (readCnt > 0) {
                final String str = new String(outputBuf, 0, readCnt);
                LOGGER.debug("readCnt: {}, str: '{}'", readCnt, str);
                stringWriter.write(outputBuf, 0, readCnt);
            }
            // In case we have an infinite loop
            if (iter++ > 100) {
                throw new RuntimeException("Too many iterations");
            }
        }

        Assertions.assertThat(stringWriter)
                .hasToString(jsonLines);

        Assertions.assertThat(stepIdx)
                .hasValue(3);

        System.out.println(stringWriter);
    }

    @Test
    void read_prettyJson() throws IOException {

        final AtomicLong stepIdx = new AtomicLong();
        Mockito.doAnswer(
                        invocation -> {
                            final Long idx = invocation.getArgument(0, Long.class);
                            stepIdx.set(idx);
                            return false;
                        })
                .when(mockSteppingController).endRecord(Mockito.anyLong());

        final String prettyJson = """
                [
                  {
                    "firstName": "John",
                    "lastName": "Doe"
                  },
                  {
                    "firstName": "Anna",
                    "lastName": "Smith"
                  },
                  {
                    "firstName": "Peter",
                    "lastName": "Jones"
                  }
                ]""";

        final StringReader stringReader = new StringReader(prettyJson);
        final StringWriter stringWriter = new StringWriter();

        final ReaderRecordDetector readerRecordDetector = new ReaderRecordDetector(
                stringReader, mockSteppingController);

        final char[] outputBuf = new char[10];

        int readCnt;
        int iter = 0;
        while (true) {
            readCnt = readerRecordDetector.read(outputBuf);
            if (readCnt == -1) {
                break;
            }
            if (readCnt > 0) {
                final String str = new String(outputBuf, 0, readCnt);
                LOGGER.debug("readCnt: {}, str: '{}'", readCnt, str);
                stringWriter.write(outputBuf, 0, readCnt);
            }
            // In case we have an infinite loop
            if (iter++ > 100) {
                throw new RuntimeException("Too many iterations");
            }
        }

        Assertions.assertThat(stringWriter)
                .hasToString(prettyJson);

        Assertions.assertThat(stepIdx)
                .hasValueGreaterThan(3); // equal to number of \n

        System.out.println(stringWriter);
    }
}
