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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;
import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/// create_feed, the tool.
class TestCreateFeedTool {

    private final FeedWorld feeds = new FeedWorld();
    private final CreateFeedTool tool = new CreateFeedTool(feeds.buildFeeds, feeds.world.guard(),
            feeds.world.records(), feeds.consent, feeds.plan, feeds.streamReader);

    @Test
    void theUserConfirmsTheFeedBeforeItIsMadeInTheBuild() {
        final String arguments = "{\"build\": \"onboard-acme\", \"name\": \"ACME-VPN-V1.0\", \"encoding\": "
                                 + "\"UTF-16LE\", \"description\": \"The VPN's audit log\"}";

        // A client that can't show forms can't ask the user: nothing is made, and no id is given to pass back.
        final ToolResult refused = call(arguments, BuildToolCalls.context());

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).startsWith("Not done: Create feed 'ACME-VPN-V1.0'. This needs the user's "
                                              + "agreement in a form")
                .doesNotContain("confirmation_id");
        assertThat(feeds.feedNamed("ACME-VPN-V1.0")).isNull();

        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of());
        final Map<String, Object> made = json(call(arguments, user.context()));

        assertThat(user.shown()).singleElement().asString().startsWith("Create feed 'ACME-VPN-V1.0'")
                .contains("- build: onboard-acme", "- feed name: ACME-VPN-V1.0", "- encoding: UTF-16LE",
                        "- stream type: Raw Events");

        final DocRef feed = feeds.feedNamed("ACME-VPN-V1.0");
        assertThat(feeds.world.guard().buildOf(feed)).contains("onboard-acme");
        assertThat(feeds.world.tags(feed.getUuid())).contains(WriteGuard.MANAGED, WriteGuard.GENERATED);
        final FeedDoc doc = feeds.feed(feed.getUuid());
        assertThat(doc.getEncoding()).isEqualTo("UTF-16LE");
        assertThat(doc.getStreamType()).isEqualTo("Raw Events");
        assertThat(doc.getDescription()).isEqualTo("The VPN's audit log");
        assertThat(made).containsEntry("type", "Feed").containsEntry("uuid", feed.getUuid())
                .containsEntry("name", "ACME-VPN-V1.0").containsEntry("stream_type", "Raw Events")
                .containsEntry("encoding", "UTF-16LE").containsKey("next");
        assertThat(String.valueOf(made.get("link"))).contains("docType=Feed&docUuid=" + feed.getUuid());
        // Sample files can go in through Stroom's own upload on the feed's Data tab.
        assertThat(String.valueOf(made.get("user_actions"))).contains("upload sample files on its Data tab");
        assertThat(String.valueOf(made.get("large_files")))
                .startsWith("Next: upload_sample feed=ACME-VPN-V1.0 files=[their paths]");
        assertThat(made).doesNotContainKey("note");
    }

    @Test
    void aFeedDifferingFromAnotherOnlyInCaseIsRefused() {
        // Qwen in VS Code made DELINEA-SECRETSERVER-V1.0 beside an earlier Delinea-SecretServer-V1.0: Stroom filed
        // the new feed's streams under the earlier spelling, and processing refused them as another build's feed.
        feeds.buildFeed("onboard-delinea", "Delinea-SecretServer-V1.0");

        final ToolResult refused = call("{\"build\": \"bb\", \"name\": \"DELINEA-SECRETSERVER-V1.0\"}",
                BuildToolCalls.context());

        assertThat(refused.isError()).isTrue();
        assertThat(refused.text()).startsWith("A feed named 'Delinea-SecretServer-V1.0' (/MCP Workspace/"
                                              + "onboard-delinea) already exists, differing from "
                                              + "'DELINEA-SECRETSERVER-V1.0' only in case.");
        // Refused before the user is asked.
        final BuildToolCalls.FormUser user = BuildToolCalls.declining();
        assertThat(call("{\"build\": \"bb\", \"name\": \"DELINEA-SECRETSERVER-V1.0\"}", user.context()).text())
                .contains("only in case");
        assertThat(user.shown()).isEmpty();
        assertThat(call("{\"build\": \"bb\", \"name\": \"DELINEA-SECRETSERVER-V2.0\"}", user.context()).text())
                .startsWith("The user did not agree to: Create feed 'DELINEA-SECRETSERVER-V2.0'");
        assertThat(user.shown()).hasSize(1);
    }

    @Test
    void aFeedOfTheSameNameIsRefusedBeforeAnythingOrCarriedOnWithInItsOwnBuild() {
        // Seen on the local stack: Stroom refused the duplicate only after the user confirmed it, as a bare 500, and
        // the new build's folder was left empty. Now refused first, saying where the feed is; in its own build,
        // carried on.
        feeds.feedAt("System/Feeds/ACME", "ACME-VPN-V1.0", "f1");

        final ToolResult refused = call("{\"build\": \"onboard-acme\", \"name\": \"ACME-VPN-V1.0\"}",
                BuildToolCalls.context());

        assertThat(refused.text()).startsWith("A feed named 'ACME-VPN-V1.0' already exists (/Feeds/ACME), and "
                                              + "Stroom keeps one feed per name.");
        assertThat(feeds.world.created).isEmpty();

        final DocRef own = feeds.buildFeed("onboard-acme", "ACME-EVENTS-V1.0");
        final Map<String, Object> again = json(call("{\"build\": \"onboard-acme\", \"name\": \"ACME-EVENTS-V1.0\"}",
                BuildToolCalls.context()));

        assertThat(again).containsEntry("existing", true).containsEntry("uuid", own.getUuid());
        assertThat(String.valueOf(again.get("note"))).contains("nothing new was made");
        assertThat(feeds.world.created).isEmpty();
    }

    @Test
    void aBuildOnAnExistingFeedMakesAFeedOnlyForSamplesTheUserChose() {
        // Asked for by the user: asked to onboard an existing feed, the agent used the sample files in the VS Code
        // workspace. The build works from the feed (its Events go to a test feed the server makes): no feed is made
        // for raw samples, unless the user names files of their own.
        feeds.facts.sources = List.of("VPN-GATEWAY");

        final ToolResult refused = call("{\"build\": \"bb\", \"name\": \"VPN-GATEWAY-SAMPLES\"}",
                BuildToolCalls.context());

        assertThat(refused.text()).startsWith("Build 'bb' works from the existing feed(s) VPN-GATEWAY:")
                .contains("user_samples=");
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of());
        final String arguments = "{\"build\": \"bb\", \"name\": \"VPN-GATEWAY-SAMPLES\", \"user_samples\": "
                                 + "[\"samples/vpn_1.csv\"]}";

        json(call(arguments, user.context()));

        // The confirmation shows the user the choice: their files instead of the feed's data.
        assertThat(user.shown()).singleElement().asString()
                .startsWith("Create test feed 'VPN-GATEWAY-SAMPLES' for your sample files (samples/vpn_1.csv) "
                            + "instead of the data in VPN-GATEWAY")
                .contains("- works from: VPN-GATEWAY");

        // The user's choice, kept: upload_sample takes samples for this feed only.
        final DocRef made = feeds.feedNamed("VPN-GATEWAY-SAMPLES");
        assertThat(feeds.world.records().entry("bb", made.getUuid()).get(BuildFeeds.USER_SAMPLES))
                .isEqualTo(List.of("samples/vpn_1.csv"));
    }

    @Test
    void aBuildOnAnExistingFeedMakesItsTestFeedUnderTheUsersName() {
        // Seen in Ask Stroom AI: the server's VPN-GATEWAY-MCP-TEST name was an earlier build's, the user chose
        // another, and the agent's create_feed (stream_type Events) was refused as a feed the build doesn't make.
        final DocRef source = feeds.feedAt("System/Feeds", "VPN-GATEWAY", "src");
        feeds.world.docs.put("src", feeds.feed("src").copy().encoding("UTF-16LE").streamType("Raw Events").build());
        feeds.facts.sources = List.of(source.getName());
        int made = 0;
        for (final String asked : List.of("\"test_feed\": true", "\"stream_type\": \"Events\"")) {
            final String name = "VPN-GATEWAY-TEST-V" + (++made);
            final Map<String, Object> result = json(call("{\"build\": \"bb\", \"name\": \"" + name + "\", " + asked
                                                         + "}", BuildToolCalls.answering(Map.of())));

            assertThat(result).containsEntry("test_feed", true).containsEntry("name", name)
                    .containsEntry("works_from", List.of("VPN-GATEWAY"));
            assertThat(String.valueOf(result.get("hint"))).startsWith("The build's Events go here from now on");
            final DocRef feed = feeds.feedNamed(name);
            // The source's settings, holding the Events, kept as its output.
            assertThat(feeds.feed(feed.getUuid()).getEncoding()).isEqualTo("UTF-16LE");
            assertThat(feeds.feed(feed.getUuid()).getDescription()).isEqualTo("Build 'bb': the Events its pipelines "
                                                                              + "make from VPN-GATEWAY's streams. "
                                                                              + "Nothing is uploaded to it; it stays "
                                                                              + "in the workspace at promotion.");
            assertThat(feeds.world.records().entry("bb", feed.getUuid()).get(BuildFeeds.OUTPUT_FOR))
                    .isEqualTo(List.of("VPN-GATEWAY"));
        }
        final BuildToolCalls.FormUser user = BuildToolCalls.declining();
        call("{\"build\": \"bb\", \"name\": \"VPN-GATEWAY-TEST-V9\", \"test_feed\": true}", user.context());
        assertThat(user.shown()).singleElement().asString()
                .startsWith("Create test feed 'VPN-GATEWAY-TEST-V9' for the Events made from VPN-GATEWAY");

        feeds.facts.sources = List.of();
        assertThat(call("{\"build\": \"bb\", \"name\": \"ACME-TEST\", \"test_feed\": true}",
                BuildToolCalls.context()).text()).contains("no test feed to name");
    }

    @Test
    void theNameTheUserSettlesOnInTheFormIsMadeAndSaid() {
        final Map<String, Object> made = json(call("{\"build\": \"bb\", \"name\": \"ACME-VPN-V1.0\"}",
                BuildToolCalls.answering(Map.of("name", "ACME-VPN-V2.0"))));

        assertThat(feeds.feedNamed("ACME-VPN-V1.0")).isNull();
        assertThat(made).containsEntry("name", "ACME-VPN-V2.0")
                .containsEntry("changed_by_user", Map.of("name", "ACME-VPN-V2.0"));
        assertThat(String.valueOf(made.get("note"))).contains("from 'ACME-VPN-V1.0' to 'ACME-VPN-V2.0'");
    }

    @Test
    void theNameTheUserSettlesOnIsCheckedAsTheProposedOneWas() {
        feeds.buildFeed("other", "acme-vpn-v2.0");

        final ToolResult refused = call("{\"build\": \"bb\", \"name\": \"ACME-VPN-V1.0\"}",
                BuildToolCalls.answering(Map.of("name", "ACME-VPN-V2.0")));

        assertThat(refused.text()).contains("differing from 'ACME-VPN-V2.0' only in case");
        assertThat(feeds.world.created).isEmpty();
    }

    @Test
    void aNameStroomRejectsComesBackWithItsRule() {
        Mockito.doThrow(new EntityServiceException("Invalid name \"acme vpn\" (^[A-Z0-9_-]{3,}$)"))
                .when(feeds.world.explorerService).create(anyString(), anyString(), any(ExplorerNode.class), any());

        final ToolResult refused = call("{\"build\": \"bb\", \"name\": \"acme vpn\"}",
                BuildToolCalls.answering(Map.of()));

        assertThat(refused.text()).isEqualTo("Stroom rejected the feed name: Invalid name \"acme vpn\" "
                                             + "(^[A-Z0-9_-]{3,}$). Propose a name that matches the pattern shown.");
    }

    @Test
    void streamsOfAnEarlierFeedOfTheNameAreNoted() {
        // A feed of this name made before and deleted leaves its streams under the name, out of sight in the UI.
        feeds.world.createTimeMs = 5_000L;
        Mockito.when(feeds.streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(new ResultPage<>(List.of(
                row(12, 1_000L, Status.UNLOCKED), row(11, 2_000L, Status.DELETED), row(13, 9_000L, Status.UNLOCKED))));

        final Map<String, Object> made = json(call("{\"build\": \"bb\", \"name\": \"ACME-VPN-V1.0\"}",
                BuildToolCalls.answering(Map.of())));

        assertThat(String.valueOf(made.get("note"))).startsWith("Stroom still holds 1 stream(s) from an earlier "
                                                                + "feed named ACME-VPN-V1.0, since deleted (e.g. "
                                                                + "[12]); the UI doesn't show them.");
    }

    @Test
    void itConfirmsItsOwnChange() {
        assertThat(tool.isReadOnly()).isFalse();
        assertThat(tool.confirmsItself()).isTrue();
        assertThat(tool.category().name()).isEqualTo("CONTENT");
    }

    private static MetaRow row(final long id, final long createMs, final Status status) {
        return new MetaRow(Meta.builder().id(id).feedName("ACME-VPN-V1.0").createMs(createMs).status(status).build(),
                null, Map.of());
    }

    private ToolResult call(final String arguments, final ToolCallContext context) {
        return tool.execute(BuildToolCalls.call(CreateFeedTool.NAME, arguments), context);
    }

    private static Map<String, Object> json(final ToolResult result) {
        assertThat(result.isError()).as(result.text()).isFalse();
        return BuildToolCalls.json(result.text());
    }
}
