package stroom.searchable.api;

import stroom.dashboard.expression.v1.Val;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DataSourceField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.DocRef;

import java.util.function.Consumer;

public interface Searchable {
    DocRef getDocRef();

    DataSource getDataSource();

    void search(ExpressionCriteria criteria, DataSourceField[] fields, Consumer<Val[]> consumer);
}
