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

import stroom.ai.impl.tools.generation.TranslationMapping;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Stream meta a mapping reads that the sample streams don't carry. Seen: an existing feed's stream downloaded and
/// uploaded again through Stroom's UI as a plain file, its original headers (the system name its translation read)
/// gone.
class TestTranslationBuildsMeta {

    private final TranslationWorld world = new TranslationWorld();

    @Test
    void metaTheSampleDoesntCarryIsSaidWithWhyAndWhatToDo() {
        Mockito.when(world.streamReader.streamAttributes(5L)).thenReturn(Map.of("attributes", Map.of(
                "Feed", "ACME", "RemoteFile", "acme.log", "ReceivedTime", "2026-10-11T00:00:00.000Z"), "stream",
                Map.of()));

        final List<String> warnings = world.builds.metaNotSent(withMeta("System", "CreatedTime", "RemoteFile"),
                List.of(5L));

        // The receipt header the sample lacks; never what stroom:meta() computes itself (CreatedTime) or what it has.
        assertThat(warnings).singleElement().satisfies(warning -> assertThat(warning)
                .startsWith("stroom:meta('System') is not among the sample streams' attributes")
                .contains("uploaded as a plain file through Stroom's UI", "a zip of the stream's parts",
                        "the upload's Meta box"));
    }

    // The test mapping, reading each attribute into a Data entry of its common fields.
    @SuppressWarnings("unchecked")
    private static TranslationMapping withMeta(final String... names) {
        final Map<String, Object> mapping = TranslationWorld.mapping();
        final List<Object> common = new ArrayList<>((List<Object>) mapping.get("common"));
        for (final String name : names) {
            common.add(Map.of("path", "EventSource/Device/Data", "data_name", name, "meta", name));
        }
        mapping.put("common", common);
        return TranslationMapping.fromJson(mapping);
    }
}
