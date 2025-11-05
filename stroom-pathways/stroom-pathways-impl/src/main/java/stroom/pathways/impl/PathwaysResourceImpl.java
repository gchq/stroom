package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.pathways.shared.AddPathway;
import stroom.pathways.shared.DeletePathway;
import stroom.pathways.shared.FindPathwayCriteria;
import stroom.pathways.shared.PathwayResultPage;
import stroom.pathways.shared.PathwaysDoc;
import stroom.pathways.shared.PathwaysResource;
import stroom.pathways.shared.UpdatePathway;
import stroom.util.shared.EntityServiceException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

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
        return pathwaysStoreProvider.get().readDocument(getDocRef(uuid));
    }

    @Override
    public PathwaysDoc update(final String uuid, final PathwaysDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return pathwaysStoreProvider.get().writeDocument(doc);
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
