package stroom.query.common.v2;

import stroom.query.api.v2.SearchRequest;

public interface StoreFactory {

    Store create(final SearchRequest searchRequest);

}
