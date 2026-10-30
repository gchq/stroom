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

import stroom.document.asset.client.component.DocumentAssetAddItemController.DialogType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

// What the dialog says is wrong with a new file's or folder's name; the dialog itself is checked
// by its workbench stories (App/Editors/VisualisationEditor)
class TestDocumentAssetAddItemController {

    private static final String ILLEGAL = "/:";

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void noName(final String name) {
        // Regression test: the message ran the type into the next word ("fileyou")
        assertThat(DocumentAssetAddItemController.problemWith(name, ILLEGAL, DialogType.FILE_DIALOG))
                .isEqualTo("Please set the name of the file you wish to create");
        assertThat(DocumentAssetAddItemController.problemWith(name, ILLEGAL, DialogType.FOLDER_DIALOG))
                .isEqualTo("Please set the name of the folder you wish to create");
    }

    @Test
    void illegalCharacter() {
        assertThat(DocumentAssetAddItemController.problemWith("a/b.css", ILLEGAL, DialogType.FILE_DIALOG))
                .isEqualTo("File names must not contain the character '/'");
        assertThat(DocumentAssetAddItemController.problemWith("a:b", ILLEGAL, DialogType.FOLDER_DIALOG))
                .isEqualTo("Folder names must not contain the character ':'");
    }

    @Test
    void validName() {
        assertThat(DocumentAssetAddItemController.problemWith("style.css", ILLEGAL, DialogType.FILE_DIALOG))
                .isNull();
        assertThat(DocumentAssetAddItemController.problemWith("style.css", null, DialogType.FILE_DIALOG))
                .isNull();
    }
}
