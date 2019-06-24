package stroom.streamstore.server;

import stroom.query.api.v2.DocRef;

import java.util.Set;

public interface CollectionService {
    Set<DocRef> getChildren(DocRef folder, String type);

    Set<DocRef> getDescendants(DocRef folder, String type);
}
