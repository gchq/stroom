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
import stroom.mcp.impl.McpTestClient;
import stroom.mcp.impl.McpTestClients;
import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/// A build's draft translation mapping is kept with the build, for the user: a new MCP session of the same user
/// (e.g. one their client started again after giving up on a call, seen in eval case 36) applies changes= to it;
/// another user's session never gets it.
class TestE2eDraftsAcrossSessions extends AbstractE2eTest {

    private static final String SAMPLE = "time,user,host,action,resource\n"
                                         + "2026-10-01T10:00:00,alice,ws01,login,\n"
                                         + "2026-10-01T10:05:00,bob,ws02,logout,\n"
                                         + "2026-10-01T10:06:00,alice,ws01,login,\n"
                                         + "2026-10-01T10:09:00,bob,ws02,logout,\n";

    @Inject
    private McpTestClients clients;

    @Test
    void aNewSessionOfTheUserAppliesChangesToTheBuildsDraftAndAnotherUsersDoesnt() {
        final String suffix = UUID.randomUUID().toString().substring(0, 8);
        final String build = "drafts-" + suffix;
        final String feed = "DRAFTS-" + suffix.toUpperCase();
        createFeed(build, feed);
        final long raw = upload(feed, SAMPLE);
        surveyed(build, feed, List.of(raw));
        agreed("build_data_splitter", args("stream_ids", List.of(raw), "save_as", feed + "-CSV", "build", build));
        proposed(build, List.of(raw), List.of(
                withKeys(eventType("Authenticate-Logon", "Authenticate", "high", when("action", "equals", "login")),
                        "action", "Logon", "actor", "user"),
                withKeys(eventType("Authenticate-Logoff", "Authenticate", "high", when("action", "equals",
                        "logout")), "action", "Logoff", "actor", "user")));
        confirmedCatalogue(build);
        // Drafted in this session.
        final Map<String, Object> draft = json("draft_translation_mapping", args("stream_ids", List.of(raw),
                "build", build, "source_name", "Drafts", "system_name", "Drafts", "environment", "Test"));
        final Map<String, Object> changes = timeChosenChanges(draftedMapping(draft));

        // Another user, an administrator too (so they can see the build), in a session of their own: no draft.
        final UserRef other = user("drafts-other-" + suffix, Set.of(AppPermission.ADMINISTRATOR));
        toolPolicy("Everything " + suffix, AiToolAccess.READ_WRITE, other);
        final McpTestClient theirs = clients.client(other, true);
        final String refused = McpTestClient.text(theirs.call("build_translation_xslt", args("build", build,
                "name", feed + "-Events", "stream_ids", List.of(raw), "schema_version", VERSION, "changes",
                changes)));
        assertThat(refused).as("another user's session").contains("you have no draft for build " + build);

        // A new session of the same user: the build's draft, from the earlier session, takes the changes.
        client = clients.client(admin, true);
        final Map<String, Object> saved = agreed("build_translation_xslt", args("build", build, "name",
                feed + "-Events", "stream_ids", List.of(raw), "schema_version", VERSION, "changes", changes));
        dump("build_translation_xslt in a new session", saved);
        assertThat(saved.get("ok")).as("saved: %s", saved.get("problems")).isEqualTo(true);
        assertThat(saved.get("changes_applied_to")).isEqualTo("the draft draft_translation_mapping made");
        assertThat((String) saved.get("changes_applied_to_one"))
                .startsWith("drafted ")
                .endsWith(" in an earlier session of yours, the latest for build " + build);
    }
}
