/*
 * Copyright 2017 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticDataShard;
import stroom.analytics.shared.AnalyticDataShardResource;
import stroom.analytics.shared.FindAnalyticDataShardCriteria;
import stroom.analytics.shared.GetAnalyticShardDataRequest;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.query.api.v2.Result;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.rest.RestUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@AutoLogged(OperationType.UNLOGGED)
class AnalyticDataShardResourceImpl implements AnalyticDataShardResource {

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final Provider<AnalyticDataStores> analyticDataStoresProvider;

    @Inject
    public AnalyticDataShardResourceImpl(final Provider<NodeService> nodeServiceProvider,
                                         final Provider<NodeInfo> nodeInfoProvider,
                                         final Provider<WebTargetFactory> webTargetFactoryProvider,
                                         final Provider<AnalyticDataStores> analyticDataStoresProvider) {
        this.nodeServiceProvider = nodeServiceProvider;
        this.nodeInfoProvider = nodeInfoProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.analyticDataStoresProvider = analyticDataStoresProvider;
    }

    @Override
    public ResultPage<AnalyticDataShard> find(final String nodeName, final FindAnalyticDataShardCriteria criteria) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
            return analyticDataStoresProvider.get().findShards(criteria);
        } else {
            final String url = NodeCallUtil
                    .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    AnalyticDataShardResource.BASE_PATH, AnalyticDataShardResource.FIND_SUB_PATH);
            try {
                // A different node to make a rest call to the required node
                WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "nodeName", nodeName);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(criteria));
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(ResultPage.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    @Override
    public Result getData(final String nodeName, final GetAnalyticShardDataRequest request) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        // If this is the node that was contacted then just resolve it locally
        if (NodeCallUtil.shouldExecuteLocally(nodeInfoProvider.get(), nodeName)) {
            return analyticDataStoresProvider.get().getData(request);
        } else {
            final String url = NodeCallUtil
                    .getBaseEndpointUrl(nodeInfoProvider.get(), nodeServiceProvider.get(), nodeName)
                    + ResourcePaths.buildAuthenticatedApiPath(
                    AnalyticDataShardResource.BASE_PATH, AnalyticDataShardResource.GET_DATA_SUB_PATH);
            try {
                // A different node to make a rest call to the required node
                WebTarget webTarget = webTargetFactoryProvider.get().create(url);
                webTarget = UriBuilderUtil.addParam(webTarget, "nodeName", nodeName);
                final Response response = webTarget
                        .request(MediaType.APPLICATION_JSON)
                        .post(Entity.json(request));
                if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                    throw new NotFoundException(response);
                } else if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }

                return response.readEntity(Result.class);
            } catch (final Throwable e) {
                throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    //    private final Provider<AnalyticNotificationDao> analyticNotificationDaoProvider;
//    private final Provider<AnalyticNotificationStateDao> analyticNotificationStateDaoProvider;
//
//    @Inject
//    AnalyticDataShardResourceImpl(final Provider<AnalyticNotificationDao> analyticNotificationDaoProvider,
//                                  final Provider<AnalyticNotificationStateDao>
//                                             analyticNotificationStateDaoProvider) {
//        this.analyticNotificationDaoProvider = analyticNotificationDaoProvider;
//        this.analyticNotificationStateDaoProvider = analyticNotificationStateDaoProvider;
//    }
//
//    @Override
//    public ResultPage<AnalyticNotificationRow> find(final FindAnalyticNotificationCriteria criteria) {
//        final AnalyticNotificationDao analyticNotificationDao = analyticNotificationDaoProvider.get();
//        final AnalyticNotificationStateDao analyticNotificationStateDao = analyticNotificationStateDaoProvider.get();
//        final List<AnalyticNotification> notifications =
//                analyticNotificationDao.getByAnalyticUuid(criteria.getAnalyticDocUuid());
//        final List<AnalyticNotificationRow> list = new ArrayList<>();
//        for (final AnalyticNotification notification : notifications) {
//            final Optional<AnalyticNotificationState> state =
//            analyticNotificationStateDao.get(notification.getUuid());
//            final AnalyticNotificationRow row = new AnalyticNotificationRow(notification, state.orElse(null));
//            list.add(row);
//        }
//        return ResultPage.createCriterialBasedList(list, criteria);
//    }
//
//    @Override
//    public AnalyticNotification create(final AnalyticNotification notification) {
//        final AnalyticNotificationDao analyticNotificationDao = analyticNotificationDaoProvider.get();
//        return analyticNotificationDao.create(notification);
//    }
//
//    @Override
//    public AnalyticNotification update(final String uuid, final AnalyticNotification notification) {
//        final AnalyticNotificationDao analyticNotificationDao = analyticNotificationDaoProvider.get();
//        return analyticNotificationDao.update(notification);
//    }
//
//    @Override
//    public Boolean delete(final String uuid, final AnalyticNotification notification) {
//        final AnalyticNotificationDao analyticNotificationDao = analyticNotificationDaoProvider.get();
//        return analyticNotificationDao.delete(notification);
//    }
}
