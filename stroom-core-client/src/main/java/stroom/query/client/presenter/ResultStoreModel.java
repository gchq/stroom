package stroom.query.client.presenter;

import stroom.data.client.presenter.CriteriaUtil;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.query.api.v2.DestroyReason;
import stroom.query.api.v2.FindResultStoreCriteria;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultStoreInfo;
import stroom.query.shared.DestroyStoreRequest;
import stroom.query.shared.ResultStoreResource;
import stroom.query.shared.UpdateStoreRequest;
import stroom.task.client.TaskHandlerFactory;
import stroom.util.client.DelayedUpdate;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ResultStoreModel {

    private static final ResultStoreResource RESULT_STORE_RESOURCE = GWT.create(ResultStoreResource.class);
    private final FindResultStoreCriteria criteria = new FindResultStoreCriteria();

    private final RestFactory restFactory;
    private final NodeManager nodeManager;
    // nodeName => List<ResultStoreInfo>
    private final Map<String, List<ResultStoreInfo>> responseMap = new HashMap<>();
    private final DelayedUpdate delayedUpdate;
    private Range range;
    private Consumer<ResultPage<ResultStoreInfo>> dataConsumer;

    @Inject
    public ResultStoreModel(final RestFactory restFactory,
                            final NodeManager nodeManager) {
        this.restFactory = restFactory;
        this.nodeManager = nodeManager;
        delayedUpdate = new DelayedUpdate(this::update);
    }

    public void fetch(final Range range,
                      final Consumer<ResultPage<ResultStoreInfo>> dataConsumer,
                      final RestErrorHandler errorHandler,
                      final TaskHandlerFactory taskHandlerFactory) {
        this.range = range;
        this.dataConsumer = dataConsumer;
        delayedUpdate.reset();
        fetchNodes(range, dataConsumer, errorHandler, taskHandlerFactory);
    }

    private void fetchNodes(final Range range,
                            final Consumer<ResultPage<ResultStoreInfo>> dataConsumer,
                            final RestErrorHandler errorHandler,
                            final TaskHandlerFactory taskHandlerFactory) {
        nodeManager.listAllNodes(
                nodeNames -> fetchTasksForNodes(range, dataConsumer, nodeNames, taskHandlerFactory),
                errorHandler, taskHandlerFactory);
    }

    private void fetchTasksForNodes(final Range range,
                                    final Consumer<ResultPage<ResultStoreInfo>> dataConsumer,
                                    final List<String> nodeNames,
                                    final TaskHandlerFactory taskHandlerFactory) {
        responseMap.clear();
        CriteriaUtil.setRange(criteria, range);
        for (final String nodeName : nodeNames) {
            restFactory
                    .create(RESULT_STORE_RESOURCE)
                    .method(res -> res.find(nodeName, criteria))
                    .onSuccess(response -> {
                        responseMap.put(nodeName, response.getValues());
                        delayedUpdate.update();
                    })
                    .onFailure(throwable -> {
                        responseMap.remove(nodeName);
                        delayedUpdate.update();
                    })
                    .taskHandlerFactory(taskHandlerFactory)
                    .exec();
        }
    }

    private void update() {
        final List<ResultStoreInfo> list = new ArrayList<>();
        responseMap.values().forEach(list::addAll);
        final ResultPage<ResultStoreInfo> resultPage =
                ResultPage.createPageLimitedList(list, criteria.getPageRequest());
        dataConsumer.accept(resultPage);
    }

    public void terminate(final String nodeName,
                          final QueryKey queryKey,
                          final Consumer<Boolean> consumer,
                          final TaskHandlerFactory taskHandlerFactory) {
        restFactory
                .create(RESULT_STORE_RESOURCE)
                .method(res -> res.terminate(nodeName, queryKey))
                .onSuccess(consumer)
                .onFailure(t -> consumer.accept(false))
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    public void destroy(final String nodeName,
                        final QueryKey queryKey,
                        final DestroyReason destroyReason,
                        final Consumer<Boolean> consumer,
                        final TaskHandlerFactory taskHandlerFactory) {
        restFactory
                .create(RESULT_STORE_RESOURCE)
                .method(res -> res.destroy(nodeName, new DestroyStoreRequest(queryKey, destroyReason)))
                .onSuccess(consumer)
                .onFailure(t -> consumer.accept(false))
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }

    public void updateSettings(final String nodeName,
                               final UpdateStoreRequest updateStoreRequest,
                               final Consumer<Boolean> consumer,
                               final TaskHandlerFactory taskHandlerFactory) {
        restFactory
                .create(RESULT_STORE_RESOURCE)
                .method(res -> res.update(nodeName, updateStoreRequest))
                .onSuccess(consumer)
                .onFailure(t -> consumer.accept(false))
                .taskHandlerFactory(taskHandlerFactory)
                .exec();
    }
}
