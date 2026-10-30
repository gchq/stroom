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

package stroom.pipeline.client.component;

import stroom.pipeline.shared.TextConverterDoc.TextConverterType;

import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextConverterEditor {

    @Test
    void theTypeChoosesTheMode() {
        assertThat(TextConverterEditor.editorMode(TextConverterType.XML_FRAGMENT, "dataSplitter"))
                .isEqualTo(AceEditorMode.STROOM_FRAGMENT_PARSER);
        assertThat(TextConverterEditor.editorMode(TextConverterType.DATA_SPLITTER, null))
                .isEqualTo(AceEditorMode.STROOM_DATA_SPLITTER);
    }

    @Test
    void withNoTypeTheCodeChoosesTheMode() {
        assertThat(TextConverterEditor.editorMode(TextConverterType.NONE, "<dataSplitter>"))
                .isEqualTo(AceEditorMode.STROOM_DATA_SPLITTER);
        assertThat(TextConverterEditor.editorMode(null, "<!ENTITY foo>"))
                .isEqualTo(AceEditorMode.STROOM_FRAGMENT_PARSER);
    }

    @Test
    void otherwiseTheCombinedParser() {
        assertThat(TextConverterEditor.editorMode(null, null))
                .isEqualTo(AceEditorMode.STROOM_COMBINED_PARSER);
        assertThat(TextConverterEditor.editorMode(TextConverterType.NONE, "  "))
                .isEqualTo(AceEditorMode.STROOM_COMBINED_PARSER);
        assertThat(TextConverterEditor.editorMode(null, "<xml/>"))
                .isEqualTo(AceEditorMode.STROOM_COMBINED_PARSER);
    }
}
