/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.DocDependencyService;
import stroom.explorer.api.ExplorerDecorator;
import stroom.explorer.api.ExplorerFavService;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.explorer.shared.FetchExplorerNodeResult;
import stroom.explorer.shared.FetchExplorerNodesRequest;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.entityevent.EntityEventBus;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@link ExplorerFavServiceImpl#getUserFavourites()} deliberately does not filter on view permission, so
 * assert that the tree still does. See gh-5719.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestExplorerServiceFavouritePermissions {

    // A folder the user cannot view, but containing a doc they can, so the tree shows it as an ancestor.
    private static final DocRef ANCESTOR_FOLDER = docRef(ExplorerConstants.FOLDER_TYPE, "Ancestor Folder");
    private static final DocRef VIEWABLE_CHILD = docRef("Dictionary", "Viewable Child");

    // A folder the user cannot view, containing nothing they can view.
    private static final DocRef HIDDEN_FOLDER = docRef(ExplorerConstants.FOLDER_TYPE, "Hidden Folder");
    private static final DocRef HIDDEN_CHILD = docRef("Dictionary", "Hidden Child");

    private static final DocRef VIEWABLE_DOC = docRef("Dictionary", "Viewable Doc");
    private static final DocRef HIDDEN_DOC = docRef("Dictionary", "Hidden Doc");

    @Mock
    private ExplorerNodeService explorerNodeService;
    @Mock
    private ExplorerTreeModel explorerTreeModel;
    @Mock
    private ExplorerActionHandlers explorerActionHandlers;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private ExplorerEventLog explorerEventLog;
    @Mock
    private Provider<ExplorerDecorator> explorerDecoratorProvider;
    @Mock
    private Provider<ExplorerFavService> explorerFavServiceProvider;
    @Mock
    private ExplorerFavService explorerFavService;
    @Mock
    private EntityEventBus entityEventBus;
    @Mock
    private DocumentPermissionService documentPermissionService;
    @Mock
    private ContentIndex contentIndex;
    @Mock
    private ExpressionPredicateFactory expressionPredicateFactory;
    @Mock
    private DocDependencyService docDependencyService;

    private ExplorerServiceImpl explorerService;

    @BeforeEach
    void setUp() {
        explorerService = new ExplorerServiceImpl(
                explorerNodeService,
                explorerTreeModel,
                explorerActionHandlers,
                securityContext,
                explorerEventLog,
                explorerDecoratorProvider,
                explorerFavServiceProvider,
                entityEventBus,
                documentPermissionService,
                contentIndex,
                expressionPredicateFactory,
                docDependencyService);

        // System
        // +- Ancestor Folder (no view)
        // |  +- Viewable Child (view)
        // +- Hidden Folder (no view)
        // |  +- Hidden Child (no view)
        // +- Viewable Doc (view)
        // +- Hidden Doc (no view)
        final TreeModel treeModel = new TreeModel(1L, 0L);
        treeModel.addRoot(ExplorerConstants.SYSTEM_NODE);
        addChild(treeModel, ExplorerConstants.SYSTEM_NODE, ANCESTOR_FOLDER);
        addChild(treeModel, node(ANCESTOR_FOLDER), VIEWABLE_CHILD);
        addChild(treeModel, ExplorerConstants.SYSTEM_NODE, HIDDEN_FOLDER);
        addChild(treeModel, node(HIDDEN_FOLDER), HIDDEN_CHILD);
        addChild(treeModel, ExplorerConstants.SYSTEM_NODE, VIEWABLE_DOC);
        addChild(treeModel, ExplorerConstants.SYSTEM_NODE, HIDDEN_DOC);

        when(explorerTreeModel.getModel()).thenReturn(UnmodifiableTreeModel.wrap(treeModel));
        when(explorerFavServiceProvider.get()).thenReturn(explorerFavService);

        // Only these two are viewable, everything else returns false by default.
        when(securityContext.hasDocumentPermission(VIEWABLE_CHILD, DocumentPermission.VIEW)).thenReturn(true);
        when(securityContext.hasDocumentPermission(VIEWABLE_DOC, DocumentPermission.VIEW)).thenReturn(true);
    }

    @Test
    void folderWithAViewableDescendantIsShown() {
        when(explorerFavService.getUserFavourites()).thenReturn(List.of(ANCESTOR_FOLDER));

        assertThat(getFavouriteNames()).containsExactly(ANCESTOR_FOLDER.getName());
    }

    @Test
    void folderWithNoViewableDescendantIsNotShown() {
        when(explorerFavService.getUserFavourites()).thenReturn(List.of(HIDDEN_FOLDER));

        assertThat(getFavouriteNames()).isEmpty();
    }

    @Test
    void docWithoutViewPermissionIsNotShown() {
        when(explorerFavService.getUserFavourites()).thenReturn(List.of(HIDDEN_DOC));

        assertThat(getFavouriteNames()).isEmpty();
    }

    @Test
    void docWithViewPermissionIsShown() {
        when(explorerFavService.getUserFavourites()).thenReturn(List.of(VIEWABLE_DOC));

        assertThat(getFavouriteNames()).containsExactly(VIEWABLE_DOC.getName());
    }

    /**
     * The unfiltered list the fav service now returns, all in one go.
     */
    @Test
    void onlyViewableFavouritesAreShown() {
        when(explorerFavService.getUserFavourites())
                .thenReturn(List.of(ANCESTOR_FOLDER, HIDDEN_FOLDER, VIEWABLE_DOC, HIDDEN_DOC));

        assertThat(getFavouriteNames())
                .containsExactlyInAnyOrder(ANCESTOR_FOLDER.getName(), VIEWABLE_DOC.getName());
    }

    /**
     * @return The names of the nodes under the Favourites root.
     */
    private List<String> getFavouriteNames() {
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
        final ExplorerNode favouritesNode = result.getRootNodes()
                .stream()
                .filter(node -> ExplorerConstants.FAVOURITES_DOC_REF.equals(node.getDocRef()))
                .findFirst()
                .orElseThrow();

        return favouritesNode.getChildren() == null
                ? List.of()
                : favouritesNode.getChildren().stream().map(ExplorerNode::getName).toList();
    }

    private static void addChild(final TreeModel treeModel, final ExplorerNode parent, final DocRef child) {
        treeModel.add(parent, node(child));
    }

    private static ExplorerNode node(final DocRef docRef) {
        return ExplorerNode.builder().docRef(docRef).build();
    }

    private static DocRef docRef(final String type, final String name) {
        return DocRef.builder().randomUuid().type(type).name(name).build();
    }
}
