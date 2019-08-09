package stroom.collection.api;

import stroom.docref.DocRef;

import java.util.Set;

public interface CollectionService {
    Set<DocRef> getChildren(DocRef folder, String type);

    Set<DocRef> getDescendants(DocRef folder, String type);
}
