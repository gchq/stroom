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

package stroom.mcp.impl.e2e;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// `activity_timeline` called as real models called it in eval cases 77 and 78 (Haiku and Sonnet, over a client
/// that doesn't sample), replayed on each case's own data ([AbstractEvalTest#questionData]) and scored as the eval
/// scores a model's calls ([EvalScore#scoreQuestion]): the case's expected sessions and rows.
///
/// Case 77's models gave one source over the whole index (endpoint, logons written `CORP\jsmith`, proxy, AI chat
/// and messages), the event type field named and no roles: with no model to classify the codes the tool asks the
/// calling model for them (typeRoles). The user's domain-qualified form the logons write (`CORP\jsmith`) is
/// offered, never read unasked (another domain's account may be someone else's): given in logonIds, the logons'
/// locks and unlocks are read too and, with the codes' roles, the week's focus sessions are the plan's. Case 78's Sonnet mapped the process to the source
/// column (`proxy`) for a user seen only in web proxy events: still no session (only a focus starts one), the
/// activity periods by site. Both models first gave dates alone, which are whole days in the call's zone.
///
/// Skipped without an Elasticsearch node unless `STROOM_TEST_ELASTIC_REQUIRED=true`.
@SuppressWarnings("unchecked")
class TestE2eTimelineModelCalls extends AbstractEvalTest {

    private static final String WEEK = "77_timeline_user_week_activity";
    private static final String PROXY_ONLY = "78_timeline_proxy_only_user";
    private static final String TOOL = "activity_timeline";

    @Test
    void sonnetsWeekCallOverTheWholeIndexAsksForTheCodesRolesThenGivesThePlansSessions() {
        final Map<String, Object> evalCase = EvalCases.load(WEEK);
        final QuestionData data = questionData(evalCase);
        try {
            final List<EvalScore.ToolUse> calls = new ArrayList<>();
            // Sonnet's first call: dates alone, read as whole days (the end date included).
            final Map<String, Object> first = sonnetsWeekCall();
            first.put("timeFrom", "2026-11-09");
            first.put("timeTo", "2026-11-16");
            calls.add(theOtherFormIsOffered(theCodesAreAskedFor(call(first))));
            // Its second, as it made it.
            final Map<String, Object> second = sonnetsWeekCall();
            calls.add(theOtherFormIsOffered(theCodesAreAskedFor(call(second))));
            // The form offered given, as the refusal says: the logons' codes are asked for too.
            second.put("logonIds", List.of("CORP\\jsmith"));
            calls.add(theLogonsCodesAreAskedFor(call(second)));
            // Then with the codes' roles, as the refusal asks.
            second.put("sources", List.of(withTypeRoles(((List<Map<String, Object>>) second.get("sources"))
                    .getFirst())));
            calls.add(theWeeksSessions(call(second)));
            scored(evalCase, calls);
        } finally {
            data.remove();
        }
    }

    @Test
    void haikusWeekCallAsksForTheCodesRolesThenGivesThePlansSessions() {
        final Map<String, Object> evalCase = EvalCases.load(WEEK);
        final QuestionData data = questionData(evalCase);
        try {
            final List<EvalScore.ToolUse> calls = new ArrayList<>();
            final Map<String, Object> source = new LinkedHashMap<>();
            source.put("index", data.elastic().doc().getUuid());
            source.put("name", "User activity");
            source.put("user", "UserName");
            source.put("host", "Host");
            source.put("process", "Process");
            source.put("title", "WindowTitle");
            source.put("window", "WindowId");
            source.put("object", "Object");
            source.put("text", "Text");
            source.put("eventTypeField", "EventType");
            source.put("time", "EventTime");
            final Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("entity", "jsmith");
            arguments.put("sources", List.of(source));
            arguments.put("timeFrom", "2026-11-09T00:00:00");
            arguments.put("timeTo", "2026-11-16T00:00:00");
            arguments.put("timeZone", "UTC");
            arguments.put("view", "both");
            arguments.put("characterise", true);
            arguments.put("lanes", "host");
            calls.add(theOtherFormIsOffered(theCodesAreAskedFor(call(arguments))));
            arguments.put("logonIds", List.of("CORP\\jsmith"));
            calls.add(theLogonsCodesAreAskedFor(call(arguments)));
            arguments.put("sources", List.of(withTypeRoles(source)));
            calls.add(theWeeksSessions(call(arguments)));
            scored(evalCase, calls);
        } finally {
            data.remove();
        }
    }

