package stroom.docstore.api;

import stroom.docref.DocRef;

import java.util.List;

public interface HasFindByName {
    List<DocRef> findByName(String name);
}
