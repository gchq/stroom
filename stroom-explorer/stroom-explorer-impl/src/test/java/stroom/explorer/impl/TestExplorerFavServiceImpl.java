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

package stroom.explorer.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.security.api.SecurityContext;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Folders are not documents so have no row in the `doc` table and cannot be decorated by
 * {@link DocFinder}. See gh-5719.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TestExplorerFavServiceImpl {

    private static final UserRef USER_REF = UserRef.builder()
            .uuid("user-uuid")
            .subjectId("user")
            .build();

    private static final DocRef FOLDER = DocRef.builder()
            .randomUuid()
            .type(ExplorerConstants.FOLDER_TYPE)
            .name("My Folder")
            .build();

    private static final DocRef DICTIONARY = DocRef.builder()
            .randomUuid()
            .type("Dictionary")
            .name("My Dictionary")
            .build();

    private static final DocRef GIT_REPO = DocRef.builder()
            .randomUuid()
            .type(GitRepoDoc.TYPE)
            .name("My Git Repo")
            .build();

    /**
     * True while the supplier passed to {@link SecurityContext#asProcessingUserResult} is running.
     */
    private final AtomicBoolean elevated = new AtomicBoolean();

    @Mock
    private ExplorerFavDao explorerFavDao;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private DocFinder docFinder;
    @Mock
    private ExplorerService explorerService;

    private ExplorerFavServiceImpl explorerFavService;

    @BeforeEach
    void setUp() {
        Mockito.when(securityContext.getUserRef()).thenReturn(USER_REF);
        Mockito.when(securityContext.asProcessingUserResult(Mockito.<Supplier<?>>any()))
                .thenAnswer(invocation -> {
                    elevated.set(true);
                    try {
                        return invocation.<Supplier<?>>getArgument(0).get();
                    } finally {
                        elevated.set(false);
                    }
                });
        explorerFavService = new ExplorerFavServiceImpl(
                explorerFavDao,
                securityContext,
                () -> docFinder,
                () -> explorerService);
    }

    @Test
    void getUserFavourites_folderIsNotDecorated() {
        Mockito.when(explorerFavDao.getUserFavourites(USER_REF)).thenReturn(List.of(FOLDER));

        assertThat(explorerFavService.getUserFavourites())
                .containsExactly(FOLDER);

        // The name comes from `explorer_node` via the dao, so the doc store must not be consulted.
        Mockito.verify(docFinder, Mockito.never()).decorateIfExists(Mockito.any());
    }

    /**
     * The tree shows folders the user has no view permission on so they can reach children they can view,
     * so filtering them out here would hide the favourite and stop the user unsetting it.
     */
    @Test
    void getUserFavourites_folderIsNotPermissionChecked() {
        Mockito.when(explorerFavDao.getUserFavourites(USER_REF)).thenReturn(List.of(FOLDER));
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.any())).thenReturn(false);

        assertThat(explorerFavService.getUserFavourites())
                .containsExactly(FOLDER);
    }

    @Test
    void getUserFavourites_nonFolderIsStillDecorated() {
        final DocRef undecorated = new DocRef(DICTIONARY.getType(), DICTIONARY.getUuid());
        Mockito.when(explorerFavDao.getUserFavourites(USER_REF)).thenReturn(List.of(undecorated));
        Mockito.when(docFinder.decorateIfExists(undecorated)).thenReturn(Optional.of(DICTIONARY));

        assertThat(explorerFavService.getUserFavourites())
                .containsExactly(DICTIONARY);
    }

    /**
     * A GitRepo is folder like in the tree, which shows it to users with no view permission on it so they
     * can reach children they can view. Decorating as the processing user is what stops it being dropped.
     */
    @Test
    void getUserFavourites_decorationRunsAsProcessingUser() {
        final DocRef undecorated = new DocRef(GIT_REPO.getType(), GIT_REPO.getUuid());
        Mockito.when(explorerFavDao.getUserFavourites(USER_REF)).thenReturn(List.of(undecorated));
        Mockito.when(docFinder.decorateIfExists(undecorated)).thenAnswer(invocation -> {
            assertThat(elevated).isTrue();
            return Optional.of(GIT_REPO);
        });

        assertThat(explorerFavService.getUserFavourites())
                .containsExactly(GIT_REPO);
    }

    /**
     * The favourites belong to the current user, so the dao lookup must happen before we elevate, else
     * every user would get the processing user's favourites.
     */
    @Test
    void getUserFavourites_daoLookupIsNotElevated() {
        Mockito.when(explorerFavDao.getUserFavourites(USER_REF)).thenAnswer(invocation -> {
            assertThat(elevated).isFalse();
            return List.of(FOLDER);
        });

        assertThat(explorerFavService.getUserFavourites())
                .containsExactly(FOLDER);
        Mockito.verify(explorerFavDao).getUserFavourites(USER_REF);
    }

    @Test
    void getUserFavourites_missingDocIsExcluded() {
        final DocRef undecorated = new DocRef(DICTIONARY.getType(), DICTIONARY.getUuid());
        Mockito.when(explorerFavDao.getUserFavourites(USER_REF)).thenReturn(List.of(FOLDER, undecorated));
        Mockito.when(docFinder.decorateIfExists(undecorated)).thenReturn(Optional.empty());

        // The folder survives, the doc with no `doc` row does not.
        assertThat(explorerFavService.getUserFavourites())
                .containsExactly(FOLDER);
    }
}
