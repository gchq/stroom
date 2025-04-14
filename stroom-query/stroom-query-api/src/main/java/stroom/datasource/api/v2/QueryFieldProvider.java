package stroom.datasource.api.v2;

import stroom.util.shared.ResultPage;

public interface QueryFieldProvider {
    ResultPage<QueryField> findFields(FindFieldCriteria criteria);
}
