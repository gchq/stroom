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

import stroom.mcp.impl.tools.BuildRecords;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;

import io.modelcontextprotocol.spec.McpSchema.ElicitFormRequest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// What promotion reads from a build's name and its record, on a real Stroom through the REAL tools (chat 195): a
/// build name the workspace hasn't got is refused naming the user's builds, never shown as a new build's empty
/// plan; and a role location recorded before typed answers were checked that is no folder ("System/feed's
/// location") is dropped and asked again, never laid out as the user's choice.
class TestE2ePromotionRecords extends AbstractE2eTest {

    private static final String RAW = "Raw Events";

    @Inject
    private BuildRecords buildRecords;

    @Test
    void aBuildNameTheWorkspaceHasntGotIsRefusedNamingTheUsersBuilds() {
        final String build = "promrec-v1-0";
        final String feed = "PROMREC-A";
        handWritten("System/Feeds/PROMREC-A", feed, textResource("hand_written_vpn.xsl"), "PROMREC-A-Events",
                "Event Data (JSON)", jsonProperties(), RAW, vpnSample());
        json("start_build", args("build", build, "feeds", List.of(feed)));

        // Seen in chat 195: "endpoint-logs" for the build "endpoint-logs-v1.0" gave "step 1 of 16, create the feed".
        final String status = refusal("build_status", args("build", "promrec"));
        assertThat(status).contains("No build 'promrec' in the workspace").contains(build)
                .doesNotContain("create_feed");
        assertThat(refusal("promote_build", args("build", "promrec"))).contains("No build 'promrec' in the "
                                                                                + "workspace", build);
        // The build that is there has its plan.
        assertThat(json("build_status", args("build", build))).containsKey("next");
    }

    @Test
    void aRecordedRoleLocationThatIsNoFolderIsDroppedAndAskedAgain() {
        final String name = "PROMLOC";
        final String build = "promloc-v1-1";
        // Its event types named Verb-Object, as a catalogue confirms them.
        final String xslt = textResource("hand_written_vpn.xsl")
                .replace("<TypeId>vpn-login-failed</TypeId>", "<TypeId>Fail-Logon</TypeId>")
                .replace("<TypeId>vpn-login</TypeId>", "<TypeId>Logon-User</TypeId>")
                .replace("<TypeId>vpn-logout</TypeId>", "<TypeId>Logoff-User</TypeId>")
                .replace("<TypeId>vpn-config-change</TypeId>", "<TypeId>Change-Config</TypeId>");
        final HandWritten written = handWritten("System/Feeds/" + name, name, xslt, name + "-Events",
                "Event Data (JSON)", jsonProperties(), RAW, vpnSample());
        // A new version of the pipeline, copied into a build of its own.
        agreed("copy_pipeline", args("build", build, "source_uuid", written.pipeline(), "new_name",
                name + "-Events-V1.1"));
        // The copy's XSLT read back into its catalogue, confirmed as promotion asks first.
        final String copiedXslt = list(json("build_status", args("build", build, "full", true)), "documents")
                .stream()
                .filter(doc -> "XSLT".equals(doc.get("type")))
                .map(doc -> String.valueOf(doc.get("uuid")))
                .findFirst()
                .orElseThrow();
        agreed("rebuild_mapping", args("uuid", copiedXslt, "stream_ids", List.of(written.raw())));
        surveyed(build, name, List.of(written.raw()));
        // Listed with its check against the sample first, as the tools ask, then confirmed.
        json(CATALOGUE, args("build", build, "stream_ids", List.of(written.raw())));
        CatalogueSteps.confirmedCatalogue(client, build);
        final Map<String, Object> first = json("promote_build", args("build", build, "destinations", Map.of()));
        dump("promote_build first review", first);
        final List<Map<String, Object>> roles = list(first, "role_locations");
        assertThat(roles).as("the review lays out each role's location: %s", first).isNotEmpty();
        final String role = String.valueOf(roles.getFirst().get("role"));
        // As an earlier chat recorded it, before typed answers were checked.
        asUser(admin, () -> buildRecords.rememberPromotionRoot(build, role, "System/feed's location", false));

        final int before = client.forms().size();
        final Map<String, Object> review = json("promote_build", args("build", build, "destinations", Map.of()));
        dump("promote_build review", review);

        assertThat(String.valueOf(review.get("layout"))).as("the layout: %s", review)
                .doesNotContain("feed's location");
        final List<ElicitFormRequest> asked = formsSince(before);
        assertThat(asked.stream().map(ElicitFormRequest::message).toList())
                .anyMatch(message -> message.contains("The location recorded for " + role + ", 'System/feed's "
                                                      + "location', is no folder path"));
        assertThat(asUser(admin, () -> buildRecords.promotionRoots(build)).get(role)).isNotEqualTo(
                "System/feed's location");
    }

    private static List<PipelineProperty> jsonProperties() {
        return List.of(PipelineProperty.builder().element("jsonParser").name("addRootObject")
                .value(new PipelinePropertyValue(false)).build());
    }

    // A record of each kind of event the hand-written XSLT translates: a read-back event type no sample record
    // recognises can't have its placements checked, so its catalogue can't be confirmed.
    private static String vpnSample() {
        return "[{\"ts\": \"2026-11-03T08:00:01.000Z\", \"action\": \"login\", \"user\": \"jbloggs\", \"src_ip\": "
               + "\"198.51.100.7\", \"host\": \"vpn-gw-1\", \"result\": \"success\"},\n"
               + " {\"ts\": \"2026-11-03T08:02:45.000Z\", \"action\": \"login\", \"user\": \"asmith\", \"src_ip\": "
               + "\"198.51.100.9\", \"host\": \"vpn-gw-1\", \"result\": \"failure\"},\n"
               + " {\"ts\": \"2026-11-03T12:10:00.000Z\", \"action\": \"logout\", \"user\": \"jbloggs\", \"src_ip\": "
               + "\"198.51.100.7\", \"host\": \"vpn-gw-1\"},\n"
               + " {\"ts\": \"2026-11-03T09:30:00.000Z\", \"action\": \"config_change\", \"user\": \"admin\", "
               + "\"host\": \"vpn-gw-1\", \"setting\": \"idle_timeout\", \"new_value\": \"900\"}]";
    }
}
