package stroom.searchable.api;

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.language.functions.ValuesConsumer;

public interface Searchable {

    DocRef getDocRef();

    DataSource getDataSource();

    DateField getTimeField();

    void search(ExpressionCriteria criteria, AbstractField[] fields, ValuesConsumer consumer);
}
