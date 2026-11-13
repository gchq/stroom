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

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed's pipelines and promotion through the REAL tools on a real Stroom, on the synthetic PEGA
/// sample under `aggregated/`, each source type group the XSLT of a pipeline of its own:
///
/// * a group whose Events go into the feed's, and, in a second build (one source type group a build), another whose
///   Events go to an output feed of its own: each pipeline made by create_pipeline group= (named for the group, its
///   XSLT the group's, the output feed set on its stream appender once create_feed group= has made it, one pipeline
///   a group); processing writes each group's Events to its feed, the other source types' records passed over and
///   counted, never warned of; a second build on the feed is refused while one is open; a second group in a build
///   is refused;
/// * a later build changing a promoted group alone (its scope naming the group, no source type added; asked whether
///   the change goes to its pipeline or a new version of it, the user choosing the pipeline): it works on a working
///   copy of the group's XSLT, run by a working copy of its pipeline; promotion writes the copy back importing the
///   original common module (the chain pointing at the originals); the next build's group and its pipeline go beside
///   the feed;
/// * a last build making a new version of the group's pipeline: a new version of its XSLT, copy_pipeline running
///   it, the registry's rows of the group moved to the new version, production left as it was.
@SuppressWarnings("unchecked")
class TestE2eSourceTypePipelines extends AbstractE2eTest {

