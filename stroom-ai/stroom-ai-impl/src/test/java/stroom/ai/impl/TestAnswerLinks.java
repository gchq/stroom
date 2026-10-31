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
import java.util.function.Function;
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
    private static final DocRef TWITTER = new DocRef("Feed", "183ff469-b853-4fa5-8462-4c1b3733bc19", "TWITTER");
    private static final DocRef JSON_FEED = new DocRef("Feed", "2e9b8b9e-3f63-4a7b-9a51-35f8a1d8e6a2", "JSON");
    private static final DocRef EVENTS_PIPELINE = new DocRef("Pipeline", "a123a852-baaf-4ead-866f-96f756da1dea",
            "Twitter-Events-V1.1");
    private static final DocRef EVENTS_DOCUMENTATION = new DocRef("Documentation",
            "ba2a4250-6a8f-4404-a4ae-6c00837ffbb3", "Twitter-Events-V1.1");
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
    void testALinkTheModelWritesIsCheckedByItsUuid() {
        // Asked for by the user: the document is the UUID's, never a guess. Given by UUID alone, or with the wrong
        // type, it is written with its own; one that isn't there (or the user may not view) is its text alone.
        final Map<String, DocRef> known = Map.of(EVENTS_PIPELINE.getUuid(), EVENTS_PIPELINE);
        final Function<String, Optional<DocRef>> resolve = uuid -> Optional.ofNullable(known.get(uuid));
        final String pipelineLink = docLink("Twitter-Events-V1.1", EVENTS_PIPELINE);

        assertThat(AnswerLinks.checkedDocLinks("Enable [Twitter-Events-V1.1](?action=open-doc&docUuid="
                                               + EVENTS_PIPELINE.getUuid() + ") now.", resolve))
                .isEqualTo("Enable " + pipelineLink + " now.");
        assertThat(AnswerLinks.checkedDocLinks("See [it](?action=open-doc&docType=Feed&docUuid="
                                               + EVENTS_PIPELINE.getUuid().toUpperCase() + ").", resolve))
                .isEqualTo("See " + docLink("it", EVENTS_PIPELINE) + ".");
        assertThat(AnswerLinks.checkedDocLinks("See [GHOST](?action=open-doc&docType=Feed&docUuid="
                                               + "00000000-0000-0000-0000-000000000000) and [x](?action=open-doc).",
                resolve)).isEqualTo("See GHOST and x.");
        // Code is left as it is, as are other links.
        final String code = "`[a](?action=open-doc&docUuid=00000000-0000-0000-0000-000000000000)` and "
                            + "[stream 1](?action=open-stream&id=1)\n```\n[b](?action=open-doc&docUuid=x)\n```";
        assertThat(AnswerLinks.checkedDocLinks(code, resolve)).isEqualTo(code);
    }

    @Test
    void testANameInCapitalsIsLinkedUnlessItIsAWordAnAnswerUses() {
        // Seen: "all of TWITTER's existing data" not linked, TWITTER taken for an ordinary word.
        final Map<String, List<DocRef>> documents = Map.of("TWITTER", List.of(TWITTER), "JSON", List.of(JSON_FEED));
        assertThat(AnswerLinks.link("It processes all of TWITTER's existing data.", documents, KNOWN_STREAMS))
                .isEqualTo("It processes all of " + docLink("TWITTER", TWITTER) + "'s existing data.");
        assertThat(AnswerLinks.link("Its records are JSON.", documents, KNOWN_STREAMS))
                .isEqualTo("Its records are JSON.");
        assertThat(AnswerLinks.link("The feed JSON holds them.", documents, KNOWN_STREAMS))
                .isEqualTo("The feed " + docLink("JSON", JSON_FEED) + " holds them.");
    }

    @Test
    void testAPipelinesNameIsThePipelinesNotItsDocumentations() {
        // Seen: "Enable filter 58 on Twitter-Events-V1.1" not linked, the pipeline's documentation sharing its
        // name.
        final Map<String, List<DocRef>> documents = Map.of("Twitter-Events-V1.1",
                List.of(EVENTS_DOCUMENTATION, EVENTS_PIPELINE));
        assertThat(AnswerLinks.link("Enable filter 58 on Twitter-Events-V1.1, on its Processors tab.", documents,
                KNOWN_STREAMS))
                .isEqualTo("Enable filter 58 on " + docLink("Twitter-Events-V1.1", EVENTS_PIPELINE)
                           + ", on its Processors tab.");
        // Its documentation, where that is said.
        assertThat(AnswerLinks.link("The documentation Twitter-Events-V1.1 is updated.", documents, KNOWN_STREAMS))
                .isEqualTo("The documentation " + docLink("Twitter-Events-V1.1", EVENTS_DOCUMENTATION)
                           + " is updated.");
    }

    @Test
    void testANameWrittenWithTypographicHyphensOrSpacesIsLinkedUnderItsName() {
        // Seen: a table of pipelines written with non-breaking hyphens (U+2011), none of them linked.
        final DocRef errors = new DocRef("Pipeline", "465bb324-f137-4538-98ba-f8758e460fd7",
                "Stroom Errors - Indexing");
        final DocRef ipv4 = new DocRef("Pipeline", "b3bc9162-561c-40ef-b897-57d2815b4b09",
                "Maxmind-GeoLite2-City-IPv4");
        final Map<String, List<DocRef>> documents = Map.of(
                EVENTS_PIPELINE.getName(), List.of(EVENTS_PIPELINE),
                errors.getName(), List.of(errors),
                ipv4.getName(), List.of(ipv4),
                FEED.getName(), List.of(FEED));

        assertThat(AnswerLinks.link("| Twitter‑Events‑V1.1 | 58 | Feed TWITTER | yes |", documents,
                KNOWN_STREAMS))
                .isEqualTo("| " + docLink("Twitter-Events-V1.1", EVENTS_PIPELINE) + " | 58 | Feed TWITTER | yes |");
        // The name's own spaces may be no-break ones, and its dash an en dash.
        assertThat(AnswerLinks.link("| Stroom Errors – Indexing | 3 | **no** |", documents,
                KNOWN_STREAMS))
                .isEqualTo("| " + docLink("Stroom Errors - Indexing", errors) + " | 3 | **no** |");
        // Followed by words of the answer's own, and as inline code.
        assertThat(AnswerLinks.link("Maxmind‑GeoLite2‑City‑IPv4 (loader); `ACME‑EVENTS`.",
                documents, KNOWN_STREAMS))
                .isEqualTo(docLink("Maxmind-GeoLite2-City-IPv4", ipv4) + " (loader); "
                           + docLink("`ACME‑EVENTS`", FEED) + ".");
        // Still not part of a longer name.
        assertThat(AnswerLinks.link("Twitter‑Events‑V1.1‑Old", documents, KNOWN_STREAMS))
                .isEqualTo("Twitter‑Events‑V1.1‑Old");
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
