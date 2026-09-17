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

package stroom.explorer;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerFavService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FetchExplorerNodesRequest;
import stroom.explorer.shared.NodeFlag;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.util.test.FileSystemTestUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestExplorerFavourites extends AbstractCoreIntegrationTest {

    @Inject
    private ExplorerService explorerService;
    @Inject
    private ExplorerNodeService explorerNodeService;
    @Inject
    private ExplorerFavService explorerFavService;
    @Inject
    private SecurityContext securityContext;

    /**
     * Folders have no row in the `doc` table so cannot be decorated like other documents. See gh-5719.
     */
    @Test
    void testFolderAndDocCanBothBeFavourited() {
        securityContext.asProcessingUser(() -> {
            final ExplorerNode systemNode = explorerNodeService.getRoot();
            final String folderName = "FavFolder_" + FileSystemTestUtil.getUniqueTestString();
            final String dictName = "FavDict_" + FileSystemTestUtil.getUniqueTestString();

            final ExplorerNode folder = explorerService.create(
                    ExplorerConstants.FOLDER_TYPE, folderName, systemNode, null);
            final ExplorerNode dict = explorerService.create(
                    DictionaryDoc.TYPE, dictName, systemNode, null);

            explorerFavService.create(folder.getDocRef());
            explorerFavService.create(dict.getDocRef());

            // The folder must survive the round trip and keep the name held in `explorer_node`.
            assertThat(explorerFavService.getUserFavourites())
                    .extracting(DocRef::getType, DocRef::getName)
                    .contains(
                            tuple(ExplorerConstants.FOLDER_TYPE, folderName),
                            tuple(DictionaryDoc.TYPE, dictName));

            // Both should show up under the Favourites root, flagged as favourites.
            final List<ExplorerNode> favChildren = getFavouriteChildren();
            assertThat(favChildren)
                    .extracting(ExplorerNode::getName)
                    .contains(folderName, dictName);
            assertThat(favChildren)
                    .allMatch(node -> node.hasNodeFlag(NodeFlag.FAVOURITE));
        });
    }

    // Permission filtering of folder favourites is covered by TestExplorerFavServiceImpl, as the
    // MockSecurityContext used by this harness grants every document permission.

    private List<ExplorerNode> getFavouriteChildren() {
        explorerService.clear();
        final FetchExplorerNodesRequest request = new FetchExplorerNodesRequest(
                Set.of(ExplorerConstants.FAVOURITES_NODE.getUniqueKey()),
                Set.of(),
                new ExplorerTreeFilter(
                        null,
                        null,
                        null,
                        null,
                        Set.of(DocumentPermission.VIEW),
                        null,
                        false,
                        null),
                Integer.MAX_VALUE,
                Set.of(),
                false);

        final FetchExplorerNodeResult result = explorerService.getData(request);
        return result.getRootNodes()
                .stream()
                .filter(node -> ExplorerConstants.FAVOURITES_DOC_REF.equals(node.getDocRef()))
                .findFirst()
                .map(ExplorerNode::getChildren)
                .orElse(List.of());
    }
}
