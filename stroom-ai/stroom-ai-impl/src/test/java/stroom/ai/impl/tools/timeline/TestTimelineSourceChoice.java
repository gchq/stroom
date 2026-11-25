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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.McpElicitations;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.TabSnapshots.StoredTabSnapshot;
import stroom.ai.impl.tools.FormAnswers;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.timeline.TimelineSourceChoice.Chosen;
import stroom.ai.impl.tools.timeline.TimelineSourceChoice.Matched;
import stroom.ai.impl.workspace.WorkspaceException;
import stroom.ai.shared.AiTabSearch;
import stroom.ai.shared.DashboardTableContext;
import stroom.dashboard.shared.DashboardDoc;
import stroom.dashboard.shared.DashboardSearchRequest;
import stroom.dashboard.shared.Search;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.search.elastic.shared.ElasticIndexDoc;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Which sources a timeline reads when neither the call nor a skill says: a form's choices, with a dashboard
/// attached and without; the user's own words matched to indexes and confirmed; all of them where no form can ask.
@SuppressWarnings("unchecked")
class TestTimelineSourceChoice {

    private static final DocRef PROXY = new DocRef(ElasticIndexDoc.TYPE, "proxy-uuid", "Web proxy");
    private static final DocRef WINDOWS = new DocRef(ElasticIndexDoc.TYPE, "windows-uuid", "Windows events");
    private static final DocRef ENDPOINT = new DocRef(ElasticIndexDoc.TYPE, "endpoint-uuid", "Endpoint events");
    private static final DocRef LUCENE = new DocRef(LuceneIndexDoc.TYPE, "lucene-uuid", "Old events");
    private static final DocRef DASHBOARD = new DocRef(DashboardDoc.TYPE, "dashboard-uuid", "Proxy dashboard");

    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final TimelineSourceChoice choice = new TimelineSourceChoice(dataSourceAccess, () -> tabSnapshots);

    @Test
    void withADashboardAttachedItsDataSourceIsTheFirstChoice() {
        supported();
        Mockito.when(dataSourceAccess.canUse(PROXY)).thenReturn(true);
        final AiTabSearch search = new AiTabSearch("Requests", "table-1", new DashboardTableContext("d", null,
                DashboardSearchRequest.builder().search(Search.builder().dataSourceRef(PROXY).build()).build()),
                null, List.of(), null, false);
        Mockito.when(tabSnapshots.forLatestQuestion(1)).thenReturn(Optional.of(new StoredTabSnapshot(1, 1,
                DASHBOARD, "/", "Proxy dashboard", null, false, null, null, 0L, List.of(), List.of(search), null)));
        final Answers answers = new Answers(Map.of("sources", TimelineSourceChoice.DASHBOARD + " (Web proxy)"));

        final Chosen chosen = choice.choose(ToolCalls.call("activity_timeline", "{}"), chat(answers), "t");

        assertThat(enumOf(answers.forms.getFirst(), "sources")).containsExactly(TimelineSourceChoice.DASHBOARD
                                                                                + " (Web proxy)",
                TimelineSourceChoice.ALL, TimelineSourceChoice.OWN_WORDS);
        assertThat(chosen.indexes()).containsExactly(PROXY);
        assertThat(chosen.all()).isFalse();
    }

    @Test
    void withoutADashboardAllSupportedIsTheDefault() {
        supported();
        final Answers answers = new Answers(Map.of("sources", TimelineSourceChoice.ALL));

        final Chosen chosen = choice.choose(ToolCalls.call("activity_timeline", "{}"), chat(answers), "t");

        final Map<String, Object> sources = (Map<String, Object>) ((Map<String, Object>) answers.forms.getFirst()
                .get("properties")).get("sources");
        assertThat((List<String>) sources.get("enum")).containsExactly(TimelineSourceChoice.ALL,
                TimelineSourceChoice.OWN_WORDS);
        assertThat(sources).containsEntry("default", TimelineSourceChoice.ALL);
        // Elastic Indexes only.
        assertThat(chosen.indexes()).containsExactly(PROXY, WINDOWS, ENDPOINT);
        assertThat(chosen.all()).isTrue();
    }

    @Test
    void theUsersWordsAreMatchedAWordMostIndexesHaveChoosingNothingAlone() {
        final List<DocRef> indexes = List.of(PROXY, WINDOWS, ENDPOINT, new DocRef(ElasticIndexDoc.TYPE, "x",
                "Mail events"));

        final Matched matched = TimelineSourceChoice.match("web proxy logs and Windows events", indexes,
                doc -> PROXY.equals(doc)
                        ? "The web proxy's logs."
                        : "");

        // "events" names three of the four: they're listed, but only those another word names are chosen.
        assertThat(matched.listed()).containsExactlyElementsOf(indexes);
        assertThat(matched.preselected()).containsExactly(PROXY, WINDOWS);
    }

