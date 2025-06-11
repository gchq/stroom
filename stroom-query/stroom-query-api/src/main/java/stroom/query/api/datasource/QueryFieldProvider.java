package stroom.query.api.datasource;

import stroom.util.shared.ResultPage;

public interface QueryFieldProvider {
    ResultPage<QueryField> findFields(FindFieldCriteria criteria);
}