    private static final String SECURITY = "PEGA security";
    private static final String INTEGRATION = "PEGA integration";
    private static final String OWN_FEED = "To an output feed of its own";
    private static final String NEW_VERSION = "A new version of";
    private static final String PIPELINE_FORM = "runs in pipeline";

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void eachGroupHasAPipelineOfItsOwnWritingItsEventsToItsFeed() {
        final String feed = "E2E-PEGA-OWN";
        final String folder = "E2E Feeds/PegaOwn";
        createFeed("pega-own-src", feed);
        final String text = TestE2eSourceTypeGroups.lines("pega/pega-1.json", 1500);
        final long stream = upload(feed, text);
        promote("pega-own-src", Map.of("Feed", folder));
        final String template = templateUuid("translation", "Event Data (JSON)");
        final List<String> records = text.lines().toList();
        final long total = records.size();
        final long securityRecords = records.stream().filter(line -> line.contains(
                "\"sourcetype\": \"PegaSecurityEvent\"")).count();
        final long integrationRecords = records.stream().filter(line -> line.contains(
                "\"sourcetype\": \"PegaIntegration\"")).count();

        // The first build: the security group, its Events into the feed's.
        final String build = "pega-own";
        json("start_build", args("build", build, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of("PegaSecurityEvent"), INTEGRATION, false));
        json("survey_feed", args("feed", feed, "build", build));
        recordSourceTypes(build, feed, SECURITY);

        // One open build per aggregated feed: a second is refused, naming this one.
        assertThat(refusal("start_build", args("build", "pega-own-x", "feeds", List.of(feed))))
                .contains("build '" + build + "' is open on it");

        final int formsBefore = client.forms().size();
        final Map<String, Object> security = group(build, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.securityMapping(), stream);
        // One source type group a build: another is refused, for a later build.
        assertThat(refusal("build_translation_xslt", args("build", build, "feeds", List.of(feed), "group",
                INTEGRATION, "source_types", List.of("PegaIntegration"), "schema_version", VERSION, "stream_ids",
                List.of(stream), "mapping", TestE2eSourceTypeGroups.integrationMapping())))
                .contains("one source type group a build", "'" + SECURITY + "'", "later build");
        // The new group asked where its Events go, never which pipeline: every group has its own.
        final List<ElicitFormRequest> placements = formsSince(formsBefore).stream()
                .filter(form -> form.message().contains("is new. It gets a pipeline of its own"))
                .toList();
        assertThat(placements).hasSize(1);
        assertThat(placements).allSatisfy(form -> assertThat(form.message()).doesNotContain("**", "`", "|"));
        final String securityName = feed + " " + SECURITY;
        final String integrationName = feed + " " + INTEGRATION;
        assertThat(map(security, "pipeline")).containsEntry("name", securityName).doesNotContainKey("own");
        assertThat(security).doesNotContainKey("output_feed");
        final String securityUuid = uuidOf(map(security, "xslt"));

        // Its pipeline, named for it by default, its XSLT the group's.
        final Map<String, Object> securityMade = agreed("create_pipeline", args("build", build, "group", SECURITY,
                "template_uuid", template));
        assertThat(securityMade).containsEntry("name", securityName).containsEntry("group", SECURITY);
        assertThat(strings(securityMade, "source_types")).containsExactly("PegaSecurityEvent");
        final String main = uuidOf(securityMade);
        // One pipeline a group.
        assertThat(refusal("create_pipeline", args("build", build, "name", feed + "-Again", "group", SECURITY,
                "template_uuid", template))).contains("a group has one pipeline");
        final Map<String, Object> mainStepped = stepSample(main, List.of(stream));
        assertThat(mainStepped).as("stepped clean: %s", mainStepped).containsEntry("verdict", "clean")
                .doesNotContainKey("record");

        // The security group's pipeline: its Events into the feed's; every other record passed over, counted,
        // never warned of.
        final Map<String, Object> mainDone = list(process(main, List.of(stream)), "streams").getFirst();
        final List<Long> mainEvents = ids(mainDone, "events");
        assertThat(mainEvents).hasSize(1);
        // The feed's Events: in a build on the feed's data, the build's test feed standing in for it.
        assertThat(meta(mainEvents.getFirst()).getFeedName()).startsWith(feed).isNotEqualTo(feed
                                                                                      + "-INTEGRATION-EVENTS");
        assertThat(TestE2eSourceTypeGroups.count(streamText(mainEvents.getFirst()), "<Event>"))
                .isEqualTo(securityRecords);
        final String mainLog = streamText(ids(mainDone, "errors").getFirst());
        assertThat(TestE2eSourceTypeGroups.passedOver(mainLog)).isEqualTo(total - securityRecords);
        assertThat(mainLog.lines().filter(line -> line.contains("WARN") && line.contains("PegaIntegration")))
                .isEmpty();
        promote(build, Map.of("Pipeline", folder));

        // The second build: the integration group, its Events into an output feed of its own.
        final String second = "pega-own-2";
        json("start_build", args("build", second, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of("PegaIntegration"), INTEGRATION, false));
        json("survey_feed", args("feed", feed, "build", second));
        recordSourceTypes(second, feed, INTEGRATION);
        final int secondFormsBefore = client.forms().size();
        final Map<String, Object> integration = group(second, feed, INTEGRATION, "PegaIntegration",
                TestE2eSourceTypeGroups.integrationMapping(), stream);
        assertThat(formsSince(secondFormsBefore).stream()
                .filter(form -> form.message().contains("is new. It gets a pipeline of its own"))).hasSize(1);
        assertThat(map(integration, "pipeline")).containsEntry("name", integrationName);
        assertThat(integration).containsEntry("output_feed", "");
        assertThat(String.valueOf(integration.get("group_next"))).contains("create_feed", "create_pipeline");
        final String integrationUuid = uuidOf(map(integration, "xslt"));

        // Its pipeline can't be made before its output feed; the build makes that in a form.
        assertThat(refusal("create_pipeline", args("build", second, "group", INTEGRATION, "template_uuid",
                template))).contains("output feed of its own, not made yet", "create_feed");
        final String outputFeed = feed + "-INTEGRATION-EVENTS";
        final Map<String, Object> made = agreed("create_feed", args("build", second, "name", outputFeed,
                "stream_type", "Events", "group", INTEGRATION));
        assertThat(strings(made, "source_types")).containsExactly("PegaIntegration");
        final Map<String, Object> ownMade = agreed("create_pipeline", args("build", second, "group", INTEGRATION,
                "template_uuid", template));
        dump("create_pipeline own", ownMade);
        assertThat(strings(ownMade, "sets")).contains("translationFilter.xslt");
        assertThat(String.valueOf(ownMade.get("filled_from_build"))).contains(".feed = " + outputFeed);
        final String own = uuidOf(ownMade);
        final Map<String, Map<String, Object>> rows = rows(uuidOf(map(integration, "registry")));
        assertThat((Map<String, Object>) rows.get("PegaIntegration").get("pipeline"))
                .containsEntry("name", integrationName).containsEntry("uuid", own);
        assertThat(rows.get("PegaIntegration")).containsEntry("output_feed", outputFeed)
                .containsEntry("group_xslt", Map.of("uuid", integrationUuid, "name", INTEGRATION));
        assertThat((Map<String, Object>) rows.get("PegaSecurityEvent").get("pipeline"))
                .containsEntry("name", securityName).containsEntry("uuid", main);
        assertThat(rows.get("PegaSecurityEvent")).doesNotContainKey("output_feed");
        assertThat(map(rows.get("PegaSecurityEvent"), "group_xslt")).containsEntry("uuid", securityUuid);
        final Map<String, Object> ownStepped = stepSample(own, List.of(stream));
        dump("step_sample", ownStepped);
        assertThat(ownStepped).as("stepped clean: %s", ownStepped).containsEntry("verdict", "clean")
                .doesNotContainKey("record");

        // The integration group's pipeline: its Events in its own output feed; the rest passed over.
        final Map<String, Object> ownDone = list(process(own, List.of(stream)), "streams").getFirst();
        final List<Long> ownEvents = ids(ownDone, "events");
        assertThat(ownEvents).hasSize(1);
        assertThat(meta(ownEvents.getFirst()).getFeedName()).isEqualTo(outputFeed);
        assertThat(TestE2eSourceTypeGroups.count(streamText(ownEvents.getFirst()), "<Event>"))
                .isEqualTo(integrationRecords);
        final String ownLog = streamText(ids(ownDone, "errors").getFirst());
        assertThat(TestE2eSourceTypeGroups.passedOver(ownLog)).isEqualTo(total - integrationRecords);
    }

