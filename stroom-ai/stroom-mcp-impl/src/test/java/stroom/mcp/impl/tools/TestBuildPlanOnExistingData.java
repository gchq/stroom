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
import stroom.security.api.SecurityContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The plan for a build on data already in Stroom: an existing feed's records, a production pipeline's new version
/// or working copy (the Stroom MCP server's `tests/test_existing_data_plan.py`). Seen in VS Code: `next` said
/// create_feed and upload_sample whatever the build was, so the agent uploaded a workspace file though the user chose
/// production streams, and made a new feed to build an indexing pipeline's next version.
class TestBuildPlanOnExistingData {

    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final BuildPlan plan = new BuildPlan(facts, new BuildWorld().guard(),
            new Consent(Mockito.mock(SecurityContext.class), () -> Mockito.mock(AiAuditLog.class)), new BuildMemory());

    @Test
    void anExistingFeedNeedsNoFeedOrUploadBeforeAnyPipeline() {
        facts.sources = List.of("ACME");
        final Map<String, Object> result = status();
        assertThat(next(result).get("step")).isEqualTo("existing data");
        assertThat(result.get("works_from")).isEqualTo(List.of("ACME"));
        assertThat(next(result).get("call")).isEqualTo(Map.of("tool", "stream_meta", "arguments",
                Map.of("feeds", List.of("ACME"))));
        assertThat(String.valueOf(next(result).get("then"))).contains("create no feed and upload nothing");
        assertThat(state(result, "feed")).isEqualTo("not needed");
        assertThat(state(result, "samples")).isEqualTo("not needed");
    }

    @Test
    void aSurveyedFeedGoesOnToTheTranslationNotAFeed() {
        facts.docs = List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"));
        facts.sources = List.of("ACME");
        final Map<String, Object> result = status();
        assertThat(next(result).get("step")).isEqualTo("translation");
        // Processed where it is, into a test feed the server makes (asked for by the user); then indexed, as a new
        // source is.
        assertThat(state(result, "processed")).isEqualTo("to do");
        assertThat(state(result, "index")).isEqualTo("to do");
    }

    @Test
    void aNewVersionOfAnEventsPipelineStepsProductionRecords() {
        facts.docs = List.of(FakeBuildFacts.doc("Pipeline", "ev2", "ACME-V1.1-Events", "version_of", "prod-ev"),
                FakeBuildFacts.doc("XSLT", "x2", "ACME-V1.1-Events"));
        facts.sources = List.of("ACME");
        facts.stages = Map.of("ev2", "translation");
        facts.mappings = Map.of("x2", FakeBuildFacts.kept("translation", Map.of()));
        final Map<String, Object> result = status();
        assertThat(next(result).get("step")).isEqualTo("stepped");
        assertThat(call(result).get("tool")).isEqualTo("step_sample");
        assertThat(arguments(result).get("stream_ids"))
                .isEqualTo("<recent raw streams of ACME (stream_meta feeds=['ACME'])>");
        // Stepped clean: documented next, then promotion; nothing about processing a feed of its own or indexing.
        facts.stepped = true;
        final Map<String, Object> done = status();
        assertThat(next(done).get("step")).isEqualTo("documented");
        assertThat(state(done, "index_documented")).isEqualTo("not needed");
    }

    @Test
    void aWorkingCopyIsDocumentedUnderTheProductionPipelinesName() {
        facts.docs = List.of(FakeBuildFacts.doc("Pipeline", "wc", "ACME-V1.0-Events copy", "working_copy_of",
                        "prod-ev"), FakeBuildFacts.doc("XSLT", "x", "X"),
                FakeBuildFacts.doc("Documentation", "d", "ACME-V1.0-Events"));
        facts.names = Map.of("prod-ev", "ACME-V1.0-Events");
        facts.stages = Map.of("wc", "translation");
        facts.stepped = true;
        assertThat(next(status()).get("step")).isEqualTo("promoted");
    }

