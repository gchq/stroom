package stroom.query.common.v2;

import stroom.datasource.api.v2.DataSourceProvider;
import stroom.datasource.api.v2.DateField;
import stroom.docref.DocRef;
import stroom.query.api.v2.SearchRequest;

public interface StoreFactory extends DataSourceProvider {

    Store create(SearchRequest searchRequest);

    DateField getTimeField(DocRef docRef);
}
