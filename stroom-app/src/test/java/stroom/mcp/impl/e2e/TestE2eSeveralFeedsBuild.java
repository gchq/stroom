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

import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.mcp.impl.McpTestClient;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.security.api.DocumentPermissionService;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// A build of three feeds of one source (decided by the user, 2026-11-25), on a real Stroom through the REAL tools:
/// two feeds of one shape in one folder (one of them dormant, its only stream 200 days old), and one in another
/// folder with a field the others lack and its own time zone, whose AGENTS doc gives `## Fields` differently and
/// whose readers differ. Every feed is surveyed in turn; their differences, volumes and the AGENTS conflict are
/// shown and answered in forms and kept in the build; the draft reads each feed's times in its own zone; the index
/// is one per feed as the readers differ. Then a promotion of a build of the three: one filter of `Feed is` terms
/// under OR covering all three, the dormant one's streams stated, the shared folder asked.
@SuppressWarnings("unchecked")
class TestE2eSeveralFeedsBuild extends AbstractE2eTest {

    private static final String RAW = "Raw Events";
    private static final String OTHER_ZONE = "Another zone (type it)";

    @Inject
    private DocumentPermissionService documentPermissionService;
    @Inject
    private ProcessorFilterService processorFilterService;

    @Test
    void threeFeedsAreSurveyedInTurnComparedAndIndexedOneEach() {
        final Feeds feeds = feeds("MANYF");
        final String build = "manyf-v1-0";

        // Every feed named: the stream_meta call covers them all.
        final Map<String, Object> started = json("start_build", args("build", build, "feeds", feeds.all()));
        dump("start_build", started);
        assertThat(map(next(started), "call")).isEqualTo(args("tool", "stream_meta", "arguments",
                args("feeds", feeds.all())));
        assertThat(String.valueOf(next(started).get("several_feeds"))).contains("for 3 feeds",
                "compare_feeds");

        // 1. Each feed surveyed in turn, next naming the next one.
        survey(build, feeds.a());
        Map<String, Object> status = json("build_status", args("build", build));
        dump("build_status after the first survey", status);
        assertThat(map(map(status, "next"), "call")).isEqualTo(args("tool", "survey_feed", "arguments",
                args("feed", feeds.b(), "build", build)));
        assertThat(map(status, "feed_surveys")).containsEntry("to_survey", List.of(feeds.b(), feeds.c()));
        survey(build, feeds.b());
        survey(build, feeds.c());
        status = json("build_status", args("build", build));
        assertThat(nextTool(status)).isEqualTo("compare_feeds");

        // 2. Compared: the union confirmed, the AGENTS conflict answered (Site-A's), each feed's zone (B's typed).
        final int before = client.forms().size();
        client.answeringWith(form -> zones(form, feeds));
        final Map<String, Object> compared = json("compare_feeds", args("build", build));
        dump("compare_feeds", compared);
        final String shown = formsText(formsSince(before));
        assertThat(shown).contains("Fields in some feeds only: site (" + feeds.b() + ")",
                "differ on fields: user", "user.name (the AGENTS doc in /Feeds/MANYF/Site-A",
                "account (the AGENTS doc in /Feeds/MANYF/Site-B").doesNotContain("| ", "**");
        assertThat(map(compared, "differences")).containsEntry("fields_only_in", Map.of("site",
                List.of(feeds.b())));
        final Map<String, Object> kept = map(compared, "kept");
        assertThat(kept.get("time_zones")).isEqualTo(Map.of(feeds.a(), "UTC", feeds.b(), "Australia/Sydney",
                feeds.c(), "UTC"));
        assertThat(String.valueOf(kept.get("agents"))).contains("fields: user", "user.name", "/Feeds/MANYF/Site-A");
        // The dormant feed included, its volume and dates stated.
        final List<Map<String, Object>> overviews = list(compared, "feeds");
        assertThat(overviews).filteredOn(feed -> feeds.c().equals(feed.get("feed"))).singleElement()
                .satisfies(feed -> {
                    assertThat(feed).containsEntry("dormant", true);
                    assertThat(number(feed.get("streams"))).isOne();
                    assertThat(String.valueOf(feed.get("said"))).contains("dormant: no new stream for 200 days");
                });
        assertThat(overviews).filteredOn(feed -> feeds.a().equals(feed.get("feed"))).singleElement()
                .satisfies(feed -> assertThat(feed).containsEntry("dormant", false));
        status = json("build_status", args("build", build));
        assertThat(list(status, "source_feeds")).hasSize(3);
        assertThat(nextTool(status)).isEqualTo(CATALOGUE);
        client.answeringWith(McpTestClient::agree);

        // 3. Catalogued, then drafted: each feed's times read in its own zone, from the stream's Feed meta.
        final List<Long> streams = new ArrayList<>();
        streams.addAll(streamIdsOf(feeds.a()));
        streams.addAll(streamIdsOf(feeds.b()));
        streams.addAll(streamIdsOf(feeds.c()));
        cataloguedAsSeeded(build, streams);
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", streams, "build",
                build));
        dump("draft_translation_mapping", draft);
        final String mapping = JsonUtil.writeValueAsString(draftedMapping(draft), false);
        assertThat(mapping).contains("\"meta\":\"Feed\"", "\"" + feeds.b() + "\":\"Australia/Sydney\"");
        assertThat(String.valueOf(draft.get("notes"))).contains("read in each feed's own zone");

        // 4. The index: the readers differ, so one per feed is proposed first, and chosen.
        final int beforeIndex = client.forms().size();
        final Map<String, Object> index = agreed("create_index_doc", args("build", build, "backend", "lucene",
                "name", feeds.a() + "-Index", "time_field", "EventTime"));
        dump("create_index_doc", index);
        assertThat(formsText(formsSince(beforeIndex))).contains("Who may read these feeds differs",
                "One index per feed (recommended)", "everyone who can use it sees every feed's events");
        assertThat(map(index, "feeds")).containsEntry("index_split", "per_feed").containsEntry("for_feed",
                feeds.a());
        assertThat(String.valueOf(map(index, "feeds").get("said"))).contains("still to make, one each: "
                                                                             + feeds.b() + ", " + feeds.c());
        assertThat(refusal("create_index_doc", args("build", build, "backend", "lucene", "name", "Shared-Index",
                "time_field", "EventTime"))).contains("name this index doc for exactly one feed");
    }

    @Test
    void aBuildOfThreeFeedsInTwoFoldersIsPromotedWithOneFilterCoveringThemAll() {
        final String name = "MANYP";
        final String siteA = "System/Feeds/" + name + "/Site-A";
        final String siteB = "System/Feeds/" + name + "/Site-B";
        final String feedA = name + "-A";
        final String feedB = name + "-B";
        final String feedC = name + "-C";
        final HandWritten written = handWritten(siteA, feedA, textResource("hand_written_vpn.xsl"),
                name + "-Events", "Event Data (JSON)", List.of(PipelineProperty.builder().element("jsonParser")
                        .name("addRootObject").value(new PipelinePropertyValue(false)).build()), RAW,
                vpnSample("2026-11-03T08:00:01.000Z"));
        create(FeedDoc.TYPE, feedB, folder(siteB));
        storeParts(feedB, List.of(Map.entry(Map.of("Feed", feedB), vpnSample("2026-11-03T09:00:01.000Z"))));
        create(FeedDoc.TYPE, feedC, folder(siteA));
        storeParts(feedC, List.of(Map.entry(Map.of("Feed", feedC), vpnSample("2026-05-09T09:00:01.000Z"))),
                System.currentTimeMillis() - Duration.ofDays(200).toMillis());
        final String build = "manyp-v1-1";
        json("start_build", args("build", build, "feeds", List.of(feedA, feedB, feedC)));
        agreed("copy_pipeline", args("build", build, "source_uuid", written.pipeline(), "new_name",
                name + "-Events-V1.1"));

        // The feeds are in two folders: where the shared documents go is asked, in a form (here, Site-A's).
        final int before = client.forms().size();
        final Map<String, Object> review = json("promote_build", args("build", build));
        dump("promote_build review", review);
        assertThat(formsText(formsSince(before))).contains("feeds in different folders");
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        // Each feed's streams stated, the dormant one's too.
        assertThat(String.valueOf(review.get("feed_streams"))).contains(feedA + " (" + RAW + "): 1 stream",
                feedB + " (" + RAW + "): 1 stream", feedC + " (" + RAW + "): 1 stream");
        final Map<String, Object> promoted = agreed("promote_build", args("build", build, "reviewed",
                review.get("review_id")));
        dump("promote_build", promoted);
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
        final List<Map<String, Object>> made = list(promoted, "processing_filters");
        assertThat(made).singleElement();
        final ProcessorFilter filter = asUser(admin, () -> processorFilterService.fetch((int) number(
                made.getFirst().get("filter_id"))).orElseThrow());
        assertThat(feedsOf(filter.getQueryData().getExpression())).containsExactlyInAnyOrder(feedA, feedB,
                feedC);
    }

    // A feed surveyed for the build, as next names the call.
    private void survey(final String build, final String feed) {
        final Map<String, Object> survey = json("survey_feed", args("feed", feed, "build", build));
        dump("survey_feed " + feed, survey);
        assertThat(survey).as("survey_feed of %s", feed).doesNotContainKey("error");
    }

    // Three feeds of one source: A and C (dormant) in Site-A, B (a site field, times in Sydney) in Site-B, whose
    // AGENTS docs give the user field differently, and B readable by a group A and C aren't.
    private Feeds feeds(final String name) {
        final String siteA = "System/Feeds/" + name + "/Site-A";
        final String siteB = "System/Feeds/" + name + "/Site-B";
        final Feeds feeds = new Feeds(name + "-A", name + "-B", name + "-C");
        create(FeedDoc.TYPE, feeds.a(), folder(siteA));
        create(FeedDoc.TYPE, feeds.c(), folder(siteA));
        create(FeedDoc.TYPE, feeds.b(), folder(siteB));
        documentationDoc(siteA, "AGENTS", "## Fields\n- user: user.name\n");
        documentationDoc(siteB, "AGENTS", "## Fields\n- user: account\n");
        storeParts(feeds.a(), List.of(Map.entry(Map.of("Feed", feeds.a()), lines(false,
                "2026-11-03 08:00:01", "2026-11-03 08:05:00"))));
        storeParts(feeds.b(), List.of(Map.entry(Map.of("Feed", feeds.b()), lines(true,
                "2026-11-03 18:00:01", "2026-11-03 18:05:00"))));
        storeParts(feeds.c(), List.of(Map.entry(Map.of("Feed", feeds.c()), lines(false,
                "2026-05-09 08:00:01", "2026-05-09 08:05:00"))), System.currentTimeMillis() - Duration.ofDays(200).toMillis());
        final UserRef readers = user("manyf-site-b-readers", Set.of(AppPermission.USE_MCP_TOOLS));
        asUser(admin, () -> {
            documentPermissionService.setPermission(feedRef(siteB, feeds.b()), readers, DocumentPermission.VIEW);
            return null;
        });
        return feeds;
    }

    // JSON lines of logins and logouts, times with no zone; with `site` where asked.
    private static String lines(final boolean site, final String... times) {
        final List<String> out = new ArrayList<>();
        int n = 0;
        for (final String time : times) {
            out.add("{\"time\": \"" + time + "\", \"user\": \"u" + n + "\", \"action\": \"" + (n % 2 == 0
                    ? "login"
                    : "logout") + "\", \"host\": \"vpn-" + n + "\"" + (site
                    ? ", \"site\": \"Sydney\""
                    : "") + "}");
            n++;
        }
        return String.join("\n", out) + "\n";
    }

    private static String vpnSample(final String time) {
        return "[{\"ts\": \"" + time + "\", \"action\": \"login\", \"user\": \"jbloggs\", \"src_ip\": "
               + "\"198.51.100.7\", \"host\": \"vpn-gw-1\", \"result\": \"success\"}]";
    }

    // The zone form answered: UTC for A and C, Australia/Sydney typed for B; every other form agreed.
    private static ElicitResult zones(final ElicitFormRequest form, final Feeds feeds) {
        final ElicitResult agreed = McpTestClient.agree(form);
        final Map<String, Object> content = new LinkedHashMap<>(agreed.content());
        final Map<?, ?> properties = (Map<?, ?>) form.requestedSchema().get("properties");
        properties.forEach((key, field) -> {
            final String title = String.valueOf(((Map<?, ?>) field).get("title"));
            if (title.startsWith("Feed " + feeds.b() + ":")) {
                content.put(String.valueOf(key), OTHER_ZONE);
                content.put(key + "_typed", "Australia/Sydney");
            } else if (title.startsWith("Feed ")) {
                content.put(String.valueOf(key), "UTC");
            }
        });
        return new ElicitResult(ElicitResult.Action.ACCEPT, content, null);
    }

    private DocRef feedRef(final String folder, final String name) {
        return documentsIn(folder, FeedDoc.TYPE, name).getFirst().getDocRef();
    }

    // The feeds a filter's Feed terms name, at any depth.
    private static List<String> feedsOf(final ExpressionOperator expression) {
        final List<String> out = new ArrayList<>();
        for (final ExpressionItem child : expression.getChildren()) {
            if (child instanceof final ExpressionOperator operator) {
                out.addAll(feedsOf(operator));
            } else if (child instanceof final ExpressionTerm term && "Feed".equals(term.getField())) {
                out.add(term.getDocRef() != null
                        ? term.getDocRef().getName()
                        : term.getValue());
            }
        }
        return out;
    }


    // --------------------------------------------------------------------------------


    // The three feeds of the first test.
    private record Feeds(String a, String b, String c) {

        List<String> all() {
            return List.of(a, b, c);
        }
    }
}
