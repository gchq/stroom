package stroom.query.common.v2;

import stroom.query.api.Result;
import stroom.query.api.ResultRequest;

public interface ResultCreator {

    Result create(DataStore dataStore, ResultRequest resultRequest);
}
