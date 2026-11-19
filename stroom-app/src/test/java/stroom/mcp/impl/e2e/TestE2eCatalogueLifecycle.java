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

import stroom.mcp.impl.McpTestClient;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// The catalogue after the first build (stroom-ai/DESIGN.md, Event type catalogue, After the first build), driving
/// the real tools (the ideas of TestE2eCoverage's parts 3 to 5, in the catalogue's terms):
///
/// 1. records the catch-all caught in processing become proposals: review_coverage groups them by shape and proposes
///    event types for them; confirmed as usual, they are applied in a new version of the translation;
/// 2. a feed's next version starts from the promoted catalogue, and the review shows the event types added, changed
///    and removed;
/// 3. a hand edit of the XSLT is read back into the catalogue (rebuild_mapping); an edit the catalogue can't express
///    is named;
/// 4. a hand-written last rule taking every remaining record and writing a real event is read back as the catch-all,
///    with its own translation, and the review names it.
@SuppressWarnings("unchecked")
class TestE2eCatalogueLifecycle extends AbstractE2eTest {

    private static final String HEADER = "time,user,host,action,resource\n";
    private static final String SAMPLE = HEADER + "2026-10-01T10:00:00,alice,ws01,login,\n"
                                         + "2026-10-01T10:05:00,bob,ws02,logout,\n"
                                         + "2026-10-01T10:06:00,alice,ws01,login,\n"
                                         + "2026-10-01T10:09:00,bob,ws02,logout,\n";
    // Logons, and two views the sample never had.
    private static final String LATER = HEADER + IntStream.rangeClosed(1, 40)
            .mapToObj(n -> switch (n) {
                case 11 -> "2026-10-02T09:01:00,carol,ws03,view,Payroll\n";
                case 31 -> "2026-10-02T09:02:00,dave,ws04,view,Firewall\n";
                default -> "2026-10-02T09:00:00,carol,ws03,login,\n";
            })
            .collect(Collectors.joining());
    private static final Map<String, Object> DESTINATIONS = Map.of("Feed", "Feeds/CatLife", "TextConverter",
            "Feeds/CatLife", "XSLT", "Feeds/CatLife", "Pipeline", "Feeds/CatLife", "Documentation",
            "Feeds/CatLife");

    @Test
    void recordsTheCatchAllCaughtInProcessingBecomeProposalsConfirmedAndApplied() {
        final Translated first = translated("cat-life-caught", "CAT-LIFE-CAUGHT");
        final long later = upload(first.feed(), LATER);
        final Map<String, Object> done = process(first.pipeline(), List.of(first.raw(), later));
        assertThat(list(done, "streams")).allSatisfy(stream -> assertThat(ids(stream, "events")).isNotEmpty());
        assertThat(unknownsIn(done)).as("the views, kept as Unknown by the catch-all").isEqualTo(2);

        // ### review_coverage: the records the catch-all caught, grouped by shape, proposed as event types for the
        // catalogue to check and the user to confirm.
        final Map<String, Object> review = json("review_coverage", args("pipeline_uuid", first.pipeline(),
                "follow_on", false));
        dump("review_coverage", review);
        assertThat(jsonOf(review)).contains("view", CATALOGUE);
        assertThat(ids(review, "affected_raw_stream_ids")).containsExactly(later);
        assertThat(nextTool(review)).isEqualTo(CATALOGUE);

        // The agent proposes the views' event type, checked against the sample and the records caught.
        final List<Long> checkedOn = List.of(first.raw(), later);
        final Map<String, Object> checked = proposed(first.build(), checkedOn, List.of(withKeys(eventType(
                "View-Resource", "View", "high", when("action", "equals", "view")), "actor", "user", "affected",
                "resource", "affected_as", "Resource")));
        assertThat(countsOf(checked)).containsEntry("View-Resource", 2L);
        assertThat(map(checkOf(checked), "uncatalogued")).containsEntry("records", 0);
        confirmedCatalogue(first.build());

        // ### Applied in a new version of the translation: the draft follows the catalogue, the views translated.
        final Map<String, Object> redrafted = json("draft_translation_mapping", args("stream_ids", checkedOn,
                "build", first.build(), "source_name", "CatLife", "system_name", "CatLife", "environment", "Test"));
        assertThat(jsonOf(map(redrafted, "mapping").get("events"))).contains("View-Resource");
        final Map<String, Object> rebuilt = agreed("build_translation_xslt", args("mapping",
                timeChosen(map(redrafted, "mapping")), "uuid", first.xslt(), "stream_ids", checkedOn, "build",
                first.build(), "change", "View events, found once the feed was processed"));
        assertThat(rebuilt.get("ok")).as("%s", rebuilt.get("problems")).isEqualTo(true);
        // The new code stepped clean before its streams are processed again (reprocess_streams asks it).
        assertThat(stepSample(first.pipeline(), checkedOn).get("verdict")).isEqualTo("clean");
        final Map<String, Object> redo = agreed("reprocess_streams", args("pipeline_uuid", first.pipeline(),
                "stream_ids", List.of(later)));
        processAll();
        final Map<String, Object> again = json("wait_for_processing", args("pipeline_uuid", first.pipeline(),
                "stream_ids", List.of(later), "filter_id", redo.get("filter_id"), "timeout_seconds", 10));
        assertThat(unknownsIn(again)).as("nothing left to the catch-all").isZero();
    }

