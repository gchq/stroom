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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.tools.SessionLimits;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.timeline.TimelineEventXml;
import stroom.ai.impl.tools.timeline.TimelineFields;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// `compare_activity` with `includeTargets`: what was done to the person scored as its own findings group (by
/// type, and accounts acting on them for the first time), never in their own findings, volume or profile; the
/// source-wide check applied to it apart; off by default.
@SuppressWarnings("unchecked")
class TestCompareActivityTargets {

    private static final int CHAT = 7;
    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 5);
    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 5, 4);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 6, 1);
    private static final LocalDate GRANTED = LocalDate.of(2026, 5, 13);
    private static final String FOLDER = "/outputs/compare/p1/";

    @TempDir
    Path tempDir;

    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final List<QueryField> fields = new ArrayList<>(List.of(QueryField.createDate("@timestamp"),
            QueryField.createKeyword("user.id"), QueryField.createKeyword("user.name"),
            QueryField.createKeyword("host.name"), QueryField.createKeyword("event.action"),
            QueryField.createKeyword("user.target.id")));
    private FakeTabWorkspace files;
    private CompareFixtures fixtures;
    private CompareActivityTool tool;

    @BeforeEach
    void setUp() {
        files = new FakeTabWorkspace(tempDir);
        fixtures = new CompareFixtures(ZONE);
        final DocRef proxy = CompareFixtures.PROXY;
        Mockito.when(dataSourceAccess.listUsable()).thenReturn(List.of(proxy));
        Mockito.when(dataSourceAccess.find(Mockito.anyString())).thenReturn(List.of(proxy));
        Mockito.when(dataSourceAccess.timeField(proxy)).thenReturn(Optional.of(QueryField.createDate("@timestamp")));
        Mockito.when(queryService.findFields(Mockito.any(FindFieldCriteria.class))).thenAnswer(invocation ->
                new ResultPage<>(fields));
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.any())).thenReturn(Map.of(
                InstructionRules.PERSON_FIELD, new FieldRule(List.of("user.id"), "/Security"),
                InstructionRules.USER_FIELD, new FieldRule(List.of("user.name"), "/Security"),
                InstructionRules.HOST_FIELD, new FieldRule(List.of("host.name"), "/Security"),
                InstructionRules.ACTION_FIELD, new FieldRule(List.of("event.action"), "/Security"),
                InstructionRules.TARGET_PERSON_FIELD, new FieldRule(List.of("user.target.id"), "/Security")));
        tool = new CompareActivityTool(files.workspace(), dataSourceAccess, () -> queryService,
                fixtures.aggregates(), new TimelineFields(agentInstructions, dataSourceAccess, Optional.empty()),
                new PeerGroups(Mockito.mock(TimelineEventXml.class)), Mockito.mock(AiAuditLog.class));
        for (int i = 0; i < 6; i++) {
            fixtures.workdays("other" + i, FROM, WINDOW_TO, 9, 17, date -> 4, date -> Map.of("host.name", "h",
                    "event.action", "View"));
        }
        fixtures.workdays("p1", FROM, WINDOW_TO, 9, 17, date -> 4, date -> Map.of("host.name", "ws-1",
                "event.action", "View"));
        // The helpdesk's routine resets of other people's passwords, a few a month; the person's own password
        // changes on themselves, theirs (never done to them).
        for (LocalDate date = FROM; date.isBefore(WINDOW_TO); date = date.plusWeeks(1)) {
            target("helpdesk", "H1", "ResetPassword", "other" + (date.getDayOfYear() % 6), date.atTime(10, 0));
            target("p1", "p1", "ChangePassword", "p1", date.atTime(11, 0));
        }
    }

    @Test
    void whatWasDoneToThePersonIsItsOwnGroupAndNeverInTheirFindings() {
        final Map<String, Object> without = json(arguments(Map.of()));
        final String profileWithout = files.content(FOLDER + "profile.csv").orElseThrow();
        // An admin, never seen acting on them, grants them a privileged group; then two failed logons against the
        // account, which no account did.
        target("admin1", "P9", "AddGroup", "p1", GRANTED.atTime(14, 0));
        target(null, null, "Logon", "p1", GRANTED.atTime(23, 0));
        target(null, null, "Logon", "p1", GRANTED.atTime(23, 5));

        final Map<String, Object> with = json(arguments(Map.of(CompareActivityTool.INCLUDE_TARGETS_ARG, true)));

        // Off by default; the person's own findings and profile the same either way.
        assertThat(without).doesNotContainKey("done_to_them");
        assertThat(with.get("findings")).isEqualTo(without.get("findings"));
        assertThat(files.content(FOLDER + "profile.csv").orElseThrow()).isEqualTo(profileWithout);
        assertThat(list(with, "findings")).noneSatisfy(finding -> assertThat(String.valueOf(finding.get("feature")))
                .startsWith(TargetScoring.PREFIX));

        final Map<String, Object> done = (Map<String, Object>) with.get("done_to_them");
        assertThat(done.get("note")).isEqualTo(CompareResult.DONE_TO_THEM);
        assertThat(list(done, "sources")).singleElement().satisfies(source -> {
            assertThat(source).containsEntry("source", "Proxy")
                    .containsEntry("in_window", Map.of("AddGroup", 1, "Logon", 2))
                    .containsEntry("new_actors", List.of("admin1"));
            assertThat(String.valueOf(source.get("by"))).startsWith("done to them, by target person id "
                                                                     + "(user.target.id");
        });
        // The week of the grant: the group, the failed logons and the new account acting on them, each a finding
        // of its own, worded as done to them.
        assertThat(list(done, "findings")).extracting(finding -> finding.get("feature"))
                .contains(TargetScoring.TYPE + "AddGroup", TargetScoring.TYPE + "Logon", TargetScoring.NEW_ACTORS,
                        TargetScoring.EVENTS)
                .doesNotContain(TargetScoring.TYPE + "ChangePassword", TargetScoring.TYPE + "ResetPassword");
        assertThat(list(done, "findings")).filteredOn(finding -> TargetScoring.NEW_ACTORS.equals(
                finding.get("feature"))).singleElement().satisfies(finding -> {
                    assertThat(finding).containsEntry("kind", "week").containsEntry("window", "2026-05-11 to "
                                                                                              + "2026-05-17");
                    assertThat(String.valueOf(finding.get("evidence"))).startsWith("Accounts acting on them for the "
                                                                                   + "first time 1 in the week "
                                                                                   + "from 2026-05-11 (admin1)");
                });
        // In the findings file, after the person's own.
        assertThat(files.content(FOLDER + "findings.csv").orElseThrow()).contains(TargetScoring.TYPE + "AddGroup");
    }

    @Test
    void aChangeDoneToEveryoneIsExplainedByTheSource() {
        // An access review that week: everyone given the same group.
        for (int i = 0; i < 6; i++) {
            target("admin1", "P9", "AddGroup", "other" + i, GRANTED.atTime(14, 0));
        }
        target("admin1", "P9", "AddGroup", "p1", GRANTED.atTime(14, 0));

        final Map<String, Object> done = (Map<String, Object>) json(arguments(Map.of(
                CompareActivityTool.INCLUDE_TARGETS_ARG, true))).get("done_to_them");

        assertThat(list(done, "findings")).extracting(finding -> finding.get("feature"))
                .doesNotContain(TargetScoring.TYPE + "AddGroup");
        assertThat((List<String>) done.get("source_wide")).anySatisfy(text -> assertThat(text)
                .startsWith("[Proxy] Done to them: AddGroup 1 in the week from 2026-05-11"));
    }

    @Test
    void theChatSaysWhatWasDoneToThemApart() {
        target("admin1", "P9", "AddGroup", "p1", GRANTED.atTime(14, 0));

        final String text = tool.execute(call(arguments(Map.of(CompareActivityTool.INCLUDE_TARGETS_ARG, true))),
                chat()).text();

        assertThat(text).contains("**Done to them** (" + CompareResult.DONE_TO_THEM + ")")
                .contains("- [Proxy] done to them, by target person id")
                .contains(": in the window AddGroup 1; accounts acting on them for the first time: admin1")
                .contains("[Proxy] Done to them: AddGroup 1 in the week from 2026-05-11");
        // Their own findings never hold it.
        assertThat(text.substring(0, text.indexOf("**Done to them**"))).doesNotContain("Done to them: AddGroup");
    }

    // ------------------------------------------------------------------------------------------------

    // An event done to someone: its actor (null for none), its action, whom it was done to.
    private void target(final String actor,
                        final String actorId,
                        final String action,
                        final String targetId,
                        final LocalDateTime local) {
        final Map<String, Object> event = new LinkedHashMap<>();
        event.put(CompareFixtures.TIME_FIELD, local.atZone(ZONE).toInstant());
        if (actorId != null) {
            event.put("user.id", actorId);
            event.put("user.name", actor);
        }
        event.put("event.action", action);
        event.put("user.target.id", targetId);
        fixtures.aggregates().source(CompareFixtures.PROXY.getUuid()).add(event);
    }

    private Map<String, Object> arguments(final Map<String, Object> more) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put(CompareActivityTool.PERSON_ARG, "p1");
        arguments.put(CompareActivityTool.WINDOW_FROM_ARG, WINDOW_FROM.toString());
        arguments.put(CompareActivityTool.WINDOW_TO_ARG, WINDOW_TO.minusDays(1).toString());
        arguments.put(CompareActivityTool.BASELINE_FROM_ARG, FROM.toString());
        arguments.put(CompareActivityTool.PEERS_ARG, false);
        arguments.put("timeZone", ZONE.getId());
        arguments.putAll(more);
        return arguments;
    }

    private Map<String, Object> json(final Map<String, Object> arguments) {
        final ToolResult result = tool.execute(call(arguments), mcp());
        assertThat(result.isError()).as(result.text()).isFalse();
        return JsonUtil.readValue(result.text(), Map.class);
    }

    private static List<Map<String, Object>> list(final Map<String, Object> json, final String key) {
        return (List<Map<String, Object>>) json.get(key);
    }

    private static ToolCall call(final Map<String, Object> arguments) {
        return ToolCalls.call(CompareActivityTool.NAME, JsonUtil.writeValueAsString(arguments, false));
    }

    private ToolCallContext mcp() {
        return ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "mcp-1",
                        new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A client")))
                .workspace(new SessionWorkspace(files.workspace(), CHAT))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(AiWorkspaceConfig::new))
                .build();
    }

    private ToolCallContext chat() {
        return ToolCallContext.builder(ToolSession.chat(CHAT))
                .workspace(new SessionWorkspace(files.workspace(), CHAT))
                .userQuestions(UserQuestions.NONE)
                .limits(SessionLimits.lookedUp(AiWorkspaceConfig::new))
                .build();
    }
}
