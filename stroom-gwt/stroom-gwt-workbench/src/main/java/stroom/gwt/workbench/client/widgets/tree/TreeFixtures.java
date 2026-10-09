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


package stroom.gwt.workbench.client.widgets.tree;

import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;

/// The explorer fixtures shared by the stories of Stroom's explorer trees and the widgets that
/// use them (`Widgets/Tree/*`, `Widgets/Selectors/DocSelectionBox`, ...).
public final class TreeFixtures {

    /// The explorer service's path, relative to the REST root.
    public static final String FETCH_EXPLORER_NODES = "/explorer/v2/fetchExplorerNodes";
    /// The path of the explorer service's `getFromDocRef`, which gets a document's node.
    public static final String GET_FROM_DOC_REF = "/explorer/v2/getFromDocRef";
    /// The path of the explorer service's `decorate`, which checks a document exists.
    public static final String DECORATE = "/explorer/v2/decorate";

    private TreeFixtures() {
        // Static utility
    }

    /// The stories' tree: a small folder/document hierarchy under a System root.
    ///
    /// @return A new fixture of the tree.
    public static ExplorerFixture fixtureTree() {
        return new ExplorerFixture(ExplorerFixture.folder("System",
                ExplorerFixture.folder("Feeds",
                        ExplorerFixture.doc("TEST_FEED", "Feed"),
                        ExplorerFixture.doc("PROD_FEED", "Feed"),
                        ExplorerFixture.doc("ARCHIVE_FEED", "Feed")),
                ExplorerFixture.folder("Pipelines",
                        ExplorerFixture.doc("Ingest", "Pipeline"),
                        ExplorerFixture.doc("Normalise", "Pipeline"),
                        ExplorerFixture.doc("Standard XSLT", "XSLT")),
                ExplorerFixture.folder("Dashboards",
                        ExplorerFixture.doc("Overview", "Dashboard"),
                        ExplorerFixture.doc("Error Report", "Dashboard")
                                .withInfo("WARNING", "Dependency out of date")
                                .withInfo("ERROR", "Missing index"),
                        ExplorerFixture.doc("Search", "Query")),
                ExplorerFixture.folder("Reference",
                        ExplorerFixture.doc("Countries", "Dictionary"),
                        ExplorerFixture.doc("Lookup", "Index"))));
    }

    /// Fixtures answering the explorer tree's requests (fetching the nodes, and getting a
    /// document's node) from the given tree.
    ///
    /// @param fixture The tree.
    /// @return A builder with the explorer's route added, to add any others to.
    public static RestFixtures.Builder explorerRoutes(final ExplorerFixture fixture) {
        return RestFixtures.builder()
                .post(FETCH_EXPLORER_NODES, request -> RestReply.json(fixture.fetch(request.getBody())))
                .post(GET_FROM_DOC_REF, request -> RestReply.json(fixture.getFromDocRef(request.getBody())));
    }
}