    @Test
    void aNextVersionStartsFromThePromotedCatalogueAndTheReviewShowsWhatChanged() {
        final Translated first = translated("cat-life-v1", "CAT-LIFE-NEXT");
        agreed("write_documentation", args("build", first.build(), "pipeline_uuid", first.pipeline(), "markdown",
                "## Purpose and data\n\nLogons.\n", "change", "Created", "stream_ids", List.of(first.raw())));
        promote(first.build());

        // ### The next version's build: its catalogue the promoted one, every event type confirmed.
        final String next = "cat-life-v2";
        agreed("copy_pipeline", args("build", next, "source_uuid", first.pipeline(), "new_name",
                first.feed() + "-Events-V1.1"));
        final Map<String, Object> listed = json(CATALOGUE, args("build", next, "stream_ids",
                List.of(first.raw())));
        dump("next version listed", listed);
        assertThat(countsOf(listed)).containsOnlyKeys("Authenticate-Logon", "Authenticate-Logoff");
        assertThat(eventTypesOf(next).values()).allSatisfy(type -> assertThat(type)
                .containsEntry("status", "confirmed"));

        // ### Changed, removed and added: the review shows each.
        proposed(next, List.of(first.raw()), List.of(
                args("op", "change", "type_id", "Authenticate-Logoff", "description", "A user signed out"),
                args("op", "remove", "type_id", "Authenticate-Logon"),
                withKeys(eventType("Authenticate-Signin", "Authenticate", "high", when("action", "equals",
                        "login")), "action", "Logon", "actor", "user")));
        final int formsBefore = client.forms().size();
        final List<String> shown = new ArrayList<>();
        Map<String, Object> step = json(CATALOGUE, args("build", next, "confirm", true));
        shown.add(jsonOf(step));
        for (int round = 0; round < 6 && !"confirmed".equals(step.get("status")); round++) {
            step = json(CATALOGUE, args("build", next, "confirm", true, "catch_all", Map.of("allow_unknown",
                    "E2E: kept as Unknown")));
            shown.add(jsonOf(step));
        }
        shown.add(formsText(formsSince(formsBefore)));
        final String review = String.join("\n", shown);
        assertThat(review).as("the review of what changed since the promoted catalogue")
                .contains("added", "Authenticate-Signin", "changed", "Authenticate-Logoff", "removed",
                        "Authenticate-Logon");
    }

