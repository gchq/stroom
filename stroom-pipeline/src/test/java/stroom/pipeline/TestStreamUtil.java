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

package stroom.pipeline;


import stroom.pipeline.reader.BOMRemovalInputStream;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.StreamUtil;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import static org.assertj.core.api.Assertions.assertThat;

class TestStreamUtil extends StroomUnitTest {

    private static final String REF_STRING = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\r\n" +
            "<test>this is a bom test</test>";

    @Test
    void printCharsets() {
    }

    @Test
    void readANSI() throws IOException {
        test("TestStreamUtil/ansi.nxml", "US-ASCII");
    }

    @Test
    void readDOS() throws IOException {
        test("TestStreamUtil/dos.nxml", "US-ASCII");
    }

    @Test
    void readUTF8() throws IOException {
        test("TestStreamUtil/utf-8.nxml", "UTF-8");
    }

    @Test
    void readUTF16() throws IOException {
        test("TestStreamUtil/utf-16le.nxml", "UTF-16LE");
    }

    @Test
    void readUTF16BE() throws IOException {
        test("TestStreamUtil/utf-16be.nxml", "UTF-16BE");
    }

    private void test(final String resourceName, final String charsetName) throws IOException {
        // Test using byte buffer.
        InputStream inputStream = StroomPipelineTestFileUtil.getInputStream(resourceName);
        BOMRemovalInputStream bomRemovalIS = new BOMRemovalInputStream(inputStream, charsetName);

        try {
            final Charset charset = Charset.forName(charsetName);
            String string = StreamUtil.streamToString(bomRemovalIS, charset);
            assertThat(string).as("Strings don't match").isEqualTo(REF_STRING);

            // Test reading single bytes.
            inputStream = StroomPipelineTestFileUtil.getInputStream(resourceName);
            bomRemovalIS = new BOMRemovalInputStream(inputStream, charsetName);

            final byte[] buffer = new byte[1024];
            int b = 0;
            int len = 0;
            for (int i = 0; i < 1024; i++) {
                b = bomRemovalIS.read();
                if (b != -1) {
                    buffer[i] = (byte) b;
                } else {
                    len = i;
                    break;
                }
            }

            string = new String(buffer, 0, len, charsetName);
            assertThat(string).as("Strings don't match").isEqualTo(REF_STRING);

        } catch (final RuntimeException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                bomRemovalIS.close();
            } catch (final IOException e) {
                // Ignore.
            } finally {
                try {
                    inputStream.close();
                } catch (final IOException e) {
                    // Ignore.
                }
            }
        }
    }
}
