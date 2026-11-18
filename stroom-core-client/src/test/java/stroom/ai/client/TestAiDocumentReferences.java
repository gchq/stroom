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

import stroom.ai.shared.DocumentReference;
import stroom.ai.shared.DocumentReferenceContext;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiDocumentReferences {

    private static final DocRef DICTIONARY = new DocRef("Dictionary", "dict-uuid", "Malicious IPs");
    private static final DocRef XSLT = new DocRef("XSLT", "xslt-uuid", "Event translation");
    private static final DocRef FOLDER = new DocRef(ExplorerConstants.FOLDER_TYPE, "folder-uuid", "Security");

    @Test
    void theSystemAndFavouritesRootsAreLeftOut() {
        assertThat(AiDocumentReferences.references(List.of(
                ExplorerConstants.SYSTEM_NODE,
                ExplorerConstants.FAVOURITES_NODE,
                node(DICTIONARY))))
                .containsExactly(new DocumentReference(DICTIONARY, null));
        assertThat(AiDocumentReferences.context(List.of(
                ExplorerConstants.SYSTEM_NODE,
                ExplorerConstants.FAVOURITES_NODE)))
                .isNull();
    }

    @Test
    void severalItemsAreAttachedTogetherInOrder() {
        final DocumentReferenceContext context = AiDocumentReferences.context(List.of(
                node(XSLT), node(DICTIONARY), node(FOLDER)));

        assertThat(context).isNotNull();
        assertThat(context.getDocuments())
                .extracting(DocumentReference::getDocRef)
                .containsExactly(XSLT, DICTIONARY, FOLDER);
        assertThat(context.getDescription()).isEqualTo("3 documents");
        assertThat(AiDocumentReferences.context(List.of(node(DICTIONARY))).getDescription())
                .isEqualTo("Dictionary 'Malicious IPs'");
    }

    @Test
    void nothingToAttach() {
        assertThat(AiDocumentReferences.context(null)).isNull();
        assertThat(AiDocumentReferences.context(List.of())).isNull();
        assertThat(AiDocumentReferences.references(Arrays.asList(null, node(XSLT))))
                .containsExactly(new DocumentReference(XSLT, null));
    }

    private static ExplorerNode node(final DocRef docRef) {
        return ExplorerNode.builder().docRef(docRef).build();
    }
}
