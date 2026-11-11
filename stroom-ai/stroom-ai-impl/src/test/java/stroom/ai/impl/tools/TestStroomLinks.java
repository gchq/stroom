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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomLinks {

    private static final StroomLinks LINKS = StroomLinks.CHAT;

    @Test
    void aDocumentLinkIsNamedAfterTheDocument() {
        assertThat(LINKS.document(new DocRef("Feed", "feed-uuid", "ACME-EVENTS")))
                .isEqualTo("[ACME-EVENTS](?action=open-doc&docType=Feed&docUuid=feed-uuid)");
    }

    @Test
    void aDocumentWithoutANameIsNamedByItsUuid() {
        assertThat(LINKS.document(new DocRef("Pipeline", "pipe-uuid", null)))
                .isEqualTo("[pipe-uuid](?action=open-doc&docType=Pipeline&docUuid=pipe-uuid)");
        assertThat(LINKS.document(new DocRef("Pipeline", "pipe-uuid", " ")))
                .isEqualTo("[pipe-uuid](?action=open-doc&docType=Pipeline&docUuid=pipe-uuid)");
    }

    @Test
    void squareBracketsInANameDoNotBreakTheLink() {
        assertThat(LINKS.document(new DocRef("Dictionary", "d", "IPs [old]")))
                .isEqualTo("[IPs (old)](?action=open-doc&docType=Dictionary&docUuid=d)");
    }

    @Test
    void aStreamLinkIsLabelled() {
        assertThat(LINKS.stream(12L)).isEqualTo("[stream 12](?action=open-stream&id=12)");
        assertThat(LINKS.stream(900L, "Error stream")).isEqualTo("[Error stream 900](?action=open-stream&id=900)");
    }

    @Test
    void aStreamWithoutAnIdIsNotLinked() {
        assertThat(LINKS.stream(null)).isEqualTo("stream ?");
        assertThat(LINKS.stream(null, "Error stream")).isEqualTo("Error stream ?");
    }

    @Test
    void anEventLinkOpensItsStreamAtTheRecord() {
        assertThat(LINKS.event(1234, 5)).isEqualTo("[event 1234:5](?action=open-stream&id=1234&recordNo=5)");
    }

    @Test
    void aCallsContextGivesTheChatsLinksUnlessToldOtherwise() {
        assertThat(ToolCalls.inChat(1).links()).isSameAs(StroomLinks.CHAT);
        assertThat(ToolCalls.outsideChat().links()).isSameAs(StroomLinks.CHAT);
    }
}
