package stroom.query.field.impl;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.datasource.api.v2.QueryFieldService;
import stroom.docref.DocRef;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.Collection;

public class QueryFieldServiceImpl implements QueryFieldService {

    private final QueryFieldDao queryDatasourceDao;

    @Inject
    public QueryFieldServiceImpl(final QueryFieldDao queryDatasourceDao) {
        this.queryDatasourceDao = queryDatasourceDao;
    }

    @Override
    public int getOrCreateFieldSource(final DocRef docRef) {
        return queryDatasourceDao.getOrCreateFieldSource(docRef);
    }

    @Override
    public void addFields(final int fieldSourceId, final Collection<FieldInfo> fields) {
        queryDatasourceDao.addFields(fieldSourceId, fields);
    }

    @Override
    public ResultPage<FieldInfo> findFieldInfo(final FindFieldInfoCriteria criteria) {
        return queryDatasourceDao.findFieldInfo(criteria);
    }
}
