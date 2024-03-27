package stroom.query.client.presenter;

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
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.fetchDetail(row))
                .onSuccess(consumer)
                .exec();
    }
}
