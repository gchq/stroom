package stroom.query.client.presenter;

import stroom.query.shared.QueryTablePreferences;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class QueryTablePreferencesHolder implements Supplier<QueryTablePreferences>, Consumer<QueryTablePreferences> {

    private QueryTablePreferences queryTablePreferences = QueryTablePreferences.builder().build();

    @Override
    public void accept(final QueryTablePreferences queryTablePreferences) {
        this.queryTablePreferences = queryTablePreferences;
    }

    @Override
    public QueryTablePreferences get() {
        return queryTablePreferences;
    }
}
