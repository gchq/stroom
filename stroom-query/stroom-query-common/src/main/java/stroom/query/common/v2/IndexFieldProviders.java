package stroom.query.common.v2;

import stroom.datasource.api.v2.IndexField;
import stroom.docref.DocRef;

public interface IndexFieldProviders {

    IndexField getIndexField(DocRef docRef, String fieldName);
}
