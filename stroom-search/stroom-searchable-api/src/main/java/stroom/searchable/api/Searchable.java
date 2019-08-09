package stroom.searchable.api;

import stroom.dashboard.expression.v1.Val;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;

import java.util.function.Consumer;

public interface Searchable {
    DocRef getDocRef();

    DataSource getDataSource();

    void search(ExpressionCriteria criteria, AbstractField[] fields, Consumer<Val[]> consumer);
}
