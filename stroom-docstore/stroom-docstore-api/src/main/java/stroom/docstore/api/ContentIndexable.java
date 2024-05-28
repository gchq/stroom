package stroom.docstore.api;

import stroom.docref.DocRef;

import java.util.Map;
import java.util.Set;

public interface ContentIndexable {

    Map<String, String> getIndexableData(DocRef docRef);

    Set<DocRef> listDocuments();

    String getType();
}
