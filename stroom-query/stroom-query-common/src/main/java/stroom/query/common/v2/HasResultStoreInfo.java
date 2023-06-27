package stroom.query.common.v2;

import stroom.query.api.v2.FindResultStoreCriteria;
import stroom.query.api.v2.ResultStoreInfo;
import stroom.util.shared.ResultPage;

public interface HasResultStoreInfo {

    ResultPage<ResultStoreInfo> find(FindResultStoreCriteria criteria);

}
