package stroom.index.impl;

import stroom.datasource.api.v2.FindIndexFieldCriteria;
import stroom.datasource.api.v2.IndexField;
import stroom.docref.DocRef;
import stroom.query.common.v2.IndexFieldProvider;
import stroom.util.shared.ResultPage;

import java.util.Collection;

public interface IndexFieldService extends IndexFieldProvider {

    void addFields(DocRef docRef, Collection<IndexField> fields);

    ResultPage<IndexField> findFields(FindIndexFieldCriteria criteria);

    void transferFieldsToDB(DocRef docRef);
}
