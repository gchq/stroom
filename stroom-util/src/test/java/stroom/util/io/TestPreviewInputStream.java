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

package stroom.util.io;


import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TestPreviewInputStream {

    @Test
    void test() throws IOException {
        final StringBuilder sb = new StringBuilder();
        for (int line = 1; line <= 100; line++) {
            sb.append("line");
            sb.append(line);
            for (int col = 1; col <= 100; col++) {
                sb.append(",col");
                sb.append(col);
            }
            sb.append("\n");
        }
        final String input = sb.toString();

        final ByteArrayInputStream inputStream = new ByteArrayInputStream(input.getBytes(StreamUtil.DEFAULT_CHARSET));

        final PreviewInputStream previewInputStream = new PreviewInputStream(inputStream);
        String data = previewInputStream.previewAsString(200, StreamUtil.DEFAULT_CHARSET_NAME);
        System.out.println(data);

        assertThat(input.startsWith(data)).isTrue();

        data = previewInputStream.previewAsString(200, StreamUtil.DEFAULT_CHARSET_NAME);
        System.out.println(data);

        assertThat(input.startsWith(data)).isTrue();

        data = previewInputStream.previewAsString(200, StreamUtil.DEFAULT_CHARSET_NAME);
        System.out.println(data);

        assertThat(input.startsWith(data)).isTrue();

        final String output = StreamUtil.streamToString(previewInputStream, true);
        assertThat(output).isEqualTo(input);
    }

    @Test
    void testDeclRemoval() {
        final Pattern XML_DECL_PATTERN = Pattern.compile("<\\?\\s*xml[^>]*>", Pattern.CASE_INSENSITIVE);
        String data = "  <?XML version=\"1.0\" encoding=\"UTF-8\"?>    ";
        data = XML_DECL_PATTERN.matcher(data).replaceFirst("");
        assertThat(data.trim().length()).isEqualTo(0);
    }
}
