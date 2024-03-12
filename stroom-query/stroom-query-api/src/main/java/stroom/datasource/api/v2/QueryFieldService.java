package stroom.datasource.api.v2;

import stroom.docref.DocRef;
import stroom.util.shared.ResultPage;

import java.util.Collection;

public interface QueryFieldService {

    int getOrCreateFieldSource(DocRef docRef);

    void addFields(int fieldSourceId, Collection<QueryField> fields);

    ResultPage<QueryField> findFieldInfo(FindFieldInfoCriteria criteria);
}
