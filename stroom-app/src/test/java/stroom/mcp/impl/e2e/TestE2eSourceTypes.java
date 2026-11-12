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
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.data.store.api.TargetUtil;
import stroom.explorer.shared.ExplorerNode;
import stroom.mcp.impl.McpTestClient;
import stroom.meta.api.MetaProperties;
import stroom.meta.api.StandardHeaderArguments;
import stroom.security.api.SecurityContext;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/// Aggregated feeds through the REAL tools on a real Stroom: the survey finds the source type field and the user
/// confirms it, the plan's source types step profiles each source type and writes the feed's registry, the user
/// choosing the build's scope; an assessment writes the registry with no scope; a field of file paths gets patterns,
/// the narrower first. A build's scope is all its survey, classification and draft see; the survey doc and registry go
/// beside the feed at promotion, and a later build carries on from them through working copies written back at its
/// own promotion. The samples are synthetic slices of `.ai/data` (PEGA logs in a JSON wrapper, Azure's audit
/// log, collector paths); the test is skipped where they are absent.
@SuppressWarnings("unchecked")
class TestE2eSourceTypes extends AbstractE2eTest {

    private static final Path DATA = Path.of("..", ".ai", "data");
    private static final String SCOPE_FORM = "source types does this build cover";
    // The PEGA slice's source types that the scoped builds leave out, as their records name them.
    private static final List<String> OUT_OF_SCOPE = List.of("\"PegaDB\"", "\"PegaRULES\"",
            "\"PegaClusterHeartbeat\"", "\"PegaIntegration\"");

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void aWrappedFeedsSourceTypesAreProfiledAndTheBuildsScopeRecorded() {
        final String feed = "E2E-PEGA-AGG";
        final String build = "pega-sources";
        createFeed("pega-src", feed);
        upload(feed, lines("pega/pega-1.json", 1500));
        upload(feed, lines("pega/pega-2.json", 1500));
        json("start_build", args("build", build, "feeds", List.of(feed)));
        // The user builds the commonest source type now, and parks the rest.
        client.answeringWith(scopeOfFirst("noisy, for later"));

        final Map<String, Object> surveyed = json("survey_feed", args("feed", feed, "build", build));
        final Map<String, Object> sources = map(surveyed, "source_types");
        assertThat(sources).containsEntry("field", "sourcetype").containsEntry("by", "user");
        assertThat(map(sources, "records_read_by_value")).containsOnlyKeys("PegaDB", "PegaRULES",
                "PegaClusterHeartbeat", "PegaIntegration", "PegaSecurityEvent", "PegaALERT");
        assertThat(formMessages()).anySatisfy(message -> assertThat(message).contains("aggregates several sources",
                "sourcetype ("));
        // Covered, the user is asked where the survey doc goes at promotion, its example records' reach said.
        assertThat(formMessages()).anySatisfy(message -> assertThat(message).contains("is covered",
                "as readable as the feed's folder lets them be"));

        final Map<String, Object> status = json("build_status", args("build", build));
        assertThat(nextStep(status)).isEqualTo("source_types");
        assertThat(nextTool(status)).isEqualTo("record_source_types");

        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed));
        assertThat(shown).containsEntry("status", "needs_review").containsEntry("field", "sourcetype");
        assertThat((String) shown.get("table")).contains("PegaSecurityEvent", "security audit");
        final Map<String, Object> written = json("record_source_types", args("build", build, "feed", feed,
                "reviewed", shown.get("review_id")));
        assertThat(formMessages()).anySatisfy(message -> assertThat(message).contains(SCOPE_FORM));
        final Map<String, Map<String, Object>> rows = byName(list(written, "rows"));
        assertThat(rows).containsOnlyKeys("PegaDB", "PegaRULES", "PegaClusterHeartbeat", "PegaIntegration",
                "PegaSecurityEvent", "PegaALERT");
        assertThat(rows.get("PegaDB")).containsEntry("match", "= PegaDB");
        assertThat((double) rows.get("PegaDB").get("share_percent")).isBetween(40.0, 50.0);
        assertThat(number(rows.get("PegaSecurityEvent").get("records_read"))).isGreaterThan(100L);
        assertThat(rows.get("PegaSecurityEvent")).containsEntry("suggested_category", "security audit");
        assertThat(rows.get("PegaClusterHeartbeat")).containsEntry("suggested_category", "diagnostic noise");
        assertThat(rows.get("PegaDB")).containsEntry("suggested_category", "operational");
        // Every source type parked until a source type group translates it: the scope's say so, the rest carry the
        // user's reason.
        assertThat(rows.values()).allSatisfy(row -> assertThat(row).containsEntry("status", "parked"));
        assertThat(String.valueOf(rows.get("PegaDB").get("reason"))).contains("in the scope of build");
        assertThat(rows.get("PegaRULES")).containsEntry("reason", "noisy, for later");
        assertThat(strings(written, "scope")).containsExactly("PegaDB");
        assertThat(map(written, "registry")).containsEntry("name", feed + " source types");

        final Map<String, Object> after = json("build_status", args("build", build));
        assertThat(stepState(after, "source_types")).isEqualTo("done");
    }

    @Test
    void anAssessmentWritesTheRegistryWithoutAScope() {
        final String feed = "E2E-AZURE-AGG";
        final String build = "azure-assess";
        createFeed("azure-src", feed);
        upload(feed, lines("azure-audit/azure-audit-1.json", 1000));
        upload(feed, lines("azure-audit/azure-audit-2.json", 1000));
        final Map<String, Object> started = json("start_onboarding", args("source_name", "Azure audit",
                "build", build, "kind", "assessment", "feed", feed));
        assertThat(started).containsEntry("kind", "assessment");
        assertThat(nextStep(started)).isEqualTo("covered");

        final Map<String, Object> surveyed = json("survey_feed", args("feed", feed, "build", build));
        assertThat(map(surveyed, "source_types")).containsEntry("field", "Workload");
        // The kinds of event are told apart within the source types: by Operation, not Workload.
        assertThat(String.valueOf(surveyed.get("kind_fields"))).contains("Operation").doesNotContain("Workload");
        assertThat(nextStep(json("build_status", args("build", build)))).isEqualTo("source_types");

        final Map<String, Object> written = json("record_source_types", args("build", build, "feed", feed,
                "system_name", "Microsoft 365", "environment", "Prod", "system_names",
                Map.of("ExchangeOnline", "Exchange Online")));
        assertThat(formMessages()).noneSatisfy(message -> assertThat(message).contains(SCOPE_FORM));
        final Map<String, Map<String, Object>> rows = byName(list(written, "rows"));
        assertThat(rows).containsOnlyKeys("SharePoint", "ExchangeOnline", "AzureActiveDirectory", "MicrosoftTeams",
                "OneDrive");
        assertThat(rows.values()).allSatisfy(row -> assertThat(row).containsEntry("status", "parked"));
        assertThat(rows.get("SharePoint")).containsEntry("suggested_category", "security audit");
        assertThat(rows.get("AzureActiveDirectory")).containsEntry("suggested_category", "security audit");
        assertThat((String) written.get("table")).contains("ExchangeOnline");
        final Map<String, Object> status = json("build_status", args("build", build));
        assertThat(stepState(status, "source_types")).isEqualTo("done");
        assertThat(map(status, "steps").entrySet().stream()
                .filter(step -> !"not needed".equals(step.getValue()))
                .map(Map.Entry::getKey)
                .toList()).containsExactly("source_types", "covered", "promoted");
    }

    @Test
    void pathsGetPatternsTheNarrowerFirst() {
        final String feed = "E2E-COLLECTOR-AGG";
        final String build = "collector-sources";
        createFeed("collector-src", feed);
        upload(feed, lines("collector-paths/collector-paths-1.json", 800));
        upload(feed, lines("collector-paths/collector-paths-2.json", 800));
        json("start_build", args("build", build, "feeds", List.of(feed)));

        final Map<String, Object> surveyed = json("survey_feed", args("feed", feed, "build", build));
        assertThat(map(surveyed, "source_types")).containsEntry("field", "LogFilePath");
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed));
        // The patterns were put to the user, with the values each covers.
        assertThat(formMessages()).anySatisfy(message -> assertThat(message).contains("^/var/log/mail/audit/",
                "/var/log/mail/audit/mailaudit.log"));
        final int forms = client.forms().size();
        final Map<String, Object> written = json("record_source_types", args("build", build, "feed", feed,
                "reviewed", shown.get("review_id"), "rows", shown.get("rows")));
        // Given back, the rows the user confirmed aren't asked about again: only the scope is.
        assertThat(client.forms().subList(forms, client.forms().size())).singleElement()
                .satisfies(form -> assertThat(form.message()).contains(SCOPE_FORM));
        final List<String> matches = list(written, "rows").stream().map(row -> (String) row.get("match")).toList();
        assertThat(matches).contains("= /var/log/secure", "~ ^/var/log/httpd/", "~ ^/opt/app/logs/");
        assertThat(matches.indexOf("~ ^/var/log/mail/audit/")).isNotNegative()
                .isLessThan(matches.indexOf("~ ^/var/log/mail/"));
        // Rows written by hand are checked: a nested pattern after its parent can never match.
        assertThat(refusal("record_source_types", args("build", build, "feed", feed, "reviewed",
                shown.get("review_id"), "rows", List.of("mail ~ ^/var/log/mail/", "audit ~ ^/var/log/mail/audit/"))))
                .contains("'audit' can never match");
    }

    @Test
    void aBuildsScopeIsAllItsSurveyClassificationAndDraftSee() {
        final String feed = "E2E-PEGA-SCOPE";
        final String build = "pega-scoped";
        final List<String> scope = List.of("PegaSecurityEvent", "PegaALERT");
        createFeed("pega-scope-src", feed);
        final String firstText = lines("pega/pega-1.json", 1500);
        final String secondText = lines("pega/pega-2.json", 1500);
        final long first = upload(feed, firstText);
        final long second = upload(feed, secondText);
        json("start_build", args("build", build, "feeds", List.of(feed)));
        client.answeringWith(scopeOf(scope, "operational, for later"));
        json("survey_feed", args("feed", feed, "build", build));
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed));
        final Map<String, Object> written = json("record_source_types", args("build", build, "feed", feed,
                "reviewed", shown.get("review_id")));
        assertThat(strings(written, "scope")).containsExactlyInAnyOrderElementsOf(scope);

        // A stream that arrives once the scope is chosen is surveyed through it: only the scope's records are read
        // and make kinds; the others are counted by source type, and the scope's share of the feed is said.
        final String laterText = slice("pega/pega-1.json", 1500, 1500);
        final long later = upload(feed, laterText);
        final Map<String, Object> surveyed = json("survey_feed", args("feed", feed, "build", build));
        dump("survey_feed through the scope", surveyed);
        final long inScope = count(laterText, scope);
        assertThat(number(surveyed.get("records_read"))).isEqualTo(inScope);
        assertThat(ids(surveyed, "streams_read")).containsExactly(later);
        final Map<String, Object> said = map(surveyed, "scope");
        assertThat(number(said.get("records_passed_over"))).isEqualTo(1500 - inScope);
        assertThat((double) said.get("share_percent")).isBetween(4.0, 15.0);
        assertThat(jsonOf(surveyed.get("shapes"))).contains("PegaSecurityEvent")
                .doesNotContain(OUT_OF_SCOPE.toArray(String[]::new));
        assertThat((String) surveyed.get("coverage")).contains("within the scope: build '" + build + "' covers "
                                                               + "PegaALERT, PegaSecurityEvent (about ");

        // Classification lists the scope's kinds alone, read from the feed's streams through the scope.
        final List<Long> streams = List.of(first, second, later);
        final Map<String, Object> listed = json("classify_kinds", args("build", build, "stream_ids", streams));
        dump("classify_kinds through the scope", listed);
        final Map<String, Object> read = map(listed, "scope");
        assertThat(number(read.get("records_in_scope"))).isEqualTo(count(firstText, scope)
                                                                  + count(secondText, scope) + inScope);
        assertThat(map(read, "records_left_out")).containsKeys("PegaDB", "PegaRULES")
                .doesNotContainKeys(scope.toArray(String[]::new));
        assertThat(jsonOf(listed.get("to_classify"))).isNotEqualTo("null")
                .doesNotContain(OUT_OF_SCOPE.toArray(String[]::new)).doesNotContain("Executing query");
        classifiedAsDrafted(build, streams);

        // The draft's rules are the scope's: nothing of the other source types' records.
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", streams, "build",
                build));
        dump("draft_translation_mapping through the scope", draft);
        assertThat(number(map(draft, "scope").get("records_in_scope"))).isEqualTo(number(read.get(
                "records_in_scope")));
        assertThat(jsonOf(draft.get("mapping"))).doesNotContain(OUT_OF_SCOPE.toArray(String[]::new))
                .doesNotContain("soapenv", "Executing query");
    }

    @Test
    void theSurveyAndRegistryGoBesideTheFeedAndALaterBuildCarriesOnFromThem() {
        final String feed = "E2E-PEGA-PROMO";
        final String folder = "E2E Feeds/PegaPromo";
        createFeed("pega-promo-src", feed);
        upload(feed, lines("pega/pega-1.json", 1000));
        upload(feed, lines("pega/pega-2.json", 1000));
        promote("pega-promo-src", Map.of("Feed", folder));
        assertThat(documentsIn("System/" + folder, "Feed", feed)).as("the feed, outside any build").hasSize(1);

        // An assessment: its survey doc and registry go beside the feed when it is promoted.
        json("start_onboarding", args("source_name", "PEGA", "build", "pega-assess", "kind", "assessment",
                "feed", feed));
        json("survey_feed", args("feed", feed, "build", "pega-assess"));
        json("record_source_types", args("build", "pega-assess", "feed", feed));
        promote("pega-assess", Map.of());
        final String surveyUuid = besideTheFeed(folder, feed + " - Survey");
        final String registryUuid = besideTheFeed(folder, feed + " source types");

        // A later build carries on from them: the survey's streams aren't read again, and its survey doc and
        // registry are working copies of those beside the feed.
        final String build = "pega-later";
        json("start_build", args("build", build, "feeds", List.of(feed)));
        client.answeringWith(scopeOf(List.of("PegaSecurityEvent"), "for a later build"));
        final Map<String, Object> carried = json("survey_feed", args("feed", feed, "build", build));
        dump("survey_feed carried on", carried);
        assertThat(String.valueOf(carried.get("carried_on_from"))).contains(surveyUuid);
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", feed));
        final Map<String, Object> written = json("record_source_types", args("build", build, "feed", feed,
                "reviewed", shown.get("review_id")));
        assertThat(strings(written, "scope")).containsExactly("PegaSecurityEvent");
        assertThat(map(written, "registry").get("uuid")).isNotEqualTo(registryUuid);
        // Data the feed receives since: upload_sample takes no feed outside a build, so as Stroom's receiver
        // stores it.
        final long later = send(feed, slice("pega/pega-2.json", 1000, 1000));
        final Map<String, Object> surveyed = json("survey_feed", args("feed", feed, "build", build));
        assertThat(ids(surveyed, "streams_read")).containsExactly(later);

        // Promoted, the working copies are written back over those beside the feed.
        promote(build, Map.of());
        assertThat(besideTheFeed(folder, feed + " - Survey")).isEqualTo(surveyUuid);
        assertThat(besideTheFeed(folder, feed + " source types")).isEqualTo(registryUuid);
        final Map<String, Object> survey = SurveyDoc.readState(documentation(surveyUuid));
        assertThat(ids(survey, "streams_read")).contains(later);
        assertThat(map(map(survey, "source_types"), "scope")).containsEntry("build", build);
        assertThat(map(SourceTypeRegistry.read(documentation(registryUuid)), "scope")).containsEntry("build", build);
    }

    // The text stored in the feed as Stroom's receiver stores data posted to its datafeed.
    private long send(final String feed, final String data) {
        return securityContext.asProcessingUserResult(() -> {
            final MetaProperties properties = MetaProperties.builder()
                    .feedName(feed)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .createMs(System.currentTimeMillis())
                    .build();
            try (final Target target = streamStore.openTarget(properties)) {
                TargetUtil.write(target, data);
                target.getAttributes().put(StandardHeaderArguments.FEED, feed);
                return target.getMeta().getId();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    // The one document of that name beside the feed, in its folder.
    private String besideTheFeed(final String folder, final String name) {
        final List<ExplorerNode> found = documentsIn("System/" + folder, "Documentation", name);
        assertThat(found).as("%s beside the feed", name).hasSize(1);
        return found.getFirst().getUuid();
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees in the form.
    private void promote(final String build, final Map<String, Object> destinations) {
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                destinations, "reviewed", review.get("review_id")));
        dump("promote_build " + build, promoted);
    }

    // A slice of a sample file, its first lines; the test is skipped where the samples are absent.
    private static String lines(final String file, final int count) {
        return slice(file, 0, count);
    }

    // A slice of a sample file: so many lines from a line; the test is skipped where the samples are absent.
    private static String slice(final String file, final int from, final int count) {
        final Path path = DATA.resolve(file);
        Assumptions.assumeTrue(Files.isRegularFile(path), "no sample at " + path.toAbsolutePath());
        try (Stream<String> stream = Files.lines(path)) {
            return String.join("\n", stream.skip(from).limit(count).toList()) + "\n";
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // The records of a text whose source type is one of those given.
    private static long count(final String text, final List<String> sourceTypes) {
        return text.lines()
                .filter(line -> sourceTypes.stream().anyMatch(type -> line.contains("\"sourcetype\": \"" + type
                                                                                    + "\"")))
                .count();
    }

    // A user who chooses these source types for the build's scope, giving their reason for the rest, and agrees to
    // everything else.
    private static Function<ElicitFormRequest, ElicitResult> scopeOf(final List<String> chosen,
                                                                     final String reason) {
        return form -> {
            final ElicitResult agreed = McpTestClient.agree(form);
            if (!form.message().contains(SCOPE_FORM)) {
                return agreed;
            }
            final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
            final Map<?, ?> properties = (Map<?, ?>) form.requestedSchema().get("properties");
            content.replaceAll((key, value) -> value instanceof Boolean
                    ? chosen.stream().anyMatch(name -> ("Build " + name + " now").equals(
                    ((Map<?, ?>) properties.get(key)).get("title")))
                    : value);
            content.put("text", reason);
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        };
    }

    // A user who chooses only the first source type offered for the build's scope, giving their reason for the
    // rest, and agrees to everything else.
    private static Function<ElicitFormRequest, ElicitResult> scopeOfFirst(final String reason) {
        return form -> {
            final ElicitResult agreed = McpTestClient.agree(form);
            if (!form.message().contains(SCOPE_FORM)) {
                return agreed;
            }
            final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
            content.replaceAll((key, value) -> value instanceof Boolean
                    ? "i1".equals(key)
                    : value);
            content.put("text", reason);
            return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
        };
    }

    private static Map<String, Map<String, Object>> byName(final List<Map<String, Object>> rows) {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        rows.forEach(row -> out.put((String) row.get("name"), row));
        return out;
    }

    // A step's state, as build_status gives the steps: each step's state by its name.
    private static Object stepState(final Map<String, Object> status, final String step) {
        return map(status, "steps").get(step);
    }
}
