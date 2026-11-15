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
import stroom.ai.impl.tools.sample.SourceTypeRows;
import stroom.data.shared.StreamTypeNames;
import stroom.data.store.api.InputStreamProvider;
import stroom.data.store.api.OutputStreamProvider;
import stroom.data.store.api.Source;
import stroom.data.store.api.Store;
import stroom.data.store.api.Target;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.MetaProperties;
import stroom.security.api.SecurityContext;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// An aggregated feed whose source types are told apart by a stream meta attribute, not a field of the records,
/// walked through the REAL tools: hosts' security events (synthetic, `aggregated/windows-meta/`, made by its
/// windows-meta-generator.py), a part a host, aggregated into streams of several parts, each part carrying its host's
/// receipt headers. A server's `WindowsServerInstalledServedRoles` lists its roles; a workstation sends none. Only
/// the directory controllers' events are wanted, and a processor filter can't read a part's headers, so the earliest
/// cull is the group's XSLT, a part at a time.
///
/// The build surveys the feed with the attribute named (`meta:<Attribute>`, never found unasked), each part's
/// records counted by its value; the registry's rows are list-member rows (`AD DS ∈ Active Directory Domain
/// Services`, and `DNS in DNS Server` in ASCII); a group of AD DS is classified, drafted and built, its pipeline
/// stepped and processed: only the controllers' parts' records become Events, every other part's passed over whole
/// and counted in the group's lines; coverage is reviewed against the group.
@SuppressWarnings("unchecked")
class TestE2eMetaSourceTypes extends AbstractE2eTest {

    private static final String ROLES = "WindowsServerInstalledServedRoles";
    private static final String DIRECTORY = "Active Directory Domain Services";
    private static final String FEED = "E2E-WALK-WINDOWS";
    private static final String RULES = """
            {"ignored_fields": ["Channel", "Provider"],
             "events": [
              {"name": "logon", "when": [{"field": "EventID", "equals": "4624"}],
               "fields": [{"path": "EventDetail/TypeId", "value": "Logon"},
                          {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                          {"path": "EventDetail/Authenticate/User/Id", "field": "TargetUserName"},
                          {"path": "EventDetail/Authenticate/Outcome/Success", "value": "true"}]},
              {"name": "logon_failed", "when": [{"field": "EventID", "equals": "4625"}],
               "fields": [{"path": "EventDetail/TypeId", "value": "LogonFailed"},
                          {"path": "EventDetail/Authenticate/Action", "value": "Logon"},
                          {"path": "EventDetail/Authenticate/User/Id", "field": "TargetUserName"},
                          {"path": "EventDetail/Authenticate/Outcome/Success", "value": "false"}]},
              {"name": "other", "allow_unknown": "ticket requests, for a later build",
               "fields": [{"path": "EventDetail/TypeId", "field": "EventID"},
                          {"path": "EventDetail/Unknown/Data", "data_name": "user",
                           "field": "TargetUserName"}]}]}""";

    @Inject
    private Store streamStore;
    @Inject
    private SecurityContext securityContext;

