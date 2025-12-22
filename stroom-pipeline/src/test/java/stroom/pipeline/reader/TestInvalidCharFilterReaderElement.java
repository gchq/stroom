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

import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.StoredErrorReceiver;
import stroom.pipeline.factory.TakesReader;
import stroom.pipeline.factory.Target;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ElementId;
import stroom.util.string.ByteArrayBuilder;

import com.google.common.io.CharStreams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TestInvalidCharFilterReaderElement {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(
            TestInvalidCharFilterReaderElement.class);

    private static final Charset charset = StandardCharsets.UTF_8;
    StoredErrorReceiver errorReceiver = null;
    ErrorReceiverProxy errorReceiverProxy = null;

    @BeforeEach
    void setUp() {
        errorReceiver = new StoredErrorReceiver();
        errorReceiverProxy = new ErrorReceiverProxy(errorReceiver);
    }

    @Test
    void test_noChange() throws IOException {
        final byte[] inputBytes = new ByteArrayBuilder()
                .append("<element>")
                .append("good")
                .append("</element>")
                .toByteArray();

        final String input = new String(inputBytes, StandardCharsets.UTF_8);
        final String output = doTest(inputBytes);

        assertThat(output)
                .isEqualTo(input);
        assertThat(errorReceiver.getTotalErrors())
                .isZero();
    }

    @Test
    void test_oneChange() throws IOException {
        final byte[] inputBytes = new ByteArrayBuilder()
                .append("<element>")
                .append(5)
                .append("</element>")
                .toByteArray();

        final String input = new String(inputBytes, StandardCharsets.UTF_8);
        final String output = doTest(inputBytes);

        assertThat(output)
                .isNotEqualTo(input);
        assertThat(output)
                .doesNotContain(String.valueOf(InvalidXMLCharFilterReaderElement.REPLACEMENT_CHAR));
        assertThat(errorReceiver.getTotalErrors())
                .isEqualTo(1);
    }

    @Test
    void test_oneChange_warnDisabled() throws IOException {
        final byte[] inputBytes = new ByteArrayBuilder()
                .append("<element>")
                .append(5)
                .append("</element>")
                .toByteArray();

        final String input = new String(inputBytes, StandardCharsets.UTF_8);
        final String output = doTest(inputBytes, false);

        assertThat(output)
                .isNotEqualTo(input);
        assertThat(output)
                .doesNotContain(String.valueOf(InvalidXMLCharFilterReaderElement.REPLACEMENT_CHAR));
        assertThat(errorReceiver.getTotalErrors())
                .isEqualTo(0);
    }

    @Test
    void test_twoChanges() throws IOException {
        final byte[] inputBytes = new ByteArrayBuilder()
                .append("<element>")
                .append(5)
                .append("</element>")
                .append("<element>")
                .append(4)
                .append("</element>")
                .toByteArray();

        final String input = new String(inputBytes, StandardCharsets.UTF_8);
        final String output = doTest(inputBytes);

        assertThat(output)
                .isNotEqualTo(input);
        assertThat(output)
                .doesNotContain(String.valueOf(InvalidXMLCharFilterReaderElement.REPLACEMENT_CHAR));
        assertThat(errorReceiver.getTotalErrors())
                .isEqualTo(1);
    }

    @Test
    void test_twoChanges_warnDisabled() throws IOException {
        final byte[] inputBytes = new ByteArrayBuilder()
                .append("<element>")
                .append(5)
                .append("</element>")
                .append("<element>")
                .append(4)
                .append("</element>")
                .toByteArray();

        final String input = new String(inputBytes, StandardCharsets.UTF_8);
        final String output = doTest(inputBytes, false);

        assertThat(output)
                .isNotEqualTo(input);
        assertThat(output)
                .doesNotContain(String.valueOf(InvalidXMLCharFilterReaderElement.REPLACEMENT_CHAR));
        assertThat(errorReceiver.getTotalErrors())
                .isEqualTo(0);
    }

    private String doTest(final byte[] inputBytes) throws IOException {
        return doTest(inputBytes, true);
    }

    private String doTest(final byte[] inputBytes, final boolean logWarnings) throws IOException {
        final InvalidCharFilterReaderElement invalidCharFilterReader = new InvalidCharFilterReaderElement(
                errorReceiverProxy);

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(inputBytes);

        invalidCharFilterReader.setInputStream(byteArrayInputStream, charset.name());
        invalidCharFilterReader.setXmlVersion(new Xml10Chars().getXmlVersion());
        invalidCharFilterReader.setWarnOnRemoval(logWarnings);

        invalidCharFilterReader.setElementId(new ElementId(invalidCharFilterReader.getClass().getSimpleName()));
        final TestTarget testTarget = new TestTarget();
        testTarget.setElementId(new ElementId(testTarget.getClass().getSimpleName()));
        invalidCharFilterReader.setTarget(testTarget);

        invalidCharFilterReader.createProcessors();
        testTarget.createProcessors();

        final Reader reader = testTarget.getReader();
        final String output = CharStreams.toString(reader);

        invalidCharFilterReader.endStream();
        LOGGER.info("output:\n{}", output);
        return output;
    }

    /**
     * @return The number of times chr appears in str.
     */
    private int containsChar(final String str, final char chr) {
        int cnt = 0;
        if (str != null) {
            int idx = 0;
            for (; ; ) {
                idx = str.indexOf(chr, idx);
                if (idx == -1) {
                    break;
                } else {
                    cnt++;
                    if (idx < str.length() - 1) {
                        idx++;
                    } else {
                        break;
                    }
                }
            }
        }
        return cnt;
    }

    // --------------------------------------------------------------------------------


    private static class TestTarget extends AbstractIOElement implements TakesReader, Target {

        private Reader reader = null;

        @Override
        public void setReader(final Reader reader) throws IOException {
            this.reader = reader;
        }

        public Reader getReader() {
            return reader;
        }
    }
}
