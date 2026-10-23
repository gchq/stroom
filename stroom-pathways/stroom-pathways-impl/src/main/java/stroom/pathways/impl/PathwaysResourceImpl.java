/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.pathways.shared.AddPathway;
import stroom.pathways.shared.DeletePathway;
import stroom.pathways.shared.FetchPathwayRequest;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.FindPathwayMutationCriteria;
import stroom.pathways.shared.PathwayMutationResultPage;
import stroom.pathways.shared.PathwayResultPage;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.PathwaysResource;
import stroom.pathways.shared.UpdatePathway;
import stroom.pathways.shared.pathway.Pathway;
import stroom.pathways.shared.pathway.PathwayUsage;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged
class PathwaysResourceImpl implements PathwaysResource {

    private final Provider<PathwaysStore> pathwaysStoreProvider;
    private final Provider<PathwaysService> pathwaysServiceProvider;

    @Inject
    PathwaysResourceImpl(final Provider<PathwaysStore> pathwaysStoreProvider,
                         final Provider<PathwaysService> pathwaysServiceProvider) {
        this.pathwaysStoreProvider = pathwaysStoreProvider;
        this.pathwaysServiceProvider = pathwaysServiceProvider;
    }

    @Override
    public PathwaysDoc fetch(final String uuid) {
        final PathwaysDoc doc = pathwaysStoreProvider.get().readDocument(getDocRef(uuid));
        if (doc == null) {
            return null;
        }
        // Stamp what the document does not hold itself: the flag that locks the shared path and shard
        // count once pathways have been written under them. Worked out once, when the document is
        // opened, and carried wherever it goes.
        return doc.copy()
                .hasSharedFileStoreData(pathwaysStoreProvider.get().hasSharedFileStoreData(uuid))
                .build();
    }

    @Override
    public PathwaysDoc update(final String uuid, final PathwaysDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        // Strip what was stamped on before persisting — it is worked out server-side on every read
        // and must never be written to the document store.
        final PathwaysDoc updated = pathwaysStoreProvider.get().writeDocument(doc.copy().build());
        if (updated == null) {
            return null;
        }
        return updated.copy()
                .hasSharedFileStoreData(pathwaysStoreProvider.get().hasSharedFileStoreData(uuid))
                .build();
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(PathwaysDoc.TYPE)
                .build();
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public PathwayResultPage findPathways(final FindPathwayCriteria criteria) {
        return pathwaysServiceProvider.get().findPathways(criteria);
    }

    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public PathwayMutationResultPage findMutations(final FindPathwayMutationCriteria criteria) {
        return pathwaysServiceProvider.get().findMutations(criteria);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public List<PathwayUsage> findUsage(final FindPathwayMutationCriteria criteria) {
        return pathwaysServiceProvider.get().findUsage(criteria);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Pathway fetchPathway(final FetchPathwayRequest request) {
        return pathwaysServiceProvider.get().fetchPathway(request);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Boolean addPathway(final AddPathway addPathway) {
        return pathwaysServiceProvider.get().addPathway(addPathway);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Boolean updatePathway(final UpdatePathway updatePathway) {
        return pathwaysServiceProvider.get().updatePathway(updatePathway);
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public Boolean deletePathway(final DeletePathway deletePathway) {
        return pathwaysServiceProvider.get().deletePathway(deletePathway);
    }
}