    // Seen in eval case 70: the next version catalogued before copy_pipeline found no catalogue and started over,
    // the confirmed event types lost. The promoted catalogue beside the feed is carried in either way, once.
    @Test
    void aNextVersionCataloguedBeforeItsPipelineIsCopiedStartsFromThePromotedCatalogue() {
        final Translated first = translated("cat-life-early-v1", "CAT-LIFE-EARLY");
        agreed("write_documentation", args("build", first.build(), "pipeline_uuid", first.pipeline(), "markdown",
                "## Purpose and data\n\nLogons.\n", "change", "Created", "stream_ids", List.of(first.raw())));
        promote(first.build());

        // ### The next version's build works from the promoted feed, and catalogues before any copy.
        final String next = "cat-life-early-v2";
        final List<Long> checkedOn = List.of(first.raw());
        surveyed(next, first.feed(), checkedOn);
        final Map<String, Object> listed = json(CATALOGUE, args("build", next, "stream_ids", checkedOn));
        dump("next version listed before copy_pipeline", listed);
        assertThat(String.valueOf(listed.get("carried"))).contains("carried into the build", "2 event type(s)");
        assertThat(countsOf(listed)).containsOnlyKeys("Authenticate-Logon", "Authenticate-Logoff");
        assertThat(eventTypesOf(next).values()).allSatisfy(type -> assertThat(type)
                .containsEntry("status", "confirmed"));
        assertThat(map(catalogueOf(next), "catch_all")).containsEntry("status", "confirmed");

        // ### An event type the promoted catalogue lacks (views, not in the sample) proposed as an addition; the rest
        // kept.
        final Map<String, Object> added = proposed(next, checkedOn, List.of(withKeys(eventType("View-Resource",
                "View", "high", when("action", "equals", "view")), "actor", "user", "affected", "resource",
                "affected_as", "Resource")));
        assertThat(countsOf(added)).containsKeys("Authenticate-Logon", "Authenticate-Logoff")
                .containsEntry("View-Resource", 0L);
        assertThat(eventTypesOf(next).get("Authenticate-Logon")).containsEntry("status", "confirmed");
        assertThat(eventTypesOf(next).get("View-Resource")).containsEntry("status", "proposed");

        // ### The pipeline copied later: the notes not carried again, nothing duplicated or lost.
        agreed("copy_pipeline", args("build", next, "source_uuid", first.pipeline(), "new_name",
                first.feed() + "-Events-V1.1"));
        assertThat(eventTypesOf(next)).containsOnlyKeys("Authenticate-Logon", "Authenticate-Logoff",
                "View-Resource");
        assertThat(eventTypesOf(next).get("Authenticate-Logoff")).containsEntry("status", "confirmed");
        final Map<String, Object> since = json(CATALOGUE, args("build", next, "stream_ids", checkedOn));
        assertThat(jsonOf(since.get("since_promoted"))).contains("added", "View-Resource");
    }

