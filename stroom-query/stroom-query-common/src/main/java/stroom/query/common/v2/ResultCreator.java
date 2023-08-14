package stroom.query.common.v2;

import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest;

public interface ResultCreator {

    Result create(DataStore dataStore, ResultRequest resultRequest);
}
