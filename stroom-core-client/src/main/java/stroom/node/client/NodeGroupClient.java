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

package stroom.node.client;

import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.NodeGroup;
import stroom.node.shared.NodeGroupChange;
import stroom.node.shared.NodeGroupResource;
import stroom.node.shared.NodeGroupState;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.shared.GWT;

import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NodeGroupClient {

    private static final NodeGroupResource NODE_GROUP_RESOURCE =
            GWT.create(NodeGroupResource.class);

    private final RestFactory restFactory;

    @Inject
    NodeGroupClient(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void find(final FindNodeGroupRequest request,
                     final Consumer<ResultPage<NodeGroup>> dataConsumer,
                     final RestErrorHandler restErrorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(NODE_GROUP_RESOURCE)
                .method(r -> r.find(request))
                .onSuccess(dataConsumer)
                .onFailure(restErrorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void checkNodeGroupName(final String name,
                                   final Consumer<NodeGroup> consumer,
                                   final RestErrorHandler errorHandler,
                                   final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(NODE_GROUP_RESOURCE)
                .method(res -> res.fetchByName(name))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void createNodeGroup(final String name,
                                final Consumer<NodeGroup> consumer,
                                final RestErrorHandler errorHandler,
                                final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(NODE_GROUP_RESOURCE)
                .method(res -> res.getOrCreate(name))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void update(final NodeGroup nodeGroup,
                       final Consumer<NodeGroup> consumer,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(NODE_GROUP_RESOURCE)
                .method(res -> res.update(nodeGroup.getId(), nodeGroup))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void fetch(final int id,
                      final Consumer<NodeGroup> consumer,
                      final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(NODE_GROUP_RESOURCE)
                .method(res -> res.fetch(id))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void fetchByName(final String name,
                            final Consumer<NodeGroup> consumer,
                            final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(NODE_GROUP_RESOURCE)
                .method(res -> res.fetchByName(name))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void delete(final int id,
                       final Consumer<Boolean> consumer,
                       final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(NODE_GROUP_RESOURCE)
                .method(res -> res.delete(id))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void getNodeGroupState(final int id,
                                  final Consumer<ResultPage<NodeGroupState>> consumer,
                                  final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(NODE_GROUP_RESOURCE)
                .method(res -> res.getNodeGroupState(id))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void updateNodeGroupState(final NodeGroupChange change,
                                     final Consumer<Boolean> consumer,
                                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(NODE_GROUP_RESOURCE)
                .method(res -> res.updateNodeGroupState(change))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
