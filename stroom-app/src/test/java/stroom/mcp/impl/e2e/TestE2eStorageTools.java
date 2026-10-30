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

import stroom.ai.shared.AiToolAccess;
import stroom.data.retention.impl.DataRetentionRulesService;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.store.impl.fs.FsVolumeService;
import stroom.data.store.impl.fs.shared.FindFsVolumeCriteria;
import stroom.data.store.impl.fs.shared.FsVolume;
import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.mcp.impl.McpTestClient;
import stroom.mcp.impl.McpTestClients;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;
import stroom.util.shared.time.TimeUnit;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// The storage tools against a real Stroom: volumes and explain_retention read its volumes, data retention rules,
/// streams (with the rule the Data screen gives each) and the impact query, and explain_processing finds a default
/// data volume group that can't be written to. Asked for after the agent, asked what happens when the data volumes
/// fill, said Stroom deletes the oldest streams.
class TestE2eStorageTools extends AbstractE2eTest {

    private static final String SAMPLE = "time,user,action\n2026-10-30T10:00:00Z,alice,login\n";

    @Inject
    private FsVolumeService fsVolumeService;
    @Inject
    private DataRetentionRulesService rulesService;
    @Inject
    private McpTestClients clients;

    // The limited user's client, made once a test asks for it.
    private McpTestClient limited;

    @Test
    void volumesGivesEachVolumeToAnAdministratorAndOnlyWhetherGroupsCanBeWrittenToOtherwise() {
        final List<FsVolume> volumes = dataVolumes();
        assertThat(volumes).as("the test Stroom's data volumes").isNotEmpty();

        final Map<String, Object> full = json("volumes", args());
        dump("volumes", full);
        assertThat(full).doesNotContainKey("view");
        assertThat(list(full, "data_volume_groups")).anySatisfy(group -> assertThat(group)
                .containsEntry("writable", true)
                .containsKey("volumes"));
        assertThat(String.valueOf(full.get("data_volume_groups"))).contains(volumes.getFirst().getPath());
        assertThat(String.valueOf(map(full, "behaviour").get("data_volumes")))
                .contains("Nothing is deleted to make room");

        final Map<String, Object> reduced = limitedClient().json("volumes", args());
        dump("volumes, reduced", reduced);
        assertThat(String.valueOf(reduced.get("view"))).startsWith("Reduced view");
        assertThat(String.valueOf(reduced)).doesNotContain(volumes.getFirst().getPath());
        assertThat(list(reduced, "data_volume_groups")).allSatisfy(group -> assertThat(group)
                .containsKeys("writable", "volume_count")
                .doesNotContainKey("volumes"));
    }

    @Test
    void explainProcessingFindsADefaultDataVolumeGroupThatCantBeWrittenTo() {
        final List<FsVolume> volumes = dataVolumes();
        assertThat(check(json("explain_processing", args()), "volumes")).containsEntry("state", "ok");
        try {
            // Every data volume made Inactive: no stream can be written to them.
            volumes.forEach(volume -> asUser(admin, () -> fsVolumeService.update(volume.copy()
                    .status(VolumeUseStatus.INACTIVE)
                    .build())));

            final Map<String, Object> result = json("explain_processing", args());
            dump("explain_processing, volumes inactive", result);

            final Map<String, Object> volumesCheck = check(result, "volumes");
            assertThat(volumesCheck).containsEntry("state", "blocking");
            assertThat(String.valueOf(volumesCheck.get("finding")))
                    .contains("has no volume a stream can be written to");
            assertThat(String.valueOf(volumesCheck.get("fix"))).contains("deletes nothing to make room");
            // The volumes tool agrees, for any user.
            assertThat(list(limitedClient().json("volumes", args("kind", "data")), "data_volume_groups"))
                    .anySatisfy(group -> assertThat(group).containsEntry("default", true)
                            .containsEntry("writable", false));
        } finally {
            dataVolumes().forEach(volume -> asUser(admin, () -> fsVolumeService.update(volume.copy()
                    .status(VolumeUseStatus.ACTIVE)
                    .build())));
        }
        assertThat(check(json("explain_processing", args()), "volumes")).containsEntry("state", "ok");
    }

    @Test
    void explainRetentionGivesTheRulesInOrderAndTheRuleThatKeepsAStream() {
        final String feed = "RETENTION-SHORT";
        createFeed("retention", feed);
        final long stream = upload(feed, SAMPLE);
        final DataRetentionRules before = asUser(admin, () -> rulesService.getOrCreate());
        try {
            asUser(admin, () -> rulesService.writeDocument(before.copy().rules(List.of(
                    DataRetentionRule.ageRule(1, 0L, "Short-lived", true, ExpressionOperator.builder()
                            .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                            .build(), 1, TimeUnit.DAYS),
                    DataRetentionRule.foreverRule(2, 0L, "Keep everything else", true,
                            ExpressionOperator.builder()
                                    .addTextTerm(MetaFields.FEED, Condition.EQUALS, "*")
                                    .build()))).build()));

            final Map<String, Object> result = json("explain_retention", args("stream_id", stream,
                    "impact", true));
            dump("explain_retention", result);

            assertThat(list(result, "rules")).extracting(rule -> rule.get("name"))
                    .containsExactly("Short-lived", "Keep everything else");
            assertThat(list(result, "streams")).singleElement().satisfies(row -> assertThat(row)
                    .containsEntry("feed", feed)
                    .containsEntry("rule", "1 Short-lived")
                    .containsEntry("keep", "1 Day")
                    .doesNotContainKey("overdue"));
            // The impact query ran on the real meta store: nothing is a day old yet.
            assertThat(result.get("impact")).isEqualTo(List.of());
            assertThat(map(result, "jobs")).containsKeys("Policy Based Data Retention", "Data Delete");
            assertThat(String.valueOf(result.get("behaviour"))).contains("Retention is by age alone")
                    .contains("never deletes data because a volume is full");

            // Without Manage Policies the rules are hidden, and the stream's feed isn't one the user can view.
            final Map<String, Object> reduced = limitedClient().json("explain_retention", args());
            dump("explain_retention, reduced", reduced);
            assertThat(String.valueOf(reduced.get("view"))).startsWith("Reduced view");
            assertThat(reduced).doesNotContainKey("rules");
            assertThat(String.valueOf(map(reduced, "jobs"))).contains("needs the Manage Jobs permission");
            assertThat(McpTestClient.text(limitedClient().call("explain_retention", args("stream_id", stream))))
                    .contains("No stream " + stream + " that you can view.");
        } finally {
            asUser(admin, () -> rulesService.writeDocument(rulesService.getOrCreate().copy()
                    .rules(before.getRules() == null
                            ? null
                            : new ArrayList<>(before.getRules()))
                    .build()));
        }
    }

    // --------------------------------------------------------------------------------

    private List<FsVolume> dataVolumes() {
        return asUser(admin, () -> fsVolumeService.find(FindFsVolumeCriteria.matchAll()).getValues());
    }

    // A user with no permission beyond using the tools, so each tool's reduced view.
    private McpTestClient limitedClient() {
        if (limited == null) {
            final UserRef user = user("storage-reader", Set.of(AppPermission.USE_MCP_TOOLS));
            toolPolicy("Storage reader", AiToolAccess.READ_ONLY, user);
            limited = clients.client(user, false);
        }
        return limited;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> check(final Map<String, Object> result, final String link) {
        return ((List<Map<String, Object>>) result.get("checks")).stream()
                .filter(check -> link.equals(check.get("link")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No check " + link + " in " + result));
    }
}