    @Test
    void aNewIndexingVersionNeedsNoFeedAndNoEventsPipeline() {
        facts.docs = List.of(FakeBuildFacts.doc("Pipeline", "ix2", "ACME-Indexing-v2", "version_of", "ix1"),
                FakeBuildFacts.doc("XSLT", "ix2-x", "ACME-v2-XSLT"));
        facts.stages = Map.of("ix2", "indexing");
        facts.mappings = Map.of("ix2-x", FakeBuildFacts.kept("index", Map.of()));
        facts.stepped = true;
        final Map<String, Object> result = status();
        assertThat(state(result, "feed")).isEqualTo("not needed");
        assertThat(state(result, "translation")).isEqualTo("not needed");
        assertThat(state(result, "documented")).isEqualTo("not needed");
        assertThat(state(result, "index")).isEqualTo("done");
        assertThat(next(result).get("step")).isEqualTo("indexed");
        assertThat(call(result).get("tool")).isEqualTo("create_processor_filter");
        assertThat(String.valueOf(arguments(result).get("stream_ids"))).startsWith("<recent Events streams from");
    }

    @Test
    void aTestFeedInSuchABuildBringsProcessingBack() {
        facts.docs = List.of(FakeBuildFacts.doc("Feed", "f", "ACME-COPY"),
                FakeBuildFacts.doc("Pipeline", "ev2", "ACME-V1.1-Events", "version_of", "prod-ev"),
                FakeBuildFacts.doc("XSLT", "x2", "X"));
        facts.sources = List.of("ACME");
        facts.stages = Map.of("ev2", "translation");
        facts.mappings = Map.of("x2", FakeBuildFacts.kept("translation", Map.of()));
        facts.stepped = true;
        facts.ownRaw = List.of(7L);
        assertThat(next(status()).get("step")).isEqualTo("processed");
    }

    @Test
    void aNewSourceStillStartsWithItsFeed() {
        assertThat(next(status()).get("step")).isEqualTo("feed");
    }