    @Test
    void onlyTheDirectoryControllersPartsBecomeEventsTheOthersPassedOverWholeByTheirMeta() {
        final String folder = "E2E Feeds/WalkWINDOWS";
        final String template = templateUuid("translation", "Event Data (JSON)");
        createFeed("walk-src-windows", FEED);
        promote("walk-src-windows", Map.of("Feed", folder));
        final List<List<Map<String, Object>>> aggregates = (List<List<Map<String, Object>>>) jsonResource(
                TestE2eSourceTypeGroups.SAMPLES + "windows-meta/windows-meta.json").get("streams");
        final List<Long> streams = new ArrayList<>();
        aggregates.forEach(parts -> streams.add(sendParts(parts)));
        final long directoryRecords = records(aggregates, true);
        final long otherRecords = records(aggregates, false);
        assertThat(directoryRecords).isPositive();
        assertThat(otherRecords).isGreaterThan(directoryRecords);
        eachPartsHeadersAreReadAsThePartWasReceived(streams, aggregates);

        final String build = "walk-windows-1";
        json("start_build", args("build", build, "feeds", List.of(FEED)));
        // Named as the user says it, in any case: spelt as the parts spell it; each part's records counted by its
        // value.
        final Map<String, Object> surveyed = json("survey_feed", args("feed", FEED, "build", build,
                "source_type_field", "meta:" + ROLES.toLowerCase()));
        dump("survey_feed", surveyed);
        final Map<String, Object> sources = map(surveyed, "source_types");
        assertThat(sources).containsEntry("field", "meta:" + ROLES).containsEntry("by", "user");
        assertThat(map(sources, "records_read_by_value")).containsKey("DNS Server, " + DIRECTORY + ", File Server")
                .containsKey(DIRECTORY + ", DNS Server").containsKey("File Server");
        // An attribute no part carries is refused, saying which they carry.
        assertThat(refusal("survey_feed", args("feed", FEED, "build", build, "stream_ids", streams,
                "source_type_field", "meta:ServerRoles"))).contains("none of the", ROLES, "RemoteHost");

        // List-member rows: a controller's list holds DNS Server too, so AD DS goes first.
        final List<String> given = List.of("AD DS " + SourceTypeRows.MEMBER_SIGN + " " + DIRECTORY,
                "DNS in DNS Server");
        final Map<String, Object> shown = json("record_source_types", args("build", build, "feed", FEED,
                "system_name", "Windows", "environment", "Prod", "rows", given));
        assertThat(shown).containsEntry("status", "needs_review");
        assertThat(strings(shown, "rows")).containsExactly("AD DS ∈ " + DIRECTORY, "DNS ∈ DNS Server");
        client.answeringWith(TestE2eSourceTypeGroups.scopeOf(List.of("AD DS"), null));
        final Map<String, Object> written = json("record_source_types", args("build", build, "feed", FEED,
                "system_name", "Windows", "environment", "Prod", "reviewed", shown.get("review_id"), "rows",
                shown.get("rows"), "group", "Directory"));
        dump("record_source_types", written);
        assertThat(strings(written, "scope")).containsExactly("AD DS");

        classified(build, streams);
        final Map<String, Object> draft = agreed("draft_translation_mapping", args("stream_ids", streams,
                "build", build));
        dump("draft_translation_mapping", draft);
        // Drafted from the controllers' parts alone.
        assertThat(map(draft, "scope")).containsEntry("records_in_scope", (int) directoryRecords);
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", build, "feeds",
                List.of(FEED), "group", "Directory", "source_types", List.of("AD DS"), "schema_version", VERSION,
                "stream_ids", streams, "mapping", withRules(map(draft, "mapping"))));
        dump("build_translation_xslt", saved);
        assertThat(saved).as("saved: %s", saved.get("problems")).containsEntry("ok", true);
        final Map<String, Object> registry = SourceTypeRegistry.read(documentation(uuidOf(map(map(saved, "group"),
                "registry"))));
        assertThat(registry).containsEntry("field", "meta:" + ROLES);
        assertThat(list(registry, "rows")).first().satisfies(row -> assertThat(row)
                .containsEntry("match", SourceTypeRows.MEMBER).containsEntry("separator", ","));
        final String xslt = readXslt(uuidOf(map(map(saved, "group"), "xslt"))).getData();
        assertThat(xslt).contains("stroom:meta('" + ROLES + "')", "tokenize($value, ',')");

        final Map<String, Object> made = agreed("create_pipeline", args("build", build, "group", "Directory",
                "template_uuid", template));
        final String pipeline = uuidOf(made);
        final Map<String, Object> stepped = stepSample(pipeline, streams);
        assertThat(stepped).as("stepped clean: %s", stepped).containsEntry("verdict", "clean");
        final Map<String, Object> done = process(pipeline, streams);
        dump("processed", done);
        assertThat(list(done, "problems")).as("%s", done).isEmpty();
        long events = 0;
        long passed = 0;
        for (final Map<String, Object> each : list(done, "streams")) {
            for (final long id : ids(each, "events")) {
                events += TestE2eSourceTypeGroups.count(streamText(id), "<Event>");
            }
            final List<Long> errors = ids(each, "errors");
            final String logged = errors.isEmpty()
                    ? ""
                    : streamText(errors.getFirst());
            passed += TestE2eSourceTypeGroups.passedOver(logged);
            assertThat(logged.lines().filter(line -> line.contains("WARN") || line.contains("ERROR"))).isEmpty();
        }
        // Each controller's record an Event; every other part's passed over whole, nothing logged of them
        // (review_coverage counts them from the raw streams' parts' meta).
        assertThat(events).isEqualTo(directoryRecords);
        assertThat(passed).isZero();

        final Map<String, Object> review = json("review_coverage", args("pipeline_uuid", pipeline, "follow_on",
                false));
        dump("review_coverage", review);
        final Map<String, Object> reviewed = map(review, "source_types");
        assertThat(reviewed).containsEntry("group", "Directory").containsEntry("field", "meta:" + ROLES)
                .doesNotContainKeys("new_source_types", "unseen_source_types");
        assertThat(number(map(map(reviewed, "processing"), "passed_over").get("records"))).isEqualTo(otherRecords);
        assertThat(list(review, "missed")).allSatisfy(kind -> assertThat(kind).containsEntry("source_type",
                "AD DS"));
    }

    // --- the steps ---

    // fetch_events childType 'Meta Data' and stream_meta parts:true give each part's own receipt headers. Seen (eval
    // 59): every part read as part 0's headers, and stream_meta's parts "couldn't be read from the stream store".
    private void eachPartsHeadersAreReadAsThePartWasReceived(final List<Long> streams,
                                                             final List<List<Map<String, Object>>> aggregates) {
        for (int s = 0; s < streams.size(); s++) {
            final List<Map<String, Object>> parts = aggregates.get(s);
            for (int part = 0; part < parts.size(); part++) {
                final Map<String, Object> read = json("fetch_events", args("streamId", streams.get(s),
                        "childType", StreamTypeNames.META, "part", part, "recordCount", 1));
                assertThat(strings(read, "records")).as("part %s of stream %s", part, streams.get(s))
                        .singleElement().asString()
                        .contains("RemoteHost:" + ((Map<String, Object>) parts.get(part).get("meta"))
                                .get("RemoteHost"));
            }
            // A range of parts, a record each, in order.
            final List<String> all = strings(json("fetch_events", args("streamId", streams.get(s),
                    "childType", StreamTypeNames.META, "recordCount", 10)), "records");
            assertThat(all).hasSize(parts.size());
            for (int part = 0; part < parts.size(); part++) {
                final Object roles = ((Map<String, Object>) parts.get(part).get("meta")).get(ROLES);
                if (roles != null) {
                    assertThat(all.get(part)).contains(ROLES + ":" + roles);
                } else {
                    // A workstation sends no roles.
                    assertThat(all.get(part)).doesNotContain(ROLES);
                }
            }
        }
        final String table = replyText("stream_meta", args("feeds", List.of(FEED), "parts", true,
                "attributes", List.of(ROLES, "RemoteHost")));
        dump("stream_meta parts", table);
        assertThat(table).doesNotContain("couldn't be read");
        aggregates.stream().flatMap(List::stream).forEach(part -> assertThat(table)
                .contains("| " + ((Map<String, Object>) part.get("meta")).get("RemoteHost") + " |"));
    }

    // classify_kinds as the agent does it with the user: each kind the rules name as they make it, the rest Unknown.
    private void classified(final String build, final List<Long> streams) {
        final Map<String, Object> listed = json("classify_kinds", args("build", build, "stream_ids", streams));
        final List<Map<String, Object>> kinds = list(listed, "to_classify");
        if (kinds.isEmpty()) {
            return;
        }
        final List<Map<String, Object>> proposals = new ArrayList<>();
        for (final Map<String, Object> kind : kinds) {
            final Map<String, Object> proposal = new LinkedHashMap<>();
            proposal.put("field", kind.get("field"));
            proposal.put("value", kind.get("value"));
            proposal.put("description", String.valueOf(kind.get("value")));
            final boolean logon = List.of("4624", "4625").contains(String.valueOf(kind.get("value")));
            proposal.put("event_detail", logon
                    ? "Authenticate"
                    : "Unknown");
            if (logon) {
                proposal.put("action", "Logon");
            }
            proposal.put("from_user", true);
            proposals.add(proposal);
        }
        json("classify_kinds", args("build", build, "proposals", proposals));
    }

    // The draft's mapping with the agent's rules: its common entries kept, a time it couldn't choose chosen (the
    // first candidate), its rules replaced.
    private static Map<String, Object> withRules(final Map<String, Object> drafted) {
        final Map<String, Object> mapping = new LinkedHashMap<>(drafted);
        final List<Map<String, Object>> common = new ArrayList<>();
        for (final Map<String, Object> entry : list(mapping, "common")) {
            final Map<String, Object> kept = new LinkedHashMap<>(entry);
            if (kept.remove("choose_from") instanceof final List<?> candidates) {
                kept.put("field", candidates.getFirst());
            }
            common.add(kept);
        }
        mapping.put("common", common);
        mapping.putAll(TestE2eSourceTypeGroups.jsonValue(RULES));
        return mapping;
    }

    // promote_build: the layout shown first (needs_review), then promoted once the user agrees in the form.
    private void promote(final String build, final Map<String, Object> destinations) {
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations",
                destinations));
        assertThat(review).as("the layout first: %s", review).containsEntry("status", "needs_review");
        final Map<String, Object> promoted = json("promote_build", args("build", build, "destinations",
                destinations, "reviewed", review.get("review_id")));
        assertThat(promoted).as("promoted: %s", promoted).containsKey("promoted");
    }

    // --- the data ---

    // One stream of parts, as a proxy aggregates them: each part a host's records and its receipt headers.
    private long sendParts(final List<Map<String, Object>> parts) {
        return securityContext.asProcessingUserResult(() -> {
            final MetaProperties properties = MetaProperties.builder()
                    .feedName(FEED)
                    .typeName(StreamTypeNames.RAW_EVENTS)
                    .createMs(System.currentTimeMillis())
                    .build();
            try (final Target target = streamStore.openTarget(properties)) {
                for (final Map<String, Object> part : parts) {
                    try (final OutputStreamProvider provider = target.next()) {
                        try (final OutputStream data = provider.get()) {
                            data.write(text(part).getBytes(StandardCharsets.UTF_8));
                        }
                        final AttributeMap attributes = new AttributeMap();
                        ((Map<String, Object>) part.get("meta")).forEach((key, value) -> attributes.put(key,
                                String.valueOf(value)));
                        try (final OutputStream meta = provider.get(StreamTypeNames.META)) {
                            AttributeMapUtil.write(attributes, meta);
                        }
                    }
                }
                return target.getMeta().getId();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    // A part's records, a JSON line each.
    private static String text(final Map<String, Object> part) {
        return String.join("\n", (List<String>) part.get("lines")) + "\n";
    }

    // The records of the parts whose roles hold the directory's (or of every other part).
    private static long records(final List<List<Map<String, Object>>> aggregates, final boolean directory) {
        return aggregates.stream()
                .flatMap(List::stream)
                .filter(part -> String.valueOf(((Map<String, Object>) part.get("meta")).get(ROLES))
                                        .contains(DIRECTORY) == directory)
                .mapToLong(part -> ((List<?>) part.get("lines")).size())
                .sum();
    }

    // A stream's text, as Stroom stores it: every part's.
    private String streamText(final long id) {
        return securityContext.asProcessingUserResult(() -> {
            try (final Source source = streamStore.openSource(id)) {
                final StringBuilder out = new StringBuilder();
                for (long part = 0; part < source.count(); part++) {
                    try (final InputStreamProvider provider = source.get(part);
                            final InputStream in = provider.get()) {
                        out.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
                return out.toString();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
