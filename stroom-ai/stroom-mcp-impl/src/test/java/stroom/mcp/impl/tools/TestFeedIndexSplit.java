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

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.security.shared.FetchDocumentUserPermissionsRequest;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// One index for a build's several feeds, or one per feed ([FeedIndexSplit], [FeedReadAccess]): decided by the user
/// (2026-11-25), the feeds' readers compared first; where they differ, one per feed proposed and a shared index only
/// on the user's explicit word; where they match, asked.
class TestFeedIndexSplit {

    private static final String BUILD = "acme";

    private final BuildWorld world = new BuildWorld();
    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final FeedReadAccess readAccess = Mockito.mock(FeedReadAccess.class);
    private final Consent consent = new Consent(() -> world.auditLog);
    private final BuildPlan plan = new BuildPlan(facts, world.guard(), consent, new BuildMemory());
    private final FeedIndexSplit split = new FeedIndexSplit(plan, world.records(), readAccess, consent,
            world.guard());

    @BeforeEach
    void setUp() {
        world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD);
        facts.sources = List.of("ACME-A", "ACME-B");
    }

    @Test
    void oneFeedIsAsBefore() {
        facts.sources = List.of("ACME");

        assertThat(split.settle(call(Map.of()), BuildToolCalls.context(), BUILD, "ACME-Index", null)).isEmpty();
        Mockito.verifyNoInteractions(readAccess);
    }

    @Test
    void feedsWithDifferentReadersAreProposedAnIndexEachAndSharingNeedsTheUsersWord() {
        Mockito.when(readAccess.access(List.of("ACME-A", "ACME-B"))).thenReturn(new FeedReadAccess.Access(
                Map.of("ACME-A", Set.of("Site A analysts"), "ACME-B", Set.of("Site B analysts")), List.of()));

        // Without forms: the question for the agent, with the consequence of sharing.
        assertThatThrownBy(() -> split.settle(call(Map.of()), BuildToolCalls.context(), BUILD, "ACME-Index", null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("Who may read these feeds differs")
                .hasMessageContaining("one index per feed is recommended")
                .hasMessageContaining("A shared index gives everyone who can use it every feed's events")
                .hasMessageContaining("index_split=per_feed or index_split=shared (shared only on their explicit "
                                      + "word")
                .hasMessageContaining("Nothing was made");
        assertThat(world.records().feedComparison(BUILD)).isEmpty();

        // Asked in a form: one per feed first, recommended.
        final BuildToolCalls.FormUser user = BuildToolCalls.user(Map.of("choice", FeedIndexSplit.PER_FEED
                                                                                  + " (recommended)"));
        final Map<String, Object> first = split.settle(call(Map.of()), user.context(), BUILD, "ACME-A-Index",
                null).orElseThrow();
        assertThat(String.valueOf(user.schemas().getFirst())).contains("One index per feed (recommended)",
                FeedIndexSplit.SHARED);
        assertThat(first).containsEntry("index_split", BuildRecords.INDEX_PER_FEED)
                .containsEntry("for_feed", "ACME-A");
        assertThat(String.valueOf(first.get("said"))).contains("still to make, one each: ACME-B");
        assertThat(world.records().feedComparison(BUILD)).containsEntry("index", BuildRecords.INDEX_PER_FEED);

        // Kept: not asked again; an index named for no feed (or for both) is refused.
        assertThatThrownBy(() -> split.settle(call(Map.of()), BuildToolCalls.context(), BUILD, "Acme-Index", null))
                .hasMessageContaining("name this index doc for exactly one feed");
        assertThat(split.settle(call(Map.of()), BuildToolCalls.context(), BUILD, "ACME-B-Index", "acme-b-events")
                .orElseThrow()).containsEntry("for_feed", "ACME-B");
        Mockito.verify(readAccess, Mockito.times(2)).access(Mockito.anyList());
    }

    @Test
    void feedsWithTheSameReadersAreAskedSharedFirstAndTheAnswerGivenIsChecked() {
        Mockito.when(readAccess.access(List.of("ACME-A", "ACME-B"))).thenReturn(new FeedReadAccess.Access(
                Map.of("ACME-A", Set.of("Analysts"), "ACME-B", Set.of("Analysts")), List.of()));

        assertThatThrownBy(() -> split.settle(call(Map.of("index_split", "both")), BuildToolCalls.context(), BUILD,
                "ACME-Index", null)).hasMessageContaining("index_split is 'per_feed' or 'shared'");
        final Map<String, Object> shared = split.settle(call(Map.of("index_split", "shared")),
                BuildToolCalls.context(), BUILD, "ACME-Index", null).orElseThrow();

        assertThat(shared).containsEntry("index_split", BuildRecords.INDEX_SHARED);
        assertThat(String.valueOf(shared.get("said"))).contains("everyone who can use it sees them all");
    }

    @Test
    void aFeedNamedInsideAnothersIsThatOthers() {
        facts.sources = List.of("ACME", "ACME-B");
        world.records().rememberFeedComparison(BUILD, Map.of("index", BuildRecords.INDEX_PER_FEED));
        world.inBuild(BUILD, LuceneIndexDoc.TYPE, "ACME-Index", null);

        final Map<String, Object> second = split.settle(call(Map.of()), BuildToolCalls.context(), BUILD,
                "ACME-B-Index", null).orElseThrow();

        assertThat(second).containsEntry("for_feed", "ACME-B");
        assertThat(String.valueOf(second.get("said"))).endsWith("holds ACME-B's events.");
    }

    @Test
    void readersAreThoseWithViewOrMoreWhereTheUserMaySeeThePermissions() {
        final DocumentPermissionService permissions = Mockito.mock(DocumentPermissionService.class);
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        final FeedNames feedNames = Mockito.mock(FeedNames.class);
        final DocRef a = new DocRef("Feed", "a", "ACME-A");
        final DocRef b = new DocRef("Feed", "b", "ACME-B");
        Mockito.when(feedNames.exact("ACME-A")).thenReturn(Optional.of(a));
        Mockito.when(feedNames.exact("ACME-B")).thenReturn(Optional.of(b));
        Mockito.when(feedNames.exact("GONE")).thenReturn(Optional.empty());
        Mockito.when(securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)).thenReturn(false);
        Mockito.when(securityContext.hasDocumentPermission(a, DocumentPermission.OWNER)).thenReturn(true);
        Mockito.when(securityContext.hasDocumentPermission(b, DocumentPermission.OWNER)).thenReturn(false);
        Mockito.when(permissions.fetchDocumentUserPermissions(Mockito.any(FetchDocumentUserPermissionsRequest.class)))
                .thenReturn(ResultPage.createUnboundedList(List.of(
                        new DocumentUserPermissions(user("Analysts"), DocumentPermission.VIEW, Set.of()),
                        new DocumentUserPermissions(user("Admins"), DocumentPermission.OWNER, Set.of()),
                        new DocumentUserPermissions(user("Users"), DocumentPermission.USE, Set.of()))));
        final FeedReadAccess access = new FeedReadAccess(() -> permissions, securityContext, feedNames);

        final FeedReadAccess.Access read = access.access(List.of("ACME-A", "ACME-B", "GONE"));

        assertThat(read.readers()).isEqualTo(Map.of("ACME-A", Set.of("Admins", "Analysts")));
        assertThat(read.unknown()).containsExactly("ACME-B", "GONE");
        assertThat(read.same()).isFalse();
        assertThat(read.said()).contains("ACME-A: Admins, Analysts", "ACME-B, GONE: not known");
        assertThat(new FeedReadAccess.Access(Map.of("A", Set.of("x"), "B", Set.of("x")), List.of()).same())
                .isTrue();
    }

    private static UserRef user(final String name) {
        return UserRef.builder().uuid(name).subjectId(name).displayName(name).group(true).build();
    }

    private static ToolCall call(final Map<String, Object> args) {
        return BuildToolCalls.call(CreateIndexDocTool.NAME, JsonUtil.writeValueAsString(args));
    }
}