    @Test
    void theUsersWordsAreShownToConfirmAndOnlyWhatTheyPickIsRead() {
        supported();
        final Answers answers = new Answers(Map.of("sources", TimelineSourceChoice.OWN_WORDS, "other",
                "windows", "indexes", List.of("Windows events")));

        final Chosen chosen = choice.choose(ToolCalls.call("activity_timeline", "{}"), chat(answers), "t");

        assertThat(enumOf(answers.forms.get(1), "indexes")).containsExactly("Windows events");
        assertThat(chosen.indexes()).containsExactly(WINDOWS);
        assertThat(chosen.how()).contains("'windows'");
    }

    @Test
    void whereNoFormCanAskEverySupportedIndexIsTakenAndADeclineBuildsNothing() {
        supported();

        final Chosen all = choice.choose(ToolCalls.call("activity_timeline", "{}"), chat(UserQuestions.NONE), "t");
        assertThat(all.indexes()).hasSize(3);
        assertThat(all.how()).contains("no form could ask which");

        final UserQuestions declining = new Answers(Map.of()) {
            @Override
            public ElicitResult ask(final String toolName, final String callId, final String intro,
                                    final Map<String, Object> form) {
                return new ElicitResult(ElicitResult.Action.DECLINE, null);
            }
        };
        assertThatThrownBy(() -> choice.choose(ToolCalls.call("activity_timeline", "{}"), chat(declining), "t"))
                .isInstanceOf(WorkspaceException.class).hasMessageContaining("didn't choose");
    }

    @Test
    void aMessageSentInsteadOfEitherFormBuildsNothingAndIsReplied() {
        // Seen in chat 197: a tool took a form put aside by the user's message as their answer.
        supported();
        assertThatThrownBy(() -> choice.choose(ToolCalls.call("activity_timeline", "{}"),
                chat(FormAnswers.sendingInstead("Just the proxy")), "t"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("The user sent a message instead of answering 'Which data sources should the "
                                      + "timeline read?': \"Just the proxy\". Nothing was done.");

        // Its words chosen, then a message sent instead of confirming the indexes they match.
        final UserQuestions confirmingInstead = new Answers(Map.of("sources", TimelineSourceChoice.OWN_WORDS,
                "other", "windows")) {
            private int asked;

            @Override
            public ElicitResult ask(final String toolName, final String callId, final String intro,
                                    final Map<String, Object> form) {
                return asked++ == 0
                        ? super.ask(toolName, callId, intro, form)
                        : new ElicitResult(ElicitResult.Action.CANCEL, null, Map.of(McpElicitations.SENT_INSTEAD,
                                "And the endpoint"));
            }
        };
        assertThatThrownBy(() -> choice.choose(ToolCalls.call("activity_timeline", "{}"), chat(confirmingInstead),
                "t"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("'Read these data sources?': \"And the endpoint\"");
    }

    private void supported() {
        Mockito.when(dataSourceAccess.listUsable()).thenReturn(List.of(PROXY, LUCENE, WINDOWS, ENDPOINT));
    }

    private static List<String> enumOf(final Map<String, Object> form, final String key) {
        final Map<String, Object> field = (Map<String, Object>) ((Map<String, Object>) form.get("properties"))
                .get(key);
        return field.get("enum") != null
                ? (List<String>) field.get("enum")
                : (List<String>) ((Map<String, Object>) field.get("items")).get("enum");
    }

    private static ToolCallContext chat(final UserQuestions questions) {
        return ToolCallContext.builder(ToolSession.chat(1)).userQuestions(questions).build();
    }


    // --------------------------------------------------------------------------------


    // A user answering each form from the same answers.
    private static class Answers implements UserQuestions {

        private final Map<String, Object> answers;
        private final List<Map<String, Object>> forms = new ArrayList<>();

        Answers(final Map<String, Object> answers) {
            this.answers = answers;
        }

        @Override
        public boolean canAsk() {
            return true;
        }

        @Override
        public ElicitResult ask(final String toolName,
                                final String callId,
                                final String intro,
                                final Map<String, Object> form) {
            forms.add(form);
            return new ElicitResult(ElicitResult.Action.ACCEPT, answers);
        }
    }
}
