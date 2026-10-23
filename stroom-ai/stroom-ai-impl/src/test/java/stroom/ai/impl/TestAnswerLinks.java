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
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongPredicate;

import static org.assertj.core.api.Assertions.assertThat;

class TestAnswerLinks {

    private static final DocRef FEED = new DocRef("Feed", "2cdab573-f54a-4d4b-867b-8943f37b3cb0", "ACME-EVENTS");
    private static final DocRef CONVERTER = new DocRef("TextConverter", "39864596-5f32-4fca-b2c4-cb61da2a0fe9",
            "ACME-EVENTS Fragments");
    private static final DocRef EVENTS_SCHEMA = new DocRef("XmlSchema", "d7c55950-7427-48ab-a1b6-11bc7b449dfd",
            "Events");
    private static final DocRef SHARED_FEED = new DocRef("Feed", "e82b2aa3-4fe8-48ba-a57a-e2b41464888f", "SHARED");
    private static final DocRef SHARED_PIPELINE = new DocRef("Pipeline", "5d8ce80a-34ba-4afa-8ee2-e65a7ad43401",
            "SHARED");
    private static final Map<String, List<DocRef>> DOCUMENTS = Map.of(
            FEED.getName(), List.of(FEED),
            CONVERTER.getName(), List.of(CONVERTER),
            EVENTS_SCHEMA.getName(), List.of(EVENTS_SCHEMA),
            "SHARED", List.of(SHARED_FEED, SHARED_PIPELINE));
    private static final LongPredicate KNOWN_STREAMS = Set.of(73144L, 73145L)::contains;

    private static String link(final String answer) {
        return AnswerLinks.link(answer, DOCUMENTS, KNOWN_STREAMS);
    }

    private static String docLink(final String text, final DocRef docRef) {
        return "[" + text + "](?action=open-doc&docType=" + docRef.getType() + "&docUuid=" + docRef.getUuid() + ")";
    }

    @Test
    void testAStreamTheToolsGaveIsLinked() {
        assertThat(link("The feed holds stream 73145, with 1,000 records."))
                .isEqualTo("The feed holds [stream 73145](?action=open-stream&id=73145), with 1,000 records.");
        assertThat(link("Stream ID 73145 is raw."))
                .isEqualTo("[Stream ID 73145](?action=open-stream&id=73145) is raw.");
    }

    @Test
    void testEachStreamOfAListIsLinked() {
        assertThat(link("Streams 73144 and 73145 are raw."))
                .isEqualTo("Streams [73144](?action=open-stream&id=73144) and "
                           + "[73145](?action=open-stream&id=73145) are raw.");
    }

    @Test
    void testAStreamTheToolsDidNotGiveIsNotLinked() {
        assertThat(link("Stream 99999 is raw.")).isEqualTo("Stream 99999 is raw.");
        // Only the one the tools gave, of a list.
        assertThat(link("Streams 99999 and 73145."))
                .isEqualTo("Streams 99999 and [73145](?action=open-stream&id=73145).");
    }

    @Test
    void testADistinctiveNameIsLinkedWhereverItIs() {
        assertThat(link("Feed **ACME-EVENTS** has no pipelines."))
                .isEqualTo("Feed **" + docLink("ACME-EVENTS", FEED) + "** has no pipelines.");
        assertThat(link("It works from ACME-EVENTS."))
                .isEqualTo("It works from " + docLink("ACME-EVENTS", FEED) + ".");
    }

    @Test
    void testTheLongestNameIsLinked() {
        assertThat(link("Saved: ACME-EVENTS Fragments."))
                .isEqualTo("Saved: " + docLink("ACME-EVENTS Fragments", CONVERTER) + ".");
    }

    @Test
    void testAnOrdinaryWordIsLinkedOnlyWhereItIsANameOrItsTypeIsNamed() {
        assertThat(link("The Events are valid.")).isEqualTo("The Events are valid.");
        assertThat(link("Validated against **Events**."))
                .isEqualTo("Validated against **" + docLink("Events", EVENTS_SCHEMA) + "**.");
        // 'schema' is a word for XmlSchema.
        assertThat(link("Validated against the Events schema."))
                .isEqualTo("Validated against the " + docLink("Events", EVENTS_SCHEMA) + " schema.");
        assertThat(link("Validated against the Events feed.")).isEqualTo("Validated against the Events feed.");
    }

