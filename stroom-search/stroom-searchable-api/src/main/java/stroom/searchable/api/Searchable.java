package stroom.searchable.api;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.shared.ResultPage;

import java.util.Optional;

public interface Searchable {

    DocRef getDocRef();

    ResultPage<QueryField> getFieldInfo(FindFieldCriteria criteria);

    Optional<String> fetchDocumentation(DocRef docRef);

    QueryField getTimeField();

    void search(ExpressionCriteria criteria, FieldIndex fieldIndex, ValuesConsumer consumer);
}
