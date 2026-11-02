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

package stroom.dashboard.impl.script;

import stroom.docref.DocRef;
import stroom.script.shared.ScriptDoc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestServedScripts {

    private static final DocRef LIBRARY = new DocRef(ScriptDoc.TYPE, "lib", "ECharts");
    private static final ScriptContentProvider ADAPTERS = script -> "Adapter".equals(script.getName())
            ? Optional.of("var adapter = 2;")
            : Optional.empty();

    @Test
    void aScriptWhoseContentComesWithStroomIsServedWithIt() {
        final ScriptDoc held = script("Adapter", "var adapter = 1;");

        final ScriptDoc served = ServedScripts.served(held, Set.of(ADAPTERS));

        assertThat(served.getData()).isEqualTo("var adapter = 2;");
        // The rest is as held, so it loads its dependencies as before.
        assertThat(served.getUuid()).isEqualTo(held.getUuid());
        assertThat(served.getName()).isEqualTo("Adapter");
        assertThat(served.getDependencies()).containsExactly(LIBRARY);
        // Its version names the content served, so a browser holding another release's loads this one.
        assertThat(served.getVersion()).startsWith("v1-").isNotEqualTo(held.getVersion());
        assertThat(ServedScripts.served(held, Set.of(script -> Optional.of("var adapter = 3;"))).getVersion())
                .isNotEqualTo(served.getVersion());
        // As held, for editing and export.
        assertThat(held.getData()).isEqualTo("var adapter = 1;");
    }

    @Test
    void anyOtherScriptIsServedAsHeld() {
        final ScriptDoc held = script("Library", "var lib;");

        assertThat(ServedScripts.served(held, Set.of(ADAPTERS))).isSameAs(held);
        assertThat(ServedScripts.served(held, Set.of())).isSameAs(held);
        assertThat(ServedScripts.served(null, Set.of(ADAPTERS))).isNull();
    }

    private static ScriptDoc script(final String name, final String data) {
        return ScriptDoc.builder()
                .uuid(name.toLowerCase())
                .name(name)
                .version("v1")
                .data(data)
                .dependencies(List.of(LIBRARY))
                .build();
    }
}
