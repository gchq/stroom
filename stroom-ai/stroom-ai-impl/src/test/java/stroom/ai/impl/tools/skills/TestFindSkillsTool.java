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

package stroom.ai.impl.tools.skills;

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.skills.SkillCatalogue.Listed;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.util.json.JsonUtil;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestFindSkillsTool {

    private static final int CHAT = 4;
    private static final DocRef HUNTS = new DocRef(ExplorerConstants.FOLDER_TYPE, "hunts", "Hunts");
    private static final DocRef DOC = new DocRef(DocumentationDoc.TYPE, "doc", SkillDoc.DOC_NAME);
    private static final DocRef OTHER_DOC = new DocRef(DocumentationDoc.TYPE, "other", SkillDoc.DOC_NAME);

    private final SkillCatalogue catalogue = Mockito.mock(SkillCatalogue.class);
    private final SkillFacts facts = Mockito.mock(SkillFacts.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final List<Map<String, Object>> forms = new ArrayList<>();
    private ElicitResult answer;
    private boolean canAsk = true;
    private FindSkillsTool tool;

    @BeforeEach
    void setUp() {
        final SkillDoc.Skill skill = SkillDoc.parse(TestSkillDoc.TIMELINE).skills().getFirst();
        final SkillDoc.Skill other = SkillDoc.parse("# User activity timeline\n\nAn older one.\n\n## Steps\n\n"
                                                    + "1. Use `table`.\n").skills().getFirst();
        Mockito.when(catalogue.readable(Mockito.any())).thenReturn(List.of(
                new Listed(DOC, HUNTS, "/Hunts", skill, 0),
                new Listed(OTHER_DOC, ExplorerConstants.SYSTEM_DOC_REF, "/", other, -1),
                new Listed(OTHER_DOC, ExplorerConstants.SYSTEM_DOC_REF, "/", SkillDoc.parse(
                        "# Firewall denies\n\nCounts denied connections.\n").skills().getFirst(), -1)));
        Mockito.when(catalogue.readDoc(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(facts.forSkill(Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(SkillTestFacts.permissive());
        tool = new FindSkillsTool(catalogue, facts, documentAccess);
    }

    @Test
    void aSkillIsReadWholeByNameTheNearestOfTwoFirst() {
        final String result = run(Map.of("name", "user activity timeline"));

        assertThat(result).startsWith("Skill 'User activity timeline' (folder /Hunts, doc doc); also named so: /")
                .contains("## Steps", "{user}");
        Mockito.verify(documentAccess).logView(Mockito.any(), Mockito.eq(DOC), Mockito.contains("find_skills"));
    }

    @Test
    void aSkillsProblemsAreSaidBeforeItRuns() {
        Mockito.when(facts.forSkill(Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(SkillTestFacts.noTools());

        assertThat(run(Map.of("name", "User activity timeline"))).contains("It has problems: say so to the user "
                                                                           + "before running it",
                "'run_stroomql', which isn't a tool");
    }

    @Test
    void runByNameAMissingRequiredParameterIsAskedInAForm() {
        answer = new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("host", "ws01"));

        final String result = run(Map.of("name", "user-activity-timeline", "arguments", Map.of("user", "jsmith")));

        assertThat(forms).singleElement().satisfies(form -> {
            assertThat(form).extractingByKey("required").isEqualTo(List.of("host"));
            assertThat(List.copyOf(((Map<?, ?>) form.get("properties")).keySet())).isEqualTo(List.of("host"));
        });
        assertThat(result).contains("Run it with user=jsmith, days=7 (default), host=ws01",
                "UserId = 'jsmith'", "now() - 7d");
    }

    @Test
    void aMissingParameterNoFormCanAskIsSaid() {
        canAsk = false;

        assertThat(run(Map.of("name", "user-activity-timeline", "arguments", Map.of())))
                .contains("Not run: it needs user, host", "Ask them in the chat");
    }

    @Test
    void aFormTheUserDeclinesRunsNothing() {
        answer = new ElicitResult(ElicitResult.Action.DECLINE, null);

        assertThat(run(Map.of("name", "user-activity-timeline", "arguments", Map.of("user", "x"))))
                .contains("Not run: the user didn't give host");
    }

    @Test
    void skillsAreSearchedByWords() {
        assertThat(run(Map.of("words", "firewall denied"))).contains("1 skill(s) matching 'firewall denied'",
                "- Firewall denies (/): Counts denied connections.");
        assertThat(run(Map.of("words", "nothing like it"))).contains("No skill you can read matches");
        assertThat(run(Map.of())).contains("3 skill(s), a line each");
    }

    @Test
    void anUnknownNameIsSaid() {
        assertThat(run(Map.of("name", "Nope"))).startsWith("No skill named 'Nope' that you can read");
    }

    private String run(final Map<String, Object> arguments) {
        final ToolCallContext context = ToolCallContext.builder(ToolSession.chat(CHAT))
                .userQuestions(new UserQuestions() {
                    @Override
                    public boolean canAsk() {
                        return canAsk;
                    }

                    @Override
                    public ElicitResult ask(final String toolName,
                                            final String callId,
                                            final String intro,
                                            final Map<String, Object> form) {
                        forms.add(form);
                        return answer;
                    }
                })
                .build();
        return tool.execute(new ToolCall("c1", FindSkillsTool.NAME, JsonUtil.writeValueAsString(arguments)),
                context).text();
    }
}
