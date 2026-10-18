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

package stroom.ai.impl;

import stroom.ai.api.AiService;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.DocumentReference;
import stroom.ai.shared.DocumentReferenceContext;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.json.JsonUtil;

import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentReferences {

    private static final DocRef DICTIONARY = new DocRef("Dictionary", "dict-uuid", "Malicious IPs");
    private static final DocRef XSLT = new DocRef("XSLT", "xslt-uuid", "Event translation");
    private static final DocRef SECRET = new DocRef("XSLT", "secret-uuid", "Secret");

    private final AiService aiService = Mockito.mock(AiService.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final DocumentReferences documentReferences = new DocumentReferences(aiService, documentAccess);
    private final AtomicInteger nextId = new AtomicInteger(100);

    @BeforeEach
    void setUp() {
        Mockito.when(aiService.createAttachment(Mockito.anyInt(), Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> new AiChatAttachment(nextId.getAndIncrement(), 1, 0L, 0L,
                        AiAttachmentStatus.PENDING, invocation.getArgument(1), null, null, false, null));
        for (final DocRef docRef : List.of(DICTIONARY, XSLT)) {
            Mockito.when(documentAccess.canView(docRef)).thenReturn(true);
            Mockito.when(documentAccess.node(docRef))
                    .thenReturn(Optional.of(ExplorerNode.builder().docRef(docRef).build()));
        }
        Mockito.when(documentAccess.path(DICTIONARY)).thenReturn("/Security/Lookups");
        Mockito.when(documentAccess.path(XSLT)).thenReturn("/Translations");
    }

    @Test
    void eachDocumentIsAttachedReadyAtOnce() {
        documentReferences.attach(1, new DocumentReferenceContext(List.of(
                new DocumentReference(DICTIONARY, null),
                new DocumentReference(XSLT, "/Made/Up"))));

        final ArgumentCaptor<String> contexts = ArgumentCaptor.forClass(String.class);
        Mockito.verify(aiService, Mockito.times(2)).createAttachment(Mockito.eq(1),
                Mockito.eq(AiAttachmentType.DOCUMENT), contexts.capture());
        // Each records its own document, with the path the server found rather than the one it was given.
        assertThat(contexts.getAllValues())
                .extracting(json -> JsonUtil.readValue(json, DocumentReferenceContext.class).getDocuments())
                .containsExactly(
                        List.of(new DocumentReference(DICTIONARY, "/Security/Lookups")),
                        List.of(new DocumentReference(XSLT, "/Translations")));
        Mockito.verify(aiService).updateAttachmentStatus(100, AiAttachmentStatus.READY, null,
                "Dictionary 'Malicious IPs'", null, false);
        Mockito.verify(aiService).updateAttachmentStatus(101, AiAttachmentStatus.READY, null,
                "XSLT 'Event translation'", null, false);
        Mockito.verify(aiService).storeMessage(1, AiMessageType.ATTACHMENT, 100, "Dictionary 'Malicious IPs'");
        Mockito.verify(aiService).storeMessage(1, AiMessageType.ATTACHMENT, 101, "XSLT 'Event translation'");
        Mockito.verify(documentAccess).logView(Mockito.eq(1), Mockito.eq(DICTIONARY), Mockito.anyString());
        Mockito.verify(documentAccess).logView(Mockito.eq(1), Mockito.eq(XSLT), Mockito.anyString());
    }

    @Test
    void aDocumentTheUserCantViewIsNotAttached() {
        Mockito.when(documentAccess.canView(SECRET)).thenReturn(false);

        documentReferences.attach(1, new DocumentReferenceContext(List.of(
                new DocumentReference(SECRET, null),
                new DocumentReference(DICTIONARY, null))));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.ERROR, DocumentReferences.CANNOT_ATTACH);
        Mockito.verify(aiService, Mockito.times(1)).createAttachment(Mockito.anyInt(), Mockito.any(),
                Mockito.anyString());
        Mockito.verify(documentAccess, Mockito.never()).node(SECRET);
        Mockito.verify(documentAccess, Mockito.never()).logView(Mockito.anyInt(), Mockito.eq(SECRET),
                Mockito.anyString());
    }

    @Test
    void aDocumentThatDoesntExistIsNotAttached() {
        final DocRef missing = new DocRef("XSLT", "missing-uuid", "Gone");
        Mockito.when(documentAccess.canView(missing)).thenReturn(true);
        Mockito.when(documentAccess.node(missing)).thenReturn(Optional.empty());

        documentReferences.attach(1, new DocumentReferenceContext(List.of(new DocumentReference(missing, null))));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.ERROR, DocumentReferences.CANNOT_ATTACH);
        Mockito.verify(aiService, Mockito.never()).createAttachment(Mockito.anyInt(), Mockito.any(),
                Mockito.anyString());
    }

    @Test
    void theNoteNamesTheDocumentAndTheToolsUntilAccessIsLost() {
        final AiChatAttachment attachment = new AiChatAttachment(100, 1, 0L, 0L, AiAttachmentStatus.READY,
                AiAttachmentType.DOCUMENT, "Dictionary 'Malicious IPs'", null, false, null, DICTIONARY,
                "/Security/Lookups");

        assertThat(((UserMessage) documentReferences.note(attachment)).singleText())
                .isEqualTo("[Referenced document: Dictionary 'Malicious IPs' (uuid dict-uuid, /Security/Lookups). "
                           + "Use document_info, read_document or document_documentation with its uuid to see "
                           + "it.]");

        Mockito.when(documentAccess.canView(DICTIONARY)).thenReturn(false);
        assertThat(((UserMessage) documentReferences.note(attachment)).singleText())
                .contains("is no longer available")
                .doesNotContain("dict-uuid");
        assertThat(documentReferences.note(new AiChatAttachment(5, 1, 0L, 0L, AiAttachmentStatus.READY,
                AiAttachmentType.DASHBOARD, "Dashboard 'A'", 3, false, null))).isNull();
    }
}
