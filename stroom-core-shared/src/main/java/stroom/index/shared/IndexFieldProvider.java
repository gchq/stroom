package stroom.index.shared;

import stroom.docref.DocRef;

public interface IndexFieldProvider {

    IndexField getIndexField(DocRef docRef, String fieldName);

    String getType();
}
