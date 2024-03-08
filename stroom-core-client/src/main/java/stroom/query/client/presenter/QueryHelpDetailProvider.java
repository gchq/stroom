package stroom.query.client.presenter;

import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryResource;

import com.google.gwt.core.client.GWT;

import java.util.function.Consumer;
import javax.inject.Inject;

public class QueryHelpDetailProvider {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final RestFactory restFactory;

    @Inject
    public QueryHelpDetailProvider(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void getDetail(QueryHelpRow row,
                          Consumer<QueryHelpDetail> consumer) {
        final Rest<QueryHelpDetail> rest = restFactory.create();
        rest
                .onSuccess(consumer)
                .call(QUERY_RESOURCE)
                .fetchDetail(row);
    }
}
