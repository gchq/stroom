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

package stroom.service.resource;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.pipeline.reader.BOMRemovalInputStream;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStreamUtil extends StroomUnitTest {
    private static final String REF_STRING = "<?xml version=\"1.1\" encoding=\"UTF-8\"?>\r\n<test>this is a bom test</test>";

    @Test
    public void printCharsets() {
    }

    @Test
    public void readANSI() {
        test("TestStreamUtil/ansi.nxml", "US-ASCII");
    }

    @Test
    public void readDOS() {
        test("TestStreamUtil/dos.nxml", "US-ASCII");
    }

    @Test
    public void readUTF8() {
        test("TestStreamUtil/utf-8.nxml", "UTF-8");
    }

    @Test
    public void readUTF16() {
        test("TestStreamUtil/utf-16le.nxml", "UTF-16LE");
    }

    @Test
    public void readUTF16BE() {
        test("TestStreamUtil/utf-16be.nxml", "UTF-16BE");
    }

    private void test(final String resourceName, final String charsetName) {
        // Test using byte buffer.
        InputStream inputStream = StroomPipelineTestFileUtil.getInputStream(resourceName);
        BOMRemovalInputStream bomRemovalIS = new BOMRemovalInputStream(inputStream, charsetName);

        try {
            final Charset charset = Charset.forName(charsetName);
            String string = StreamUtil.streamToString(bomRemovalIS, charset);
            Assert.assertEquals("Strings don't match", REF_STRING, string);

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
            Assert.assertEquals("Strings don't match", REF_STRING, string);

        } catch (final Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                bomRemovalIS.close();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                try {
                    inputStream.close();
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
    }
}
