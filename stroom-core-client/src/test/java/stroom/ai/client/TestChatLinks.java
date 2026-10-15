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

package stroom.ai.client;

import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestChatLinks {

    private static final String UUID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90";

    @Test
    void openDocLinksAreRecognisedWhateverTheirHost() {
        final DocRef expected = new DocRef("Pipeline", UUID);

        assertThat(ChatLinks.openDocTarget(
                "https://stroom.example.com/stroom/ui?action=open-doc&docType=Pipeline&docUuid=" + UUID))
                .isEqualTo(expected);
        // FastMCP may know Stroom by an internal address.
        assertThat(ChatLinks.openDocTarget(
                "http://stroom-internal:8080/?docUuid=" + UUID.toUpperCase() + "&docType=Pipeline&action=open-doc"))
                .isEqualTo(expected);
        assertThat(ChatLinks.openDocTarget("?action=open-doc&docType=Pipeline&docUuid=" + UUID + "#top"))
                .isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Not open-doc, or no action.
            "https://stroom/?action=delete-doc&docType=Pipeline&docUuid=" + UUID,
            "https://stroom/?docType=Pipeline&docUuid=" + UUID,
            // A missing or badly formed type or UUID.
            "https://stroom/?action=open-doc&docUuid=" + UUID,
            "https://stroom/?action=open-doc&docType=Pipeline",
            "https://stroom/?action=open-doc&docType=Pipe%20line&docUuid=" + UUID,
            "https://stroom/?action=open-doc&docType=Pipeline&docUuid=not-a-uuid",
            "https://stroom/?action=open-doc&docType=Pipeline&docUuid=" + UUID + "x",
            // A parameter given twice could be read two ways.
            "https://stroom/?action=open-doc&docType=Pipeline&docType=Feed&docUuid=" + UUID,
            "https://stroom/?action=open-doc&action=other&docType=Pipeline&docUuid=" + UUID,
            "https://stroom/path#action=open-doc&docType=Pipeline&docUuid=" + UUID,
            ""})
    void otherLinksAreNotOpenedInPlace(final String href) {
        assertThat(ChatLinks.openDocTarget(href)).isNull();
    }

    @Test
    void nullIsNotALink() {
        assertThat(ChatLinks.openDocTarget(null)).isNull();
        assertThat(ChatLinks.isExternal(null)).isFalse();
    }

    @Test
    void externalLinks() {
        assertThat(ChatLinks.isExternal("https://example.com")).isTrue();
        assertThat(ChatLinks.isExternal(" HTTP://example.com")).isTrue();
        assertThat(ChatLinks.isExternal("mailto:someone@example.com")).isTrue();
        assertThat(ChatLinks.isExternal("javascript:alert(1)")).isFalse();
        assertThat(ChatLinks.isExternal("#footnote-1")).isFalse();
    }
}
