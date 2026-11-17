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
import stroom.explorer.shared.ExplorerNode;
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
/// * a later build changing a promoted group alone (its scope naming the group, no source type added): nothing asked,
///   a promoted pipeline and the code it runs change only by a new version (decided by the user, 2026-11-17): a new
///   version of the group's XSLT, a working copy of its pipeline refused, copy_pipeline running the new XSLT in the
///   pipeline's new version, the registry's rows of the group moved to it, production left as it was; promoted, the
///   new pipeline and its XSLT go in a version folder beside the old ones (left as they were), importing the original
///   common module, and the registry beside the feed names the new version for the group's rows;
/// * the next build's group and its pipeline go beside the feed;
/// * a last build making a new version of the new version, the same way, in a version folder of its own.
@SuppressWarnings("unchecked")
class TestE2eSourceTypePipelines extends AbstractE2eTest {

    private static final String SECURITY = "PEGA security";
    private static final String INTEGRATION = "PEGA integration";
    private static final String OWN_FEED = "To an output feed of its own";
    // The form that asked whether a change to a promoted group went to its pipeline or a new version of it: never
    // asked now, a new version being the only way.
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
        client.answeringWith(answering(List.of("PegaSecurityEvent"), INTEGRATION));
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

        // The security group's pipeline: its Events into the feed's; every other record passed over, never logged
        // nor warned of, counted by review_coverage.
        final Map<String, Object> mainDone = list(process(main, List.of(stream)), "streams").getFirst();
        final List<Long> mainEvents = ids(mainDone, "events");
        assertThat(mainEvents).hasSize(1);
        // The feed's Events: in a build on the feed's data, the build's test feed standing in for it.
        assertThat(meta(mainEvents.getFirst()).getFeedName()).startsWith(feed).isNotEqualTo(feed
                                                                                      + "-INTEGRATION-EVENTS");
        assertThat(TestE2eSourceTypeGroups.count(streamText(mainEvents.getFirst()), "<Event>"))
                .isEqualTo(securityRecords);
        final List<Long> mainErrors = ids(mainDone, "errors");
        final String mainLog = mainErrors.isEmpty()
                ? ""
                : streamText(mainErrors.getFirst());
        // Nothing logged of the records passed over: review_coverage tells them from the raw stream.
        assertThat(TestE2eSourceTypeGroups.passedOver(mainLog)).isZero();
        assertThat(passedOverInProcessing(main)).isEqualTo(total - securityRecords);
        assertThat(mainLog.lines().filter(line -> line.contains("WARN") && line.contains("PegaIntegration")))
                .isEmpty();
        promote(build, Map.of("Pipeline", folder));