    @Test
    void testANameOfDocumentsOfTwoTypesIsLinkedOnlyWhereItsTypeIsNamed() {
        assertThat(link("Use SHARED.")).isEqualTo("Use SHARED.");
        assertThat(link("The pipeline SHARED reads the feed 'SHARED'."))
                .isEqualTo("The pipeline " + docLink("SHARED", SHARED_PIPELINE) + " reads the feed '"
                           + docLink("SHARED", SHARED_FEED) + "'.");
    }

    @Test
    void testInlineCodeIsLinkedOnlyWhenItIsExactlyAName() {
        assertThat(link("Saved `ACME-EVENTS Fragments`, reading `ACME-EVENTS/raw`."))
                .isEqualTo("Saved " + docLink("`ACME-EVENTS Fragments`", CONVERTER) + ", reading `ACME-EVENTS/raw`.");
    }

    @Test
    void testEachIsLinkedOnceALine() {
        assertThat(link("ACME-EVENTS and ACME-EVENTS, stream 73145 and stream 73145.\nACME-EVENTS"))
                .isEqualTo(docLink("ACME-EVENTS", FEED) + " and ACME-EVENTS, "
                           + "[stream 73145](?action=open-stream&id=73145) and stream 73145.\n"
                           + docLink("ACME-EVENTS", FEED));
    }

    @Test
    void testLinksCodeBlocksAndUrlsAreLeftAlone() {
        final String answer = """
                Already [ACME-EVENTS](?action=open-doc&docType=Feed&docUuid=x) and [stream 73145](https://x).
                See https://example.com/ACME-EVENTS.
                ```
                ACME-EVENTS stream 73145
                ```
                """;
        assertThat(link(answer)).isEqualTo(answer);
    }

    @Test
    void testPartOfALongerNameIsNotLinked() {
        assertThat(link("ACME-EVENTS-V2 and XACME-EVENTS")).isEqualTo("ACME-EVENTS-V2 and XACME-EVENTS");
    }

    @Test
    void testTheChatsToolResultsSayWhatMayBeLinked() {
        final AiService aiService = Mockito.mock(AiService.class);
        final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
        final DocRef hidden = new DocRef("Feed", "11111111-2222-3333-4444-555555555555", "HIDDEN-FEED");
        Mockito.when(aiService.getMessages(7)).thenReturn(List.of(
                new AiChatMessage(1, 7, 0L, AiMessageType.TOOL_RESULT, null,
                        "{\"text\":\"Feed 'ACME-EVENTS'\\nUUID: " + FEED.getUuid().toUpperCase()
                        + "\\nstream 73145; also " + hidden.getUuid() + "\"}", 1, null),
                // Said by the model, not given by a tool.
                new AiChatMessage(2, 7, 0L, AiMessageType.AI_RESPONSE, null, "stream 55555", 1, null)));
        Mockito.when(documentAccess.find(FEED.getUuid())).thenReturn(Optional.of(node(FEED)));
        // The user can't view it.
        Mockito.when(documentAccess.find(hidden.getUuid())).thenReturn(Optional.empty());

        assertThat(new AnswerLinks(aiService, documentAccess)
                .link(7, "ACME-EVENTS, HIDDEN-FEED, stream 73145 and stream 55555."))
                .isEqualTo(docLink("ACME-EVENTS", FEED) + ", HIDDEN-FEED, "
                           + "[stream 73145](?action=open-stream&id=73145) and stream 55555.");
    }

    @Test
    void testAnAnswerIsKeptAsItWasIfItCannotBeLinked() {
        final AiService aiService = Mockito.mock(AiService.class);
        Mockito.when(aiService.getMessages(7)).thenThrow(new RuntimeException("no database"));

        assertThat(new AnswerLinks(aiService, Mockito.mock(DocumentAccess.class)).link(7, "stream 73145"))
                .isEqualTo("stream 73145");
        assertThat(new AnswerLinks(aiService, Mockito.mock(DocumentAccess.class)).link(7, null)).isNull();
    }

    private static ExplorerNode node(final DocRef docRef) {
        return ExplorerNode.builder().docRef(docRef).build();
    }
}
