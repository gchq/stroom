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

package stroom.cache.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.cache.shared.CacheInfo;
import stroom.cache.shared.CacheInfoResponse;
import stroom.cache.shared.CacheResource;
import stroom.cache.shared.FindCacheInfoCriteria;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.util.HasHealthCheck;
import stroom.util.guice.ResourcePaths;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.RestResource;
import stroom.util.shared.StringCriteria;

import javax.inject.Inject;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.List;

// TODO : @66 add event logging
class CacheResourceImpl implements CacheResource, RestResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CacheResourceImpl.class);

    private final NodeService nodeService;
    private final NodeInfo nodeInfo;
    private final WebTargetFactory webTargetFactory;
    private final CacheManagerService cacheManagerService;

    @Inject
    CacheResourceImpl(final NodeService nodeService,
                      final NodeInfo nodeInfo,
                      final WebTargetFactory webTargetFactory,
                      final CacheManagerService cacheManagerService) {
        this.nodeService = nodeService;
        this.nodeInfo = nodeInfo;
        this.webTargetFactory = webTargetFactory;
        this.cacheManagerService = cacheManagerService;
    }

    @Override
    public List<String> list() {
        return cacheManagerService.getCacheNames();
    }

    @Override
    public CacheInfoResponse info(final String cacheName, final String nodeName) {
        CacheInfoResponse result;
        try {
            // If this is the node that was contacted then just return our local info.
            if (nodeInfo.getThisNodeName().equals(nodeName)) {
                final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
                criteria.setName(new StringCriteria(cacheName, null));
                final List<CacheInfo> list = cacheManagerService.find(criteria);
                result = new CacheInfoResponse();
                result.init(list);

            } else {
                String url = NodeCallUtil.getUrl(nodeService, nodeName);
                url += ResourcePaths.API_ROOT_PATH + "/cache/info";
                final Response response = webTargetFactory
                        .create(url)
                        .queryParam("cacheName", cacheName)
                        .queryParam("nodeName", nodeName)
                        .request(MediaType.APPLICATION_JSON)
                        .get();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                result = response.readEntity(CacheInfoResponse.class);
                if (result == null) {
                    throw new RuntimeException("Unable to contact node \"" + nodeName + "\" at URL: " + url);
                }
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }

        // Add the node name.
        for (final CacheInfo value : result.getValues()) {
            value.setNodeName(nodeName);
        }

        return result;
    }

    @Override
    public Long clear(final String cacheName, final String nodeName) {
        Long result;
        try {
            // If this is the node that was contacted then just return our local info.
            if (nodeInfo.getThisNodeName().equals(nodeName)) {
                final FindCacheInfoCriteria criteria = new FindCacheInfoCriteria();
                criteria.setName(new StringCriteria(cacheName, null));
                result = cacheManagerService.clear(criteria);

            } else {
                String url = NodeCallUtil.getUrl(nodeService, nodeName);
                url += ResourcePaths.API_ROOT_PATH + "/cache";
                final Response response = webTargetFactory
                        .create(url)
                        .queryParam("cacheName", cacheName)
                        .queryParam("nodeName", nodeName)
                        .request(MediaType.APPLICATION_JSON)
                        .delete();
                if (response.getStatus() != 200) {
                    throw new WebApplicationException(response);
                }
                result = response.readEntity(Long.class);
                if (result == null) {
                    throw new RuntimeException("Unable to contact node \"" + nodeName + "\" at URL: " + url);
                }
            }

        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw new ServerErrorException(Status.INTERNAL_SERVER_ERROR, e);
        }

        return result;
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}