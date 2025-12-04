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
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.explorer.api.ExplorerFavService;
import stroom.explorer.shared.ExplorerFavouriteResource;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import event.logging.MultiObject;
import event.logging.UpdateEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

import static stroom.event.logging.rs.api.AutoLogged.OperationType.UNLOGGED;

@AutoLogged(UNLOGGED)
public class ExplorerFavouriteResourceImpl implements ExplorerFavouriteResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ExplorerFavouriteResourceImpl.class);

    private final Provider<ExplorerFavService> explorerFavService;
    private final Provider<StroomEventLoggingService> eventLoggingService;

    @Inject
    ExplorerFavouriteResourceImpl(final Provider<ExplorerFavService> explorerFavService,
                                  final Provider<StroomEventLoggingService> eventLoggingService) {
        this.explorerFavService = explorerFavService;
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public void createUserFavourite(final DocRef docRef) {
        LOGGER.debug("Setting document {} as favourite", docRef);
        eventLoggingService.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "update"))
                .withDescription("Set document as favourite")
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .withObjects(eventLoggingService.get().convert(docRef))
                                .build())
                        .build())
                .withSimpleLoggedAction(() -> {
                    explorerFavService.get().create(docRef);
                })
                .runActionAndLog();
    }

    @Override
    public void deleteUserFavourite(final DocRef docRef) {
        LOGGER.debug("Unsetting document {} as favourite", docRef);
        eventLoggingService.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "update"))
                .withDescription("Unset document as favourite")
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .withObjects(eventLoggingService.get().convert(docRef))
                                .build())
                        .build())
                .withSimpleLoggedAction(() -> {
                    explorerFavService.get().delete(docRef);
                })
                .runActionAndLog();
    }

    @Override
    public List<DocRef> getUserFavourites() {
        return explorerFavService.get().getUserFavourites();
    }
}
