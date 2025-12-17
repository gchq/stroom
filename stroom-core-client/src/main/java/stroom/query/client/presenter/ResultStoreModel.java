/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.client.presenter;

import stroom.data.client.presenter.CriteriaUtil;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.node.client.NodeManager;
import stroom.query.api.DestroyReason;
import stroom.query.api.FindResultStoreCriteria;
import stroom.query.api.QueryKey;
import stroom.query.api.ResultStoreInfo;
import stroom.query.shared.DestroyStoreRequest;
import stroom.query.shared.ResultStoreResource;
import stroom.query.shared.UpdateStoreRequest;
import stroom.task.client.TaskMonitorFactory;
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
                      final TaskMonitorFactory taskMonitorFactory) {
        this.range = range;
        this.dataConsumer = dataConsumer;
        delayedUpdate.reset();
        fetchNodes(range, dataConsumer, errorHandler, taskMonitorFactory);
    }

    private void fetchNodes(final Range range,
                            final Consumer<ResultPage<ResultStoreInfo>> dataConsumer,
                            final RestErrorHandler errorHandler,
                            final TaskMonitorFactory taskMonitorFactory) {
        nodeManager.listAllNodes(
                nodeNames -> fetchTasksForNodes(range, dataConsumer, nodeNames, taskMonitorFactory),
                errorHandler, taskMonitorFactory);
    }

    private void fetchTasksForNodes(final Range range,
                                    final Consumer<ResultPage<ResultStoreInfo>> dataConsumer,
                                    final List<String> nodeNames,
                                    final TaskMonitorFactory taskMonitorFactory) {
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
                    .taskMonitorFactory(taskMonitorFactory)
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
                          final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESULT_STORE_RESOURCE)
                .method(res -> res.terminate(nodeName, queryKey))
                .onSuccess(consumer)
                .onFailure(t -> consumer.accept(false))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void destroy(final String nodeName,
                        final QueryKey queryKey,
                        final DestroyReason destroyReason,
                        final Consumer<Boolean> consumer,
                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESULT_STORE_RESOURCE)
                .method(res -> res.destroy(nodeName, new DestroyStoreRequest(queryKey, destroyReason)))
                .onSuccess(consumer)
                .onFailure(t -> consumer.accept(false))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void updateSettings(final String nodeName,
                               final UpdateStoreRequest updateStoreRequest,
                               final Consumer<Boolean> consumer,
                               final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(RESULT_STORE_RESOURCE)
                .method(res -> res.update(nodeName, updateStoreRequest))
                .onSuccess(consumer)
                .onFailure(t -> consumer.accept(false))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
