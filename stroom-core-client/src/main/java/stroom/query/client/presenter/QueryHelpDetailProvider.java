package stroom.query.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryResource;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;
import javax.inject.Inject;

public class QueryHelpDetailProvider implements HasTaskMonitorFactory, HasHandlers {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    @Inject
    public QueryHelpDetailProvider(final EventBus eventBus,
                                   final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
    }

    public void getDetail(final QueryHelpRow row,
                          final Consumer<QueryHelpDetail> consumer) {
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.fetchDetail(row))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
