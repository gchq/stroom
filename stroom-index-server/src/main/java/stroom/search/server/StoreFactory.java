package stroom.search.server;

import stroom.query.api.v2.SearchRequest;
import stroom.query.common.v2.Store;

public interface StoreFactory {

    Store create(final SearchRequest searchRequest);

}
