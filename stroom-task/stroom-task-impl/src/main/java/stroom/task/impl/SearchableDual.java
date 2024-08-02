package stroom.task.impl;

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.searchable.api.Searchable;
import stroom.util.shared.ResultPage;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SearchableDual implements Searchable {

    private static final DocRef DOC_REF = new DocRef(
            "Searchable",
            "Dual",
            "Dual");

    private static final QueryField DUMMY_FIELD = QueryField.createText(
            "Dummy", true);

    private static final List<QueryField> FIELDS = Collections.singletonList(DUMMY_FIELD);

    @Override
    public DocRef getDocRef() {
        return DOC_REF;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return FieldInfoResultPageBuilder.builder(criteria).addAll(FIELDS).build();
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public QueryField getTimeField() {
        return null;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        final String[] fields = fieldIndex.getFields();
        final Val[] valArr = new Val[fields.length];
        for (int i = 0; i < fields.length; i++) {
            valArr[i] = ValString.create("X");
        }
        consumer.accept(Val.of(valArr));
    }
}
