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

package stroom.search.solr.search;

import stroom.datasource.api.v2.DataSource;
import stroom.docref.DocRef;
import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.SearchRequest;
import stroom.query.api.v2.SearchResponse;
import stroom.query.api.v2.TableResult;
import stroom.query.common.v2.SearchResponseCreator;
import stroom.query.common.v2.SearchResponseCreatorCache;
import stroom.query.common.v2.SearchResponseCreatorManager;
import stroom.search.solr.SolrIndexStore;
import stroom.search.solr.shared.SolrIndexDataSourceFieldUtil;
import stroom.search.solr.shared.SolrIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import com.codahale.metrics.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Tag(name = "Solr Queries")
@Path("/stroom-solr-index" + ResourcePaths.V2)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class StroomSolrIndexQueryResource implements RestResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomSolrIndexQueryResource.class);

    private final SearchResponseCreatorManager searchResponseCreatorManager;
    private final SolrIndexStore solrIndexStore;
    private final SecurityContext securityContext;

    @Inject
    StroomSolrIndexQueryResource(final SolrSearchResponseCreatorManager searchResponseCreatorManager,
                                 final SolrIndexStore solrIndexStore,
                                 final SecurityContext securityContext) {
        this.searchResponseCreatorManager = searchResponseCreatorManager;
        this.solrIndexStore = solrIndexStore;
        this.securityContext = securityContext;
    }

    @POST
    @Path("/dataSource")
    @Timed
    @Operation(summary = "Submit a request for a data source definition, supplying the DocRef for the data source")
    public DataSource getDataSource(@Parameter(description = "DocRef", required = true) final DocRef docRef) {
        return securityContext.useAsReadResult(() -> {
            final SolrIndexDoc index = solrIndexStore.readDocument(docRef);
            return new DataSource(SolrIndexDataSourceFieldUtil.getDataSourceFields(index));
        });
    }

    @POST
    @Path("/search")
    @Timed
    @Operation(summary = "Submit a search request")
    public SearchResponse search(
            @Parameter(description = "SearchRequest", required = true) final SearchRequest request) {

        //if this is the first call for this query key then it will create a searchResponseCreator (& store) that have
        //a lifespan beyond the scope of this request and then begin the search for the data
        //If it is not the first call for this query key then it will return the existing searchResponseCreator with
        //access to whatever data has been found so far
        final SearchResponseCreator searchResponseCreator = searchResponseCreatorManager.get(
                new SearchResponseCreatorCache.Key(request));

        //create a response from the data found so far, this could be complete/incomplete
        SearchResponse searchResponse = searchResponseCreator.create(request);

        LOGGER.trace(() ->
                getResponseInfoForLogging(request, searchResponse));

        return searchResponse;
    }

    private String getResponseInfoForLogging(
            @Parameter(description = "SearchRequest", required = true) final SearchRequest request,
            final SearchResponse searchResponse) {
        String resultInfo;

        if (searchResponse.getResults() != null) {
            resultInfo = "\n" + searchResponse.getResults().stream()
                    .map(result -> {
                        if (result instanceof FlatResult) {
                            FlatResult flatResult = (FlatResult) result;
                            return LogUtil.message(
                                    "  FlatResult - componentId: {}, size: {}, ",
                                    flatResult.getComponentId(),
                                    flatResult.getSize());
                        } else if (result instanceof TableResult) {
                            TableResult tableResult = (TableResult) result;
                            return LogUtil.message(
                                    "  TableResult - componentId: {}, rows: {}, totalResults: {}, " +
                                            "resultRange: {}",
                                    tableResult.getComponentId(),
                                    tableResult.getRows().size(),
                                    tableResult.getTotalResults(),
                                    tableResult.getResultRange());
                        } else {
                            return "  Unknown type " + result.getClass().getName();
                        }
                    })
                    .collect(Collectors.joining("\n"));
        } else {
            resultInfo = "null";
        }

        return LogUtil.message("Return search response, key: {}, result sets: {}, " +
                        "complete: {}, errors: {}, results: {}",
                request.getKey().toString(),
                searchResponse.getResults(),
                searchResponse.complete(),
                searchResponse.getErrors(),
                resultInfo);
    }

    @POST
    @Path("/destroy")
    @Timed
    @Operation(summary = "Destroy a running query")
    public Boolean destroy(@Parameter(description = "QueryKey", required = true) final QueryKey queryKey) {
        searchResponseCreatorManager.remove(new SearchResponseCreatorCache.Key(queryKey));
        return Boolean.TRUE;
    }
}
