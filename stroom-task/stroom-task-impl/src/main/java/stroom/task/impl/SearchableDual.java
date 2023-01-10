package stroom.task.impl;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.searchable.api.Searchable;

import java.util.Collections;
import java.util.List;

public class SearchableDual implements Searchable {

    private static final DocRef DOC_REF = new DocRef(
            "Searchable",
            "Dual",
            "Dual");

    private static final AbstractField DUMMY_FIELD = new TextField(
            "Dummy", true);

    private static final List<AbstractField> FIELDS = Collections.singletonList(DUMMY_FIELD);

    private static final DataSource DATA_SOURCE = DataSource.builder().fields(FIELDS).build();

    @Override
    public DocRef getDocRef() {
        return DOC_REF;
    }

    @Override
    public DataSource getDataSource() {
        return DATA_SOURCE;
    }

    @Override
    public DateField getTimeField() {
        return null;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final AbstractField[] fields,
                       final ValuesConsumer consumer) {
        final Val[] valArr = new Val[fields.length];
        for (int i = 0; i < fields.length; i++) {
            valArr[i] = ValString.create("X");
        }
        consumer.add(valArr);
    }
}
