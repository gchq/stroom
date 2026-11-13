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

import stroom.ai.impl.tools.generation.DispatcherXslt;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.data.store.api.Source;
import stroom.data.store.api.SourceUtil;
import stroom.data.store.api.Store;
import stroom.mcp.impl.McpTestClient;
import stroom.security.api.SecurityContext;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed's pipelines and promotion through the REAL tools on a real Stroom, on the synthetic PEGA
/// sample under `aggregated/`:
///
/// * a group placed in a pipeline of its own, writing to an output feed of its own: its own dispatcher handles only
///   its group, and the feed's pipeline passes its records over as handled by that pipeline, counted, never warned
///   of; a second build on the feed is refused while this one is open;
/// * a later build adding a group to the feed's promoted pipeline and changing a promoted group: it works on working
///   copies (the dispatcher's and the group's, its stepping running them), and promotion writes them back with the
///   dispatcher regenerated against the originals' UUIDs, the new group beside the feed.
@SuppressWarnings("unchecked")
class TestE2eSourceTypePipelines extends AbstractE2eTest {

    private static final String SECURITY = "PEGA security";
    private static final String INTEGRATION = "PEGA integration";
    private static final String OWN_PIPELINE = "A pipeline of its own";
    private static final String OWN_FEED = "To an output feed of its own";

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void aGroupInAPipelineOfItsOwnWritesToItsOwnFeedAndTheFeedsPipelinePassesItOver() {
        final String feed = "E2E-PEGA-OWN";
        final String build = "pega-own";
        createFeed("pega-own-src", feed);
        final String text = TestE2eSourceTypeGroups.lines("pega/pega-1.json", 1500);
        final long stream = upload(feed, text);
        json("start_build", args("build", build, "feeds", List.of(feed)));
        // The integration group goes in a pipeline of its own, to an output feed of its own.
        client.answeringWith(answering(List.of("PegaSecurityEvent", "PegaIntegration"), INTEGRATION));
        json("survey_feed", args("feed", feed, "build", build));
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed,
                "system_name", "PEGA", "environment", "Prod"));
        json("record_source_types", args("build", build, "feed", feed, "system_name", "PEGA", "environment",
                "Prod", "reviewed", shown.get("review_id")));

        // One open build per aggregated feed: a second is refused, naming this one.
        assertThat(refusal("start_build", args("build", "pega-own-2", "feeds", List.of(feed))))
                .contains("build '" + build + "' is open on it");

        final Map<String, Object> security = group(build, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.securityMapping(), stream);
        final Map<String, Object> integration = group(build, feed, INTEGRATION, "PegaIntegration",
                TestE2eSourceTypeGroups.integrationMapping(), stream);
        assertThat(map(security, "pipeline")).containsEntry("own", false);
        assertThat(map(integration, "pipeline")).containsEntry("own", true);
        assertThat(String.valueOf(integration.get("group_next"))).contains("create_feed", "create_pipeline");
        final String mainDispatcher = uuidOf(map(security, "dispatcher"));
        final String ownDispatcher = uuidOf(map(integration, "dispatcher"));
        assertThat(ownDispatcher).isNotEqualTo(mainDispatcher);
        assertThat(readXslt(mainDispatcher).getData()).doesNotContain("mode=\"pega_integration\"");
        assertThat(readXslt(ownDispatcher).getData()).contains("mode=\"pega_integration\"")
                .doesNotContain("mode=\"pega_security\"");

        // Its pipeline of its own can't be made before its output feed; the build makes that in a form.
        final String template = templateUuid("translation", "Event Data (JSON)");
        assertThat(refusal("create_pipeline", args("build", build, "name", feed + "-Integration-Events",
                "template_uuid", template, "set_properties", List.of(docProperty("translationFilter", "xslt",
                        ownDispatcher, "XSLT"))))).contains("output feed", "create_feed");
        final String outputFeed = feed + "-INTEGRATION-EVENTS";
        final Map<String, Object> made = agreed("create_feed", args("build", build, "name", outputFeed,
                "stream_type", "Events", "group", INTEGRATION));
        assertThat(strings(made, "source_types")).containsExactly("PegaIntegration");

        // The feed's pipeline: two dispatchers in the build, so it names its own.
        assertThat(refusal("create_pipeline", args("build", build, "name", feed + "-Events", "template_uuid",
                template))).contains(feed + " dispatcher", feed + " " + INTEGRATION + " dispatcher");
        final String main = uuidOf(agreed("create_pipeline", args("build", build, "name", feed + "-Events",
                "template_uuid", template, "set_properties", List.of(docProperty("translationFilter", "xslt",
                        mainDispatcher, "XSLT")))));
        final Map<String, Object> ownMade = agreed("create_pipeline", args("build", build, "name",
                feed + "-Integration-Events", "template_uuid", template, "set_properties",
                List.of(docProperty("translationFilter", "xslt", ownDispatcher, "XSLT"))));
        dump("create_pipeline own", ownMade);
        assertThat(strings(ownMade, "source_types")).containsExactly("PegaIntegration");
        assertThat(String.valueOf(ownMade.get("filled_from_build"))).contains(".feed = " + outputFeed);
        final String own = uuidOf(ownMade);
        // Each dispatcher says which pipeline handles the other's records.
        assertThat(readXslt(mainDispatcher).getData()).contains("pipeline '" + feed + "-Integration-Events'");
        assertThat(readXslt(ownDispatcher).getData()).contains("pipeline '" + feed + "-Events'");
        final Map<String, Map<String, Object>> rows = rows(uuidOf(map(integration, "registry")));
        assertThat((Map<String, Object>) rows.get("PegaIntegration").get("pipeline"))
                .containsEntry("name", feed + "-Integration-Events");
        assertThat(rows.get("PegaIntegration")).containsEntry("output_feed", outputFeed);
        assertThat((Map<String, Object>) rows.get("PegaSecurityEvent").get("pipeline"))
                .containsEntry("name", feed + "-Events");

        final String mainCode = readXslt(mainDispatcher).getData();
        final Map<String, Object> mainStepped = stepSample(main, List.of(stream));
        dump("step_sample main", mainStepped);
        assertThat(mainStepped).as("main stepped clean: %s", mainStepped).containsEntry("verdict", "clean")
                .doesNotContainKey("record");
        final Map<String, Object> ownStepped = stepSample(own, List.of(stream));
        dump("step_sample own", ownStepped);
        assertThat(ownStepped).as("own stepped clean: %s", ownStepped).containsEntry("verdict", "clean")
                .doesNotContainKey("record");
        assertThat(readXslt(mainDispatcher).getData()).as("the feed's dispatcher unchanged by stepping")
                .isEqualTo(mainCode);
        // Each pipeline's clean step of its current code is recorded, whichever was stepped last.
        assertThat(json("build_status", args("build", build)).toString()).doesNotContain(
                "Pipeline '" + feed + "-Events': no clean step");
        final List<String> records = text.lines().toList();
        final long securityRecords = records.stream().filter(line -> line.contains(
                "\"sourcetype\": \"PegaSecurityEvent\"")).count();
        final long integrationRecords = records.stream().filter(line -> line.contains(
                "\"sourcetype\": \"PegaIntegration\"")).count();

        // The feed's pipeline: the security group's Events; the integration records passed over as handled by the
        // group's own pipeline, counted, with no warning.
        final Map<String, Object> mainDone = list(process(main, List.of(stream)), "streams").getFirst();
        final List<Long> mainEvents = ids(mainDone, "events");
        assertThat(mainEvents).hasSize(1);
        assertThat(TestE2eSourceTypeGroups.count(streamText(mainEvents.getFirst()), "<Event>"))
                .isEqualTo(securityRecords);
        final String mainLog = streamText(ids(mainDone, "errors").getFirst());
        assertThat(handled(mainLog, "PegaIntegration", feed + "-Integration-Events")).isEqualTo(integrationRecords);
        assertThat(mainLog).doesNotContain("WARN " + DispatcherXslt.NEW_SOURCE_TYPE);
        assertThat(mainLog.lines().filter(line -> line.contains("PegaIntegration") && line.contains("WARN")))
                .isEmpty();

        // The group's own pipeline: its Events in its own output feed; the security records handled elsewhere.
        final Map<String, Object> ownDone = list(process(own, List.of(stream)), "streams").getFirst();
        final List<Long> ownEvents = ids(ownDone, "events");
        assertThat(ownEvents).hasSize(1);
        assertThat(meta(ownEvents.getFirst()).getFeedName()).isEqualTo(outputFeed);
        assertThat(TestE2eSourceTypeGroups.count(streamText(ownEvents.getFirst()), "<Event>"))
                .isEqualTo(integrationRecords);
        final String ownLog = streamText(ids(ownDone, "errors").getFirst());
        assertThat(handled(ownLog, "PegaSecurityEvent", feed + "-Events")).isEqualTo(securityRecords);
    }

    @Test
    void aLaterBuildWorksOnWorkingCopiesAndPromotionRegeneratesTheDispatcherAgainstTheOriginals() {
        final String feed = "E2E-PEGA-WC";
        final String folder = "E2E Feeds/PegaWc";
        createFeed("pega-wc-src", feed);
        final String text = TestE2eSourceTypeGroups.lines("pega/pega-1.json", 1500);
        final long stream = upload(feed, text);
        promote("pega-wc-src", Map.of("Feed", folder));

        // The first build: the security group in the feed's pipeline, promoted beside the feed.
        final String first = "pega-wc-1";
        json("start_build", args("build", first, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of("PegaSecurityEvent"), null));
        json("survey_feed", args("feed", feed, "build", first));
        recordSourceTypes(first, feed);
        final Map<String, Object> security = group(first, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.securityMapping(), stream);
        final String securityUuid = uuidOf(map(security, "xslt"));
        final String dispatcherUuid = uuidOf(map(security, "dispatcher"));
        final String pipelineName = feed + "-Events";
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", first, "name", pipelineName,
                "template_uuid", templateUuid("translation", "Event Data (JSON)"))));
        promote(first, Map.of("Pipeline", folder));
        // The modules beside the feed, with the registry.
        assertThat(folderOf(securityUuid)).isEqualTo(folderOf(dispatcherUuid)).endsWith("PegaWc");
        assertThat(documentsIn("System/" + folder, "XSLT", feed + " common")).hasSize(1);
        assertThat(documentsIn("System/" + folder, "XSLT", feed + " helpers")).hasSize(1);

        // A later build: the integration group, and the security group changed.
        final String later = "pega-wc-2";
        json("start_build", args("build", later, "feeds", List.of(feed)));
        final int formsBefore = client.forms().size();
        client.answeringWith(answering(List.of("PegaIntegration"), null));
        json("survey_feed", args("feed", feed, "build", later));
        recordSourceTypes(later, feed);
        final Map<String, Object> integration = group(later, feed, INTEGRATION, "PegaIntegration",
                TestE2eSourceTypeGroups.integrationMapping(), stream);
        // Asked with the feed's pipeline in production the default, and a new version of it offered.
        final ElicitFormRequest asked = formsSince(formsBefore).stream()
                .filter(form -> form.message().contains("'" + INTEGRATION + "'"))
                .findFirst()
                .orElseThrow();
        final List<?> options = (List<?>) ((Map<?, ?>) ((Map<?, ?>) asked.requestedSchema().get("properties"))
                .get("choice")).get("enum");
        assertThat(options).hasSize(3);
        assertThat(String.valueOf(options.getFirst())).contains("The feed's pipeline, " + pipelineName);
        assertThat(String.valueOf(options.getLast())).contains("A new version of " + pipelineName);
        final String integrationUuid = uuidOf(map(integration, "xslt"));
        // The dispatcher is changed through a working copy, importing the promoted security group.
        final String dispatcherCopy = uuidOf(map(integration, "dispatcher"));
        assertThat(dispatcherCopy).isNotEqualTo(dispatcherUuid);
        assertThat(readXslt(dispatcherCopy).getData()).contains("uuid='" + securityUuid + "'",
                "uuid='" + integrationUuid + "'");

        // The security group changed: a working copy of it, which the build's dispatcher imports.
        final Map<String, Object> changed = TestE2eSourceTypeGroups.securityMapping();
        final String changedText = jsonOf(changed).replace("\"Login\"", "\"LoginV2\"");
        final Map<String, Object> securityAgain = group(later, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.jsonValue(changedText), stream);
        final String securityCopy = uuidOf(map(securityAgain, "xslt"));
        assertThat(securityCopy).isNotEqualTo(securityUuid);
        assertThat(readXslt(securityCopy).getData()).contains("LoginV2");
        assertThat(readXslt(dispatcherCopy).getData()).contains("uuid='" + securityCopy + "'")
                .doesNotContain("uuid='" + securityUuid + "'");
        assertThat(readXslt(securityUuid).getData()).doesNotContain("LoginV2");

        // The feed's pipeline stepped in the build through a working copy of it: its dispatcher the build's.
        final Map<String, Object> copied = agreed("copy_pipeline", args("build", later, "source_uuid", pipeline,
                "new_name", pipelineName, "working_copy", true));
        final String workingPipeline = uuidOf(copied);
        assertThat(String.valueOf(copied.get("copied_documents"))).contains(dispatcherCopy);
        final List<String> records = text.lines().toList();
        final int loginAt = TestE2eSourceTypeGroups.indexOf(records, "\\\"eventType\\\": \\\"login\\\"", null);
        final int soapAt = TestE2eSourceTypeGroups.indexOf(records, "\"PegaIntegration\"", "Envelope>\"}");
        assertThat(translationOutput(workingPipeline, stream, loginAt)).contains("LoginV2");
        assertThat(children(parse(translationOutput(workingPipeline, stream, soapAt)).getDocumentElement(),
                "Event")).hasSize(1);

        // Promoted: the working copies written back, the dispatcher importing the originals, the new group beside
        // the feed, and the registry naming the originals.
        promote(later, Map.of());
        final String dispatcher = readXslt(dispatcherUuid).getData();
        assertThat(dispatcher).contains("uuid='" + securityUuid + "'", "uuid='" + integrationUuid + "'",
                "mode=\"pega_integration\"").doesNotContain(securityCopy, dispatcherCopy);
        assertThat(readXslt(securityUuid).getData()).contains("LoginV2");
        assertThat(folderOf(integrationUuid)).isEqualTo(folderOf(dispatcherUuid));
        final Map<String, Object> registry = SourceTypeRegistry.read(documentation(documentsIn("System/" + folder,
                "Documentation", feed + SourceTypeRegistry.SUFFIX).getFirst().getUuid()));
        assertThat(jsonOf(registry)).contains(securityUuid, integrationUuid, dispatcherUuid)
                .doesNotContain(securityCopy, dispatcherCopy);
        // Production now translates the new group: the feed's pipeline, as promoted.
        assertThat(children(parse(translationOutput(pipeline, stream, soapAt)).getDocumentElement(), "Event"))
                .hasSize(1);
        assertThat(translationOutput(pipeline, stream, loginAt)).contains("LoginV2");
    }

    // build_translation_xslt group=: the group saved with its mapping, its pipeline as the user chose it.
    private Map<String, Object> group(final String build,
                                      final String feed,
                                      final String name,
                                      final String sourceType,
                                      final Map<String, Object> mapping,
                                      final long stream) {
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", build, "feeds",
                List.of(feed), "group", name, "source_types", List.of(sourceType), "schema_version", VERSION,
                "stream_ids", List.of(stream), "mapping", mapping));
        dump("build_translation_xslt " + name, saved);
        assertThat(saved).as("group %s saved: %s", name, saved.get("problems")).containsEntry("ok", true);
        return map(saved, "group");
    }

    // record_source_types: the table, then the scope form.
    private void recordSourceTypes(final String build, final String feed) {
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed));
        json("record_source_types", args("build", build, "feed", feed, "reviewed", shown.get("review_id")));
    }

    // The registry's rows by source type.
    private Map<String, Map<String, Object>> rows(final String registryUuid) {
        final Map<String, Object> registry = SourceTypeRegistry.read(documentation(registryUuid));
        final Map<String, Map<String, Object>> rows = new LinkedHashMap<>();
        list(registry, "rows").forEach(row -> rows.put((String) row.get("name"), row));
        return rows;
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees in the form.
    private void promote(final String build, final Map<String, Object> destinations) {
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                destinations, "reviewed", review.get("review_id")));
        dump("promote_build " + build, promoted);
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
    }

    // The records of a source type the dispatcher's lines say it passed over as another pipeline's, added up.
    private static long handled(final String logged, final String sourceType, final String pipeline) {
        final Matcher matcher = Pattern.compile(Pattern.quote(DispatcherXslt.PASSED_OVER) + "(\\d+)"
                                                + Pattern.quote(DispatcherXslt.PASSED_OVER_OF + sourceType
                                                                + DispatcherXslt.HANDLED_BY + "pipeline '" + pipeline
                                                                + "'") + "$", Pattern.MULTILINE).matcher(logged);
        long total = 0;
        while (matcher.find()) {
            total += Long.parseLong(matcher.group(1));
        }
        return total;
    }

    // A stream's text, as Stroom stores it.
    private String streamText(final long id) {
        return securityContext.asProcessingUserResult(() -> {
            try (final Source source = streamStore.openSource(id)) {
                return SourceUtil.readString(source);
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    // A user who chooses these source types for the build's scope, places the group named (if any) in a pipeline of
    // its own writing to an output feed of its own, and agrees to everything else (a new group in the feed's
    // pipeline, the default).
    private static Function<ElicitFormRequest, ElicitResult> answering(final List<String> scope,
                                                                       final String ownGroup) {
        final Function<ElicitFormRequest, ElicitResult> scoping = TestE2eSourceTypeGroups.scopeOf(scope,
                "for later builds");
        return form -> {
            if (form.message().contains(TestE2eSourceTypeGroups.SCOPE_FORM)) {
                return scoping.apply(form);
            }
            final ElicitResult agreed = McpTestClient.agree(form);
            // The pipeline asked for a new group, then where a pipeline of its own writes its Events.
            if (ownGroup == null || !form.message().contains("'" + ownGroup + "'")
                || !(form.message().contains("is new") || form.message().contains("runs in a pipeline of its own"))) {
                return agreed;
            }
            final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
            final Map<?, ?> properties = (Map<?, ?>) form.requestedSchema().get("properties");
            properties.forEach((key, field) -> {
                final List<?> options = (List<?>) ((Map<?, ?>) field).get("enum");
                options.stream()
                        .map(String::valueOf)
                        .filter(option -> option.startsWith(OWN_PIPELINE) || option.startsWith(OWN_FEED))
                        .findFirst()
                        .ifPresent(option -> content.put(String.valueOf(key), option));
            });
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        };
    }
}
