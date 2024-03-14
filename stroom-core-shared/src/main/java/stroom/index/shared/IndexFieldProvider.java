package stroom.index.shared;

import stroom.datasource.api.v2.IndexField;
import stroom.docref.DocRef;

public interface IndexFieldProvider {

    IndexField getIndexField(DocRef docRef, String fieldName);

    String getType();
}