    @Test
    void sonnetsProxyOnlyCallWithTheProcessAsTheSourceColumnGivesNoSessionButPeriodsBySite() {
        final Map<String, Object> evalCase = EvalCases.load(PROXY_ONLY);
        final QuestionData data = questionData(evalCase);
        try {
            final List<EvalScore.ToolUse> calls = new ArrayList<>();
            final Map<String, Object> source = new LinkedHashMap<>();
            source.put("index", "Any source activity");
            source.put("user", "UserName");
            source.put("host", "Host");
            source.put("process", "Source");
            source.put("object", "KeyValue");
            final Map<String, Object> arguments = new LinkedHashMap<>();
            arguments.put("entity", "pnguyen");
            arguments.put("sources", List.of(source));
            arguments.put("timeFrom", "2026-11-16T00:00:00Z");
            arguments.put("timeTo", "2026-11-18T00:00:00Z");
            arguments.put("timeZone", "UTC");
            arguments.put("view", "both");
            final EvalScore.ToolUse asMade = call(arguments);
            assertThat(asMade.refused()).as(asMade.text()).isFalse();
            assertThat(asMade.result()).containsEntry("session_count", 0);
            calls.add(asMade);
            // The same with dates alone, as it first gave them: 16 and 17 November whole.
            arguments.put("timeFrom", "2026-11-16");
            arguments.put("timeTo", "2026-11-17");
            final EvalScore.ToolUse dates = call(arguments);
            assertThat(dates.refused()).as(dates.text()).isFalse();
            assertThat((Map<String, Object>) dates.result().get("time")).containsEntry("from", "2026-11-16T00:00:00Z")
                    .containsEntry("to", "2026-11-18T00:00:00Z");
            assertThat(dates.result().get("rows")).isEqualTo(asMade.result().get("rows"));
            scored(evalCase, calls);
        } finally {
            data.remove();
        }
    }

    // ------------------------------------------------------------------------------------------------

    private static Map<String, Object> sonnetsWeekCall() {
        final Map<String, Object> source = new LinkedHashMap<>();
        source.put("index", "User activity");
        source.put("name", "User activity");
        source.put("user", "UserName");
        source.put("host", "Host");
        source.put("process", "Process");
        source.put("title", "WindowTitle");
        source.put("window", "WindowId");
        source.put("object", "Object");
        source.put("eventTypeField", "EventType");
        final Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("entity", "jsmith");
        arguments.put("sources", List.of(source));
        arguments.put("timeFrom", "2026-11-09T00:00:00Z");
        arguments.put("timeTo", "2026-11-16T00:00:00Z");
        arguments.put("view", "both");
        arguments.put("characterise", true);
        arguments.put("text", "Text");
        return arguments;
    }

    // The source with its codes' roles, as a model gives them from the refusal's list.
    private static Map<String, Object> withTypeRoles(final Map<String, Object> source) {
        final Map<String, Object> roles = new LinkedHashMap<>();
        roles.put("focus", "focus");
        roles.put("open", "open");
        roles.put("close", "close");
        roles.put("lock", "idle");
        roles.put("unlock", "active");
        for (final String action : List.of("clipboard", "save", "removable_copy", "web", "prompt", "message")) {
            roles.put(action, "action");
        }
        final Map<String, Object> out = new LinkedHashMap<>(source);
        out.put("typeRoles", roles);
        return out;
    }

    private EvalScore.ToolUse call(final Map<String, Object> arguments) {
        final CallToolResult result = client.call(TOOL, arguments);
        final EvalScore.ToolUse used = toolUse(TOOL, arguments, result);
        dump(TOOL, used.refused()
                ? used.text()
                : used.result());
        return used;
    }

    // Refused, never a timeline built from codes nothing classified: the codes and their counts given, and how to
    // give their roles. Never a time it can't read. jsmith alone: the logons' CORP\jsmith isn't read.
    private static EvalScore.ToolUse theCodesAreAskedFor(final EvalScore.ToolUse used) {
        assertThat(used.refused()).as(used.text()).isTrue();
        assertThat(used.text()).doesNotContain("isn't a time", "lock 11").contains("typeRoles", "EventType",
                "focus 55");
        return used;
    }

    // The logons' CORP\jsmith offered, for the calling model to give in logonIds if it is the same person's.
    private static EvalScore.ToolUse theOtherFormIsOffered(final EvalScore.ToolUse used) {
        assertThat(used.text()).contains("Other written forms of jsmith, not read: User activity: CORP\\jsmith. "
                                         + "Give them in logonIds (with jsmith) if they are the same person's.");
        return used;
    }

    // With CORP\jsmith given, the logons' codes are among those asked for.
    private static EvalScore.ToolUse theLogonsCodesAreAskedFor(final EvalScore.ToolUse used) {
        assertThat(used.refused()).as(used.text()).isTrue();
        assertThat(used.text()).contains("typeRoles", "focus 55", "lock 11", "unlock 10")
                .doesNotContain("Other written forms");
        return used;
    }

    private static EvalScore.ToolUse theWeeksSessions(final EvalScore.ToolUse used) {
        assertThat(used.refused()).as(used.text()).isFalse();
        assertThat(used.result()).containsEntry("entity", "jsmith").containsEntry("session_count", 63);
        // The logons, written CORP\jsmith and given in logonIds, read with the rest: 360 events of jsmith and 21
        // locks and unlocks.
        assertThat(used.result()).containsEntry("events_read", 381);
        return used;
    }

    private static void scored(final Map<String, Object> evalCase, final List<EvalScore.ToolUse> calls) {
        final EvalScore score = new EvalScore(String.valueOf(evalCase.get("id")));
        score.scoreQuestion(evalCase, calls);
        assertThat(score.passed()).as(score.summary()).isTrue();
    }
}