    @Test
    void aHandEditIsReadBackIntoTheCatalogueAndOneItCannotExpressIsNamed() {
        final Translated built = translated("cat-life-edit", "CAT-LIFE-EDIT");
        final String code = readXslt(built.xslt()).getData();
        assertThat(code).contains("'login'");

        // ### The logon's value edited by hand: read back, the catalogue's way of recognising it changed.
        editXslt(built.xslt(), doc -> doc.copy().data(code.replaceFirst("'login'", "'signin'")).build());
        final Map<String, Object> rebuilt = agreed("rebuild_mapping", args("uuid", built.xslt(), "stream_ids",
                List.of(built.raw()), "change", "Hand edit carried into the catalogue"));
        dump("rebuild_mapping", rebuilt);
        assertThat(rebuilt.get("saved")).as("rebuilt: %s", rebuilt).isNotNull();
        assertThat(jsonOf(list(eventTypesOf(built.build()).get("Authenticate-Logon"), "recognised_by")))
                .contains("signin")
                .doesNotContain("\"login\"");

        // ### An edit the catalogue can't express: named, the catalogue as it was.
        final String edited = readXslt(built.xslt()).getData();
        final String iterate = "<xsl:iterate select=\"data[@name='user']\"><xsl:param name=\"seen\" select=\"()\"/>"
                               + "<xsl:if test=\"not(@value = $seen)\"><Data Name=\"seen\" Value=\"{@value}\"/>"
                               + "</xsl:if><xsl:next-iteration><xsl:with-param name=\"seen\" "
                               + "select=\"($seen, @value)\"/></xsl:next-iteration></xsl:iterate>";
        editXslt(built.xslt(), doc -> doc.copy().data(edited.replaceFirst("</Authenticate>",
                Matcher.quoteReplacement(iterate + "</Authenticate>"))).build());
        final Map<String, Map<String, Object>> before = eventTypesOf(built.build());
        final Map<String, Object> unread = client.json("rebuild_mapping", args("uuid", built.xslt(),
                "stream_ids", List.of(built.raw())));
        assertThat(unread.get("saved")).isNull();
        assertThat(strings(unread, "unread")).anySatisfy(note -> assertThat(note).contains("iterate not read"));
        assertThat(eventTypesOf(built.build())).isEqualTo(before);
    }