    @Test
    void aLaterBuildChangesAPromotedGroupThroughWorkingCopiesAndANewVersionIsCopied() {
        final String feed = "E2E-PEGA-WC";
        final String folder = "E2E Feeds/PegaWc";
        createFeed("pega-wc-src", feed);
        final String text = TestE2eSourceTypeGroups.lines("pega/pega-1.json", 1500);
        final long stream = upload(feed, text);
        promote("pega-wc-src", Map.of("Feed", folder));
        final String template = templateUuid("translation", "Event Data (JSON)");

        // The first build: the security group and its pipeline, promoted beside the feed.
        final String first = "pega-wc-1";
        json("start_build", args("build", first, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of("PegaSecurityEvent"), null, false));
        json("survey_feed", args("feed", feed, "build", first));
        recordSourceTypes(first, feed, SECURITY);
        final Map<String, Object> security = group(first, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.securityMapping(), stream);
        final String securityUuid = uuidOf(map(security, "xslt"));
        final String commonUuid = uuidOf(map(security, "common"));
        final String pipelineName = feed + " " + SECURITY;
        final String pipeline = uuidOf(agreed("create_pipeline", args("build", first, "group", SECURITY,
                "template_uuid", template)));
        promote(first, Map.of("Pipeline", folder));
        // The modules beside the feed, with the registry and the group's pipeline.
        assertThat(folderOf(securityUuid)).isEqualTo(folderOf(commonUuid)).endsWith("PegaWc");
        assertThat(folderOf(pipeline)).endsWith("PegaWc");
        assertThat(documentsIn("System/" + folder, "XSLT", feed + " helpers")).hasSize(1);

        // A later build changes the security group alone: its scope names the group, no source type added; the
        // user chooses to change its pipeline.
        final String later = "pega-wc-2";
        json("start_build", args("build", later, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of(), null, false));
        json("survey_feed", args("feed", feed, "build", later));
        final Map<String, Object> scoped = recordSourceTypes(later, feed, SECURITY);
        assertThat(strings(scoped, "scope")).containsExactly("PegaSecurityEvent");
        final int formsBefore = client.forms().size();
        final String changedText = jsonOf(TestE2eSourceTypeGroups.securityMapping()).replace("\"Login\"",
                "\"LoginV2\"");
        final Map<String, Object> securityAgain = group(later, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.jsonValue(changedText), stream);
        final ElicitFormRequest asked = formsSince(formsBefore).stream()
                .filter(form -> form.message().contains(PIPELINE_FORM))
                .findFirst()
                .orElseThrow();
        assertThat(asked.message()).contains("'" + SECURITY + "'", pipelineName, "in production");
        assertThat(formsSince(formsBefore)).noneSatisfy(form -> assertThat(form.message()).contains(
                "'" + SECURITY + "' (PegaSecurityEvent) is new"));
        final String securityCopy = uuidOf(map(securityAgain, "xslt"));
        assertThat(securityCopy).isNotEqualTo(securityUuid);
        assertThat(readXslt(securityCopy).getData()).contains("LoginV2", "uuid='" + commonUuid + "'");
        assertThat(readXslt(securityUuid).getData()).doesNotContain("LoginV2");
        assertThat(String.valueOf(securityAgain.get("group_next"))).contains("copy_pipeline", "working_copy=true");

        // The group's pipeline stepped in the build through a working copy of it: it runs the group's working copy.
        final Map<String, Object> copied = agreed("copy_pipeline", args("build", later, "source_uuid", pipeline,
                "new_name", pipelineName, "working_copy", true));
        final String workingPipeline = uuidOf(copied);
        assertThat(String.valueOf(copied.get("copied_documents"))).contains(securityCopy);
        final List<String> records = text.lines().toList();
        final int loginAt = TestE2eSourceTypeGroups.indexOf(records, "\\\"eventType\\\": \\\"login\\\"", null);
        final int soapAt = TestE2eSourceTypeGroups.indexOf(records, "\"PegaIntegration\"", "Envelope>\"}");
        assertThat(translationOutput(workingPipeline, stream, loginAt)).contains("LoginV2");

        // Promoted: the working copy written back importing the original common module.
        promote(later, Map.of("Pipeline", folder));
        assertThat(readXslt(securityUuid).getData()).contains("LoginV2", "uuid='" + commonUuid + "'")
                .doesNotContain(securityCopy);
        // Production translates as changed.
        assertThat(translationOutput(pipeline, stream, loginAt)).contains("LoginV2");

        // The next build: the integration group, with a pipeline of its own beside the feed.
        final String next = "pega-wc-3";
        json("start_build", args("build", next, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of("PegaIntegration"), null, false));
        json("survey_feed", args("feed", feed, "build", next));
        recordSourceTypes(next, feed, INTEGRATION);
        final Map<String, Object> integration = group(next, feed, INTEGRATION, "PegaIntegration",
                TestE2eSourceTypeGroups.integrationMapping(), stream);
        final String integrationUuid = uuidOf(map(integration, "xslt"));
        assertThat(readXslt(integrationUuid).getData()).contains("uuid='" + commonUuid + "'");
        final String integrationPipeline = uuidOf(agreed("create_pipeline", args("build", next, "group",
                INTEGRATION, "template_uuid", template)));
        assertThat(children(parse(translationOutput(integrationPipeline, stream, soapAt)).getDocumentElement(),
                "Event")).hasSize(1);
        promote(next, Map.of("Pipeline", folder));
        assertThat(readXslt(integrationUuid).getData()).contains("uuid='" + commonUuid + "'");
        assertThat(folderOf(integrationUuid)).isEqualTo(folderOf(securityUuid));
        final Map<String, Object> registry = SourceTypeRegistry.read(documentation(documentsIn("System/" + folder,
                "Documentation", feed + SourceTypeRegistry.SUFFIX).getFirst().getUuid()));
        assertThat(jsonOf(registry)).contains(securityUuid, integrationUuid, pipeline, integrationPipeline)
                .doesNotContain(securityCopy, workingPipeline);

        // A last build: a new version of the security group's pipeline, the user's choice.
        final String last = "pega-wc-4";
        json("start_build", args("build", last, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of(), null, true));
        json("survey_feed", args("feed", feed, "build", last));
        recordSourceTypes(last, feed, SECURITY);
        final Map<String, Object> versioned = group(last, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.jsonValue(changedText.replace("LoginV2", "LoginV3")), stream);
        final String securityVersion = uuidOf(map(versioned, "xslt"));
        assertThat(securityVersion).isNotEqualTo(securityUuid);
        assertThat((Map<String, Object>) versioned.get("pipeline")).containsEntry("new_version", true);
        assertThat(String.valueOf(versioned.get("group_next"))).contains("copy_pipeline", "working_copy=false");
        final Map<String, Object> version = agreed("copy_pipeline", args("build", last, "source_uuid", pipeline,
                "new_name", pipelineName + " v2", "working_copy", false));
        dump("copy_pipeline new version", version);
        final String versionPipeline = uuidOf(version);
        assertThat(strings(version, "source_types")).containsExactly("PegaSecurityEvent");
        assertThat(String.valueOf(version.get("copied_documents"))).contains(securityVersion);
        final Map<String, Map<String, Object>> rows = rows(uuidOf(map(versioned, "registry")));
        assertThat((Map<String, Object>) rows.get("PegaSecurityEvent").get("pipeline"))
                .containsEntry("name", pipelineName + " v2").containsEntry("uuid", versionPipeline)
                .doesNotContainKey("new_version");
        assertThat(map(rows.get("PegaSecurityEvent"), "group_xslt")).containsEntry("uuid", securityVersion);
        assertThat((Map<String, Object>) rows.get("PegaIntegration").get("pipeline"))
                .containsEntry("uuid", integrationPipeline);
        assertThat(translationOutput(versionPipeline, stream, loginAt)).contains("LoginV3");
        // Production as it was: the old version runs on.
        assertThat(readXslt(securityUuid).getData()).contains("LoginV2").doesNotContain("LoginV3");
        assertThat(translationOutput(pipeline, stream, loginAt)).contains("LoginV2");
    }