    @Test
    void anOnboardedExistingFeedGoesOnToIndexingAfterItsDocumentation() {
        // Seen in Ask Stroom AI: the user asked for an index, and after the events pipeline's documentation, and
        // again after save_xslt index_plan=, next said "step 9 of 9, promoted": a build on an existing feed counted
        // indexing only once it held an index doc or an indexing pipeline.
        final List<Map<String, Object>> docs = List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"),
                FakeBuildFacts.doc("Pipeline", "ev", "ACME-Events"), FakeBuildFacts.doc("XSLT", "x", "ACME-Events"),
                FakeBuildFacts.doc("Documentation", "d", "ACME-Events"));
        facts.docs = docs;
        facts.sources = List.of("ACME");
        facts.stages = Map.of("ev", "translation");
        facts.stepped = true;
        facts.survey = survey(true, null, List.of(5L), null, null);
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()));
        final Map<String, Object> result = status();
        for (final String step : List.of("index", "indexing_pipeline", "indexed", "index_documented")) {
            assertThat(state(result, step)).as(step).isEqualTo("to do");
        }
        // Until the backend is known to be Elasticsearch.
        assertThat(state(result, "index_template")).isEqualTo("not needed");
        assertThat(String.valueOf(next(result).get("progress"))).startsWith("step 5 of 12, processed");
        final List<Map<String, Object>> withPlan = new ArrayList<>(docs);
        withPlan.add(FakeBuildFacts.doc("XSLT", "ixx", "ACME - Indexing"));
        facts.docs = withPlan;
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()),
                "ixx", FakeBuildFacts.kept("index", Map.of()));
        assertThat(state(status(), "index")).isEqualTo("to do");
    }

    @Test
    void indexingAnExistingFeedsEventsCountsFromItsIndexingXslt() {
        // save_xslt index_plan= before the index doc (as the agent did): the indexing steps are counted from then.
        facts.docs = List.of(FakeBuildFacts.doc("XSLT", "ixx", "ACME - Indexing"));
        facts.sources = List.of("ACME-EVENTS");
        facts.mappings = Map.of("ixx", FakeBuildFacts.kept("index", Map.of()));
        final Map<String, Object> result = status();
        assertThat(next(result).get("step")).isEqualTo("index");
        assertThat(state(result, "indexed")).isEqualTo("to do");
    }

    @Test
    void aSurveyedFeedWithMoreLocationsThanNextCanGiveIsSteppedOverItsStreams() {
        // Seen in Ask Stroom AI: 66 locations, and create_pipeline's next said step_records with a placeholder for
        // them, while the plan's step says step_sample.
        surveyedFeed();
        facts.survey = survey(true, null, List.of(5L), null, null);
        assertThat(call(status())).isEqualTo(Map.of("tool", "step_sample", "arguments", Map.of("pipeline_uuid",
                "ev", "stream_ids", List.of(5L))));
    }

    @Test
    void aNewSourcesSurveyOfItsOwnFeedKeepsIndexingToDo() {
        // Seen: onboarding surveyed its sample with the build, and the plan took it for an existing feed and skipped
        // indexing. sourceFeeds leaves the build's own feed out, so nothing makes this build one on existing data.
        facts.docs = List.of(FakeBuildFacts.doc("Feed", "f", "ACME"),
                FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"),
                FakeBuildFacts.doc("Pipeline", "ev", "ACME-Events"), FakeBuildFacts.doc("XSLT", "x", "ACME-Events"),
                FakeBuildFacts.doc("Documentation", "d", "ACME-Events"));
        facts.stages = Map.of("ev", "translation");
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()));
        facts.stepped = true;
        facts.ownRaw = List.of(7L);
        final Map<String, Object> result = status();
        for (final String step : List.of("index", "indexing_pipeline", "indexed")) {
            assertThat(state(result, step)).as(step).isEqualTo("to do");
        }
        assertThat(state(result, "covered")).isEqualTo("not needed");
        assertThat(state(result, "cef_pipeline")).isEqualTo("not needed");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aCefBuildGoesFromItsMappingToItsPipelineAndPromotion(final boolean fromFeed) {
        // CEF output had no steps: next never reached promotion, and without start_build feeds= it said create_feed.
        facts.sources = fromFeed
                ? List.of("ACME-EVENTS")
                : List.of();
        facts.docs = List.of(FakeBuildFacts.doc("XSLT", "c", "ACME-CEF"));
        facts.mappings = Map.of("c", FakeBuildFacts.kept("cef", Map.of()));
        final Map<String, Object> result = status();
        assertThat(state(result, "cef_mapping")).isEqualTo("done");
        assertThat(next(result).get("step")).isEqualTo("cef_pipeline");
        for (final String step : List.of("feed", "translation", "index")) {
            assertThat(state(result, step)).as(step).isEqualTo("not needed");
        }
        facts.docs = List.of(FakeBuildFacts.doc("XSLT", "c", "ACME-CEF"),
                FakeBuildFacts.doc("Pipeline", "cp", "ACME-CEF"), FakeBuildFacts.doc("Documentation", "d", "ACME-CEF"));
        facts.stages = Map.of("cp", "forwarding");
        facts.stepped = true;
        assertThat(next(status()).get("step")).isEqualTo("promoted");
    }

    @Test
    void aDiscoveryBuildIsKnownByItsPlanWhateverItsParser() {
        // An XML discovery pipeline from an XMLParser indexing template reads as indexing: its plan says discovery.
        facts.docs = List.of(FakeBuildFacts.doc("Feed", "f", "RAW"), FakeBuildFacts.doc("Pipeline", "dp",
                "RAW-Discovery"), FakeBuildFacts.doc("XSLT", "dx", "RAW-Discovery"));
        facts.stages = Map.of("dp", "indexing");
        facts.mappings = Map.of("dx", FakeBuildFacts.kept("index", Map.of("discovery", Map.of("input", "xml"))));
        facts.stepped = true;
        facts.ownRaw = List.of(7L);
        final Map<String, Object> result = status();
        assertThat(state(result, "translation")).isEqualTo("not needed");
        assertThat(state(result, "processed")).isEqualTo("not needed");
        assertThat(next(result).get("step")).isEqualTo("index_template");
        // Raw streams: refused with one.
        final BuildPlan.NextCall call = BuildPlan.nextCall("indexed", BuildPlan.CallArgs.of("b", List.of("RAW"),
                List.of(1L), List.of(1L), null, "dp").withDiscovery(true));
        assertThat(((Map<?, ?>) call.call().get("arguments")).containsKey("source_pipeline_uuid")).isFalse();
    }

    @Test
    void anExistingFeedIsSteppedWhereItIsThenSurveyedUntilCovered() {
        final List<Map<String, Object>> locations = List.of(Map.of("stream", 5, "part", 0, "record", 3));
        surveyedFeed();
        facts.survey = survey(false, locations, null, null, null);
        assertThat(call(status())).isEqualTo(Map.of("tool", "step_records", "arguments", Map.of("pipeline_uuid",
                "ev", "locations", locations)));
        facts.stepped = true;
        final Map<String, Object> stepped = status();
        assertThat(next(stepped).get("step")).isEqualTo("covered");
        assertThat(call(stepped)).isEqualTo(Map.of("tool", "survey_feed", "arguments", Map.of("feed", "ACME",
                "build", "b")));
        facts.survey = survey(true, locations, List.of(5L), null, null);
        final Map<String, Object> covered = status();
        // Then the surveyed streams are processed where they are (their Events go to the build's test feed).
        assertThat(next(covered).get("step")).isEqualTo("processed");
        assertThat(call(covered)).isEqualTo(Map.of("tool", "create_processor_filter", "arguments",
                Map.of("pipeline_uuid", "ev", "stream_ids", List.of(5L))));
    }

    @Test
    void indexingAnExistingFeedsEventsNamesThemAndTheirPipeline() {
        facts.docs = List.of(FakeBuildFacts.doc("Index", "i", "acme-v1"));
        facts.sources = List.of("ACME-EVENTS");
        final Map<String, Object> result = status();
        assertThat(next(result).get("step")).isEqualTo("indexing_pipeline");
        assertThat(String.valueOf(next(result).get("then"))).contains("events_stream_ids=");
        facts.docs = List.of(FakeBuildFacts.doc("Index", "i", "acme-v1"),
                FakeBuildFacts.doc("Pipeline", "ix", "ACME-Indexing"), FakeBuildFacts.doc("XSLT", "ixx",
                        "ACME-Indexing"));
        facts.stages = Map.of("ix", "indexing");
        facts.mappings = Map.of("ixx", FakeBuildFacts.kept("index", Map.of()));
        facts.stepped = true;
        final Map<String, Object> indexed = status();
        assertThat(next(indexed).get("step")).isEqualTo("indexed");
        assertThat(String.valueOf(arguments(indexed).get("source_pipeline_uuid")))
                .startsWith("<the events pipeline that wrote");
    }

    @Test
    void aWorkingCopyOfAnIndexingPipelineIsNotIndexedInTheBuild() {
        // A change upstream reaches it: stepped clean in the build; indexing production Events is the user's. Its
        // documentation is the production pipeline's, under its name.
        facts.docs = List.of(FakeBuildFacts.doc("Pipeline", "wc", "ACME-Indexing copy", "working_copy_of", "prod-ix"),
                FakeBuildFacts.doc("XSLT", "x", "X"), FakeBuildFacts.doc("Documentation", "d", "prod-ix"));
        facts.sources = List.of("ACME");
        facts.stages = Map.of("wc", "indexing");
        facts.mappings = Map.of("x", FakeBuildFacts.kept("index", Map.of()));
        facts.stepped = true;
        final Map<String, Object> result = status();
        assertThat(state(result, "indexed")).isEqualTo("done");
        assertThat(state(result, "index_documented")).isEqualTo("done");
    }

    @Test
    void aHandEditedTranslationIsReadBackBeforeItIsDocumented() {
        // Code a mapping can't express, saved with save_xslt: the docs stayed stale and next said write_documentation
        // again and again.
        facts.docs = List.of(FakeBuildFacts.doc("Pipeline", "ev", "ACME-V1.1-Events", "version_of", "prod-ev"),
                FakeBuildFacts.doc("XSLT", "x", "X"));
        facts.sources = List.of("ACME");
        facts.stages = Map.of("ev", "translation");
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()));
        facts.stepped = true;
        facts.keptXslt = "x";
        facts.checks = List.of("Pipeline 'ACME-V1.1-Events': its XSLT differs from what its mapping generates: ...");
        final Map<String, Object> result = status();
        assertThat(next(result).get("step")).isEqualTo("documented");
        assertThat(call(result)).isEqualTo(Map.of("tool", "rebuild_mapping", "arguments", Map.of("uuid", "x",
                "pipeline_uuid", "ev")));
    }

    @Test
    void anExistingFeedsFormatIsReadFromItsSurvey() {
        // Seen (e2e_existing_feed): with no raw stream of its own, the plan profiled the survey's examples and
        // failed on a key the profile doesn't have.
        surveyedFeed();
        facts.survey = survey(false, null, List.of(5L), "time,user,action\n10:00,alice,login\n", null);
        facts.profiler = text -> FakeBuildFacts.profileOf("delimited");
        // Delimited text needs a Data Splitter.
        assertThat(state(status(), "converter")).isEqualTo("to do");
    }

    @Test
    void aJsonFeedsLongRecordsCutInItsSurveyDocNeedNoConverter() {
        // Seen in testing: JSON lines of 4,720 characters, kept cut at 4,000 in the survey doc, joined with the others
        // read as text, and build_status asked for a Data Splitter (step 1 of 14) all through the build.
        final String cut = jsonLine(2, 4720).substring(0, StroomBuildFacts.MAX_EXAMPLE_CHARS);
        final Map<String, Object> kept = new LinkedHashMap<>();
        kept.put("saturated", true);
        kept.put("format", "json lines");
        kept.put("shapes", Map.of("a", Map.of("examples", List.of(
                Map.of("text", jsonLine(1, 0), "location", Map.of("stream", 5, "part", 0, "record", 1)),
                Map.of("text", cut, "location", Map.of("stream", 5, "part", 0, "record", 2)),
                Map.of("text", jsonLine(3, 0), "location", Map.of("stream", 5, "part", 0, "record", 3))))));
        final Map<String, Object> survey = StroomBuildFacts.combine(List.of(kept)).orElseThrow();
        assertThat(String.valueOf(survey.get("sample"))).doesNotContain(cut);
        assertThat(String.valueOf(survey.get("sample")).lines()).hasSize(2);
        surveyedFeed();
        facts.survey = survey;
        facts.profiler = text -> FakeBuildFacts.profileOf(text.startsWith("{")
                ? "json lines"
                : "unknown text");
        final Map<String, Object> result = status();
        assertThat(((Map<?, ?>) result.get("sample")).get("format")).isEqualTo("json lines");
        assertThat(state(result, "converter")).isEqualTo("not needed");
        assertThat(next(result).get("step")).isNotEqualTo("converter");
    }

    @Test
    void withoutWholeExamplesTheSurveysOwnFormatStands() {
        // Every example cut (none left), or examples that don't profile together: build_status failed on them.
        for (final String sample : new String[]{null, "{\"id\": 1, \"detail\": \"cut sh\n{\"id\": 2}"}) {
            surveyedFeed();
            facts.survey = survey(true, null, List.of(5L), sample, "json lines");
            facts.profiler = text -> {
                throw new ToolRefusal("The samples don't profile together");
            };
            final Map<String, Object> result = status();
            assertThat(((Map<?, ?>) result.get("sample")).get("format")).isEqualTo("json lines");
            assertThat(state(result, "converter")).isEqualTo("not needed");
        }
    }

    @Test
    void anEventsPipelineReadingJsonItselfNeedsNoConverter() {
        // Whatever the examples read as: the pipeline's JSON parser, stepped clean, is what reads the feed.
        surveyedFeed();
        facts.survey = survey(false, null, List.of(5L), "time,user,action\n10:00,alice,login\n", null);
        facts.profiler = text -> FakeBuildFacts.profileOf("delimited");
        facts.parsers = Map.of("ev", "JSONParser");
        assertThat(state(status(), "converter")).isEqualTo("not needed");
        facts.parsers = Map.of("ev", "DSParser");
        assertThat(state(status(), "converter")).isEqualTo("to do");
    }

    @Test
    void anExistingFeedsCurrentTranslationIsWhereItsNextVersionStarts() {
        // Seen in testing: asked to onboard a feed "using the current pipeline as a reference", the agent surveyed
        // and drafted anew, losing the current XSLT's lookups, with a 50 KB mapping to send in one call.
        facts.sources = List.of("FG");
        facts.translating = List.of(Map.of("name", "FG-V1.2-Events", "uuid", "prod-ev"));
        Map<String, Object> result = status();
        assertThat(next(result).get("translated_by")).isEqualTo(List.of(Map.of("name", "FG-V1.2-Events", "uuid",
                "prod-ev")));
        final String then = String.valueOf(next(result).get("then"));
        assertThat(then).startsWith("FG is translated now by 'FG-V1.2-Events' (prod-ev). For its next version")
                .contains("copy_pipeline source_uuid=<it> rename={'V1.2': 'V1.3'}")
                .contains("build_translation_xslt uuid=<the copy's XSLT> changes= only what changes")
                .contains("create no feed and upload nothing");
        // A feed nothing translates yet: the plan as it was.
        facts.translating = List.of();
        result = status();
        assertThat(next(result).containsKey("translated_by")).isFalse();
        assertThat(String.valueOf(next(result).get("then"))).startsWith("this build works from");
    }

    private void surveyedFeed() {
        facts.docs = List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"),
                FakeBuildFacts.doc("Pipeline", "ev", "ACME-Events"), FakeBuildFacts.doc("XSLT", "x", "X"));
        facts.sources = List.of("ACME");
        facts.stages = Map.of("ev", "translation");
        facts.mappings = Map.of("x", FakeBuildFacts.kept("translation", Map.of()));
    }

    private static Map<String, Object> survey(final boolean saturated,
                                              final Object locations,
                                              final List<Long> streams,
                                              final String sample,
                                              final String format) {
        final Map<String, Object> survey = new LinkedHashMap<>();
        survey.put("saturated", saturated);
        survey.put("locations", locations);
        survey.put("streams", streams);
        survey.put("sample", sample);
        survey.put("format", format);
        return survey;
    }

    private static String jsonLine(final int n, final int size) {
        return "{\"id\": " + n + ", \"action\": \"login\", \"detail\": \"" + "x".repeat(size) + "\"}";
    }

    private Map<String, Object> status() {
        return plan.status("b", null);
    }

    private static Map<?, ?> next(final Map<String, Object> result) {
        return (Map<?, ?>) result.get("next");
    }

    private static Map<?, ?> call(final Map<String, Object> result) {
        return (Map<?, ?>) next(result).get("call");
    }

    private static Map<?, ?> arguments(final Map<String, Object> result) {
        return (Map<?, ?>) call(result).get("arguments");
    }

    private static Object state(final Map<String, Object> result, final String step) {
        return TestBuildPlan.states(result).get(step);
    }
}