    @Test
    void aHandWrittenLastRuleWritingARealEventIsReadBackAsTheCatchAll() {
        final String build = "cat-life-hand";
        final String feed = "CAT-LIFE-HAND";
        createFeed(build, feed);
        final long raw = upload(feed, CSV_SAMPLE);
        surveyed(build, feed, List.of(raw));
        final String converter = saveTextConverter(build, feed, CSV_SPLITTER);
        // The failed logons catalogued, the rest left to the catch-all, and the XSLT generated from the catalogue.
        proposed(build, List.of(raw), List.of(withKeys(eventType("Authenticate-FailedLogon", "Authenticate", "high",
                when("result", "equals", "fail")), "action", "Logon", "actor", "user", "success", "false")));
        confirmedCatalogue(build);
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "build", build, "source_name", "CatLife", "system_name", "CatLife", "environment", "Test"));
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping",
                timeChosen(map(draft, "mapping")), "stream_ids", List.of(raw), "build", build, "name",
                feed + "-Events", "schema_version", VERSION));
        assertThat(saved.get("ok")).as("XSLT saved: %s", saved.get("problems")).isEqualTo(true);
        final String uuid = uuidOf(map(saved, "saved"));
        createPipeline(build, feed + "-Events", templateUuid("translation", "Event Data (Text)"),
                docProperty("dsParser", "textConverter", converter, "TextConverter"),
                docProperty("translationFilter", "xslt", uuid, "XSLT"));
        // Edited by hand in Stroom: the catch-all's rule, taking every record left, writes a logon (not Unknown).
        // A whole hand-written XSLT (its own record template, a choose inside the Event) is not what rebuild_mapping
        // reads: it reads hand edits of the generated translation back into its mapping and catalogue.
        final String code = readXslt(uuid).getData();
        final int rest = code.indexOf("Rule 'uncatalogued'");
        assertThat(rest).as("the catch-all's rule in the generated XSLT:%n%s", code).isPositive();
        final String head = code.substring(0, rest);
        final String tail = code.substring(rest);
        final int from = tail.indexOf("<Unknown>");
        final int to = tail.indexOf("</Unknown>") + "</Unknown>".length();
        assertThat(from).as("the catch-all writes Unknown:%n%s", tail).isPositive();
        final String logon = tail.substring(0, from).replaceFirst("<TypeId>Unknown</TypeId>",
                "<TypeId>Authenticate-Logon</TypeId>")
                             + "<Authenticate><Action>Logon</Action><User><Id><xsl:value-of "
                             + "select=\"data[@name='user']/@value\"/></Id></User></Authenticate>"
                             + tail.substring(to);
        assertThat(logon).as("its TypeId written as a logon's:%n%s", tail).contains("Authenticate-Logon");
        editXslt(uuid, doc -> doc.copy().data(head + logon).build());

        final Map<String, Object> rebuilt = agreed("rebuild_mapping", args("uuid", uuid, "stream_ids",
                List.of(raw), "change", "The hand-written translation read into the catalogue"));
        dump("rebuild_mapping hand-written", rebuilt);
        assertThat(rebuilt.get("saved")).as("rebuilt: %s", rebuilt).isNotNull();
        assertThat(jsonOf(rebuilt)).as("the review names the catch-all").contains("catch-all");
        final Map<String, Object> catalogue = catalogueOf(build);
        assertThat(list(catalogue, "event_types")).extracting(type -> type.get("type_id"))
                .containsExactly("Authenticate-FailedLogon");
        assertThat(jsonOf(catalogue.get("catch_all"))).as("the catch-all, with its own translation")
                .contains("Authenticate-Logon", "Authenticate");
    }

    // A feed and build translated from the catalogue (logons and logoffs confirmed), its pipeline stepped clean.
    private Translated translated(final String build, final String feed) {
        createFeed(build, feed);
        final long raw = upload(feed, SAMPLE);
        surveyed(build, feed, List.of(raw));
        final Map<String, Object> converter = map(agreed("build_data_splitter", args("stream_ids", List.of(raw),
                "save_as", feed + "-CSV", "build", build)), "saved");
        proposed(build, List.of(raw), List.of(
                withKeys(eventType("Authenticate-Logon", "Authenticate", "high", when("action", "equals", "login")),
                        "action", "Logon", "actor", "user"),
                withKeys(eventType("Authenticate-Logoff", "Authenticate", "high", when("action", "equals",
                        "logout")), "action", "Logoff", "actor", "user")));
        confirmedCatalogue(build);
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "build", build, "source_name", "CatLife", "system_name", "CatLife", "environment", "Test"));
        final Map<String, Object> saved = agreed("build_translation_xslt", args("mapping",
                timeChosen(map(draft, "mapping")), "stream_ids", List.of(raw), "build", build, "name",
                feed + "-Events", "schema_version", VERSION));
        assertThat(saved.get("ok")).as("XSLT saved: %s", saved.get("problems")).isEqualTo(true);
        final String xslt = uuidOf(map(saved, "saved"));
        final String pipeline = (String) createPipeline(build, feed + "-Events",
                templateUuid("translation", "Event Data (Text)"),
                docProperty("dsParser", "textConverter", (String) converter.get("uuid"), "TextConverter"),
                docProperty("translationFilter", "xslt", xslt, "XSLT")).get("uuid");
        assertThat(stepSample(pipeline, List.of(raw)).get("verdict")).as("the sample steps clean")
                .isEqualTo("clean");
        return new Translated(build, feed, raw, xslt, pipeline);
    }

    // The Unknown events of the streams processed.
    private long unknownsIn(final Map<String, Object> done) {
        long unknowns = 0;
        for (final Map<String, Object> stream : list(done, "streams")) {
            for (final Long events : ids(stream, "events")) {
                final List<Element> written = children(parse(storedText(events)).getDocumentElement(), "Event");
                unknowns += written.stream().filter(event -> at(event, "EventDetail/Unknown").isPresent()).count();
            }
        }
        return unknowns;
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees.
    private void promote(final String build) {
        client.answeringWith(McpTestClient::agree);
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                DESTINATIONS));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                DESTINATIONS, "reviewed", review.get("review_id")));
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
    }


    // --------------------------------------------------------------------------------


    // A build translated from its catalogue.
    private record Translated(String build, String feed, long raw, String xslt, String pipeline) {

    }
}