    // build_translation_xslt group=: the group saved with its mapping, where it goes as the user chose it.
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

    // record_source_types: the table, then the scope form, the build's group suggested by this name.
    private Map<String, Object> recordSourceTypes(final String build, final String feed, final String group) {
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed));
        final Map<String, Object> written = json("record_source_types", args("build", build, "feed", feed,
                "reviewed", shown.get("review_id"), "group", group));
        assertThat(written).as("scoped: %s", written).containsEntry("group", group);
        return written;
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

    // A user who chooses these source types for the build's scope, gives the group named (if any) an output feed of
    // its own, makes a new version of a group's promoted pipeline when asked (or else changes it), and agrees to
    // everything else (a new group's Events into the feed's, the default).
    private static Function<ElicitFormRequest, ElicitResult> answering(final List<String> scope,
                                                                       final String ownGroup,
                                                                       final boolean newVersion) {
        final Function<ElicitFormRequest, ElicitResult> scoping = TestE2eSourceTypeGroups.scopeOf(scope, null);
        return form -> {
            if (form.message().contains(TestE2eSourceTypeGroups.SCOPE_FORM)) {
                return scoping.apply(form);
            }
            final ElicitResult agreed = McpTestClient.agree(form);
            final String wanted;
            if (ownGroup != null && form.message().contains("'" + ownGroup + "'")
                && form.message().contains("is new")) {
                wanted = OWN_FEED;
            } else if (newVersion && form.message().contains(PIPELINE_FORM)) {
                wanted = NEW_VERSION;
            } else {
                return agreed;
            }
            final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
            final Map<?, ?> properties = (Map<?, ?>) form.requestedSchema().get("properties");
            properties.forEach((key, field) -> {
                final List<?> options = (List<?>) ((Map<?, ?>) field).get("enum");
                if (options != null) {
                    options.stream()
                            .map(String::valueOf)
                            .filter(option -> option.startsWith(wanted))
                            .findFirst()
                            .ifPresent(option -> content.put(String.valueOf(key), option));
                }
            });
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        };
    }
}
