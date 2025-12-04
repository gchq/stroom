/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.explorer.api.ExplorerFavService;
import stroom.explorer.api.ExplorerService;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Objects;

public class ExplorerFavServiceImpl implements ExplorerFavService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExplorerFavServiceImpl.class);

    private final ExplorerFavDao explorerFavDao;
    private final SecurityContext securityContext;
    private final Provider<DocRefInfoService> docRefInfoService;
    private final Provider<ExplorerService> explorerService;

    @Inject
    ExplorerFavServiceImpl(final ExplorerFavDao explorerFavDao,
                           final SecurityContext securityContext,
                           final Provider<DocRefInfoService> docRefInfoService,
                           final Provider<ExplorerService> explorerService) {
        this.explorerFavDao = explorerFavDao;
        this.securityContext = securityContext;
        this.docRefInfoService = docRefInfoService;
        this.explorerService = explorerService;
    }

    @Override
    public void create(final DocRef docRef) {
        final UserRef userRef = getCurrentUser();
        explorerFavDao.createFavouriteForUser(docRef, userRef);
        explorerService.get().rebuildTree();
    }

    @Override
    public void delete(final DocRef docRef) {
        final UserRef userRef = getCurrentUser();
        explorerFavDao.deleteFavouriteForUser(docRef, userRef);
        explorerService.get().rebuildTree();
    }

    @Override
    public List<DocRef> getUserFavourites() {
        final UserRef userRef = getCurrentUser();
        return explorerFavDao.getUserFavourites(getCurrentUser())
                .stream()
                .map(docRef -> {
                    try {
                        return docRefInfoService.get().decorate(docRef);
                    } catch (final RuntimeException e) {
                        // Doc info couldn't be found, probably due to a document that exists in the `explorer_node`
                        // table, but not `doc`.
                        LOGGER.error("Missing doc referenced by favourite: {}, user: {}", docRef, userRef);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public boolean isFavourite(final DocRef docRef) {
        return explorerFavDao.isFavourite(docRef, getCurrentUser());
    }

    private UserRef getCurrentUser() {
        final UserRef userRef = securityContext.getUserRef();
        Objects.requireNonNull(userRef, "No logged in user");
        return userRef;
    }
}
