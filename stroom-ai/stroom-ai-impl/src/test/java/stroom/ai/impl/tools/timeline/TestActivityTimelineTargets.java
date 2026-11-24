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

import stroom.ai.api.ElasticEsql;
import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.tools.SessionLimits;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.instructions.AgentInstructions;
import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.instructions.InstructionRules.FieldRule;
import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.ai.impl.tools.query.DataSourceDocumentation;
import stroom.ai.impl.tools.sequence.InMemoryKeyStreams;
import stroom.ai.impl.tools.skills.SkillCatalogue;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// `activity_timeline` with `includeTargets`: what was done to the person (their target fields, by person id, their
/// own acts on themselves left out) in a lane of its own per source, listed with a role column, and never in their
/// activity; off by default.
@SuppressWarnings("unchecked")
class TestActivityTimelineTargets {

    private static final int CHAT = 1;
    private static final DocRef CLOUD = new DocRef(ElasticIndexDoc.TYPE, "cloud-uuid", "Cloud");
    private static final DocRef PROXY = new DocRef(ElasticIndexDoc.TYPE, "proxy-uuid", "Proxy");
    private static final String FOLDER = "/outputs/timelines/P1/";

    @TempDir
    Path tempDir;

    private final InMemoryKeyStreams streams = new InMemoryKeyStreams();
    private final DataSourceAccess dataSourceAccess = Mockito.mock(DataSourceAccess.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private FakeTabWorkspace files;
    private ActivityTimelineTool tool;
    private List<Map<String, Object>> cloud;
    private List<Map<String, Object>> proxy;

    @BeforeEach
    void setUp() {
        files = new FakeTabWorkspace(tempDir);
        Mockito.when(dataSourceAccess.find(Mockito.anyString())).thenAnswer(invocation -> {
            final String given = invocation.getArgument(0);
            return List.of(CLOUD, PROXY).stream()
                    .filter(doc -> doc.getUuid().equals(given) || doc.getName().equals(given))
                    .toList();
        });
        // The cloud audit has who acted and whom it was done to; the proxy only who acted.
        final Map<String, String> cloudMapping = Map.of("@timestamp", "date", "user.id", "keyword", "user.name",
                "keyword", "user.target.id", "keyword", "user.target.name", "keyword", "event.action", "keyword",
                "host.name", "keyword");
        final Map<String, String> proxyMapping = Map.of("@timestamp", "date", "user.id", "keyword", "user.name",
                "keyword", "event.action", "keyword", "host.name", "keyword");
        Mockito.when(queryService.findFields(Mockito.any())).thenAnswer(invocation -> {
            final DocRef doc = invocation.<FindFieldCriteria>getArgument(0).getDataSourceRef();
            final List<QueryField> fields = new ArrayList<>();
            (CLOUD.equals(doc)
                    ? cloudMapping
                    : proxyMapping).forEach((name, type) -> fields.add("date".equals(type)
                    ? QueryField.createDate(name)
                    : QueryField.createKeyword(name)));
            return new ResultPage<>(fields);
        });
        // The site's standard: the person and whom an action was done to, by their person ids.
        Mockito.when(agentInstructions.fieldsRuled(Mockito.any(), Mockito.any())).thenReturn(Map.of(
                InstructionRules.PERSON_FIELD, new FieldRule(List.of("user.id"), "/"),
                InstructionRules.USER_FIELD, new FieldRule(List.of("user.name"), "/"),
                InstructionRules.ACTION_FIELD, new FieldRule(List.of("event.action"), "/"),
                InstructionRules.TARGET_PERSON_FIELD, new FieldRule(List.of("user.target.id"), "/")));
        cloud = streams.add(CLOUD, "@timestamp", cloudMapping);
        proxy = streams.add(PROXY, "@timestamp", proxyMapping);
        final TimelineFields timelineFields = new TimelineFields(agentInstructions, dataSourceAccess,
                Optional.empty(),
                () -> Mockito.mock(DataSourceDocumentation.class));
        final TimelineSourceChoice sourceChoice = new TimelineSourceChoice(dataSourceAccess,
                () -> Mockito.mock(TabSnapshots.class));
        tool = new ActivityTimelineTool(files.workspace(), () -> streams, dataSourceAccess, () -> queryService,
                Mockito.mock(AiAuditLog.class), Mockito.mock(TimelineEventXml.class), timelineFields, sourceChoice,
                () -> Mockito.mock(SkillCatalogue.class), Mockito.mock(DocumentAccess.class), agentInstructions,
                () -> Mockito.mock(ElasticEsql.class), () -> Mockito.mock(DataSourceDocumentation.class));
        // The person's own: a sign-in, a file read, and a password they changed themselves (their own act on their
        // own account: theirs, never done to them).
        cloud.add(doc("09:00", "P1", "pat", "Logon", "P1", "pat"));
        cloud.add(doc("09:10", "P1", "pat", "View", null, null));
        cloud.add(doc("09:20", "P1", "pat", "ChangePassword", "P1", "pat"));
        // Done to them: a privileged group granted by an admin, then a failed logon against the account (no
        // account acted).
        cloud.add(doc("10:00", "P9", "admin1", "AddGroup", "P1", "pat"));
        cloud.add(doc("11:00", null, null, "Logon", "P1", "pat"));
        // Done to someone else: never read.
        cloud.add(doc("10:30", "P9", "admin1", "ResetPassword", "P2", "bob"));
        proxy.add(doc("09:30", "P1", "pat", "View", null, null));
    }

    @Test
    void whatWasDoneToThePersonIsListedApartAndNeverInTheirActivity() {
        final Map<String, Object> without = json(arguments(Map.of()));
        final String spansWithout = spans();
        final String actionsWithout = files.content(FOLDER + ActivityTimelineTool.ACTIONS_FILE).orElseThrow();

        final Map<String, Object> with = json(arguments(Map.of("includeTargets", true)));

        // Off by default.
        assertThat(without).doesNotContainKey("done_to_them");
        assertThat(spansWithout).doesNotContain(",target");
        // Their own events are the same either way: the counts, the sessions and spans, the actions.
        assertThat(with.get("sources")).isEqualTo(without.get("sources"));
        assertThat(with.get("events_read")).isEqualTo(without.get("events_read")).isEqualTo(4L);
        assertThat(with.get("actions")).isEqualTo(without.get("actions"));
        assertThat(with.get("spans")).isEqualTo(without.get("spans"));
        assertThat(with.get("rows")).isEqualTo(without.get("rows"));
        assertThat(spans().lines().filter(line -> line.endsWith("," + TimelineTargets.ACTOR)).toList())
                .isEqualTo(spansWithout.lines().filter(line -> line.endsWith("," + TimelineTargets.ACTOR))
                        .toList());

        final Map<String, Object> done = (Map<String, Object>) with.get("done_to_them");
        assertThat(done).containsEntry("events", 2L).containsEntry("note", TimelineTargets.KEPT_APART);
        final List<Map<String, Object>> sources = (List<Map<String, Object>>) done.get("sources");
        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source).containsEntry("source", "Cloud").containsEntry("events", 2L)
                    .containsEntry("kinds", Map.of("AddGroup", 1L, "Logon", 1L))
                    .containsEntry("done_by", List.of("admin1", TimelineTargets.NO_ACCOUNT));
            assertThat((String) source.get("by")).startsWith("done to them, by target person id (user.target.id")
                    .contains("their own acts by user.id left out");
        });
        assertThat((List<Map<String, Object>>) done.get("left_out")).singleElement().satisfies(left -> {
            assertThat(left).containsEntry("source", "Proxy");
            assertThat((String) left.get("why")).contains("'target person:'");
        });
        assertThat((List<Map<String, Object>>) done.get("listed")).extracting(event -> event.get("type"),
                event -> event.get("by")).containsExactly(org.assertj.core.groups.Tuple.tuple("AddGroup", "admin1"),
                org.assertj.core.groups.Tuple.tuple("Logon", TimelineTargets.NO_ACCOUNT));
        // In the tables, a lane of their own, each an instant, the role said.
        assertThat(spans()).contains("t1,2026-11-10T10:00:00Z,2026-11-10T10:00:00Z,AddGroup by admin1,AddGroup by "
                                     + "admin1,done to them: Cloud,target,")
                .contains("t2,2026-11-10T11:00:00Z,2026-11-10T11:00:00Z,Logon by no account,Logon by no account,"
                          + "done to them: Cloud,target,")
                .doesNotContain("ResetPassword").doesNotContain("ChangePassword by");
        assertThat(files.content(FOLDER + ActivityTimelineTool.ACTIONS_FILE).orElseThrow().lines()
                .filter(line -> line.endsWith("," + TimelineTargets.TARGET)))
                .containsExactly("2026-11-10T10:00:00Z,,,AddGroup by admin1,,,,Cloud,,,target",
                        "2026-11-10T11:00:00Z,,,Logon by no account,,,,Cloud,,,target");
        assertThat(actionsWithout.lines().findFirst().orElseThrow()).endsWith(",role");
    }

    @Test
    void theChatSaysWhatWasDoneToThemApart() {
        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments(Map.of("includeTargets", true)), false)), chat()).text();

        assertThat(text).contains("Done to them: 2 event(s). " + TimelineTargets.KEPT_APART)
                .contains("- Cloud: done to them, by target person id")
                .contains(": 2 (AddGroup 1, Logon 1; by admin1, no account)")
                .contains("- Proxy: not read (")
                .contains("  - 2026-11-10 10:00 AddGroup by admin1 (Cloud)");
    }

    // ------------------------------------------------------------------------------------------------

    private String spans() {
        return files.content(FOLDER + ActivityTimelineTool.SPANS_FILE).orElseThrow();
    }

    private static Map<String, Object> doc(final String time,
                                           final String actorId,
                                           final String actor,
                                           final String action,
                                           final String targetId,
                                           final String target) {
        final Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("@timestamp", Instant.parse("2026-11-10T" + time + ":00Z").toEpochMilli());
        if (actorId != null) {
            doc.put("user.id", actorId);
            doc.put("user.name", actor);
        }
        doc.put("event.action", action);
        doc.put("host.name", "ws-01");
        if (targetId != null) {
            doc.put("user.target.id", targetId);
            doc.put("user.target.name", target);
        }
        return doc;
    }

    private Map<String, Object> arguments(final Map<String, Object> more) {
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("person", "P1");
        arguments.put("timeFrom", "2026-11-10T00:00:00Z");
        arguments.put("timeTo", "2026-11-10T18:00:00Z");
        arguments.put("sources", List.of("Cloud", "Proxy"));
        arguments.put("timeZone", "UTC");
        arguments.putAll(more);
        return arguments;
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

    private Map<String, Object> json(final Map<String, Object> arguments) {
        final String text = tool.execute(ToolCalls.call(ActivityTimelineTool.NAME, JsonUtil.writeValueAsString(
                arguments, false)), mcp()).text();
        assertThat(text).as(text).startsWith("{");
        return (Map<String, Object>) longs(JsonUtil.readValue(text, Map.class));
    }

    // The value with every whole number as a Long, as JSON reads small ones as Integers.
    private static Object longs(final Object value) {
        if (value instanceof final Integer number) {
            return number.longValue();
        } else if (value instanceof final Map<?, ?> map) {
            final Map<Object, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(key, longs(item)));
            return copy;
        } else if (value instanceof final List<?> list) {
            return list.stream().map(TestActivityTimelineTargets::longs).toList();
        }
        return value;
    }
}
