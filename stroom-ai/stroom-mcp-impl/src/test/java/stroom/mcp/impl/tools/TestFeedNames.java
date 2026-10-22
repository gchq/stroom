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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.feed.shared.FeedDoc;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;

class TestFeedNames {

    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final UserQuestions questions = Mockito.mock(UserQuestions.class);
    private FeedNames feedNames;
    private ToolCallContext context;

    @BeforeEach
    void setUp() {
        Mockito.when(documentAccess.canView(any())).thenReturn(true);
        Mockito.when(docFinder.findByName(FeedDoc.TYPE, "*", true)).thenReturn(List.of(
                feed("ACME-VPN-EVENTS"), feed("Delinea-SecretServer-V1.0"), feed("FORTIGATE")));
        feedNames = new FeedNames(() -> docFinder, documentAccess);
        context = ToolCallContext.builder(ToolSession.chat(1)).userQuestions(questions).build();
    }

    @Test
    void testRatioIsDifflibs() {
        // As Python's difflib.SequenceMatcher(None, a, b).ratio() gives them.
        assertThat(FeedNames.ratio("abcd", "bcde")).isCloseTo(0.75, within(1e-9));
        assertThat(FeedNames.ratio("acme-vpn", "acme-vpn-events")).isCloseTo(16.0 / 23, within(1e-9));
        assertThat(FeedNames.ratio("", "")).isEqualTo(1.0);
    }

    @Test
    void testTheClosestNamesHoldTheNameGivenFirst() {
        assertThat(FeedNames.closest("acme-vpn", List.of("ACME-VPN-EVENTS", "FORTIGATE", "ACME-WEB"), 3))
                .containsExactly("ACME-VPN-EVENTS", "ACME-WEB");
        assertThat(FeedNames.closest("zzz", List.of("ACME-VPN-EVENTS"), 3)).isEmpty();
    }

    @Test
    void testAFeedOfTheSameNameInAnotherCaseIsTheOne() {
        // Seen (Qwen, VS Code): DELINEA-SECRETSERVER-V1.0 made beside an earlier Delinea-SecretServer-V1.0.
        assertThat(feedNames.resolve("delinea-secretserver-v1.0", "describe_feed", null, context))
                .isEqualTo("Delinea-SecretServer-V1.0");
    }

    @Test
    void testAnExactNameIsTakenAsItIs() {
        Mockito.when(docFinder.findByName(FeedDoc.TYPE, "FORTIGATE")).thenReturn(List.of(feed("FORTIGATE")));
        assertThat(feedNames.resolve(" FORTIGATE ", "describe_feed", null, context)).isEqualTo("FORTIGATE");
    }

    @Test
    void testAMisspeltNameIsOfferedItsClosestInAForm() {
        Mockito.when(questions.canAsk()).thenReturn(true);
        Mockito.when(questions.ask(anyString(), any(), anyString(), anyMap()))
                .thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("feed", "ACME-VPN-EVENTS")));
        assertThat(feedNames.resolve("ACME-VPN", "describe_feed", "c1", context)).isEqualTo("ACME-VPN-EVENTS");

        // None of them: the work stops until the user says.
        Mockito.when(questions.ask(anyString(), any(), anyString(), anyMap()))
                .thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("feed", FeedNames.NOT_LISTED)));
        assertThatThrownBy(() -> feedNames.resolve("ACME-VPN", "describe_feed", "c1", context))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("Don't go on until they say");
    }

    @Test
    void testWithNoFormTheClosestAreNamedAndTheUserAsked() {
        assertThatThrownBy(() -> feedNames.resolve("ACME-VPN", "describe_feed", null, context))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("The closest here: ACME-VPN-EVENTS")
                .hasMessageContaining("don't pick one yourself");
        assertThatThrownBy(() -> feedNames.resolve("ZZZZZZ", "describe_feed", null, context))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("No feed named 'ZZZZZZ', nor any like it, in this Stroom.");
    }

    private static DocRef feed(final String name) {
        return new DocRef(FeedDoc.TYPE, name.toLowerCase(), name);
    }
}
