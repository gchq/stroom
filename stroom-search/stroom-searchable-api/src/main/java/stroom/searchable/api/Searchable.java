package stroom.searchable.api;

import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;

public interface Searchable {

    DocRef getDocRef();

    DataSource getDataSource();

    void search(ExpressionCriteria criteria, AbstractField[] fields, ValuesConsumer consumer);
}
