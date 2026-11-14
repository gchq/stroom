/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolRefusal;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A sample sent with context data ([ContextSamples]): packed as Stroom's receipt takes child streams, a part each.
class TestContextSamples {

    @Test
    void eachPartIsADatAndCtxPairInOrder() throws IOException {
        final List<ContextSamples.Part> parts = ContextSamples.parts(null, null, List.of(
                Map.of("sample", "<a/>", "context", "<c1/>"),
                Map.of("sample", "<b/>", "context", "<c2/>")));
        final List<String> entries = new ArrayList<>();
        try (final ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(ContextSamples.zip(parts)))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName() + "=" + new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        assertThat(entries).containsExactly("001.dat=<a/>", "001.ctx=<c1/>", "002.dat=<b/>", "002.ctx=<c2/>");
        assertThat(ContextSamples.FILE_NAME).endsWith(".zip");
    }

    @Test
    void oneSampleWithItsContextIsOnePartAndNoContextIsAPlainUpload() {
        assertThat(ContextSamples.parts("<a/>", "<c/>", null)).containsExactly(new ContextSamples.Part("<a/>",
                "<c/>"));
        assertThat(ContextSamples.parts("<a/>", null, null)).isEmpty();
    }

    @Test
    void partsGivenWronglyAreRefused() {
        assertThatThrownBy(() -> ContextSamples.parts("<a/>", "<c/>", List.of(Map.of("sample", "x",
                "context", "y")))).isInstanceOf(ToolRefusal.class).hasMessageContaining("not both");
        assertThatThrownBy(() -> ContextSamples.parts(null, null, List.of())).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("parts is a list");
        assertThatThrownBy(() -> ContextSamples.parts(null, null, List.of(Map.of("sample", "<a/>"))))
                .isInstanceOf(ToolRefusal.class).hasMessageContaining("context data as context");
        assertThatThrownBy(() -> ContextSamples.parts(null, "<c/>", null)).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("raw data as sample");
    }
}
