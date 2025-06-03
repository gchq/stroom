package stroom.query.common.v2;

import stroom.query.api.FindResultStoreCriteria;
import stroom.query.api.ResultStoreInfo;
import stroom.util.shared.ResultPage;

public interface HasResultStoreInfo {

    ResultPage<ResultStoreInfo> find(FindResultStoreCriteria criteria);

}
