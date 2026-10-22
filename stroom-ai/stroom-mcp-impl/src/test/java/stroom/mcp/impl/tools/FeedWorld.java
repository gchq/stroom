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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.StreamReader;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.MetaRow;
import stroom.security.api.SecurityContext;
import stroom.util.shared.ResultPage;

import org.mockito.Mockito;

import java.util.List;
import java.util.Objects;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/// The feed tools' world: the in-memory explorer of [BuildWorld], whose feeds Stroom finds by name, a build plan
/// whose source feeds the test sets ([FakeBuildFacts#sources]), and no streams unless the test gives some.
final class FeedWorld {

    final BuildWorld world = new BuildWorld();
    final FakeBuildFacts facts = new FakeBuildFacts();
    final DocFinder docFinder = Mockito.mock(DocFinder.class);
    final StreamReader streamReader = Mockito.mock(StreamReader.class);
    final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    final Consent consent = new Consent(securityContext, () -> Mockito.mock(AiAuditLog.class));
    final BuildPlan plan = new BuildPlan(facts, world.guard(), consent, new BuildMemory());
    final FeedNames feedNames = new FeedNames(() -> docFinder, world.documentAccess);
    final BuildFeeds buildFeeds = new BuildFeeds(feedNames, world.guard(), world.records(), plan,
            world.documentAccess);

    FeedWorld() {
        Mockito.when(docFinder.findByName(eq(FeedDoc.TYPE), anyString(), anyBoolean())).thenAnswer(inv ->
                feedsNamed(inv.getArgument(1)));
        Mockito.when(docFinder.findByName(eq(FeedDoc.TYPE), anyString())).thenAnswer(inv ->
                feedsNamed(inv.getArgument(1)));
        Mockito.when(streamReader.find(Mockito.any(), anyInt(), anyBoolean()))
                .thenReturn(new ResultPage<>(List.<MetaRow>of()));
    }

    /// @return A feed made in a build by the tools.
    DocRef buildFeed(final String build, final String name) {
        return world.inBuild(build, FeedDoc.TYPE, name, null);
    }

    /// @return A feed made by people, in a folder outside the workspace.
    DocRef feedAt(final String folder, final String name, final String uuid) {
        return world.add(FeedDoc.TYPE, name, world.folder(folder).uuid, uuid).docRef();
    }

    /// @return The feed's document as it is now.
    FeedDoc feed(final String uuid) {
        return (FeedDoc) world.docs.get(uuid);
    }

    // The feeds of exactly the name, or all of them for '*', as Stroom finds them by name.
    private List<DocRef> feedsNamed(final String name) {
        return world.nodes.values().stream()
                .filter(node -> FeedDoc.TYPE.equals(node.type))
                .filter(node -> "*".equals(name) || node.name.equals(name))
                .map(BuildWorld.Node::docRef)
                .toList();
    }

    /// @return The feed of the name in the world, if there is one.
    DocRef feedNamed(final String name) {
        return world.nodes.values().stream()
                .filter(node -> FeedDoc.TYPE.equals(node.type) && Objects.equals(node.name, name))
                .map(BuildWorld.Node::docRef)
                .findFirst()
                .orElse(null);
    }
}
