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

package stroom.dashboard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dashboard.shared.DownloadQueryAction;
import stroom.dashboard.shared.SearchRequest;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.util.EntityServiceExceptionUtil;
import stroom.query.api.v2.ResultRequest;
import stroom.resource.ResourceStore;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
@TaskHandlerBean(task = DownloadQueryAction.class)
class DownloadQueryActionHandler extends AbstractTaskHandler<DownloadQueryAction, ResourceGeneration> {

    private transient static final Logger LOGGER = LoggerFactory.getLogger(DownloadQueryActionHandler.class);

    private static final Pattern NON_BASIC_CHARS = Pattern.compile("[^A-Za-z0-9-_ ]");
    private static final Pattern MULTIPLE_SPACE = Pattern.compile(" +");

    private final SearchRequestMapper searchRequestMapper;
    private final ResourceStore resourceStore;

    @Inject
    DownloadQueryActionHandler(final SearchRequestMapper searchRequestMapper,
                               final ResourceStore resourceStore) {
        this.searchRequestMapper = searchRequestMapper;
        this.resourceStore = resourceStore;
    }

    @Override
    public ResourceGeneration exec(final DownloadQueryAction action) {

        try {
            if (action.getSearchRequest() == null) {
                throw new EntityServiceException("Query is empty");
            }
            final SearchRequest searchRequest = action.getSearchRequest();

            //API users will typically want all data so ensure Fetch.ALL is set regardless of what it was before
            if (searchRequest != null && searchRequest.getComponentResultRequests() != null) {
                searchRequest.getComponentResultRequests()
                        .forEach((k, componentResultRequest) ->
                                componentResultRequest.setFetch(ResultRequest.Fetch.ALL));
            }

            //convert our internal model to the model used by the api
            stroom.query.api.v2.SearchRequest apiSearchRequest = searchRequestMapper.mapRequest(
                    action.getDashboardQueryKey(),
                    searchRequest);

            if (apiSearchRequest == null) {
                throw new EntityServiceException("Query could not be mapped to a SearchRequest");
            }

            //generate the export file
            String fileName = action.getDashboardQueryKey().toString();
            fileName = NON_BASIC_CHARS.matcher(fileName).replaceAll("");
            fileName = MULTIPLE_SPACE.matcher(fileName).replaceAll(" ");
            fileName = fileName + ".json";

            final ResourceKey resourceKey = resourceStore.createTempFile(fileName);
            final Path outputFile = resourceStore.getTempFile(resourceKey);

            JsonUtil.writeValue(outputFile, apiSearchRequest);

            return new ResourceGeneration(resourceKey, new ArrayList<>());
        } catch (final RuntimeException e) {
            throw EntityServiceExceptionUtil.create(e);
        }
    }
}
