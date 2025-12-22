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

package stroom.script;


import stroom.dashboard.impl.script.ScriptStore;
import stroom.docref.DocRef;
import stroom.script.shared.ScriptDoc;
import stroom.test.AbstractCoreIntegrationTest;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestScriptStoreImpl extends AbstractCoreIntegrationTest {

    @Inject
    private ScriptStore scriptStore;

    @Test
    void testUTF8Resource() {
        final String data = "var π = Math.PI, τ = 2 * π, halfπ = π / 2, ε = 1e-6, ε2 = ε * ε, " +
                "d3_radians = π / 180, d3_degrees = 180 / π;";

        final DocRef docRef = scriptStore.createDocument("test");
        final ScriptDoc script = scriptStore.readDocument(docRef);
        script.setData(data);
        scriptStore.writeDocument(script);
        final ScriptDoc loaded = scriptStore.readDocument(docRef);

        assertThat(loaded.getData()).isEqualTo(data);
        final List<ScriptDoc> linkedScripts = scriptStore.fetchLinkedScripts(docRef, null);
        assertThat(linkedScripts).hasSize(1);
    }
}
