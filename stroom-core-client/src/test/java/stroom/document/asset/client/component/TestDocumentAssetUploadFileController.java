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

import static org.assertj.core.api.Assertions.assertThat;

// The name an uploaded file is given; the dialog itself is checked by its workbench stories
// (App/Editors/VisualisationEditor)
class TestDocumentAssetUploadFileController {

    @Test
    void removesEveryIllegalCharacter() {
        assertThat(DocumentAssetUploadFileController.removeIllegalCharacters("a/b:c/d.css", "/:"))
                .isEqualTo("abcd.css");
    }

    @Test
    void keepsALegalName() {
        assertThat(DocumentAssetUploadFileController.removeIllegalCharacters("style.css", "/:"))
                .isEqualTo("style.css");
    }

    @Test
    void keepsEverythingWhenNothingIsIllegal() {
        assertThat(DocumentAssetUploadFileController.removeIllegalCharacters("a/b:c", null))
                .isEqualTo("a/b:c");
        assertThat(DocumentAssetUploadFileController.removeIllegalCharacters("a/b:c", ""))
                .isEqualTo("a/b:c");
    }
}
