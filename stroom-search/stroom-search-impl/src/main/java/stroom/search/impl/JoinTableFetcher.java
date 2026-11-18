/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.impl;

import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.query.api.Query;
import stroom.query.api.QueryKey;
import stroom.query.common.v2.JoinTable;
import stroom.query.common.v2.JoinTableTransfer;
import stroom.query.common.v2.JoinTables;
import stroom.query.common.v2.JoinTables.Registration;
import stroom.security.api.SecurityContext;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/// Fetches a search's join tables from the node that runs the search, for a node that searches the
/// search's first source, so it can apply the joins to its own rows.
public class JoinTableFetcher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(JoinTableFetcher.class);

    private final NodeInfo nodeInfo;
    private final NodeService nodeService;
    private final WebTargetFactory webTargetFactory;
    private final JoinTables joinTables;
    private final SecurityContext securityContext;

    @Inject
    public JoinTableFetcher(final NodeInfo nodeInfo,
                            final NodeService nodeService,
                            final WebTargetFactory webTargetFactory,
                            final JoinTables joinTables,
                            final SecurityContext securityContext) {
        this.nodeInfo = nodeInfo;
        this.nodeService = nodeService;
        this.webTargetFactory = webTargetFactory;
        this.joinTables = joinTables;
        this.securityContext = securityContext;
    }

    /// Makes sure this node holds the join tables of a node search's search, if its query needs them,
    /// fetching them from the node that runs the search unless this node already holds them, as it
    /// does when it is that node. The tables are held for the caller until it releases them, which it
    /// does once its coprocessors use them, so tables nothing uses are never left behind.
    ///
    /// @param nodeSearchTask The node search.
    /// @return The tables held for the caller, which it must release with [JoinTables#release], or
    /// empty if the query needs none.
    /// @throws SearchException If the tables are needed and can't be fetched.
    public Optional<Registration> fetchIfNeeded(final NodeSearchTask nodeSearchTask) {
        final Query query = nodeSearchTask.getQuery();
        final QueryKey queryKey = nodeSearchTask.getKey();
        if (!query.needsJoinSupport()) {
            return Optional.empty();
        }
        final Optional<Registration> held = joinTables.acquire(queryKey);
        if (held.isPresent()) {
            return held;
        }
        final String sourceNode = nodeSearchTask.getSourceNode();
        if (sourceNode == null) {
            throw new SearchException("The search has joins, but the node running it didn't say which it is, so "
                                      + "its join tables can't be fetched");
        }

        final String url = NodeCallUtil.getBaseEndpointUrl(nodeInfo, nodeService, sourceNode)
                           + ResourcePaths.buildAuthenticatedApiPath(
                RemoteSearchResource.BASE_PATH,
                RemoteSearchResource.JOIN_TABLES_PATH_PART);
        LOGGER.debug("Fetching the join tables of search {} from {}", queryKey, sourceNode);
        WebTarget webTarget = webTargetFactory.create(url);
        webTarget = UriBuilderUtil.addParam(webTarget, "queryKey", queryKey.getUuid());
        try (final InputStream inputStream = webTarget
                .request(MediaType.APPLICATION_OCTET_STREAM)
                .get(InputStream.class)) {
            final List<JoinTable> tables = JoinTableTransfer.read(inputStream, queryKey, joinTables);
            // Another start of the same search may have fetched them meanwhile, in which case its are
            // used and these are closed.
            return Optional.of(joinTables.registerAndAcquire(queryKey, query, tables, securityContext.getUserRef()));
        } catch (final Exception e) {
            LOGGER.debug(e::getMessage, e);
            throw new SearchException("Unable to fetch the join tables of the search from node " + sourceNode
                                      + ": " + e.getMessage(), e);
        }
    }
}
