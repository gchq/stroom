package stroom.query.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryResource;
import stroom.task.client.DefaultTaskListener;
import stroom.task.client.HasTaskHandlerFactory;
import stroom.task.client.TaskHandlerFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Inject;

public class QueryHelpDetailProvider implements HasTaskHandlerFactory, HasHandlers {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private TaskHandlerFactory taskHandlerFactory = new DefaultTaskListener(this);

    @Inject
    public QueryHelpDetailProvider(final EventBus eventBus,
                                   final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
    }

    public void getDetail(QueryHelpRow row,
                          Consumer<QueryHelpDetail> consumer) {
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.fetchDetail(row))
                .onSuccess(consumer)
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    @Override
    public void setTaskHandlerFactory(final TaskHandlerFactory taskHandlerFactory) {
        this.taskHandlerFactory = taskHandlerFactory;
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