        // The second build: the integration group, its Events into an output feed of its own.
        final String second = "pega-own-2";
        json("start_build", args("build", second, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of("PegaIntegration"), INTEGRATION));
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
        final List<Long> ownErrors = ids(ownDone, "errors");
        assertThat(TestE2eSourceTypeGroups.passedOver(ownErrors.isEmpty()
                ? ""
                : streamText(ownErrors.getFirst()))).isZero();
        assertThat(passedOverInProcessing(own)).isEqualTo(total - integrationRecords);
    }

    @Test
    void laterBuildsChangeAPromotedGroupInNewVersionsBesideTheOld() {
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
        client.answeringWith(answering(List.of("PegaSecurityEvent"), null));
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
        // The common module and helpers beside the feed, with the registry; the group's XSLT and pipeline in the
        // group's folder there.
        assertThat(folderOf(commonUuid)).endsWith("PegaWc");
        assertThat(folderOf(securityUuid)).isEqualTo(folderOf(pipeline)).endsWith("PegaWc/" + SECURITY);
        assertThat(documentsIn("System/" + folder, "XSLT", feed + " helpers")).hasSize(1);
        final String helpersUuid = documentsIn("System/" + folder, "XSLT", feed + " helpers").getFirst().getUuid();

        // A later build changes the security group alone: its scope names the group, no source type added. The
        // change goes to a new version of the group's XSLT, nothing asked (a promoted pipeline and the code it runs
        // are never changed in place, decided by the user 2026-11-17).
        final String later = "pega-wc-2";
        json("start_build", args("build", later, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of(), null));
        json("survey_feed", args("feed", feed, "build", later));
        final Map<String, Object> scoped = recordSourceTypes(later, feed, SECURITY);
        assertThat(strings(scoped, "scope")).containsExactly("PegaSecurityEvent");
        final int formsBefore = client.forms().size();
        final String changedText = jsonOf(TestE2eSourceTypeGroups.securityMapping()).replace("\"Login\"",
                "\"LoginV2\"");
        final Map<String, Object> versioned = group(later, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.jsonValue(changedText), stream);
        assertThat(formsSince(formsBefore)).noneSatisfy(form -> assertThat(form.message())
                .containsAnyOf(PIPELINE_FORM, "'" + SECURITY + "' (PegaSecurityEvent) is new"));
        final String securityVersion = uuidOf(map(versioned, "xslt"));
        assertThat(securityVersion).isNotEqualTo(securityUuid);
        assertThat(readXslt(securityVersion).getData()).contains("LoginV2", "uuid='" + commonUuid + "'");
        assertThat(readXslt(securityUuid).getData()).doesNotContain("LoginV2");
        assertThat((Map<String, Object>) versioned.get("pipeline")).containsEntry("new_version", true);
        assertThat(String.valueOf(versioned.get("group_next"))).contains("copy_pipeline")
                .doesNotContain("working_copy");
        // A working copy of the group's pipeline is refused; its new version runs the group's new XSLT.
        assertThat(refusal("copy_pipeline", args("build", later, "source_uuid", pipeline, "new_name", pipelineName,
                "working_copy", true))).contains("never a working copy", "Nothing was copied");
        final Map<String, Object> version = agreed("copy_pipeline", args("build", later, "source_uuid", pipeline,
                "new_name", pipelineName + " v2"));
        dump("copy_pipeline new version", version);
        final String versionPipeline = uuidOf(version);
        assertThat(strings(version, "source_types")).containsExactly("PegaSecurityEvent");
        assertThat(String.valueOf(version.get("copied_documents"))).contains(securityVersion);
        final Map<String, Map<String, Object>> rows = rows(uuidOf(map(versioned, "registry")));
        assertThat((Map<String, Object>) rows.get("PegaSecurityEvent").get("pipeline"))
                .containsEntry("name", pipelineName + " v2").containsEntry("uuid", versionPipeline)
                .doesNotContainKey("new_version");
        assertThat(map(rows.get("PegaSecurityEvent"), "group_xslt")).containsEntry("uuid", securityVersion);
        final List<String> records = text.lines().toList();
        final int loginAt = TestE2eSourceTypeGroups.indexOf(records, "\\\"eventType\\\": \\\"login\\\"", null);
        final int soapAt = TestE2eSourceTypeGroups.indexOf(records, "\"PegaIntegration\"", "Envelope>\"}");
        assertThat(translationOutput(versionPipeline, stream, loginAt)).contains("LoginV2");
        // Production as it was: the old version runs on.
        assertThat(translationOutput(pipeline, stream, loginAt)).doesNotContain("LoginV2");

        // Promoted: the new version and its XSLT in a version folder beside the old, which is left as it was, its
        // chain pointing at the originals of the common and helpers modules; the registry beside the feed names
        // the new version for the group's rows.
        promote(later, Map.of("Pipeline", folder));
        final String versionFolder = folderOf(versionPipeline);
        assertThat(versionFolder).endsWith("PegaWc/" + SECURITY + "/v2");
        assertThat(folderOf(securityVersion)).isEqualTo(versionFolder);
        assertThat(folderOf(pipeline)).endsWith("PegaWc/" + SECURITY).isEqualTo(folderOf(securityUuid));
        assertThat(folderOf(commonUuid)).endsWith("PegaWc");
        assertThat(readXslt(securityUuid).getData()).contains("uuid='" + commonUuid + "'")
                .doesNotContain("LoginV2");
        assertThat(translationOutput(pipeline, stream, loginAt)).doesNotContain("LoginV2");
        assertThat(readXslt(securityVersion).getData()).contains("LoginV2", "uuid='" + commonUuid + "'");
        assertThat(readXslt(commonUuid).getData()).contains("uuid='" + helpersUuid + "'");
        assertThat(documentsIn("System/" + folder, "XSLT", feed + " common")).hasSize(1);
        assertThat(translationOutput(versionPipeline, stream, loginAt)).contains("LoginV2");
        final Map<String, Map<String, Object>> promotedRows = rows(registryUuid(folder, feed));
        assertThat((Map<String, Object>) promotedRows.get("PegaSecurityEvent").get("pipeline"))
                .containsEntry("name", pipelineName + " v2").containsEntry("uuid", versionPipeline)
                .doesNotContainKey("new_version");
        assertThat(map(promotedRows.get("PegaSecurityEvent"), "group_xslt")).containsEntry("uuid",
                securityVersion);

        // The next build: the integration group, with a pipeline of its own beside the feed.
        final String next = "pega-wc-3";
        json("start_build", args("build", next, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of("PegaIntegration"), null));
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
        assertThat(folderOf(integrationUuid)).isEqualTo(folderOf(integrationPipeline))
                .endsWith("PegaWc/" + INTEGRATION);
        final Map<String, Map<String, Object>> withIntegration = rows(registryUuid(folder, feed));
        assertThat((Map<String, Object>) withIntegration.get("PegaSecurityEvent").get("pipeline"))
                .containsEntry("uuid", versionPipeline);
        assertThat((Map<String, Object>) withIntegration.get("PegaIntegration").get("pipeline"))
                .containsEntry("uuid", integrationPipeline);

        // A last build: a new version of the new version, the same way.
        final String last = "pega-wc-4";
        json("start_build", args("build", last, "feeds", List.of(feed)));
        client.answeringWith(answering(List.of(), null));
        json("survey_feed", args("feed", feed, "build", last));
        recordSourceTypes(last, feed, SECURITY);
        final Map<String, Object> again = group(last, feed, SECURITY, "PegaSecurityEvent",
                TestE2eSourceTypeGroups.jsonValue(changedText.replace("LoginV2", "LoginV3")), stream);
        final String securityThird = uuidOf(map(again, "xslt"));
        assertThat(securityThird).isNotIn(securityUuid, securityVersion);
        assertThat((Map<String, Object>) again.get("pipeline")).containsEntry("new_version", true);
        final Map<String, Object> third = agreed("copy_pipeline", args("build", last, "source_uuid",
                versionPipeline, "new_name", pipelineName + " v3"));
        final String thirdPipeline = uuidOf(third);
        assertThat(strings(third, "source_types")).containsExactly("PegaSecurityEvent");
        assertThat(String.valueOf(third.get("copied_documents"))).contains(securityThird);
        final Map<String, Map<String, Object>> lastRows = rows(uuidOf(map(again, "registry")));
        assertThat((Map<String, Object>) lastRows.get("PegaSecurityEvent").get("pipeline"))
                .containsEntry("name", pipelineName + " v3").containsEntry("uuid", thirdPipeline);
        assertThat((Map<String, Object>) lastRows.get("PegaIntegration").get("pipeline"))
                .containsEntry("uuid", integrationPipeline);
        assertThat(translationOutput(thirdPipeline, stream, loginAt)).contains("LoginV3");
        assertThat(translationOutput(versionPipeline, stream, loginAt)).contains("LoginV2")
                .doesNotContain("LoginV3");

        promote(last, Map.of("Pipeline", folder));
        assertThat(folderOf(thirdPipeline)).endsWith("PegaWc/" + SECURITY + "/v3")
                .isEqualTo(folderOf(securityThird));
        assertThat(folderOf(versionPipeline)).isEqualTo(versionFolder);
        assertThat(readXslt(securityVersion).getData()).contains("LoginV2").doesNotContain("LoginV3");
        assertThat(readXslt(securityThird).getData()).contains("LoginV3", "uuid='" + commonUuid + "'");
        assertThat(translationOutput(thirdPipeline, stream, loginAt)).contains("LoginV3");
        final Map<String, Map<String, Object>> lastPromoted = rows(registryUuid(folder, feed));
        assertThat((Map<String, Object>) lastPromoted.get("PegaSecurityEvent").get("pipeline"))
                .containsEntry("name", pipelineName + " v3").containsEntry("uuid", thirdPipeline)
                .doesNotContainKey("new_version");
        assertThat(map(lastPromoted.get("PegaSecurityEvent"), "group_xslt")).containsEntry("uuid", securityThird);
        assertThat((Map<String, Object>) lastPromoted.get("PegaIntegration").get("pipeline"))
                .containsEntry("uuid", integrationPipeline);
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

    // The registry beside the feed: its UUID.
    private String registryUuid(final String folder, final String feed) {
        final List<ExplorerNode> registries = documentsIn("System/" + folder, "Documentation",
                feed + SourceTypeRegistry.SUFFIX);
        assertThat(registries).hasSize(1);
        return registries.getFirst().getUuid();
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
    // its own, and agrees to everything else (a new group's Events into the feed's, the default; a new version's
    // names as proposed).
    private static Function<ElicitFormRequest, ElicitResult> answering(final List<String> scope,
                                                                       final String ownGroup) {
        final Function<ElicitFormRequest, ElicitResult> scoping = TestE2eSourceTypeGroups.scopeOf(scope, null);
        return form -> {
            if (form.message().contains(TestE2eSourceTypeGroups.SCOPE_FORM)) {
                return scoping.apply(form);
            }
            final ElicitResult agreed = McpTestClient.agree(form);
            if (ownGroup == null || !form.message().contains("'" + ownGroup + "'")
                || !form.message().contains("is new")) {
                return agreed;
            }
            final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
            final Map<?, ?> properties = (Map<?, ?>) form.requestedSchema().get("properties");
            properties.forEach((key, field) -> {
                final List<?> options = (List<?>) ((Map<?, ?>) field).get("enum");
                if (options != null) {
                    options.stream()
                            .map(String::valueOf)
                            .filter(option -> option.startsWith(OWN_FEED))
                            .findFirst()
                            .ifPresent(option -> content.put(String.valueOf(key), option));
                }
            });
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        };
    }
}
