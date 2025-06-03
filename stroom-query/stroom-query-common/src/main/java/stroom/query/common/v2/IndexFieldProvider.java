package stroom.query.common.v2;

import stroom.docref.DocRef;
import stroom.query.api.datasource.IndexField;

public interface IndexFieldProvider {

    IndexField getIndexField(DocRef docRef, String fieldName);

    String getDataSourceType();
}
