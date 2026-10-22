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

import stroom.dictionary.shared.DictionaryDoc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// save_dictionary (the Stroom MCP server's `tools/translation.py` `save_dictionary`: its case in
/// `tests/test_surface.py`, creating or updating by uuid).
class TestSaveDictionaryTool {

    private final TranslationWorld world = new TranslationWorld();
    private final SaveDictionaryTool tool = new SaveDictionaryTool(world.docs);

    @Test
    void saveToolsCreateOrUpdateByUuid() {
        final Map<String, Object> made = world.call(tool, Map.of("build", "acme-v1", "name", "VIP", "text",
                "a\n\n b \n", "description", "The VIPs, from the user"));
        assertThat(made).containsEntry("type", DictionaryDoc.TYPE).containsEntry("name", "VIP")
                .containsEntry("entries", 2);
        final String uuid = String.valueOf(made.get("uuid"));
        final DictionaryDoc doc = (DictionaryDoc) world.world.docs.get(uuid);
        assertThat(doc.getData()).isEqualTo("a\n\n b \n");
        assertThat(doc.getDescription()).isEqualTo("The VIPs, from the user");

        final Map<String, Object> changed = world.call(tool, Map.of("build", "acme-v1", "name", "VIP", "text",
                "a", "uuid", uuid));
        assertThat(changed).containsEntry("uuid", uuid).containsEntry("entries", 1);
        assertThat(((DictionaryDoc) world.world.docs.get(uuid)).getData()).isEqualTo("a");
    }

    @Test
    void aSecondDictionaryOfTheNameIsRefused() {
        world.call(tool, Map.of("build", "acme-v1", "name", "VIP", "text", "a"));

        assertThat(String.valueOf(world.call(tool, Map.of("build", "acme-v1", "name", "VIP", "text", "b"))
                .get("error"))).contains("A Dictionary named 'VIP' is already in build acme-v1");
    }
}
