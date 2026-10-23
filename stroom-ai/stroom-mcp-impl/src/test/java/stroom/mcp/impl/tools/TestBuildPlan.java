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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The plan with its state, and `next` in results (the Stroom MCP server's `tests/test_plan.py`, and the
/// with_next case of `tests/test_consent.py`).
class TestBuildPlan {

    private static final ToolSession SESSION = new ToolSession(ToolSession.Kind.MCP, "s1",
            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client"));

    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final Consent consent = new Consent(() -> Mockito.mock(AiAuditLog.class));
    private final BuildWorld world = new BuildWorld();
    private final BuildPlan plan = new BuildPlan(facts, world.guard(), consent, new BuildMemory());

    TestBuildPlan() {
        Mockito.when(securityContext.getUserRef()).thenReturn(UserRef.builder().uuid("u1").build());
    }

    @Test
    void theChecklistCoversBothStagesInOrder() {
        final List<Object> steps = BuildPlan.checklist().stream().map(step -> step.get("step")).toList();
        assertThat(steps.subList(0, 3)).containsExactly("feed", "samples", "converter");
        assertThat(steps.getLast()).isEqualTo("promoted");
        assertThat(steps.indexOf("stepped")).isLessThan(steps.indexOf("processed"));
        assertThat(steps.indexOf("processed")).isLessThan(steps.indexOf("documented"));
        assertThat(steps.indexOf("documented")).isLessThan(steps.indexOf("index"));
        assertThat(BuildPlan.onboardingChecklist()).hasSize(15);
    }

    @Test
    void resultsCarryTheNextStepUntilPromotion() {
        final Map<String, Object> samples = Map.of("step", "samples", "do", "upload", "tools", "upload_sample");
        assertThat(planWithNext(samples).withNext(context(), "b", result("type", "Feed")))
                .containsEntry("next", samples)
                .containsEntry("done", false);
        final Map<String, Object> promoted = Map.of("step", "promoted", "do", "promote", "tools", "promote_build");
        assertThat(planWithNext(promoted).withNext(context(), "b", result("type", "Feed"))).doesNotContainKey("done");
        // A confirmation round, or no build, passes through untouched; a failing status never fails the write.
        assertThat(plan.withNext(context(), "b", result("status", "needs_confirmation")))
                .isEqualTo(Map.of("status", "needs_confirmation"));
        assertThat(plan.withNext(context(), null, result("x", 1))).isEqualTo(Map.of("x", 1));
        facts.docsFail = new RuntimeException("stroom down");
        assertThat(plan.withNext(context(), "b", result("x", 1))).isEqualTo(Map.of("x", 1));
    }

    @Test
    void nextSaysWhatToDoWhenItsToolIsHidden() {
        // Seen: create_pipeline hidden in a VS Code tool group; the agent wrote a handoff note and stopped.
        final Map<String, Object> next = Map.of("step", "pipeline", "call", Map.of("tool", "create_pipeline",
                "arguments", Map.of()));
        final Map<?, ?> shown = (Map<?, ?>) planWithNext(next).withNext(context(), "b", result("ok", true))
                .get("next");
        assertThat(String.valueOf(shown.get("if_missing")))
                .startsWith("create_pipeline not in your tool list? Call the activate_* tool")
                // Seen (Qwen3 Coder Next): 360 calls to made-up activate_* names; only listed ones, else ask.
                .contains("Only a name your list shows")
                .contains("ask the user to enable create_pipeline");
    }

    @Test
    void theResultTellsTheAgentTheUserChangedTheName() {
        final Consent.Request request = Consent.Request.confirmation("create_feed", "Create feed", Map.of())
                .withEditable("name", "Feed name", "FIREWALL-EDGE-V1.0");
        final ToolCallContext renamed = asking(new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("name", "ACME-FW-V1.0")));
        consent.require(new ToolCall("1", "create_feed", "{}"), renamed, request);
        final Map<String, Object> result = planWithNext(null).withNext(renamed, "b",
                result("type", "Feed", "name", "ACME-FW-V1.0"));
        assertThat(result.get("changed_by_user")).isEqualTo(Map.of("name", "ACME-FW-V1.0"));
        assertThat(String.valueOf(result.get("note")))
                .contains("from 'FIREWALL-EDGE-V1.0' to 'ACME-FW-V1.0': use 'ACME-FW-V1.0' from here on");
        // Accepted as proposed: nothing to say.
        final ToolCallContext same = asking(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of()));
        consent.require(new ToolCall("1", "create_feed", "{}"), same, request);
        assertThat(planWithNext(null).withNext(same, "b", result("type", "Feed"))).doesNotContainKey("changed_by_user");
    }

    @Test
    void theBuildIsRememberedForTheSession() {
        assertThatThrownBy(() -> plan.resolveBuild(context(), null, "create_feed"))
                .hasMessage("create_feed needs build: the build this work belongs to, as start_onboarding or "
                            + "start_build named it (e.g. 'onboard-acme-door'). build_status shows a build's state.");
        assertThat(plan.resolveBuild(context(), "b1", "create_feed")).isEqualTo("b1");
        assertThat(plan.resolveBuild(context(), null, "create_feed")).isEqualTo("b1");
        planWithNext(null).withNext(context(), "b2", result("x", 1));
        // Another session doesn't share it.
        final ToolCallContext other = ToolCallContext.builder(new ToolSession(ToolSession.Kind.MCP, "s2",
                SESSION.caller())).build();
        assertThatThrownBy(() -> plan.resolveBuild(other, null, "create_feed")).isInstanceOf(ToolRefusal.class);
    }

    @Test
    void theBuildOfAManagedDocumentIsItsFolders() {
        final DocRef managed = world.inBuild("b1", "XSLT", "X", "x");
        final DocRef production = world.add("XSLT", "Prod", world.folder("System/" + BuildWorld.WORKSPACE + "/b1")
                .uuid, "prod").docRef();
        assertThat(plan.buildOf(managed)).contains("b1");
        // Not managed, whatever folder it is in.
        assertThat(plan.buildOf(production)).isEmpty();
        assertThat(planWithNext(null).nextFor(context(), production, result("x", 1))).isEqualTo(Map.of("x", 1));
    }

    @Test
    void nextIsOneCallWithWhatTheBuildAlreadyKnows() {
        // A small model given a list of tools for the next step deliberated between them until its context ran out.
        BuildPlan.NextCall call = BuildPlan.nextCall("stepped", BuildPlan.CallArgs.of("acme-v1", List.of("ACME"),
                List.of(7L, 8L), List.of(), "p-1", null));
        assertThat(call.call()).isEqualTo(Map.of("tool", "step_sample", "arguments", Map.of("pipeline_uuid", "p-1",
                "stream_ids", List.of(7L, 8L))));
        assertThat(call.then()).contains("build_translation_xslt uuid=");
        call = BuildPlan.nextCall("indexed", BuildPlan.CallArgs.of("acme-v1", List.of("ACME"), List.of(7L),
                List.of(9L), "p-1", "p-2"));
        assertThat(call.call().get("arguments")).isEqualTo(Map.of("pipeline_uuid", "p-2", "stream_ids", List.of(9L),
                "source_pipeline_uuid", "p-1"));
        // Every step has one.
        for (final Map<String, Object> step : BuildPlan.checklist()) {
            assertThat(BuildPlan.nextCall(String.valueOf(step.get("step")), BuildPlan.CallArgs.of("b", List.of("F"),
                    List.of(1L), List.of(2L), "p", "q")).call()).containsKeys("tool", "arguments");
        }
    }

    @Test
    void everyIndexingCallSaysWhatTheUserDoes() {
        final List<Object[]> cases = new ArrayList<>();
        for (final String processing : new String[]{null, "running", "finished", "disabled"}) {
            cases.add(new Object[]{"indexed", processing});
        }
        for (final String processing : new String[]{null, "running", "disabled"}) {
            cases.add(new Object[]{"processed", processing});
        }
        for (final Object[] each : cases) {
            final String step = (String) each[0];
            final String processing = (String) each[1];
            final BuildPlan.NextCall call = BuildPlan.nextCall(step, new BuildPlan.CallArgs("b", List.of("F"),
                    List.of(1L), List.of(2L), "p", "q", processing, Map.of("uuid", "i", "backend", "lucene"),
                    List.of(), false, null, null, null));
            // The sample's filter is created once the user approves it (asked for by the user); one left disabled is
            // waited on only once the user enables it.
            if (processing == null) {
                assertThat(call.then()).as(step).contains("approves it in a form");
            }
            if ("disabled".equals(processing)) {
                assertThat(call.then()).as(step).contains("enable");
            }
        }
    }

    @Test
    void anIndexIsIndexedOnceVerifiedNotOnceStepped() {
        // Seen in VS Code: stepping the indexing pipeline clean counted as indexed, so the plan led on to promotion
        // with nothing agreed, committed or indexed.
        final Map<String, Object> unagreed = elasticStatus(false, false, List.of());
        final Map<String, Object> state = states(unagreed);
        assertThat(state.get("indexing_pipeline")).isEqualTo("done");
        assertThat(state.get("index_template")).isEqualTo("to do");
        assertThat(state.get("indexed")).isEqualTo("to do");
        final Map<?, ?> next = (Map<?, ?>) unagreed.get("next");
        assertThat(next.get("step")).isEqualTo("index_template");
        final Map<?, ?> call = (Map<?, ?>) next.get("call");
        assertThat(call.get("tool")).isEqualTo("propose_index_template");
        assertThat(((Map<?, ?>) call.get("arguments")).get("pipeline_uuid")).isEqualTo("ix");
        assertThat(String.valueOf(((Map<?, ?>) call.get("arguments")).get("example_template")))
                .startsWith("<the user's example index template");

        // Agreed: the processor filter, then the wait while it runs, then verify_index; done once that passes.
        final List<Map<?, ?>> nexts = new ArrayList<>();
        for (final List<Map<String, Object>> filters : List.of(List.<Map<String, Object>>of(),
                List.of(filter(false, true)), List.of(filter(true, true)), List.of(filter(false, false)))) {
            nexts.add((Map<?, ?>) elasticStatus(false, true, filters).get("next"));
        }
        assertThat(nexts).extracting(n -> (Object) n.get("step")).containsOnly("indexed");
        assertThat(nexts).extracting(n -> (Object) ((Map<?, ?>) n.get("call")).get("tool"))
                .containsExactly("create_processor_filter", "wait_for_processing", "verify_index",
                        "wait_for_processing");
        assertThat(String.valueOf(nexts.get(3).get("then"))).startsWith("only once the user says they enabled its "
                                                                         + "filter");
        final Map<?, ?> verify = (Map<?, ?>) ((Map<?, ?>) nexts.get(2).get("call")).get("arguments");
        assertThat(verify.get("index_uuid")).isEqualTo("ix-doc");
        assertThat(verify.get("backend")).isEqualTo("elasticsearch");

        final Map<String, Object> done = elasticStatus(true, true, List.of(filter(true, true)));
        assertThat(states(done).get("indexed")).isEqualTo("done");
        assertThat(((Map<?, ?>) done.get("next")).get("step")).isEqualTo("index_documented");
    }

    @Test
    void nextSaysWhereThePlanStandsForTheAgentsTaskList() {
        // Seen in testing: the agent's checklist stayed on 'Process samples into Events' while the work went on.
        final List<Map<String, Object>> steps = List.of(step("feed", "done"), step("covered", "not needed"),
                step("processed", "done"), step("validated", "to do"), step("documented", "to do"));
        assertThat(BuildPlan.progress(steps, "validated")).isEqualTo("step 3 of 4, validated; done: feed, processed");
    }

    @Test
    void theCoreToolsCarryTheirPlanStep() {
        assertThat(BuildPlan.onboardingLabel("create_pipeline")).isEqualTo("Onboarding step 5 of 15 (pipeline): ");
        assertThat(BuildPlan.onboardingLabel("start_onboarding")).isEqualTo("Onboarding start: ");
        assertThat(BuildPlan.onboardingLabel("build_status")).isEqualTo("Onboarding, any step: ");
        assertThat(BuildPlan.onboardingLabel("locate_event")).isEmpty();
    }

    @Test
    void aSamplesFormatSaysWhetherItNeedsATextConverter() {
        assertThat(BuildPlan.textFormat(FakeBuildFacts.profileOf("key=value")).get("needs_text_converter"))
                .isEqualTo(true);
        assertThat(BuildPlan.textFormat(FakeBuildFacts.profileOf("xml fragments")).get("needs_text_converter"))
                .isEqualTo(true);
        assertThat(BuildPlan.textFormat(FakeBuildFacts.profileOf("json lines")).get("needs_text_converter"))
                .isEqualTo(false);
        // Lines that aren't JSON: the Data Splitter route, as the JSONParser would stop at the first of them.
        final Map<String, Object> malformed = new LinkedHashMap<>(FakeBuildFacts.profileOf("json lines"));
        malformed.put("malformed_lines", List.of(3));
        assertThat(BuildPlan.textFormat(malformed)).containsEntry("format", "json lines (some not JSON)")
                .containsEntry("needs_text_converter", true);
    }

    @Test
    void theSamplesStepSendsEachFileFromTheUsersDiskNotItsText() {
        // The Stroom MCP server's FINDINGS (0.16.18, VS Code): `next` after create_feed named upload_sample with
        // sample="<one file's text>", and the agent read each file (cut at 2,000 characters) and uploaded its first
        // two records. The plan's call is upload_sample with files=, a command per file for the user's terminal;
        // sample= only for text the user pasted.
        final Map<String, Object> status = plan.status("b", new DocRef("Feed", "f", "ACME"));
        final Map<?, ?> next = (Map<?, ?>) status.get("next");
        assertThat(next.get("step")).isEqualTo("samples");
        assertThat(next.get("call")).isEqualTo(Map.of("tool", "upload_sample", "arguments", Map.of("feed", "ACME",
                "files", List.of("<each sample file's path, as the user's terminal sees it>"))));
        assertThat(String.valueOf(next.get("then")))
                .contains("run each command it gives in the user's terminal")
                .contains("sample= is only for text the user pasted into the chat")
                .contains("From then on give tools stream_ids, not the text");
    }

    @Test
    void aJustCreatedDocCountsThoughTheListingLeavesItOut() {
        // Seen in the plan walk: create_feed's next named the feed step again, inviting a second feed.
        final Map<String, Object> status = plan.status("b", new DocRef("Feed", "f", "ACME"));
        assertThat(states(status).get("feed")).isEqualTo("done");
        assertThat(((Map<?, ?>) status.get("next")).get("step")).isEqualTo("samples");
    }

    // The plan of an Elasticsearch build with an events pipeline, an index doc and an indexing pipeline, both
    // stepped clean, its events processed and documented.
    private Map<String, Object> elasticStatus(final boolean indexingVerified,
                                              final boolean agreed,
                                              final List<Map<String, Object>> filters) {
        facts.docs = List.of(FakeBuildFacts.doc("Feed", "f", "ACME"),
                FakeBuildFacts.doc("TextConverter", "tc", "ACME-CSV"),
                FakeBuildFacts.doc("XSLT", "x", "ACME-Translation"),
                FakeBuildFacts.doc("Pipeline", "ev", "ACME-Events"),
                FakeBuildFacts.doc("Documentation", "d", "ACME-Events"),
                FakeBuildFacts.doc("ElasticIndex", "ix-doc", "ACME-V1"),
                FakeBuildFacts.doc("Pipeline", "ix", "ACME-Indexing"));
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()));
        facts.stages = Map.of("ev", "translation", "ix", "indexing");
        facts.ownRaw = List.of(7L);
        facts.events = List.of(9L);
        facts.profiler = text -> FakeBuildFacts.profileOf("delimited");
        facts.stepped = true;
        facts.validated = true;
        facts.verified = indexingVerified;
        facts.destinations = Map.of("ix", Map.of("index name", "acme-v1", "cluster", "ES"));
        facts.agreementProblem = agreed
                ? null
                : "not agreed";
        facts.filters = filters;
        return plan.status("b", null);
    }

    private BuildPlan planWithNext(final Map<String, Object> next) {
        return new BuildPlan(facts, world.guard(), consent, new BuildMemory()) {
            @Override
            public Optional<Map<String, Object>> nextStep(final String build, final DocRef made) {
                return Optional.ofNullable(next);
            }
        };
    }

    private static Map<String, Object> filter(final boolean finished, final boolean enabled) {
        return Map.of("finished", finished, "enabled", enabled);
    }

    private static Map<String, Object> step(final String name, final String state) {
        return Map.of("step", name, "state", state);
    }

    static Map<String, Object> states(final Map<String, Object> status) {
        final Map<String, Object> states = new LinkedHashMap<>();
        ((List<?>) status.get("steps")).forEach(step -> states.put(String.valueOf(((Map<?, ?>) step).get("step")),
                ((Map<?, ?>) step).get("state")));
        return states;
    }

    private static Map<String, Object> result(final Object... keysAndValues) {
        final Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            result.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return result;
    }

    private static ToolCallContext context() {
        return ToolCallContext.builder(SESSION).build();
    }

    private static ToolCallContext asking(final ElicitResult answer) {
        return ToolCallContext.builder(SESSION).userQuestions(new UserQuestions() {
            @Override
            public boolean canAsk() {
                return true;
            }

            @Override
            public ElicitResult ask(final String toolName,
                                    final String callId,
                                    final String intro,
                                    final Map<String, Object> form) {
                return answer;
            }
        }).build();
    }
}
