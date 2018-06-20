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

package stroom.data.store.impl.fs;

import org.junit.jupiter.api.Test;
import stroom.test.RawInputSegmentWriter;
import stroom.util.io.StreamUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the raw input segment writer is capable of determining the input
 * type (XML and text) correctly and that it inserts segment boundaries in the
 * correct places.
 */
class TestRawInputSegmentWriter {
    @Test
    void testSimpleWriteThenRead() {
        final String text = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><records><record>text1</record><record>text2</record><record>text3</record></records>";
        final InputStream bais = new ByteArrayInputStream(text.getBytes(StreamUtil.DEFAULT_CHARSET));

        final ByteArrayOutputStream dataBuffer = new ByteArrayOutputStream();
        final ByteArrayOutputStream indexBuffer = new ByteArrayOutputStream();

        final RawInputSegmentWriter writer = new RawInputSegmentWriter();
        writer.write(bais, new RASegmentOutputStream(dataBuffer, indexBuffer));

        assertThat(new String(dataBuffer.toByteArray(), StreamUtil.DEFAULT_CHARSET)).isEqualTo(text);
    }
}
