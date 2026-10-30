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

package stroom.document.asset.client.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// What the dialog says is wrong with a file's or folder's new name; the dialog itself is checked
// by its workbench stories (App/Editors/VisualisationEditor)
class TestDocumentAssetRenameController {

    private static final String ILLEGAL = "/:";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void noName(final String name) {
        assertThat(DocumentAssetRenameController.problemWith(name, true, ILLEGAL))
                .isEqualTo("Please set the name of the file");
        assertThat(DocumentAssetRenameController.problemWith(name, false, ILLEGAL))
                .isEqualTo("Please set the name of the folder");
    }

    @Test
    void illegalCharacter() {
        assertThat(DocumentAssetRenameController.problemWith("a:b", true, ILLEGAL))
                .isEqualTo("The name must not contain the character ':'");
    }

    @Test
    void validName() {
        assertThat(DocumentAssetRenameController.problemWith("main.js", true, ILLEGAL))
                .isNull();
    }
}
