package stroom.query.client;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryResource;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;

import java.util.function.Consumer;

public class QueryClient {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final RestFactory restFactory;

    @Inject
    public QueryClient(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void loadQueryDoc(final DocRef queryRef,
                             final Consumer<QueryDoc> consumer,
                             final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.fetch(queryRef.getUuid()))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
